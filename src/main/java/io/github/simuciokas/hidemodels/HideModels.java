package io.github.simuciokas.hidemodels;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * <p>Config: {@code config/hidemodels.txt}, one id fragment per line, {@code #} for comments.
 * Re-read automatically at most once a second when the file's timestamp changes, so edits apply
 * without a restart and without a command or keybind.
 */
public final class HideModels {

    public static final String MOD_ID = "hidemodels";

    private static final Path CONFIG = Path.of("config", MOD_ID + ".txt");
    private static final long RELOAD_INTERVAL_MS = 1000L;

    /** Plugin channels a server uses to turn this mod off, or back on, for its own players. */
    public static final String CHANNEL_DISABLE = MOD_ID + ":disable";
    public static final String CHANNEL_ENABLE = MOD_ID + ":enable";

    /** Lower-cased id fragments; an entity is hidden when its item_model contains any of them. */
    private static volatile String[] patterns = new String[0];
    private static volatile boolean enabled = true;

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
        for (String raw : Files.readAllLines(CONFIG)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equalsIgnoreCase("off") || line.equalsIgnoreCase("disabled")) {
                on = false;
                continue;
            }
            pats.add(line.toLowerCase(Locale.ROOT));
        }
        patterns = pats.toArray(new String[0]);
        enabled = on;
        System.out.println("[" + MOD_ID + "] loaded " + patterns.length + " pattern(s), enabled=" + enabled);
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
                # Put "off" on a line by itself to disable without emptying the list.
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
