package io.github.simuciokas.hidemodels;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
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
     * Handles {@code /hidemodels ...}, returning true when the command was ours and so must not be
     * sent on to the server. Called from the client's command-send path.
     */
    public static boolean handleCommand(String command) {
        if (command == null) {
            return false;
        }
        final String line = command.trim();
        final String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.equals(MOD_ID) && !lower.startsWith(MOD_ID + " ")) {
            return false;
        }
        maybeReload();                       // so the report reflects a config saved a moment ago
        final String rest = line.length() > MOD_ID.length()
                ? line.substring(MOD_ID.length()).trim() : "";
        final String[] arg = rest.isEmpty() ? new String[0] : rest.split("\\s+");

        if (arg.length == 0 || arg[0].equalsIgnoreCase("help")) {
            NearbyModels.say(Component.literal("hidemodels " + version() + "- " + patterns.length
                    + " pattern(s), " + (enabled ? "on" : "off")
                    + (firstPersonOnly ? ", first person only" : "")
                    + (serverDisabled ? ", DISABLED BY SERVER" : "")).withStyle(ChatFormatting.AQUA));
            NearbyModels.say(Component.literal("  /hidemodels list [radius]  - models nearby, grouped by model")
                    .withStyle(ChatFormatting.GRAY));
            NearbyModels.say(Component.literal("  /hidemodels list bones [radius]  - individual bone ids")
                    .withStyle(ChatFormatting.GRAY));
            NearbyModels.say(Component.literal("  edit config/" + MOD_ID
                    + ".txt to change what is hidden (default radius " + listRadius + ")")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return true;
        }
        if (arg[0].equalsIgnoreCase("list")) {
            boolean bones = false;
            int at = 1;
            if (arg.length > at && arg[at].equalsIgnoreCase("bones")) {
                bones = true;
                at++;
            }
            double radius = listRadius;
            if (arg.length > at) {
                final double parsed = parseRadius(arg[at]);
                if (parsed <= 0) {
                    NearbyModels.say(Component.literal("hidemodels: '" + arg[at]
                            + "' is not a radius in blocks").withStyle(ChatFormatting.RED));
                    return true;
                }
                radius = parsed;
            }
            NearbyModels.report(radius, bones);
            return true;
        }
        NearbyModels.say(Component.literal("hidemodels: unknown subcommand '" + arg[0]
                + "' - try /hidemodels help").withStyle(ChatFormatting.RED));
        return true;
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

    /** Version straight from the jar metadata, so the help line cannot drift from the build. */
    private static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString() + " ")
                .orElse("");
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
                # DIRECTIVES, each on a line of its own:
                #     off                 disable without emptying the list
                #     first-person-only   hide only while the camera is in first person, so the
                #                         model reappears in third person (F5) - useful when you
                #                         want a mount out of your view but still want to see it
                #     list-radius 32      default radius for /hidemodels list
                #
                # IN GAME: /hidemodels list [radius] prints every model around you with its piece
                # count and distance, marking the ones this file already hides - so the ids can be
                # read off the screen instead of unzipping a resource pack. "list bones" prints
                # individual bone ids, for hiding one piece of a model.
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
