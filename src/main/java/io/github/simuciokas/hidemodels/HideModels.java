/*
 * Copyright (C) 2026 Simuciokas
 *
 * This file is part of Hide Models.
 *
 * Hide Models is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License version 3 as published by the Free Software Foundation.
 *
 * Hide Models is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Hide Models.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.simuciokas.hidemodels;

import io.github.simuciokas.hidemodels.mixin.ItemDisplayAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Hides chosen ModelEngine model pieces client-side, by their {@code item_model} id.
 *
 * <p>A piece is an {@code item_display} or an armor stand whose item carries an
 * {@code item_model} component, e.g. {@code modelengine:some_mount/head}. A trailing slash hides a
 * whole model, no slash hides one bone.
 *
 * <p>Entities are never removed - only their rendering is cancelled, so hitboxes, interactions and
 * the server's view are untouched.
 *
 * <p>Config: {@code config/hidemodels.txt}, re-read within a second of a change.
 */
public final class HideModels {

    public static final String MOD_ID = "hidemodels";

    private static final Path CONFIG = Path.of("config", MOD_ID + ".txt");
    private static final long RELOAD_INTERVAL_MS = 1000L;

    private static final double DEFAULT_LIST_RADIUS = 32.0;
    private static final double MAX_LIST_RADIUS = 256.0;

    /** Plugin channels a server uses to turn this mod off, or back on, for its own players. */
    public static final String CHANNEL_DISABLE = MOD_ID + ":disable";
    public static final String CHANNEL_ENABLE = MOD_ID + ":enable";

    /**
     * Lower-cased id fragments; an entity is hidden when its item_model contains any of them. The
     * unsaved lines and every profile that is on, flattened once at load, so the render path never
     * sees profiles at all.
     */
    private static volatile String[] patterns = new String[0];
    /** The lines before the first profile heading, which always apply. */
    private static volatile List<String> unsaved = List.of();
    private static volatile List<Profile> profiles = List.of();

    /** A saved list of lines, applied while it is on. */
    public record Profile(String name, boolean on, List<String> patterns) {
    }
    private static volatile boolean enabled = true;

    /** When set, hiding applies only while the camera is in first person. */
    private static volatile boolean firstPersonOnly;

    /** How far the Nearby tab looks, in blocks. */
    private static volatile double listRadius = DEFAULT_LIST_RADIUS;

    /**
     * Where the screen's panel sits, as fractions of the room it has to move in: 0 0 is the
     * top left, 1 0 the top right. Fractions rather than pixels, so the spot survives a different
     * window size or GUI scale.
     */
    private static volatile double guiX;
    private static volatile double guiY;

    /** Set while the current server has opted out. Cleared on disconnect, never persisted. */
    private static volatile boolean serverDisabled;

    private static long lastCheck;
    private static long lastModified = -1;
    private static long lastSize = -1;

    private HideModels() {
    }

    /**
     * The component type this reads, resolved on FIRST USE and then cached.
     *
     * Looked up by id, not named: DataComponents.ITEM_MODEL exists only from 1.21.2, while the
     * registry has held component types since 1.20.5 and returns null for an id a version lacks -
     * which is why custom_model_data sits beside item_model.
     *
     * Iterated rather than queried by key, because a key is an Identifier, whose class name
     * differs across the range.
     *
     * Lazy, because a client mixin can load during bootstrap while the registries are still
     * filling; resolving then would cache a null for the whole session.
     */
    private static DataComponentType<?> modelComponent;
    private static boolean modelComponentResolved;

    private static DataComponentType<?> modelComponent() {
        if (!modelComponentResolved) {
            modelComponent = findComponent("minecraft:item_model", "minecraft:custom_model_data");
            modelComponentResolved = true;
        }
        return modelComponent;
    }

    private static DataComponentType<?> findComponent(String... ids) {
        for (final String want : ids) {
            for (final DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
                if (want.equals(registryId(BuiltInRegistries.DATA_COMPONENT_TYPE, type))) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * A registry key as a string, and the channel id of a custom payload.
     *
     * <p>Both return the type that is {@code ResourceLocation} up to 1.21.10 and {@code Identifier}
     * from 1.21.11. Neither caller wants the object, only its toString - but naming it in a
     * descriptor splits the NeoForge jar at that version, where official names are what run.
     *
     * <p>Candidates are checked against every supported version by tools/verify_targets.py.
     */
    // An entry with no owner is a member name only: the runtime takes the part after the '#' and
    // the owner would be a second intermediary name to keep correct for nothing.
    private static final String[] REGISTRY_GET_KEY = {
        "net.minecraft.core.Registry#getKey(java.lang.Object)",
        "#method_10221",                       // intermediary, 1.20.5 through 1.21.11
    };
    private static final String[] PAYLOAD_TYPE_ID = {
        "net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type#id()",
        "#comp_2242",                          // intermediary, a record component
    };

    private static java.lang.reflect.Method registryGetKey;
    private static java.lang.reflect.Method payloadTypeId;

    private static String registryId(Object registry, Object value) {
        if (registryGetKey == null) {
            registryGetKey = findMethod(registry.getClass(), REGISTRY_GET_KEY, Object.class);
        }
        return invokeToString(registryGetKey, registry, value);
    }

    /** The plugin channel a custom payload arrived on. Called from the packet mixin. */
    public static String channelId(Object payloadType) {
        if (payloadTypeId == null) {
            payloadTypeId = findMethod(payloadType.getClass(), PAYLOAD_TYPE_ID);
        }
        return invokeToString(payloadTypeId, payloadType);
    }

    private static java.lang.reflect.Method findMethod(Class<?> owner, String[] candidates,
                                                       Class<?>... params) {
        for (String candidate : candidates) {
            // The parameter list is for the verifier, which uses it to pin an overload;
            // reflection wants the bare name.
            String name = candidate.substring(candidate.indexOf('#') + 1);
            final int args = name.indexOf('(');
            if (args >= 0) {
                name = name.substring(0, args);
            }
            for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
                try {
                    return c.getMethod(name, params);
                } catch (NoSuchMethodException | RuntimeException ignored) {
                    // the other namespace, or declared further up
                }
            }
        }
        return null;
    }

    private static String invokeToString(java.lang.reflect.Method m, Object target, Object... args) {
        if (m == null) {
            return "";
        }
        try {
            return String.valueOf(m.invoke(target, args));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    public static String modelIdOf(net.minecraft.world.item.ItemStack stack) {
        final DataComponentType<?> type = modelComponent();
        if (type == null || stack == null || stack.isEmpty()) {
            return null;
        }
        final Object value = componentValue(stack, type);
        return (value == null) ? null : value.toString();
    }

    /**
     * {@code ItemStack.get(DataComponentType)}, by name because the name is what moves.
     *
     * <p>Its intermediary name is method_57824 up to 1.21.4 and method_58694 from 1.21.5, so a
     * direct call bakes one of them into the jar and splits an otherwise identical build in two.
     * The parameter type can be named - only the method cannot.
     *
     * <p>Resolved once. The candidates are checked against every supported version by
     * tools/verify_targets.py, which fails the build if a version calls it something else.
     */
    private static final String[] COMPONENT_GET = {
        "net.minecraft.world.item.ItemStack#get(net.minecraft.core.component.DataComponentType)",
        "net.minecraft.class_1799#method_57824",
        "net.minecraft.class_1799#method_58694",
    };

    private static java.lang.reflect.Method componentGet;
    private static boolean componentGetResolved;

    private static Object componentValue(net.minecraft.world.item.ItemStack stack,
                                         DataComponentType<?> type) {
        if (!componentGetResolved) {
            for (String candidate : COMPONENT_GET) {
                // The parameter list is for the verifier, which uses it to pin an overload;
            // reflection wants the bare name.
            String name = candidate.substring(candidate.indexOf('#') + 1);
            final int args = name.indexOf('(');
            if (args >= 0) {
                name = name.substring(0, args);
            }
                try {
                    componentGet = net.minecraft.world.item.ItemStack.class
                            .getMethod(name, DataComponentType.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // the other namespace, or the other side of the rename
                }
            }
            componentGetResolved = true;
        }
        if (componentGet == null) {
            return null;
        }
        try {
            return componentGet.invoke(stack, type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The model id an entity is showing, or null when it is not showing one.
     *
     * <p>Two shapes carry a model: an item_display, and an armor stand wearing the piece on its
     * head. Both put the same component on the same kind of ItemStack.
     *
     * <p>Named rather than overloading modelIdOf, because a null literal would pick between
     * ItemStack and Entity by guesswork and one caller passes exactly that.
     */
    public static String modelIdOfEntity(Entity entity) {
        if (entity instanceof Display.ItemDisplay display) {
            return modelIdOf(((ItemDisplayAccessor) display).hidemodels$getItemStack());
        }
        // Head only: the other slots hold what a stand is actually wearing.
        if (entity instanceof ArmorStand stand) {
            return modelIdOf(stand.getItemBySlot(EquipmentSlot.HEAD));
        }
        return null;
    }

    /**
     * Asked once per entity per frame, so the checks run cheapest first: a type test that rejects
     * almost everything, then the config, then the component read that allocates, then the scan.
     */
    public static boolean shouldHide(Entity entity) {
        if (!(entity instanceof Display.ItemDisplay) && !(entity instanceof ArmorStand)) {
            return false;
        }
        if (!couldHideAnything()) {
            return false;
        }
        final String id = modelIdOfEntity(entity);
        return id != null && matches(id);
    }

    private static boolean couldHideAnything() {
        if (serverDisabled) {
            return false;                 // checked first: the server's word beats the config
        }
        if (!enabled || patterns.length == 0) {
            return false;
        }
        return !firstPersonOnly || inFirstPerson();
    }

    /** Must be called once per client tick; each loader's entrypoint does it. */
    public static void tick() {
        maybeReload();
    }

    private static boolean matches(String itemModelId) {
        final String id = itemModelId.toLowerCase(Locale.ROOT);
        final String[] pats = patterns;
        for (int i = 0; i < pats.length; i++) {
            if (id.contains(pats[i])) {
                return true;
            }
        }
        return false;
    }

    public static boolean hidden(String itemModelId) {
        return itemModelId != null && couldHideAnything() && matches(itemModelId);
    }

    /**
     * Is this id in the list? Unlike {@link #hidden(String)} this ignores the camera, the server
     * opt-out and the off switch, so the report can mark entries whatever the current state.
     */
    public static boolean listed(String itemModelId) {
        if (itemModelId == null) {
            return false;
        }
        return matches(itemModelId);
    }

    /** The config line that hides this id, which may be broader than the id; null if none does. */
    public static String coveredBy(String itemModelId) {
        return itemModelId == null ? null : coveringPattern(itemModelId.toLowerCase(Locale.ROOT));
    }

    public static double listRadius() {
        maybeReload();
        return listRadius;
    }

    public static boolean isEnabled() {
        maybeReload();
        return enabled;
    }

    public static boolean isFirstPersonOnly() {
        maybeReload();
        return firstPersonOnly;
    }

    public static double guiX() {
        maybeReload();
        return guiX;
    }

    public static double guiY() {
        maybeReload();
        return guiY;
    }

    public static String[] patterns() {
        maybeReload();
        return patterns.clone();
    }

    public static List<String> unsaved() {
        maybeReload();
        return unsaved;
    }

    public static List<Profile> profiles() {
        maybeReload();
        return profiles;
    }

    /**
     * Which list hides this id: "" for the unsaved lines, else the name of a profile that is on;
     * null when nothing does. The unsaved lines are asked first, being where a click edits.
     */
    public static String hidingList(String itemModelId) {
        if (itemModelId == null) {
            return null;
        }
        final String id = itemModelId.toLowerCase(Locale.ROOT);
        for (String pattern : unsaved) {
            if (id.contains(pattern)) {
                return "";
            }
        }
        for (Profile profile : profiles) {
            if (profile.on()) {
                for (String pattern : profile.patterns()) {
                    if (id.contains(pattern)) {
                        return profile.name();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Adds a pattern to the unsaved lines and applies it immediately. Everything else in the file
     * - comments, profiles, their order - is left as it was.
     */
    public static void add(String raw) {
        final String pattern = raw.trim().toLowerCase(Locale.ROOT);
        if (pattern.isEmpty() || pattern.startsWith("#")) {
            NearbyModels.say(Component.literal("hidemodels: '" + raw + "' is not an id")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        // A broader fragment may already cover this id without being equal to it.
        final String covering = coveringPattern(pattern);
        if (covering != null) {
            NearbyModels.say(Component.literal("hidemodels: already hidden by '" + covering + "'")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (edit(text -> {
            ConfigText.append(text.top(), pattern);
            return true;
        })) {
            confirm("hiding '" + pattern + "' (" + patterns.length + " pattern"
                    + (patterns.length == 1 ? "" : "s") + ")", true);
        }
    }

    /**
     * Removes a pattern, matching the line exactly rather than by substring.
     *
     * <p>Substring removal would be a trap: removing {@code mount/head} while the file says
     * {@code mount/} would either delete the broader line - hiding far more than asked - or do
     * nothing while claiming success. So an exact line is removed, and a broader line that still
     * covers the id is reported rather than touched.
     */
    public static void remove(String raw) {
        remove(raw, false);
    }

    /**
     * Unhides a whole model: its own line, and the line of every one of its bones - the ones the
     * screen writes when single bones are hidden. A broader line that also covers it is left
     * alone and reported, as for a single id.
     */
    public static void removeModel(String model) {
        remove(model, true);
    }

    /** Only from the unsaved lines; a profile's lines change through the profile. */
    private static void remove(String raw, boolean withBones) {
        final String pattern = raw.trim().toLowerCase(Locale.ROOT);
        if (pattern.isEmpty()) {
            NearbyModels.say(Component.literal("hidemodels: remove what?").withStyle(ChatFormatting.RED));
            return;
        }
        final int[] removed = {0};
        if (!edit(text -> {
            removed[0] = ConfigText.removeIf(text.top(),
                    line -> line.equals(pattern) || (withBones && line.startsWith(pattern)));
            return removed[0] > 0;
        })) {
            return;
        }
        if (removed[0] == 0) {
            final String list = hidingList(pattern);
            final String covering = coveringPattern(pattern);
            if (list != null && !list.isEmpty()) {
                NearbyModels.say(Component.literal("hidemodels: '" + pattern + "' is in the profile '"
                        + list + "' - open it to change it").withStyle(ChatFormatting.YELLOW));
            } else if (covering != null) {
                NearbyModels.say(Component.literal("hidemodels: '" + pattern
                        + "' is not a line of its own - it is covered by '" + covering
                        + "', remove that instead").withStyle(ChatFormatting.YELLOW));
            } else {
                NearbyModels.say(Component.literal("hidemodels: '" + pattern
                        + "' is not in the list").withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        confirm("stopped hiding '" + pattern + "'"
                + (withBones && removed[0] > 1 ? " and its bones" : "") + " (" + patterns.length
                + " pattern" + (patterns.length == 1 ? "" : "s") + " left)", true);
    }

    /**
     * Moves the unsaved lines into a new profile, switched on so nothing changes on screen.
     *
     * @return the name it was given, or null when there was nothing unsaved to move
     */
    public static String saveUnsavedAsProfile() {
        final String name = freeProfileName();
        final int moved = moveUnsaved(text -> text.addProfile(name));
        if (moved == 0) {
            return null;
        }
        confirm("saved " + moved + " line" + (moved == 1 ? "" : "s") + " as the profile '" + name + "'",
                true);
        return name;
    }

    public static void addUnsavedToProfile(String name) {
        final int moved = moveUnsaved(text -> text.profile(name));
        if (moved > 0) {
            confirm("added " + moved + " line" + (moved == 1 ? "" : "s") + " to '" + name + "'", true);
        }
    }

    public static void setProfileOn(String name, boolean on) {
        final boolean[] changed = {false};
        edit(text -> {
            final ConfigText.Section s = text.profile(name);
            if (s == null || s.on == on) {
                return false;
            }
            s.on = on;
            changed[0] = true;
            return true;
        });
        if (changed[0]) {
            confirm("profile '" + name + "' " + (on ? "on" : "off"), true);
        }
    }

    /**
     * Renames a profile. Refused when the new name is empty, would break the heading - it cannot
     * hold a bracket - or belongs to another profile already.
     */
    public static boolean renameProfile(String from, String to) {
        final String name = to == null ? "" : to.trim();
        if (name.isEmpty() || name.contains("[") || name.contains("]")) {
            return false;
        }
        final boolean[] done = {false};
        edit(text -> {
            final ConfigText.Section s = text.profile(from);
            final ConfigText.Section clash = text.profile(name);
            if (s == null || (clash != null && clash != s) || s.name.equals(name)) {
                return false;
            }
            s.name = name;
            done[0] = true;
            return true;
        });
        return done[0];
    }

    /** The profile and every line in it. */
    public static void deleteProfile(String name) {
        final boolean[] gone = {false};
        edit(text -> gone[0] = text.sections.remove(text.profile(name)));
        if (gone[0]) {
            confirm("deleted the profile '" + name + "'", true);
        }
    }

    public static void addToProfile(String name, String pattern) {
        final String line = pattern.trim().toLowerCase(Locale.ROOT);
        edit(text -> {
            final ConfigText.Section s = text.profile(name);
            if (s == null || line.isEmpty()) {
                return false;
            }
            ConfigText.append(s, line);
            return true;
        });
    }

    public static void removeFromProfile(String name, String pattern) {
        final String line = pattern.trim().toLowerCase(Locale.ROOT);
        edit(text -> {
            final ConfigText.Section s = text.profile(name);
            return s != null && ConfigText.removeIf(s, l -> l.equals(line)) > 0;
        });
    }

    /** Moves every unsaved pattern line, leaving comments and directives where they are. */
    private static int moveUnsaved(java.util.function.Function<ConfigText, ConfigText.Section> target) {
        final int[] moved = {0};
        edit(text -> {
            final ConfigText.Section to = target.apply(text);
            if (to == null) {
                return false;
            }
            final List<String> keep = new ArrayList<>();
            for (String raw : text.top().lines) {
                if (isPattern(raw)) {
                    ConfigText.append(to, raw.trim().toLowerCase(Locale.ROOT));
                    moved[0]++;
                } else {
                    keep.add(raw);
                }
            }
            text.top().lines.clear();
            text.top().lines.addAll(keep);
            return moved[0] > 0;
        });
        return moved[0];
    }

    /** Profile 1, Profile 2, ... the first that is free. */
    private static String freeProfileName() {
        for (int n = 1; ; n++) {
            final String name = "Profile " + n;
            boolean taken = false;
            for (Profile p : profiles) {
                taken |= p.name().equalsIgnoreCase(name);
            }
            if (!taken) {
                return name;
            }
        }
    }

    /**
     * Reads the config as sections, applies a change, and writes it back only if the change says
     * it changed anything; then reloads, so the result shows on the next frame.
     */
    private static boolean edit(java.util.function.Predicate<ConfigText> change) {
        try {
            if (!Files.isRegularFile(CONFIG)) {
                writeDefaults();
            }
            final ConfigText text = ConfigText.parse(Files.readAllLines(CONFIG));
            if (change.test(text)) {
                Files.write(CONFIG, text.render());
                reloadNow();
            }
            return true;
        } catch (IOException e) {
            NearbyModels.say(Component.literal("hidemodels: could not write config/" + MOD_ID
                    + ".txt - " + e).withStyle(ChatFormatting.RED));
            return false;
        }
    }

    /**
     * The directives, as the Settings tab sets them.
     *
     * <p>Each edits the config, so a setting changed in game and one typed into the file cannot
     * disagree. Written in the canonical spelling even where the parser also accepts an alias.
     */
    public static void setEnabled(boolean on) {
        directive(on ? null : "off", "off", "disabled");
        confirm("hiding " + (on ? "on" : "off"), on || patterns.length == 0);
    }

    public static void setFirstPersonOnly(boolean only) {
        directive(only ? "first-person-only" : null,
                  "first-person-only", "firstperson", "first-person");
        confirm("first person only: " + (only ? "on" : "off"), true);
    }

    public static void setListRadius(double blocks) {
        final double clamped = Math.min(Math.max(blocks, 1.0), MAX_LIST_RADIUS);
        directive("list-radius " + fmt(clamped), "list-radius");
        confirm("list radius: " + fmt(clamped) + " blocks", true);
    }

    /** Each clamped to 0..1. The top left is the default, so placing it there removes the line. */
    public static void setGuiPosition(double x, double y) {
        final double cx = parseFraction(Double.toString(x), 0);
        final double cy = parseFraction(Double.toString(y), 0);
        final String at = fraction(cx) + " " + fraction(cy);
        directive(cx == 0 && cy == 0 ? null : "gui-position " + at, "gui-position");
        confirm("gui position: " + at, true);
    }

    /** Three decimals at most; one would make a placed panel jump by a tenth of the screen. */
    private static String fraction(double v) {
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("\\.?0+$", "");
    }

    /** A fraction from 0 to 1, clamped; anything unreadable gives the fallback. */
    private static double parseFraction(String raw, double fallback) {
        try {
            final double v = Double.parseDouble(raw.trim());
            return Double.isNaN(v) ? fallback : Math.min(Math.max(v, 0.0), 1.0);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Rewrites one directive in the config: removes every spelling of it, then appends the new one.
     *
     * <p>Removal takes the aliases too, or setting something off would leave a line the parser
     * still honours - and the setting would appear to ignore the click.
     *
     * @param line     the directive to write, or null to only remove it
     * @param spellings every form the parser recognises, matched case-insensitively
     */
    private static void directive(String line, String... spellings) {
        edit(text -> {
            // Directives apply to everything, so every section is cleared of them, and the new one
            // goes with the unsaved lines at the top.
            for (ConfigText.Section s : text.sections) {
                ConfigText.removeIf(s, trimmed -> {
                    for (String spelling : spellings) {
                        // A directive with a value ("list-radius 32") is a prefix match; "off"
                        // must match the whole line, or a pattern containing the word would go.
                        final boolean takesValue = spelling.equals("list-radius")
                                || spelling.equals("gui-position");
                        if (takesValue ? trimmed.startsWith(spelling) : trimmed.equals(spelling)) {
                            return true;
                        }
                    }
                    return false;
                });
            }
            if (line != null) {
                ConfigText.append(text.top(), line);
            }
            return true;
        });
    }

    /** One decimal at most, so "32" does not print as "32.0" in a config line. */
    private static String fmt(double v) {
        final double rounded = Math.round(v * 10.0) / 10.0;
        return (rounded == Math.rint(rounded)) ? Long.toString((long) rounded)
                                               : Double.toString(rounded);
    }

    /**
     * Whether a change also says so in chat. The screen shows every change it makes, so only a
     * debugging run does: -Dhidemodels.debug=true, or -Pdebug on the demo. Warnings always do,
     * since they explain a click that changed nothing.
     */
    public static boolean debug() {
        return Boolean.getBoolean(MOD_ID + ".debug");
    }

    private static void confirm(String what, boolean good) {
        if (debug()) {
            NearbyModels.say(Component.literal("hidemodels: " + what)
                    .withStyle(good ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
    }

    private static String coveringPattern(String id) {
        final String[] pats = patterns;
        for (int i = 0; i < pats.length; i++) {
            if (id.contains(pats[i])) {
                return pats[i];
            }
        }
        return null;
    }

    /** Re-reads the config at once, so a change from the screen shows on the next frame. */
    private static void reloadNow() {
        try {
            if (Files.isRegularFile(CONFIG)) {
                changedSinceRead();
            } else {
                lastModified = 0;
            }
            lastCheck = System.currentTimeMillis();
            load();
        } catch (IOException e) {
            // Same rule as the poll: a broken read keeps the previous list rather than emptying it.
        }
    }

    /** Parses a radius in blocks, clamped to what one scan can sensibly cover. 0 means invalid. */
    private static double parseRadius(String raw) {
        try {
            final double v = Double.parseDouble(raw.trim());
            if (!(v > 0)) {
                return 0;
            }
            return Math.min(v, MAX_LIST_RADIUS);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Whether the camera is in first person right now.
     *
     * <p>Read live rather than cached - the perspective key changes it between frames. Defaults
     * to true before the client has options, so a listed model never flashes into view at startup.
     */
    private static boolean inFirstPerson() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return true;
        }
        final CameraType view = mc.options.getCameraType();
        return view == null || view.isFirstPerson();
    }

    /**
     * A custom payload arrived. Only our two control channels mean anything; everything else the
     * server sends is ignored here and handled as usual.
     */
    public static void onServerChannel(String channel) {
        if (CHANNEL_DISABLE.equals(channel)) {
            if (!serverDisabled) {
                serverDisabled = true;
                System.out.println("[" + MOD_ID + "] this server has opted out - hiding is off for this session");
            }
        } else if (CHANNEL_ENABLE.equals(channel)) {
            if (serverDisabled) {
                serverDisabled = false;
                System.out.println("[" + MOD_ID + "] this server has re-enabled hiding");
            }
        }
    }

    public static void clearServerOverride() {
        serverDisabled = false;
    }

    public static boolean isServerDisabled() {
        return serverDisabled;
    }

    /** Cheap timestamp poll rather than a watch service - this runs once per client tick. */
    private static void maybeReload() {
        final long now = System.currentTimeMillis();
        if (now - lastCheck < RELOAD_INTERVAL_MS) {
            return;
        }
        lastCheck = now;
        try {
            if (!Files.isRegularFile(CONFIG)) {
                if (lastModified != 0) {
                    writeDefaults();
                    lastModified = 0;
                }
                return;
            }
            if (changedSinceRead()) {
                load();
            }
        } catch (IOException e) {
            // A broken config must never take the renderer down: keep the previous list.
        }
    }

    /**
     * Whether the config's modified time or size differs from when it was last read, taking the
     * new values as read. The size as well, because file times come from a clock that can hold
     * one value for ~16 ms: a rewrite that soon after the last one keeps the time, rarely the size.
     */
    private static boolean changedSinceRead() throws IOException {
        final BasicFileAttributes file = Files.readAttributes(CONFIG, BasicFileAttributes.class);
        final long mtime = file.lastModifiedTime().toMillis();
        if (mtime == lastModified && file.size() == lastSize) {
            return false;
        }
        lastModified = mtime;
        lastSize = file.size();
        return true;
    }

    /** A line naming models: not blank, not a comment, not a directive. */
    static boolean isPattern(String raw) {
        final String line = raw.trim().toLowerCase(Locale.ROOT);
        return !line.isEmpty() && !line.startsWith("#") && !line.equals("off")
                && !line.equals("disabled") && !line.equals("first-person-only")
                && !line.equals("firstperson") && !line.equals("first-person")
                && !line.startsWith("list-radius") && !line.startsWith("gui-position");
    }

    private static void load() throws IOException {
        final ConfigText text = ConfigText.parse(Files.readAllLines(CONFIG));
        final java.util.Set<String> effective = new java.util.LinkedHashSet<>();
        final List<String> loose = new ArrayList<>();
        final List<Profile> saved = new ArrayList<>();
        boolean on = true;
        boolean fp = false;
        double radius = DEFAULT_LIST_RADIUS;
        double gx = 0;
        double gy = 0;
        for (ConfigText.Section section : text.sections) {
            final List<String> pats = new ArrayList<>();
            for (String raw : section.lines) {
                final String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Directives apply to everything, whichever section they are written in.
                if (line.equalsIgnoreCase("off") || line.equalsIgnoreCase("disabled")) {
                    on = false;
                    continue;
                }
                // Before the pattern branch, so a directive is never read as an id fragment.
                if (line.equalsIgnoreCase("first-person-only") || line.equalsIgnoreCase("firstperson")
                        || line.equalsIgnoreCase("first-person")) {
                    fp = true;
                    continue;
                }
                if (line.toLowerCase(Locale.ROOT).startsWith("list-radius")) {
                    final double parsed = parseRadius(line.substring("list-radius".length()));
                    if (parsed > 0) {
                        radius = parsed;
                    }
                    continue;                   // a malformed value keeps the default, never a pattern
                }
                if (line.toLowerCase(Locale.ROOT).startsWith("gui-position")) {
                    final String[] xy = line.substring("gui-position".length()).trim().split("\\s+");
                    if (xy.length == 2) {
                        gx = parseFraction(xy[0], gx);
                        gy = parseFraction(xy[1], gy);
                    }
                    continue;
                }
                pats.add(line.toLowerCase(Locale.ROOT));
            }
            if (section.name == null) {
                loose.addAll(pats);
                effective.addAll(pats);
            } else {
                saved.add(new Profile(section.name, section.on, List.copyOf(pats)));
                if (section.on) {
                    effective.addAll(pats);
                }
            }
        }
        patterns = effective.toArray(new String[0]);
        unsaved = List.copyOf(loose);
        profiles = List.copyOf(saved);
        enabled = on;
        firstPersonOnly = fp;
        listRadius = radius;
        guiX = gx;
        guiY = gy;
        System.out.println("[" + MOD_ID + "] loaded " + patterns.length + " pattern(s), "
                + profiles.size() + " profile(s), enabled=" + enabled
                + ", firstPersonOnly=" + firstPersonOnly + ", listRadius=" + listRadius);
    }

    /**
     * The list ships EMPTY on purpose: which ids exist is entirely up to the server's resource
     * pack, so any preset would be wrong everywhere but one server. The comment block explains
     * how to find the real ones.
     */
    private static void writeDefaults() {
        final String text = """
                # hidemodels - one item_model id fragment per line; matched as a SUBSTRING.
                #
                # A model piece is an item_display entity - or an armor stand wearing the piece on
                # its head, which is how the older plugins and the legacy modes of the newer ones
                # render - whose item carries an item_model component. Either way the same line
                # here hides it. A trailing slash hides the whole model, no slash hides one bone:
                #     some_mount/         whole model
                #     some_mount/head     just the head, so you can see past it while riding
                #
                # FINDING IDS. An item_model id resolves to an item definition file inside the
                # server's resource pack:
                #     modelengine:some_mount/head
                #       -> assets/modelengine/items/some_mount/head.json
                # The client caches the pack it was sent under <gamedir>/downloads (the file has
                # no extension; downloads/log.json maps each name back to its original URL), so
                # unzipping that and listing assets/*/items/** gives every id the server can
                # ever show you.
                #
                # BE SPECIFIC. Scenery, props and interactive models are item_displays too - a
                # too-broad fragment will hide things you still want to see, such as teleporters
                # or signposts.
                #
                # DIRECTIVES, each on a line of its own. The screen's Settings tab writes these
                # same lines, so the screen and this file can never disagree:
                #     off                 disable without emptying the list
                #     first-person-only   hide only while the camera is in first person, so the
                #                         model reappears in third person (F5) - useful when you
                #                         want a mount out of your view but still want to see it
                #     list-radius 32      how far the screen's Nearby tab looks, in blocks
                #     gui-position 1 0    where the screen sits, as fractions of the free space:
                #                         0 0 top left, 1 0 top right, 1 1 bottom right
                #
                # PROFILES. A line in square brackets starts a profile: the lines under it, up to
                # the next one, are a saved list that applies while it is on. "off" after the
                # brackets keeps it saved but not applied. Lines above the first profile always
                # apply - the screen calls them unsaved.
                #     [Mounts]
                #     modelengine:some_mount/
                #     [PvP] off
                #     modelengine:wings/
                #
                # IN GAME: /hidemodels, or a key you bind under Hide Models in Controls, opens a
                # screen listing every model around you. Clicking one hides it, which adds its id
                # to the unsaved lines; the Hidden tab takes lines back out, saves them as a
                # profile, and switches profiles on and off.
                #
                # Saved changes apply within a second; no restart needed.
                """;
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, text);
            System.out.println("[" + MOD_ID + "] wrote default config to " + CONFIG.toAbsolutePath());
            load();
        } catch (IOException e) {
            System.out.println("[" + MOD_ID + "] could not write config: " + e);
        }
    }
}
