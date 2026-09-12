package io.github.simuciokas.hidemodels.test;

import io.github.simuciokas.hidemodels.HideModels;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

/**
 * A self-contained startup check that needs NOTHING but Fabric Loader.
 *
 * <p>WHY THIS EXISTS ALONGSIDE THE GAMETEST. Fabric's client gametest API covers far more, but it
 * was only added in 1.21.4 - so on 1.21.3 and earlier, six of the eighteen supported versions,
 * there is no harness to run and this is the only automated proof that a line of the mod runs.
 * It also carries the three versions that have a harness but cannot load a world on a CI runner,
 * 1.21.9 through 1.21.11.
 * (26.x DOES have one, contrary to an earlier note here: the module is published for it, and
 * gradle/run26.gradle launches it by hand. The thing 26.x lacks is Loom, not the harness.)
 *
 * <p>So this deliberately depends on nothing beyond {@code ClientModInitializer}, which every
 * version has, and drives itself from a watcher thread rather than a tick event - a tick event
 * would mean Fabric API or a second mixin, and the whole point is to need neither.
 *
 * <p>WHAT IT CANNOT COVER. There is no world and no player, so the render hook, the command mixin
 * and ChatOut are all out of reach; the gametest covers those where it can run. What this does
 * cover is the one thing most likely to break when a version moves underneath the mod: whether the
 * item_model component can still be resolved from the registry at all, and whether the config
 * still drives the matcher.
 *
 * <p>Gated on a system property so an ordinary {@code runClient} is never hijacked by it.
 */
public final class HideModelsSmokeTest implements ClientModInitializer {

    public static final String ENABLE_PROPERTY = "hidemodels.smoketest";

    private static final String TEST_MODEL = "hidemodels:smoke_model";
    private static final Path CONFIG = Path.of("config", "hidemodels.txt");
    /** Read by the build. Relative to the run directory, which is the game's working dir. */
    private static final Path RESULT = Path.of("smoketest-result.txt");
    private static final long TIMEOUT_MS = 180_000L;

    @Override
    public void onInitializeClient() {
        if (System.getProperty(ENABLE_PROPERTY) == null) {
            return;
        }
        final Thread watcher = new Thread(HideModelsSmokeTest::run, "hidemodels-smoketest");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void run() {
        try {
            waitForClient();
            checkComponentResolves();
            checkConfigDrivesMatcher();
            checkCommandsEditTheList();
            report("PASS - component resolved, the config drove the matcher, add/remove worked");
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            report("FAIL - " + t);
        }
    }

    /**
     * Up and rendering, which is when the registries are populated and frozen.
     *
     * <p>Judged by the frame counter rather than by what is on screen, because this one class has
     * to compile on every supported version and the screen is exactly what moved: 1.21.x reads
     * {@code mc.screen} and 26.x reads {@code mc.gui.screen()}. isRunning() and getFps() are on
     * Minecraft itself across the whole range, and a non-zero fps says more than a field would
     * anyway - it means the client survived startup and is actually drawing frames.
     *
     * <p>It does mean the check can pass during the loading overlay rather than at the title
     * screen. That costs nothing here: neither assertion below touches the game's state, and a
     * client that dies mid-reload still fails the build, because the verdict file never appears.
     */
    private static void waitForClient() throws InterruptedException {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.isRunning() && mc.getFps() > 0) {
                Thread.sleep(500L);      // a beat for the first frames
                return;
            }
            Thread.sleep(250L);
        }
        throw new AssertionError("the client never started rendering within " + TIMEOUT_MS + "ms");
    }

    /**
     * The registry lookup found a component type on this version.
     *
     * <p>Asserted through the private field rather than a test-only accessor on HideModels: the
     * production class should not grow API for the benefit of a test, and this is the mod's own
     * package. modelIdOf() is called first because the resolution is deliberately lazy.
     */
    private static void checkComponentResolves() throws Exception {
        HideModels.modelIdOf(null);                     // triggers the lazy lookup, returns null
        final Field field = HideModels.class.getDeclaredField("modelComponent");
        field.setAccessible(true);
        if (field.get(null) == null) {
            throw new AssertionError(
                    "no item_model/custom_model_data component type could be resolved from the "
                            + "registry - the mod would silently hide nothing on this version");
        }
    }

    private static void checkConfigDrivesMatcher() throws IOException, InterruptedException {
        write("# cleared by the smoke test");
        waitUntil(() -> !HideModels.hidden(TEST_MODEL), "the pattern list never started empty");
        write(TEST_MODEL);
        waitUntil(() -> HideModels.hidden(TEST_MODEL),
                  "the pattern was written to the config but hidden() never returned true");
        if (HideModels.hidden("hidemodels:not_listed")) {
            throw new AssertionError("hidden() matched an id that was never listed");
        }
    }

    /**
     * The add and remove commands, which are the only part of them that works without a world.
     *
     * <p>Calls the handler directly rather than through the connection, because there is no server
     * to have a connection to. That skips the mixin - the gametest covers interception where it can
     * run - but it does cover everything the handler itself does: parsing, writing the config, and
     * forcing the reload. Below 1.21.4, where no harness exists, this is the only automated proof
     * that the commands do anything at all, and the file-writing half is the half most likely to
     * break on a machine or a version nobody tried.
     */
    private static void checkCommandsEditTheList() throws IOException, InterruptedException {
        final String id = "hidemodels:smoke_command";
        write("# cleared by the smoke test");
        waitUntil(() -> !HideModels.hidden(id), "the pattern list never started empty");

        HideModels.handleCommand(HideModels.MOD_ID + " add " + id);
        waitUntil(() -> HideModels.hidden(id),
                  "/hidemodels add wrote nothing the matcher picked up");

        HideModels.handleCommand(HideModels.MOD_ID + " remove " + id);
        waitUntil(() -> !HideModels.hidden(id),
                  "/hidemodels remove left the id hidden");
    }

    /** Polling is not just waiting here: every hidden() call is what drives the reload check. */
    private static void waitUntil(java.util.function.BooleanSupplier check, String failure)
            throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            if (check.getAsBoolean()) {
                return;
            }
            Thread.sleep(200L);
        }
        throw new AssertionError(failure);
    }

    private static void write(String body) throws IOException {
        Files.createDirectories(CONFIG.getParent());
        Files.writeString(CONFIG, body + System.lineSeparator());
    }

    private static void say(String message) {
        System.out.println("[hidemodels-smoketest] " + message);
        System.out.flush();
    }

    /**
     * Report through a FILE, not an exit code, and then stop caring how the process dies.
     *
     * <p>Both obvious exits are wrong here. halt() from this thread while the render thread is
     * inside a GL call makes Windows fast-fail the JVM with 0xC0000409; System.exit() is no better,
     * because Minecraft's shutdown hooks call into GLFW off the main thread and trip the same
     * thing. Either way Gradle sees a crashed process and reports FAILED however well the test
     * went - and a pass that reads as a failure would block every green build.
     *
     * <p>So the verdict goes somewhere the process cannot corrupt on its way out, and the build
     * reads it from there. How the JVM ends stops mattering, which is the only way to make this
     * reliable across ten versions of a program that was never built to be scripted.
     */
    private static void report(String verdict) {
        try {
            Files.writeString(RESULT, verdict + System.lineSeparator());
        } catch (IOException e) {
            say("could not write the result file: " + e);
        }
        say(verdict);
        Runtime.getRuntime().halt(verdict.startsWith("PASS") ? 0 : 1);
    }
}
