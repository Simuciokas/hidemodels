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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/**
 * Hides chosen ModelEngine model pieces client-side, by their {@code item_model} id.
 *
 * <p>Every piece of a ModelEngine model is an {@code item_display} entity whose item carries an
 * {@code item_model} component, e.g. {@code modelengine:some_mount/head}. Listing those ids lets a
 * single bone be hidden ({@code some_mount/head}) or a whole model ({@code some_mount/}), and
 * leaves everything else alone - which matters, because scenery and interactive props are
 * item_displays too, and a blanket "remove nearby item_displays" approach eats them as well.
 *
 * <p>This mod does NOT remove entities. It cancels their rendering, so hitboxes, interactions and
 * the server's view of the world are untouched.
 *
 * <p>Config: {@code config/hidemodels.txt}, one id fragment per line, {@code #} for comments,
 * plus the directives {@code off}, {@code first-person-only} and {@code list-radius}. Re-read
 * automatically at most once a second when the file's timestamp changes, so edits apply without a
 * restart and without a keybind.
 *
 * <p>{@code /hidemodels list [radius]} reports the ids around you, which is how the config gets
 * filled in without unzipping a resource pack.
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

    /** Lower-cased id fragments; an entity is hidden when its item_model contains any of them. */
    private static volatile String[] patterns = new String[0];
    private static volatile boolean enabled = true;

    /** When set, hiding applies only while the camera is in first person. */
    private static volatile boolean firstPersonOnly;

    /** Default radius for /hidemodels list, in blocks. */
    private static volatile double listRadius = DEFAULT_LIST_RADIUS;

    /** Set while the current server has opted out. Cleared on disconnect, never persisted. */
    private static volatile boolean serverDisabled;

    private static long lastCheck;
    private static long lastModified = -1;

    private HideModels() {
    }

    /**
     * True when this item_model id should not be rendered. Called from the render hook for every
     * item_display in view, so it stays allocation-free on the hot path.
     */
    /**
     * The component type this reads, resolved on FIRST USE and then cached.
     *
     * LOOKED UP BY ID RATHER THAN NAMED. DataComponents.ITEM_MODEL is a static field that exists
     * only from 1.21.2, so naming it pins the source to that version and up; the registry has held
     * component types since 1.20.5, and asking for an id a version lacks simply returns null. One
     * source path therefore covers every version that has components at all - which is why
     * custom_model_data sits beside item_model here: where both exist the first wins, and where
     * only the second does, that is what there is to key on.
     *
     * The registry is ITERATED rather than queried by key, because a key is an Identifier - the one
     * type whose name changed mid-range - and constructing one would reintroduce exactly the
     * coupling this avoids.
     *
     * LAZY ON PURPOSE. Resolving this in a static initialiser would run it whenever the class
     * happens to load, and a client mixin can load during bootstrap while the registries are still
     * being populated: the lookup would find nothing, cache the null forever, and the mod would
     * silently do nothing for the whole session with no error to show for it. First use is during
     * rendering, by which point the registry is long since frozen and complete.
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
                if (want.equals(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)))) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * The model id on an item, or null when it carries none.
     *
     * <p>Both callers want the same thing and each used to read the component itself; one place now
     * owns which component that is, so the version handling lives in exactly one spot.
     */
    public static String modelIdOf(net.minecraft.world.item.ItemStack stack) {
        final DataComponentType<?> type = modelComponent();
        if (type == null || stack == null || stack.isEmpty()) {
            return null;
        }
        final Object value = stack.get(type);
        return (value == null) ? null : value.toString();
    }

    public static boolean hidden(String itemModelId) {
        if (serverDisabled) {
            return false;                 // checked first: the server's word beats the config
        }
        maybeReload();
        if (!enabled || itemModelId == null) {
            return false;
        }
        if (firstPersonOnly && !inFirstPerson()) {
            return false;
        }
        final String id = itemModelId.toLowerCase(Locale.ROOT);
        final String[] pats = patterns;
        for (int i = 0; i < pats.length; i++) {
            if (id.contains(pats[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does the hide list cover this id? Unlike {@link #hidden(String)} this ignores the camera, the
     * server opt-out and the off switch - it answers "is this in my list", which is what the
     * command's report needs in order to mark entries.
     */
    public static boolean listed(String itemModelId) {
        if (itemModelId == null) {
            return false;
        }
        final String id = itemModelId.toLowerCase(Locale.ROOT);
        final String[] pats = patterns;
        for (int i = 0; i < pats.length; i++) {
            if (id.contains(pats[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * The status line and the usage lines, printed by {@code /hidemodels} with no arguments.
     *
     * <p>Public because the command tree lives in the loader-specific source set now: Fabric
     * registers it through Fabric API and NeoForge through its own event, and both call into this.
     */
    public static void status() {
        NearbyModels.say(Component.literal("hidemodels " + version() + "- " + patterns.length
                + " pattern(s), " + (enabled ? "on" : "off")
                + (firstPersonOnly ? ", first person only" : "")
                + (serverDisabled ? ", DISABLED BY SERVER" : "")).withStyle(ChatFormatting.AQUA));
        NearbyModels.say(Component.literal("  /hidemodels list [radius]  - models nearby, grouped by model")
                .withStyle(ChatFormatting.GRAY));
        NearbyModels.say(Component.literal("  /hidemodels list bones [radius]  - individual bone ids")
                .withStyle(ChatFormatting.GRAY));
        NearbyModels.say(Component.literal("  /hidemodels add <id>  - hide it now (or click an id in the list)")
                .withStyle(ChatFormatting.GRAY));
        NearbyModels.say(Component.literal("  /hidemodels remove <id>  - stop hiding it")
                .withStyle(ChatFormatting.GRAY));
        NearbyModels.say(Component.literal("  /hidemodels on | off  - hiding without emptying the list")
                .withStyle(ChatFormatting.GRAY));
        NearbyModels.say(Component.literal("  /hidemodels first-person on | off  - hide only while "
                + "the camera is in first person").withStyle(ChatFormatting.GRAY));
        NearbyModels.say(Component.literal("  /hidemodels radius <blocks>  - default radius for list")
                .withStyle(ChatFormatting.GRAY));
        NearbyModels.say(Component.literal("  config/" + MOD_ID
                + ".txt holds the list and the directives (default radius " + listRadius + ")")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The configured default radius for {@code /hidemodels list}, in blocks. */
    public static double listRadius() {
        maybeReload();
        return listRadius;
    }

    /** The patterns currently loaded, for the remove command's suggestions. */
    public static String[] patterns() {
        maybeReload();
        return patterns.clone();
    }

    /**
     * Adds a pattern and applies it immediately.
     *
     * <p>Appends to the config rather than rewriting it: the file is hand-edited and full of
     * comments and directives explaining itself, and a round-trip through this class would flatten
     * all of that. A line is added at the end, where a person would have put it.
     */
    public static void add(String raw) {
        final String pattern = raw.trim().toLowerCase(Locale.ROOT);
        if (pattern.isEmpty() || pattern.startsWith("#")) {
            NearbyModels.say(Component.literal("hidemodels: '" + raw + "' is not an id")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        // Already covered is not the same as already present: a broader fragment may cover this id
        // without being equal to it, and adding the narrower one would be a no-op the user cannot
        // see. Say which line is doing the work instead.
        final String covering = coveringPattern(pattern);
        if (covering != null) {
            NearbyModels.say(Component.literal("hidemodels: already hidden by '" + covering + "'")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        try {
            if (!Files.isRegularFile(CONFIG)) {
                writeDefaults();
            }
            final String existing = Files.readString(CONFIG);
            final String sep = existing.isEmpty() || existing.endsWith("\n") ? "" : System.lineSeparator();
            Files.writeString(CONFIG, existing + sep + pattern + System.lineSeparator());
            reloadNow();
            NearbyModels.say(Component.literal("hidemodels: hiding '" + pattern + "' ("
                    + patterns.length + " pattern" + (patterns.length == 1 ? "" : "s") + ")")
                    .withStyle(ChatFormatting.GREEN));
        } catch (IOException e) {
            NearbyModels.say(Component.literal("hidemodels: could not write config/" + MOD_ID
                    + ".txt - " + e).withStyle(ChatFormatting.RED));
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
        final String pattern = raw.trim().toLowerCase(Locale.ROOT);
        if (pattern.isEmpty()) {
            NearbyModels.say(Component.literal("hidemodels: remove what?").withStyle(ChatFormatting.RED));
            return;
        }
        try {
            if (!Files.isRegularFile(CONFIG)) {
                NearbyModels.say(Component.literal("hidemodels: nothing is hidden yet")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
            final List<String> kept = new ArrayList<>();
            int removed = 0;
            for (String line : Files.readAllLines(CONFIG)) {
                if (line.trim().toLowerCase(Locale.ROOT).equals(pattern)) {
                    removed++;
                    continue;
                }
                kept.add(line);
            }
            if (removed == 0) {
                final String covering = coveringPattern(pattern);
                if (covering != null) {
                    NearbyModels.say(Component.literal("hidemodels: '" + pattern
                            + "' is not a line of its own - it is covered by '" + covering
                            + "', remove that instead").withStyle(ChatFormatting.YELLOW));
                } else {
                    NearbyModels.say(Component.literal("hidemodels: '" + pattern
                            + "' is not in the list").withStyle(ChatFormatting.YELLOW));
                }
                return;
            }
            Files.write(CONFIG, kept);
            reloadNow();
            NearbyModels.say(Component.literal("hidemodels: stopped hiding '" + pattern + "' ("
                    + patterns.length + " pattern" + (patterns.length == 1 ? "" : "s") + " left)")
                    .withStyle(ChatFormatting.GREEN));
        } catch (IOException e) {
            NearbyModels.say(Component.literal("hidemodels: could not write config/" + MOD_ID
                    + ".txt - " + e).withStyle(ChatFormatting.RED));
        }
    }

    /**
     * The three directives, as commands.
     *
     * <p>Each one edits the config the same way add and remove do - the file stays the thing that
     * is true, so a setting changed in game and a setting typed into the file cannot disagree, and
     * a command is never a second place state might live.
     *
     * <p>Written in the CANONICAL spelling even when the file used an alias: the parser accepts
     * "firstperson" and "first-person" as well, but a file this mod has written should read the way
     * the documentation does.
     */
    public static void setEnabled(boolean on) {
        directive(on ? null : "off", "off", "disabled");
        say("hiding " + (on ? "on" : "off"), on || patterns.length == 0);
    }

    public static void setFirstPersonOnly(boolean only) {
        directive(only ? "first-person-only" : null,
                  "first-person-only", "firstperson", "first-person");
        say("first person only: " + (only ? "on" : "off"), true);
    }

    public static void setListRadius(double blocks) {
        final double clamped = Math.min(Math.max(blocks, 1.0), MAX_LIST_RADIUS);
        directive("list-radius " + fmt(clamped), "list-radius");
        say("list radius: " + fmt(clamped) + " blocks", true);
    }

    /**
     * Rewrites one directive in the config: removes every spelling of it, then appends the new one.
     *
     * <p>Removal takes the aliases too, or setting something off would leave a line the parser
     * still honours - and the setting would appear to ignore the command.
     *
     * @param line     the directive to write, or null to only remove it
     * @param spellings every form the parser recognises, matched case-insensitively
     */
    private static void directive(String line, String... spellings) {
        try {
            if (!Files.isRegularFile(CONFIG)) {
                writeDefaults();
            }
            final List<String> kept = new ArrayList<>();
            for (String existing : Files.readAllLines(CONFIG)) {
                final String trimmed = existing.trim().toLowerCase(Locale.ROOT);
                boolean drop = false;
                for (String spelling : spellings) {
                    // "list-radius 32" is a prefix match; "off" must match the whole line, or a
                    // pattern containing the word would be eaten.
                    if (spelling.equals("list-radius") ? trimmed.startsWith(spelling)
                                                       : trimmed.equals(spelling)) {
                        drop = true;
                        break;
                    }
                }
                if (!drop) {
                    kept.add(existing);
                }
            }
            if (line != null) {
                kept.add(line);
            }
            Files.write(CONFIG, kept);
            reloadNow();
        } catch (IOException e) {
            NearbyModels.say(Component.literal("hidemodels: could not write config/" + MOD_ID
                    + ".txt - " + e).withStyle(ChatFormatting.RED));
        }
    }

    /** One decimal at most, so "32" does not print as "32.0" in a config line. */
    private static String fmt(double v) {
        final double rounded = Math.round(v * 10.0) / 10.0;
        return (rounded == Math.rint(rounded)) ? Long.toString((long) rounded)
                                               : Double.toString(rounded);
    }

    private static void say(String what, boolean good) {
        NearbyModels.say(Component.literal("hidemodels: " + what)
                .withStyle(good ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    /** The loaded pattern that already covers this id, or null. */
    private static String coveringPattern(String id) {
        final String[] pats = patterns;
        for (int i = 0; i < pats.length; i++) {
            if (id.contains(pats[i])) {
                return pats[i];
            }
        }
        return null;
    }

    /**
     * Re-reads the config at once, rather than waiting up to a second for the poll.
     *
     * <p>A command that edits the file should be believed immediately: the confirmation message
     * reports the new pattern count, and the next frame should already hide the thing.
     */
    private static void reloadNow() {
        try {
            lastModified = Files.isRegularFile(CONFIG)
                    ? Files.getLastModifiedTime(CONFIG).toMillis() : 0;
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
     * Version straight from the jar metadata, so the help line cannot drift from the build.
     *
     * <p>Asked of the loader through LoaderInfo, which has a copy per loader: this class knows
     * about Minecraft and nothing else, which is what lets the same file build for Fabric and for
     * NeoForge without a single conditional.
     */
    private static String version() {
        return LoaderInfo.modVersion();
    }

    /**
     * Whether the camera is in first person right now.
     *
     * <p>Read live rather than cached: the perspective key changes it between frames, and this is
     * already being called from the render thread, so there is nothing to synchronise against.
     * Defaults to true if the client is not far enough along to have options, so a model listed
     * for hiding never flashes into view during startup.
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

    /** Called on disconnect: an opt-out lasts for one connection only. */
    public static void clearServerOverride() {
        serverDisabled = false;
    }

    /** Whether the current server has opted out. */
    public static boolean isServerDisabled() {
        return serverDisabled;
    }

    /** Cheap timestamp poll rather than a watch service - this runs from the render thread. */
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
            final long mtime = Files.getLastModifiedTime(CONFIG).toMillis();
            if (mtime == lastModified) {
                return;
            }
            lastModified = mtime;
            load();
        } catch (IOException e) {
            // A broken config must never take the renderer down: keep the previous list.
        }
    }

    private static void load() throws IOException {
        final List<String> pats = new ArrayList<>();
        boolean on = true;
        boolean fp = false;
        double radius = DEFAULT_LIST_RADIUS;
        for (String raw : Files.readAllLines(CONFIG)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equalsIgnoreCase("off") || line.equalsIgnoreCase("disabled")) {
                on = false;
                continue;
            }
            // Directives are matched before patterns so they can never be read as an id fragment.
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
            pats.add(line.toLowerCase(Locale.ROOT));
        }
        patterns = pats.toArray(new String[0]);
        enabled = on;
        firstPersonOnly = fp;
        listRadius = radius;
        System.out.println("[" + MOD_ID + "] loaded " + patterns.length + " pattern(s), enabled=" + enabled
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
                # Every piece of a ModelEngine model is an item_display whose item carries an
                # item_model component. A trailing slash hides the whole model, no slash hides
                # one bone:
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
                # DIRECTIVES, each on a line of its own. Every one of them also has a command,
                # which edits THIS FILE and reloads it - so the two can never disagree:
                #     off                 disable without emptying the list   (/hidemodels off)
                #     first-person-only   hide only while the camera is in first person, so the
                #                         model reappears in third person (F5) - useful when you
                #                         want a mount out of your view but still want to see it
                #                                              (/hidemodels first-person on)
                #     list-radius 32      default radius for /hidemodels list (/hidemodels radius 32)
                #
                # IN GAME: /hidemodels list [radius] prints every model around you with its piece
                # count and distance, marking the ones this file already hides - so the ids can be
                # read off the screen instead of unzipping a resource pack. "list bones" prints
                # individual bone ids, for hiding one piece of a model.
                #
                # CLICK AN ID in that list to hide it, or type /hidemodels add <id>. Lines added
                # that way land at the end of this file, below whatever you have written here.
                # /hidemodels remove <id> takes one back out.
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
