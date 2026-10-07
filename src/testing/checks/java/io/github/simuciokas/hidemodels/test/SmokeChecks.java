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
import java.nio.file.attribute.FileTime;
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
 * <p>WHAT IT CANNOT COVER. There is no world and no player, so the render hook, the screen, the
 * registered command and ChatOut are out of reach; the gametest covers those where it can run.
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
            checkAddAndRemove();
            checkSettings();
            checkProfiles();
            checkKeyRegistered();
            checkScreenOpens();
            report("PASS - component resolved, config drove the matcher, add/remove, settings and "
                    + "profiles worked, key registered, screen opened");
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
     * Adding and removing, as a click in the screen does it: writing the config and forcing the
     * reload, which is the part that works without a world.
     */
    private static void checkAddAndRemove() throws IOException, InterruptedException {
        final String id = "hidemodels:smoke_added";
        write("# cleared by the smoke test");
        waitUntil(() -> !HideModels.hidden(id), "the pattern list never started empty");

        HideModels.add(id);
        waitUntil(() -> HideModels.hidden(id),
                  "add() wrote nothing the matcher picked up");

        HideModels.remove(id);
        waitUntil(() -> !HideModels.hidden(id),
                  "remove() left the id hidden");
    }

    /**
     * The directives, driven the way the Settings tab drives them.
     *
     * <p>Each writes the config and reloads, and the observable effect is the point: off means
     * hidden() stops saying yes even though the pattern is still listed, and on brings it back.
     * That distinction - disabled versus empty - is the whole reason the directive exists.
     */
    private static void checkSettings() throws IOException, InterruptedException {
        final String id = "hidemodels:smoke_setting";
        write("# cleared by the smoke test");
        HideModels.add(id);
        waitUntil(() -> HideModels.hidden(id), "the id was not hidden before testing the switch");

        HideModels.setEnabled(false);
        waitUntil(() -> !HideModels.hidden(id), "turning hiding off did not stop it");
        if (!HideModels.listed(id)) {
            throw new AssertionError("off emptied the list - it should only stop it being applied");
        }

        HideModels.setEnabled(true);
        waitUntil(() -> HideModels.hidden(id), "turning hiding back on did not resume it");

        // These two have no effect visible without a camera or a world, so what is checked is that
        // they write something the parser reads back - a silent no-op is the likely failure.
        HideModels.setListRadius(48.0);
        waitUntil(() -> HideModels.listRadius() == 48.0,
                  "setting the radius did not change it");
        HideModels.setListRadius(32.0);

        HideModels.setFirstPersonOnly(true);
        HideModels.setFirstPersonOnly(false);

        HideModels.setGuiPosition(1, 0.5);
        waitUntil(() -> HideModels.guiX() == 1.0 && HideModels.guiY() == 0.5,
                  "a screen position of 1 0.5 did not stick");
        HideModels.setGuiPosition(0, 0);
        HideModels.remove(id);
    }

    /**
     * Profiles, through the calls the screen makes: the unsaved lines saved as one, which then
     * hides only while it is on; more added to it; renamed; deleted with its lines. A comment and
     * a profile nobody touched must come through all of it as they were.
     *
     * <p>The file is written with the modified time it already had, as a rewrite within one tick
     * of the file system's clock leaves it - here, straight after the mod's own last write.
     */
    private static void checkProfiles() throws IOException, InterruptedException {
        final String a = "hidemodels:profile_a";
        final String b = "hidemodels:profile_b";
        final FileTime before = Files.getLastModifiedTime(CONFIG);
        write("# kept by the smoke test" + System.lineSeparator()
                + "[Untouched] off" + System.lineSeparator() + "hidemodels:untouched");
        Files.setLastModifiedTime(CONFIG, before);
        waitUntil(() -> HideModels.profiles().size() == 1,
                  "a profile heading was not read from a rewrite that kept its modified time");
        if (HideModels.hidden("hidemodels:untouched")) {
            throw new AssertionError("a profile that is off still hides its lines");
        }

        HideModels.add(a);
        waitUntil(() -> HideModels.hidden(a), "an unsaved line did not hide");
        final String name = HideModels.saveUnsavedAsProfile();
        if (name == null || !HideModels.unsaved().isEmpty()) {
            throw new AssertionError("saving left lines unsaved, or made no profile");
        }
        waitUntil(() -> name.equals(HideModels.hidingList(a)),
                  "a line saved into a profile that is on does not hide through it");

        HideModels.setProfileOn(name, false);
        waitUntil(() -> !HideModels.hidden(a), "switching the profile off did not show its line");
        HideModels.setProfileOn(name, true);
        waitUntil(() -> HideModels.hidden(a), "switching it back on did not hide it again");

        HideModels.add(b);
        HideModels.addUnsavedToProfile(name);
        waitUntil(() -> HideModels.unsaved().isEmpty() && name.equals(HideModels.hidingList(b)),
                  "adding the unsaved lines to the profile did not move them into it");

        if (!HideModels.renameProfile(name, "Smoke")) {
            throw new AssertionError("renaming the profile was refused");
        }
        waitUntil(() -> "Smoke".equals(HideModels.hidingList(a)), "the rename did not stick");

        HideModels.deleteProfile("Smoke");
        waitUntil(() -> HideModels.profiles().size() == 1 && !HideModels.hidden(a)
                        && !HideModels.hidden(b), "deleting the profile left it, or its lines");

        final String file = Files.readString(CONFIG);
        if (!file.contains("# kept by the smoke test") || !file.contains("[Untouched] off")
                || !file.contains("hidemodels:untouched")) {
            throw new AssertionError("editing profiles disturbed the rest of the file:"
                    + System.lineSeparator() + file);
        }
    }

    /**
     * The open key made it into Controls. Keys.create() returns null rather than throwing when
     * neither constructor shape matches, so without this a version that moved it would just lose
     * the key.
     */
    private static void checkKeyRegistered() {
        for (net.minecraft.client.KeyMapping key : Minecraft.getInstance().options.keyMappings) {
            if (io.github.simuciokas.hidemodels.Keys.OPEN.equals(key.getName())) {
                // A section of its own: a string up to 1.21.8, a Category after, both naming us.
                if (!String.valueOf(key.getCategory()).contains(HideModels.MOD_ID)) {
                    throw new AssertionError("the open key is in " + key.getCategory()
                            + " rather than a section of its own");
                }
                // Controls sorts every key by category, which is where an unknown one can throw.
                java.util.Arrays.sort(Minecraft.getInstance().options.keyMappings.clone());
                return;
            }
        }
        throw new AssertionError("the open key is not in Controls - it never registered");
    }

    /**
     * The screen opens, draws and closes again - the first thing every user does, and on NeoForge
     * the only run it gets. With no world it lists nothing; a second of frames is the check.
     */
    private static void checkScreenOpens() throws InterruptedException {
        final Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> io.github.simuciokas.hidemodels.Screens.open(mc,
                new io.github.simuciokas.hidemodels.HiddenListScreen(
                        (net.minecraft.client.gui.screens.Screen) currentScreen(mc))));
        waitUntil(() -> currentScreen(mc) instanceof io.github.simuciokas.hidemodels.HiddenListScreen,
                  "the screen never opened");
        Thread.sleep(1000L);
        mc.execute(() -> ((net.minecraft.client.gui.screens.Screen) currentScreen(mc)).onClose());
        waitUntil(() -> !(currentScreen(mc) instanceof io.github.simuciokas.hidemodels.HiddenListScreen),
                  "the screen did not close");
    }

    /** mc.gui.screen() from 26.1, mc.screen before; by name, since these checks run in development. */
    private static Object currentScreen(Minecraft mc) {
        try {
            final Object gui = mc.getClass().getField("gui").get(mc);
            return gui.getClass().getMethod("screen").invoke(gui);
        } catch (ReflectiveOperationException e) {
            try {
                return mc.getClass().getField("screen").get(mc);
            } catch (ReflectiveOperationException e2) {
                return null;
            }
        }
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
