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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.simuciokas.hidemodels.HiddenListScreen;
import io.github.simuciokas.hidemodels.HideModels;
import io.github.simuciokas.hidemodels.Keys;
import io.github.simuciokas.hidemodels.NearbyModels;
import net.minecraft.client.KeyMapping;
import io.github.simuciokas.hidemodels.fabric.Cmd;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
     * <p>By signature, not by name: the standalone launcher runs intermediary-named mods against
     * the obfuscated jar, where this answers to something like {@code method_54650}.
     *
     * <p>{@code void (String, Screen)} is the only such method on the connection across this range.
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

    /**
     * The render hook itself: {@code shouldRender}, whichever shape this version declares.
     *
     * <p>By shape, not by name, for the same reason as the clicked-command lookup below. Entity
     * first, three doubles, and on 26.3 a trailing float; nothing else on the dispatcher matches.
     */
    private static Method findShouldRender(Class<?> type) {
        for (Method m : type.getMethods()) {
            if (m.getReturnType() != boolean.class) {
                continue;
            }
            final Class<?>[] p = m.getParameterTypes();
            final boolean shape = (p.length == 5) || (p.length == 6 && p[5] == float.class);
            if (!shape || !Entity.class.isAssignableFrom(p[0])) {
                continue;
            }
            if (p[2] == double.class && p[3] == double.class && p[4] == double.class) {
                return m;
            }
        }
        return null;
    }

    /**
     * mc.gui.screen() from 26.1, mc.screen before.
     *
     * <p>The old field is found by type: production names it field_1755, and it is the only
     * Screen field Minecraft has on any 1.20.5 to 1.21.11 version.
     */
    private static Object screenOf(Object mc) throws ReflectiveOperationException {
        try {
            final Object gui = mc.getClass().getField("gui").get(mc);
            return gui.getClass().getMethod("screen").invoke(gui);
        } catch (ReflectiveOperationException e) {
            for (java.lang.reflect.Field f : mc.getClass().getFields()) {
                if (f.getType() == Screen.class) {
                    return f.get(mc);
                }
            }
            throw e;
        }
    }

    /**
     * Presses the nth button on the open screen, counting from 1 in the order they were added.
     *
     * <p>Through the button's OnPress field, found by type: Button.onPress() gained a parameter
     * in 1.21.10 and is named method_25306 in production, while OnPress.onPress(Button) is the
     * same everywhere and gets remapped with this class.
     */
    private static void press(int nth) {
        try {
            final Object scr = screenOf(net.minecraft.client.Minecraft.getInstance());
            int n = 0;
            for (Object child : ((Screen) scr).children()) {
                if (!(child instanceof net.minecraft.client.gui.components.Button btn) || ++n != nth) {
                    continue;
                }
                for (java.lang.reflect.Field f : net.minecraft.client.gui.components.Button.class
                        .getDeclaredFields()) {
                    if (f.getType() == net.minecraft.client.gui.components.Button.OnPress.class) {
                        f.setAccessible(true);
                        ((net.minecraft.client.gui.components.Button.OnPress) f.get(btn)).onPress(btn);
                        return;
                    }
                }
                throw new AssertionError("Button has no OnPress field on this version");
            }
            throw new AssertionError("the open screen has no button " + nth + " (it has " + n + ")");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not press button " + nth, e);
        }
    }

    /** Children on the open screen: one per row, drawn or not. */
    private static int childCount() {
        try {
            return ((Screen) screenOf(net.minecraft.client.Minecraft.getInstance())).children().size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not read the open screen", e);
        }
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

            // 1b. THE GROUPING THE DRILL-DOWN RESTS ON: a piece count is only clickable on rows
            //     whose key ends in a slash, so grouping that stopped trimming there would make
            //     the link vanish rather than break.
            singleplayer.getServer().runCommand(
                    "summon item_display ~ ~1 ~ {item:{id:\"stone\",components:"
                            + "{\"minecraft:item_model\":\"hidemodels:rig/head\"}}}");
            singleplayer.getServer().runCommand(
                    "summon item_display ~ ~1 ~ {item:{id:\"stone\",components:"
                            + "{\"minecraft:item_model\":\"hidemodels:rig/body\"}}}");
            context.waitTicks(20);

            context.runOnClient(client -> {
                final java.util.List<String> ids = NearbyModels.nearbyIds(32.0);
                if (!ids.contains("hidemodels:rig/")) {
                    throw new AssertionError("two bones of one model did not group into "
                            + "'hidemodels:rig/' - the piece count would not be clickable. Got "
                            + ids);
                }
            });

            // 2. THE REGISTERED COMMAND. Sent through the client's own connection on purpose:
            //    that is the path Fabric API's client command layer intercepts, and running it
            //    server-side would bypass the very thing being tested.
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

            // 4. TAB COMPLETION, asked of the real dispatcher rather than of our own provider.
            //    The suggestions are the whole reason the command was worth registering properly,
            //    and "it is wired up" is not the same claim as "the client gets the list" - the
            //    provider could be attached to the wrong node, or never reached at all.
            //
            //    A null source is safe here and only here: these nodes declare no requirement, so
            //    brigadier never touches it, and neither provider reads the context.
            context.runOnClient(client -> {
                final CommandDispatcher<FabricClientCommandSource> dispatcher = Cmd.dispatcher();
                if (dispatcher == null) {
                    throw new AssertionError("no client command dispatcher - the command never registered");
                }
                final Suggestions suggestions = dispatcher.getCompletionSuggestions(
                        dispatcher.parse(HideModels.MOD_ID + " remove ",
                                         (FabricClientCommandSource) null)).join();
                final boolean offered = suggestions.getList().stream()
                        .anyMatch(s -> TEST_MODEL.equals(s.getText()));
                if (!offered) {
                    throw new AssertionError("/hidemodels remove did not suggest the hidden id "
                            + TEST_MODEL + " - got " + suggestions.getList());
                }
                System.out.println("[hidemodels-gametest] remove suggested "
                        + suggestions.getList().size() + " id(s)");

                // THE DRILL-DOWN PARSES. A model row's piece count links to this exact shape, and
                // the click is unattended: if the node arrangement is wrong the user gets a red
                // parse error in chat with no way to tell it came from a link they clicked rather
                // than from something they typed. Parsing is the whole assertion - running it needs
                // a world with models in it, which the earlier checks already cover.
                final String drill = HideModels.MOD_ID + " list bones 32.0 modelengine:some_mount/";
                final ParseResults<FabricClientCommandSource> parsed =
                        dispatcher.parse(drill, (FabricClientCommandSource) null);
                // Both halves matter: an unknown argument shows up as an exception, but a tree that
                // simply has nowhere to put the model name parses the prefix happily and leaves the
                // rest unread - which would run the UNFILTERED list and look like it worked.
                if (!parsed.getExceptions().isEmpty() || parsed.getReader().canRead()) {
                    throw new AssertionError("the clickable piece count builds a command the tree "
                            + "does not fully accept: " + drill + " - unread '"
                            + parsed.getReader().getRemaining() + "', " + parsed.getExceptions());
                }
            });

            // 5. THE COMMANDS THAT EDIT THE LIST, asserted the way a user would: type it, and the
            //    thing is hidden without touching a file or waiting.
            final String byCommand = "hidemodels:added_by_command";
            context.runOnClient(client ->
                    client.getConnection().sendCommand(HideModels.MOD_ID + " add " + byCommand));
            context.waitFor(client -> HideModels.hidden(byCommand));

            context.runOnClient(client ->
                    client.getConnection().sendCommand(HideModels.MOD_ID + " remove " + byCommand));
            context.waitFor(client -> !HideModels.hidden(byCommand));

            // 6. THE CLICKED PATH, on the versions that have one: from 1.21.6 a clicked
            //    run_command calls sendUnattendedCommand rather than sendCommand. Losing it sends
            //    the command to the server, where it reads as a typo rather than a bug here.
            //
            //    Reflection because this source also compiles for 1.21.4, where the method does
            //    not exist.
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
            // Printed, because "the test passed" otherwise reads the same whether the clicked
            // path ran or was skipped.
            System.out.println("[hidemodels-gametest] clicked-command path "
                    + (clickable[0] ? "exercised" : "absent on this version (pre-1.21.6)"));
            if (clickable[0]) {
                context.waitFor(client -> HideModels.hidden(byClick));
                context.runOnClient(client -> client.getConnection()
                        .sendCommand(HideModels.MOD_ID + " remove " + byClick));
                context.waitFor(client -> !HideModels.hidden(byClick));
            }

            // 7. THE OTHER SHAPE A MODEL ARRIVES IN: an armor stand wearing the piece on its
            //    head, which is what the older plugins and the legacy modes of the newer ones
            //    produce. Same component, same config line, different entity class - and a type
            //    check naming only Display.ItemDisplay would silently ignore all of it.
            //
            //    A SHAPE TEST, NOT A PLUGIN TEST: no ModelEngine is installed or needed. Whether a
            //    given plugin still emits this shape is that plugin's business.
            //
            //    Equipped by /item replace rather than summon NBT on purpose: the entity's own
            //    equipment tag was ArmorItems before 1.21.5 and equipment after, while this command
            //    reads the same on every version the gametest runs on.
            final String standModel = "hidemodels:legacy_rig/head";
            singleplayer.getServer().runCommand(
                    "summon armor_stand ~ ~1 ~ {Tags:[\"hidemodels_stand\"]}");
            singleplayer.getServer().runCommand(
                    "item replace entity @e[tag=hidemodels_stand,limit=1] armor.head with "
                            + "stone[minecraft:item_model=\"" + standModel + "\"]");
            context.waitTicks(20);

            context.runOnClient(client -> {
                boolean seen = false;
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (standModel.equals(HideModels.modelIdOfEntity(entity))) {
                        seen = true;
                    }
                }
                if (!seen) {
                    throw new AssertionError("an armor stand wearing " + standModel + " did not "
                            + "resolve to that id - the legacy model shape is not being read");
                }
            });

            writeConfig(standModel);
            context.waitFor(client -> HideModels.hidden(standModel));
            System.out.println("[hidemodels-gametest] armor stand shape hidden");

            // 8. THE RENDER HOOK IS ACTUALLY APPLIED. Everything above tests the matcher, not
            //    the mixin that acts on it - which only became worth checking when 26.3 forced
            //    both injectors to require = 0, where matching nothing is silent.
            //
            //    A NULL FRUSTUM IS THE POINT: the hook cancels at HEAD before anything reads it,
            //    so a working mod returns false, and one whose hook never applied falls through to
            //    vanilla and throws. Both failures are loud.
            context.runOnClient(client -> {
                final Object dispatcher = client.getEntityRenderDispatcher();
                final Method shouldRender = findShouldRender(dispatcher.getClass());
                if (shouldRender == null) {
                    throw new AssertionError("no shouldRender on the entity render dispatcher - "
                            + "the mixin's target has moved and this test cannot see it");
                }
                Entity hiddenOne = null;
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (standModel.equals(HideModels.modelIdOfEntity(entity))) {
                        hiddenOne = entity;
                    }
                }
                if (hiddenOne == null) {
                    throw new AssertionError("the armor stand went missing before the render check");
                }
                final Object[] args = shouldRender.getParameterCount() == 5
                        ? new Object[] {hiddenOne, null, 0.0, 0.0, 0.0}
                        : new Object[] {hiddenOne, null, 0.0, 0.0, 0.0, 0.0f};
                try {
                    final Object answer = shouldRender.invoke(dispatcher, args);
                    if (!Boolean.FALSE.equals(answer)) {
                        throw new AssertionError("shouldRender said " + answer + " for a listed "
                                + "model - the render hook is not applied on this version");
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("shouldRender threw for a listed model, which means "
                            + "the hook did not cancel and vanilla ran instead", e);
                }
                System.out.println("[hidemodels-gametest] render hook cancels ("
                        + shouldRender.getParameterCount() + "-arg shouldRender)");
            });

            // 9. THE CLICK EVENT RESOLVES. ClickRun builds it reflectively, so nothing at compile
            //    time proves the names are right - and a failure returns Style.EMPTY, which looks
            //    like plain text rather than an error. This is the only check that it worked, and
            //    it has to run in production mode too: dev keeps official names, while a released
            //    1.21.x jar runs against intermediary and takes the other candidate.
            context.runOnClient(client -> {
                final net.minecraft.network.chat.Style styled =
                        io.github.simuciokas.hidemodels.ClickRun.style("/hidemodels help");
                if (net.minecraft.network.chat.Style.EMPTY.equals(styled)) {
                    throw new AssertionError("ClickRun produced no click event - neither the modern "
                            + "nor the legacy ClickEvent shape resolved on this version");
                }
                System.out.println("[hidemodels-gametest] click event resolved");
            });

            // 10. THE SERVER OPT-OUT'S REFLECTIVE HALF. HideModels.channelId reads the id off a
            //     payload type by reflection, because that id is ResourceLocation up to 1.21.10 and
            //     Identifier after - naming it would split the NeoForge jar.
            //
            //     The id comes OUT OF A REGISTRY rather than being constructed, and the payload
            //     type is built through its constructor rather than with new, because both of those
            //     would name the moving type here and break this test's own compile on half the
            //     range. CustomPacketPayload.Type is stable; only what it holds is not.
            context.runOnClient(client -> {
                try {
                    final Object id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(net.minecraft.world.item.Items.STONE);
                    final Object type = net.minecraft.network.protocol.common.custom
                            .CustomPacketPayload.Type.class.getConstructors()[0].newInstance(id);
                    final String read = HideModels.channelId(type);
                    if (!"minecraft:stone".equals(read)) {
                        throw new AssertionError("channelId read '" + read + "' from a payload type "
                                + "whose id is minecraft:stone - the reflective id() lookup is wrong "
                                + "on this version, so a server could not switch the mod off");
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not build a payload type to test channelId", e);
                }
                System.out.println("[hidemodels-gametest] channel id read reflectively");
            });

            // THE GUI SWEEP, off unless asked for: -PguiSweep captures the screen at every GUI
            // scale so a layout change can be eyeballed at the sizes people actually play at.
            //
            // SCALE RATHER THAN RESOLUTION, because the layout only sees the effective GUI size -
            // the window divided by the scale - and 1920x1080 at scale 4 gives it less room than
            // 1280x720 at scale 2. The window stays put; the scale is what moves.
            if (System.getProperty("hidemodels.guisweep") != null) {
                for (int i = 0; i < 24; i++) {
                    singleplayer.getServer().runCommand(
                            "summon item_display ~ ~1 ~ {item:{id:\"stone\",components:"
                                    + "{\"minecraft:item_model\":\"modelengine:mount_" + i
                                    + "/body\"}}}");
                }
                writeConfig("modelengine:mount_3/");
                context.waitTicks(30);
                context.runOnClient(client ->
                        client.getConnection().sendCommand("hidemodels gui"));
                context.waitTicks(20);

                // One capture at whatever scale the client is on, which is all a production-mode
                // run can do: the resize call is remapped there and this is a dev tool.
                context.takeScreenshot("sweep-asis");
                context.waitTicks(3);

                for (int scale : new int[] {1, 2, 3, 4}) {
                    final boolean[] resized = {false};
                    context.runOnClient(client -> {
                        client.options.guiScale().set(scale);
                        // resizeDisplay() up to 1.21.11, resizeGui() from 26.1 - dev names only.
                        for (String name : new String[] {"resizeGui", "resizeDisplay"}) {
                            try {
                                client.getClass().getMethod(name).invoke(client);
                                resized[0] = true;
                                return;
                            } catch (ReflectiveOperationException ignored) {
                                // the other spelling, or a remapped runtime
                            }
                        }
                    });
                    if (!resized[0]) {
                        System.out.println("[hidemodels-gametest] no resize call here - "
                                + "sweep needs a development runtime");
                        break;
                    }
                    context.waitTicks(10);
                    final String[] tabs = {"nearby", "hidden", "settings"};
                    for (int t = 0; t < tabs.length; t++) {
                        final int nth = t + 1;
                        context.runOnClient(client -> press(nth));
                        context.waitTicks(5);
                        context.takeScreenshot("sweep-scale" + scale + "-" + tabs[t]);
                        context.waitTicks(3);
                    }
                    context.runOnClient(client -> press(1));
                }
                context.runOnClient(client -> client.options.guiScale().set(0));
            }

            // 11. THE HIDDEN TAB, and the one rule that makes it usable: unhiding edits the
            //     config but leaves the row where it is. A row that vanished under the cursor
            //     would make undoing a misclick impossible - the list is a snapshot, re-read only
            //     when the tab is.
            writeConfig("meg:ghost/");
            context.waitTicks(10);
            context.runOnClient(client -> client.getConnection().sendCommand("hidemodels add meg:spectre/"));
            context.waitTicks(10);
            context.runOnClient(client -> client.getConnection().sendCommand("hidemodels gui"));
            context.waitTicks(20);
            // Buttons in the order the screen adds them: the three tabs, then the rows.
            context.runOnClient(client -> press(2));
            context.waitTicks(15);
            context.runOnClient(client -> {
                final int before = childCount();
                press(4);
                final int after = childCount();
                if (before != after) {
                    throw new AssertionError("the row went away when unhidden: " + before
                            + " rows became " + after);
                }
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (HideModels.listed("meg:ghost/")) {
                    throw new AssertionError("unhiding from the hidden tab did not edit the config");
                }
                System.out.println("[hidemodels-gametest] hidden tab: config edited, row kept");
            });
            context.waitTicks(5);

            // 12. THE SETTINGS TAB, which must leave the config exactly as the matching command
            //     would. Buttons there: the tabs, hiding, first person only, radius -, radius +.
            context.runOnClient(client -> client.getConnection().sendCommand("hidemodels radius 30"));
            context.waitTicks(10);
            context.runOnClient(client -> press(3));
            context.waitTicks(10);
            context.runOnClient(client -> {
                press(4);
                if (HideModels.isEnabled()) {
                    throw new AssertionError("the hiding toggle did not turn hiding off");
                }
                press(4);
                if (!HideModels.isEnabled()) {
                    throw new AssertionError("the hiding toggle did not turn hiding back on");
                }
                press(5);
                if (!HideModels.isFirstPersonOnly()) {
                    throw new AssertionError("the first-person toggle did not turn it on");
                }
                press(5);
                if (HideModels.isFirstPersonOnly()) {
                    throw new AssertionError("the first-person toggle did not turn it back off");
                }
                // Off a multiple on purpose: + goes to the next one, not 30 + 8.
                press(7);
                if (HideModels.listRadius() != 32.0) {
                    throw new AssertionError("radius + took 30 to " + HideModels.listRadius()
                            + ", not 32");
                }
                press(6);
                press(6);
                if (HideModels.listRadius() != 16.0) {
                    throw new AssertionError("radius - twice took 32 to " + HideModels.listRadius()
                            + ", not 16");
                }
                System.out.println("[hidemodels-gametest] settings tab: toggles and radius written");
            });
            context.waitTicks(5);

            // 13. THE KEY. It ships unbound, so it is bound here first; the press then goes
            //     through the real keyboard handler and the client tick, which is the whole path.
            context.runOnClient(client -> {
                try {
                    ((Screen) screenOf(client)).onClose();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not close the screen", e);
                }
            });
            context.waitTicks(5);
            final KeyMapping[] openKey = {null};
            context.runOnClient(client -> {
                for (KeyMapping k : client.options.keyMappings) {
                    if (Keys.OPEN.equals(k.getName())) {
                        openKey[0] = k;
                    }
                }
                if (openKey[0] == null) {
                    throw new AssertionError("the open key is not in Controls - it never registered");
                }
                openKey[0].setKey(InputConstants.getKey("key.keyboard.h"));
                KeyMapping.resetMapping();
            });
            context.getInput().pressKey(openKey[0]);
            context.waitTicks(5);
            context.runOnClient(client -> {
                final Object scr;
                try {
                    scr = screenOf(client);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not read the open screen", e);
                }
                openKey[0].setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                if (!(scr instanceof HiddenListScreen)) {
                    throw new AssertionError("the key opened " + scr + " rather than the screen");
                }
                System.out.println("[hidemodels-gametest] key opens the screen");
            });
            context.waitTicks(5);

            // Kept for a human to look at when a run fails; asserts nothing by itself, because a
            // screenshot comparison would fail on every unrelated resource-pack or lighting change.
            context.takeScreenshot("hidemodels-after-hide");
        }
    }
}
