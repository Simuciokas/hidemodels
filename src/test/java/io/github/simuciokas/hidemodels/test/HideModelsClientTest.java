package io.github.simuciokas.hidemodels.test;

import io.github.simuciokas.hidemodels.HideModels;
import io.github.simuciokas.hidemodels.mixin.ItemDisplayAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Display;
import java.io.IOException;
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

    @Override
    public void runTest(ClientGameTestContext context) {
        // Before the world, so the very first hidden() call already sees an empty list.
        writeConfig("# cleared by the client gametest");

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();

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

            // 3. THE MATCHER, fed the way the mod is actually configured: by its file. There is
            //    no "add" command - patterns are edited into config/hidemodels.txt and picked up
            //    by a poll that watches the mtime, at most once a RELOAD_INTERVAL_MS.
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

            // Kept for a human to look at when a run fails; asserts nothing by itself, because a
            // screenshot comparison would fail on every unrelated resource-pack or lighting change.
            context.takeScreenshot("hidemodels-after-hide");
        }
    }
}
