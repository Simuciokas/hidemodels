package io.github.simuciokas.hidemodels.test;

import io.github.simuciokas.hidemodels.HideModels;
import io.github.simuciokas.hidemodels.mixin.ItemDisplayAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Display;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.Entity;

/**
 * Drives a real client and checks the mod actually works, rather than merely loading.
 *
 * <p>WHY THIS EXISTS. Everything else about this mod is verified statically: it compiles, its mixin
 * targets resolve against each version's client jar, and the jar loads to the main menu. None of
 * that exercises a single line of the mod's own logic, and the two things most likely to break
 * across versions are exactly the lines a compiler cannot judge - the component looked up by id
 * from the registry, and the command intercepted on its way to the server.
 *
 * <p>NO ModelEngine AND NO SERVER NEEDED. The mod keys on a vanilla component carried by a vanilla
 * entity, so a summoned item_display reproduces the real condition exactly. That is what makes this
 * runnable on any version, in CI, without anything installed.
 */
public final class HideModelsClientTest implements FabricClientGameTest {

    private static final String TEST_MODEL = "hidemodels:test_model";
    private static final Path CONFIG = Path.of("config", "hidemodels.txt");

    /**
     * The run directory SURVIVES BETWEEN RUNS, including across Minecraft versions - so a config
     * left by an earlier run would already contain the pattern, and the test would pass without
     * the write under test doing anything at all. Every run therefore starts by clearing it and
     * asserting the id is NOT hidden, which makes the transition the thing being tested rather
     * than the end state.
     */
    private static void writeConfig(String body) {
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, body + System.lineSeparator());
        } catch (IOException e) {
            throw new AssertionError("could not write " + CONFIG, e);
        }
    }

    /**
     * Is this the method a clicked run_command ends up calling?
     *
     * <p>BY SIGNATURE, NOT BY NAME, because this test runs in two different worlds. Under Loom the
     * runtime keeps official names and {@code sendUnattendedCommand} is findable; under the
     * standalone launcher the client is the real obfuscated jar with intermediary-named mods, where
     * the same method answers to something like {@code method_54650}. A name check silently found
     * nothing there and reported the path as absent on a version that has it - which is exactly the
     * false pass this section exists to prevent.
     *
     * <p>{@code void (String, Screen)} is specific enough to be unambiguous on every version this
     * runs against - it is the only such method on the connection. If a future version adds a
     * second one, this would need the intermediary name per version rather than a signature.
     */
    private static boolean isUnattendedCommandSend(Method m) {
        if (m.getParameterCount() != 2 || m.getReturnType() != void.class) {
            return false;
        }
        final Class<?>[] params = m.getParameterTypes();
        if (params[0] != String.class || !Screen.class.isAssignableFrom(params[1])) {
            return false;
        }
        return true;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        // Before the world, so the very first hidden() call already sees an empty list.
        writeConfig("# cleared by the client gametest");

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            // NO waitForChunksRender() HERE, deliberately. Nothing below looks at terrain - the
            // entity is read out of the client level and the rest is the mod's own state - and that
            // call is the only step in this test that needs FRAMES rather than ticks. On a machine
            // with no GPU the frame rate is capped precisely so the render thread stops starving
            // the integrated server, which makes waiting on chunk rebuilds both unnecessary and the
            // first thing to time out.

            // The id must start out NOT hidden, or nothing below proves the write mattered.
            context.waitFor(client -> !HideModels.hidden(TEST_MODEL));

            // The same shape ModelEngine produces: an item_display whose item carries item_model.
            singleplayer.getServer().runCommand(
                    "summon item_display ~ ~1 ~ {item:{id:\"stone\",components:"
                            + "{\"minecraft:item_model\":\"" + TEST_MODEL + "\"}}}");
            context.waitTicks(20);

            // 1. THE COMPONENT READ. This is the refactored path - the type resolved from the
            //    registry by id rather than named, and its value handled as Object. If a version
            //    moved either, this is where it shows.
            context.runOnClient(client -> {
                String found = null;
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (!(entity instanceof Display.ItemDisplay display)) {
                        continue;
                    }
                    final String id = HideModels.modelIdOf(
                            ((ItemDisplayAccessor) display).hidemodels$getItemStack());
                    if (TEST_MODEL.equals(id)) {
                        found = id;
                    }
                }
                if (found == null) {
                    throw new AssertionError(
                            "the summoned item_display's model id did not come back as "
                                    + TEST_MODEL + " - the component lookup is not working on this version");
                }
            });

            // 2. THE COMMAND HOOK. Sent through the client's own connection on purpose: that is
            //    the path ClientPacketListenerMixin intercepts, and it is one of the two mixins a
            //    launch-to-main-menu smoke test never loads. Running it server-side would bypass
            //    the very thing being tested. "help" because it is a real subcommand - an invented
            //    one still proves interception (the mod answers "unknown subcommand") but leaves a
            //    confusing line in the log.
            context.runOnClient(client -> client.getConnection().sendCommand("hidemodels help"));
            context.waitTicks(10);

            // 3. THE MATCHER, fed by the FILE rather than by the add command - both routes exist
            //    and both are checked: this one covers the mtime poll that picks up a hand edit,
            //    step 4 below covers the command that writes and reloads immediately.
            writeConfig(TEST_MODEL);

            //    waitFor rather than a fixed sleep: each call to hidden() is what drives the
            //    reload check, so polling both waits AND does the work. It throws on timeout, so a
            //    config that never loads fails the test instead of hanging it.
            context.waitFor(client -> HideModels.hidden(TEST_MODEL));

            context.runOnClient(client -> {
                if (HideModels.hidden("hidemodels:something_else")) {
                    throw new AssertionError(
                            "hidden() matched an id that was never added - the matcher is too eager");
                }
            });

            // 4. THE COMMANDS THAT EDIT THE LIST. add/remove write the config themselves and force
            //    a reload, so the assertion is the same one a user makes: type it, and the thing is
            //    hidden without touching a file or waiting.
            final String byCommand = "hidemodels:added_by_command";
            context.runOnClient(client ->
                    client.getConnection().sendCommand(HideModels.MOD_ID + " add " + byCommand));
            context.waitFor(client -> HideModels.hidden(byCommand));

            context.runOnClient(client ->
                    client.getConnection().sendCommand(HideModels.MOD_ID + " remove " + byCommand));
            context.waitFor(client -> !HideModels.hidden(byCommand));

            // 5. THE CLICKED PATH, on the versions that have one. Clicking a run_command component
            //    calls sendUnattendedCommand rather than sendCommand from 1.21.6 on, which is a
            //    SECOND injection point - and one that is easy to lose silently, because losing it
            //    means the command goes to the server instead, where it looks like a typo rather
            //    than a bug in this mod.
            //
            //    Reached by reflection because this one test source compiles for 1.21.4 too, where
            //    the method does not exist. Safe here and only here: a development runtime keeps
            //    the official names, while a released jar runs against intermediary and would need
            //    the remapped name - which is exactly why the mod itself uses a mixin and not this.
            final String byClick = "hidemodels:added_by_click";
            final boolean[] clickable = {false};
            context.runOnClient(client -> {
                final Object connection = client.getConnection();
                for (Method m : connection.getClass().getMethods()) {
                    if (!isUnattendedCommandSend(m)) {
                        continue;
                    }
                    clickable[0] = true;
                    try {
                        // null screen: the mod cancels at HEAD, so nothing ever reads it.
                        m.invoke(connection, HideModels.MOD_ID + " add " + byClick, null);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError("could not drive the clicked-command path", e);
                    }
                    break;
                }
            });
            // Printed rather than inferred: without it, "the test passed" reads the same whether the
            // clicked path was exercised or quietly skipped, and skipping is the failure this
            // section exists to catch.
            System.out.println("[hidemodels-gametest] clicked-command path "
                    + (clickable[0] ? "exercised" : "absent on this version (pre-1.21.6)"));
            if (clickable[0]) {
                context.waitFor(client -> HideModels.hidden(byClick));
                context.runOnClient(client -> client.getConnection()
                        .sendCommand(HideModels.MOD_ID + " remove " + byClick));
                context.waitFor(client -> !HideModels.hidden(byClick));
            }

            // Kept for a human to look at when a run fails; asserts nothing by itself, because a
            // screenshot comparison would fail on every unrelated resource-pack or lighting change.
            context.takeScreenshot("hidemodels-after-hide");
        }
    }
}
