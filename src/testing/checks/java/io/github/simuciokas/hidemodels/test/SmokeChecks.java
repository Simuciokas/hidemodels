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
package io.github.simuciokas.hidemodels.test;

import io.github.simuciokas.hidemodels.HideModels;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * The startup checks, shared by both loaders' harnesses.
 *
 * <p>Fabric's client gametest API exists only from 1.21.4, so on 1.21.3 and earlier this is the
 * only automated proof that a line of the mod runs. It also covers the NeoForge jar, which has no
 * Fabric harness.
 *
 * <p>IT NAMES NO LOADER. Everything here is Minecraft and the mod's own code, which is what lets
 * one copy serve a ClientModInitializer on Fabric and a @Mod constructor on NeoForge. It drives
 * itself from a watcher thread rather than a tick event for the same reason.
 *
 * <p>WHAT IT CANNOT COVER. There is no world and no player, so the render hook, the registered
 * command and ChatOut are out of reach; the gametest covers those where it can run.
 */
public final class SmokeChecks {

    public static final String ENABLE_PROPERTY = "hidemodels.smoketest";

    private static final String TEST_MODEL = "hidemodels:smoke_model";
    private static final Path CONFIG = Path.of("config", "hidemodels.txt");
    /** Read by the build. Relative to the run directory, which is the game's working dir. */
    private static final Path RESULT = Path.of("smoketest-result.txt");
    private static final long TIMEOUT_MS = 180_000L;

    private SmokeChecks() {
    }

    /**
     * Starts the checks on a watcher thread, or does nothing unless the property is set.
     *
     * <p>Called by whichever loader's entrypoint got there first - a ClientModInitializer on
     * Fabric, a @Mod constructor on NeoForge. Nothing below this line knows or cares which.
     */
    public static void start() {
        if (System.getProperty(ENABLE_PROPERTY) == null) {
            return;
        }
        final Thread watcher = new Thread(SmokeChecks::run, "hidemodels-smoketest");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void run() {
        try {
            waitForClient();
            checkComponentResolves();
            checkConfigDrivesMatcher();
            checkCommandsEditTheList();
            checkSettingsCommands();
            checkKeyRegistered();
            report("PASS - component resolved, config drove the matcher, add/remove and settings "
                    + "worked, key registered");
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            report("FAIL - " + t);
        }
    }

    /**
     * Up and rendering, which is when the registries are populated and frozen.
     *
     * <p>Judged by the frame counter, not by what is on screen: the screen accessor differs
     * across the range ({@code mc.screen} on 1.21.x, {@code mc.gui.screen()} on 26.x) while
     * isRunning() and getFps() do not.
     *
     * <p>It can therefore pass during the loading overlay rather than at the title screen, which
     * costs nothing - no assertion below touches game state.
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
     * <p>Calls add/remove directly rather than through the command, because a client command
     * needs a connection and there is no server here. What it covers is what they do: writing the
     * config and forcing the reload.
     */
    private static void checkCommandsEditTheList() throws IOException, InterruptedException {
        final String id = "hidemodels:smoke_command";
        write("# cleared by the smoke test");
        waitUntil(() -> !HideModels.hidden(id), "the pattern list never started empty");

        HideModels.add(id);
        waitUntil(() -> HideModels.hidden(id),
                  "/hidemodels add wrote nothing the matcher picked up");

        HideModels.remove(id);
        waitUntil(() -> !HideModels.hidden(id),
                  "/hidemodels remove left the id hidden");
    }

    /**
     * The directives, driven the way the commands drive them.
     *
     * <p>Each writes the config and reloads, and the observable effect is the point: off means
     * hidden() stops saying yes even though the pattern is still listed, and on brings it back.
     * That distinction - disabled versus empty - is the whole reason the directive exists.
     */
    private static void checkSettingsCommands() throws IOException, InterruptedException {
        final String id = "hidemodels:smoke_setting";
        write("# cleared by the smoke test");
        HideModels.add(id);
        waitUntil(() -> HideModels.hidden(id), "the id was not hidden before testing the switch");

        HideModels.setEnabled(false);
        waitUntil(() -> !HideModels.hidden(id), "/hidemodels off did not stop the hiding");
        if (!HideModels.listed(id)) {
            throw new AssertionError("off emptied the list - it should only stop it being applied");
        }

        HideModels.setEnabled(true);
        waitUntil(() -> HideModels.hidden(id), "/hidemodels on did not resume the hiding");

        // These two have no effect visible without a camera or a world, so what is checked is that
        // they write something the parser reads back - a silent no-op is the likely failure.
        HideModels.setListRadius(48.0);
        waitUntil(() -> HideModels.listRadius() == 48.0,
                  "/hidemodels radius did not change the configured radius");
        HideModels.setListRadius(32.0);

        HideModels.setFirstPersonOnly(true);
        HideModels.setFirstPersonOnly(false);

        HideModels.setGuiOnRight(true);
        waitUntil(HideModels::isGuiOnRight, "/hidemodels gui-side right did not stick");
        HideModels.setGuiOnRight(false);
        HideModels.remove(id);
    }

    /**
     * The open key made it into Controls. Keys.create() returns null rather than throwing when
     * neither constructor shape matches, so without this a version that moved it would just lose
     * the key.
     */
    private static void checkKeyRegistered() {
        for (net.minecraft.client.KeyMapping key : Minecraft.getInstance().options.keyMappings) {
            if (io.github.simuciokas.hidemodels.Keys.OPEN.equals(key.getName())) {
                return;
            }
        }
        throw new AssertionError("the open key is not in Controls - it never registered");
    }

    /**
     * Waits for the client to catch up with a config the test just wrote.
     *
     * <p>The reload is driven by the client tick now, not by these calls - so this really is
     * waiting, and a client that stopped ticking would time out here rather than hang forever.
     */
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
     * <p>Both obvious exits are wrong: halt() from this thread while the render thread is in a GL
     * call makes Windows fast-fail the JVM with 0xC0000409, and System.exit() trips the same
     * through Minecraft's GLFW shutdown hooks. Either way Gradle sees a crashed process and reports
     * FAILED however well the test went, so the verdict goes where the exit cannot corrupt it.
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
