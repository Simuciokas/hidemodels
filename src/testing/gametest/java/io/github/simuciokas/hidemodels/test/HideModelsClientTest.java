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

import com.mojang.blaze3d.platform.InputConstants;
import io.github.simuciokas.hidemodels.HiddenListScreen;
import io.github.simuciokas.hidemodels.HideModels;
import io.github.simuciokas.hidemodels.Keys;
import io.github.simuciokas.hidemodels.NearbyModels;
import net.minecraft.client.KeyMapping;
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
 * from the registry, and the screen the whole mod is driven from.
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
     * The render hook itself: {@code shouldRender}, whichever shape this version declares.
     *
     * <p>By shape, not by name: the standalone launcher runs intermediary-named mods against the
     * obfuscated jar, where it has a method_ name. Entity first, three doubles, and on 26.3 a
     * trailing float; nothing else on the dispatcher matches.
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

    /**
     * Puts the cursor on the first row under the tabs. The cursor is in window pixels, the layout
     * in GUI pixels, so the point is scaled - at whatever scale the window actually got.
     */
    private static void hoverFirstRow(ClientGameTestContext context) {
        hoverFirstRow(context, false);
    }

    /** As above, for a panel against the right edge: 30 GUI pixels in from that side instead. */
    private static void hoverFirstRow(ClientGameTestContext context, boolean right) {
        final double[] at = {1, 30};
        context.runOnClient(client -> {
            at[0] = client.getWindow().getGuiScale();
            at[1] = right ? client.getWindow().getGuiScaledWidth() - 30 : 30;
        });
        context.getInput().setCursorPos(at[1] * at[0], 54 * at[0]);
        context.waitTicks(3);
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

            // 1b. THE GROUPING THE NEARBY TAB RESTS ON: it lists models, not bones, so two bones
            //     of one model must come back as a single row ending in a slash.
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
                            + "'hidemodels:rig/' - the Nearby tab would list them apart. Got "
                            + ids);
                }
            });

            // 2. THE COMMAND OPENS THE SCREEN. Sent through the client's own connection on purpose:
            //    that is the path Fabric API's client command layer intercepts, and running it
            //    server-side would bypass the very thing being tested.
            context.runOnClient(client -> client.getConnection().sendCommand(HideModels.MOD_ID));
            context.waitTicks(10);
            context.runOnClient(client -> {
                try {
                    final Object scr = screenOf(client);
                    if (!(scr instanceof HiddenListScreen)) {
                        throw new AssertionError("/hidemodels opened " + scr + ", not the screen");
                    }
                    ((Screen) scr).onClose();
                    System.out.println("[hidemodels-gametest] /hidemodels opens the screen");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not read the open screen", e);
                }
            });
            context.waitTicks(5);

            // 3. THE MATCHER, fed by the FILE rather than by the screen - both routes exist and
            //    both are checked: this one covers the mtime poll that picks up a hand edit, step 4
            //    below covers the write a click makes, which reloads immediately.
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

            // 4. ADDING AND REMOVING, the calls a click in the screen makes: the id is hidden at
            //    once and shown again at once, without waiting for the poll.
            final String added = "hidemodels:added_in_game";
            context.runOnClient(client -> HideModels.add(added));
            context.waitFor(client -> HideModels.hidden(added));
            context.runOnClient(client -> HideModels.remove(added));
            context.waitFor(client -> !HideModels.hidden(added));

            // 5. THE OTHER SHAPE A MODEL ARRIVES IN: an armor stand wearing the piece on its
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

            // 6. THE RENDER HOOK IS ACTUALLY APPLIED. Everything above tests the matcher, not
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

            // 7. THE SERVER OPT-OUT'S REFLECTIVE HALF. HideModels.channelId reads the id off a
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
                        client.getConnection().sendCommand(HideModels.MOD_ID));
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
                    context.waitTicks(3);
                    hoverFirstRow(context);
                    context.takeScreenshot("sweep-scale" + scale + "-hover");
                    context.runOnClient(client -> {
                        HideModels.setGuiPosition(1, 0);
                        press(1);
                    });
                    context.waitTicks(3);
                    hoverFirstRow(context, true);
                    context.takeScreenshot("sweep-scale" + scale + "-right");
                    context.runOnClient(client -> {
                        HideModels.setGuiPosition(0, 0);
                        press(1);
                    });
                    context.getInput().setCursorPos(0, 0);
                    context.waitTicks(3);
                }
                context.runOnClient(client -> client.options.guiScale().set(0));
            }

            // 8. THE HIDDEN TAB, and the one rule that makes a list usable: taking a line out
            //     edits the config but leaves the row where it is. A row that vanished under the
            //     cursor would make undoing a misclick impossible - an open list is a snapshot,
            //     re-read only when it is opened again.
            writeConfig("meg:ghost/");
            context.waitTicks(10);
            context.runOnClient(client -> HideModels.add("meg:spectre/"));
            context.waitTicks(10);
            context.runOnClient(client -> client.getConnection().sendCommand(HideModels.MOD_ID));
            context.waitTicks(20);
            // Buttons in the order the screen adds them: the three tabs, then the rows. The Hidden
            // tab's front page is Unsaved, its cell, Save; opening Unsaved shows back, then lines.
            context.runOnClient(client -> press(2));
            context.waitTicks(10);
            context.runOnClient(client -> press(4));
            context.waitTicks(10);
            context.runOnClient(client -> {
                final int before = childCount();
                press(5);
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

            //     PROFILES from the same tab: the unsaved lines saved as one, which then hides only
            //     while it is on, and opened to take a line out and put it back. On the front
            //     page with nothing unsaved: Unsaved, its cell, then the profile and its count.
            context.runOnClient(client -> HideModels.add("hidemodels:rig/"));
            context.waitTicks(5);
            context.runOnClient(client -> {
                press(2);
                press(6);
                if (HideModels.profiles().size() != 1 || !HideModels.unsaved().isEmpty()) {
                    throw new AssertionError("Save unsaved as a profile left " + HideModels.unsaved()
                            + " unsaved and " + HideModels.profiles().size() + " profile(s)");
                }
            });
            context.waitTicks(5);
            context.takeScreenshot("profiles-lists");
            context.runOnClient(client -> {
                press(6);
                if (HideModels.profiles().get(0).on() || HideModels.listed("hidemodels:rig/")) {
                    throw new AssertionError("clicking the profile did not switch it off");
                }
                press(6);
                if (!HideModels.listed("hidemodels:rig/")) {
                    throw new AssertionError("clicking it again did not switch it back on");
                }
                // Open, a profile shows the way back, its name box, Copy and Delete, then its lines.
                press(7);
                final int before = childCount();
                final String line = HideModels.profiles().get(0).patterns().get(0);
                press(7);
                if (HideModels.profiles().get(0).patterns().contains(line) || childCount() != before) {
                    throw new AssertionError("taking a line out of the profile did not edit it, "
                            + "or the row went away");
                }
                press(7);
                if (!HideModels.profiles().get(0).patterns().contains(line)) {
                    throw new AssertionError("clicking the line again did not put it back");
                }
            });
            context.waitTicks(5);
            //     Renaming, by real clicks and keys: the name box is an invisible vanilla EditBox,
            //     and the input reaching it is the part worth testing. Its row is the third under
            //     the header, after the tabs and the way back.
            final double[] guiScale = {1};
            context.runOnClient(client -> guiScale[0] = client.getWindow().getGuiScale());
            context.getInput().setCursorPos(30 * guiScale[0], 72 * guiScale[0]);
            context.waitTicks(2);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.getInput().pressKey(InputConstants.KEY_END);
            for (int i = 0; i < 16; i++) {
                context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            }
            context.getInput().typeChars("Mounts");
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (!"Mounts".equals(HideModels.profiles().get(0).name())) {
                    throw new AssertionError("typing into the name box did not rename the profile: "
                            + HideModels.profiles().get(0).name());
                }
                System.out.println("[hidemodels-gametest] profiles: renamed by typing");
            });
            context.takeScreenshot("profile-open");
            //     + beside a profile adds the unsaved lines to it. With something unsaved the front
            //     page is Unsaved, its cell, Save, then the profile, its +, its count.
            context.runOnClient(client -> {
                press(4);
                HideModels.add("meg:plus/");
                press(2);
                press(8);
                if (!HideModels.unsaved().isEmpty()
                        || !HideModels.profiles().get(0).patterns().contains("meg:plus/")) {
                    throw new AssertionError("+ did not move the unsaved line into the profile");
                }
                System.out.println("[hidemodels-gametest] profiles: saved, switched, opened, edited, "
                        + "added to");
            });
            context.waitTicks(5);
            //     Copy and Paste, through the real clipboard: the profile copied, deleted, then
            //     pasted back as it was. Whatever the clipboard held before is put back after.
            context.runOnClient(client -> {
                final String held = client.keyboardHandler.getClipboard();
                final HideModels.Profile was = HideModels.profiles().get(0);
                try {
                    press(7);
                    press(5);
                    final String copied = client.keyboardHandler.getClipboard();
                    if (!copied.startsWith("[" + was.name() + "]")
                            || !copied.contains(was.patterns().get(0))) {
                        throw new AssertionError("Copy profile put this on the clipboard: " + copied);
                    }
                    press(6);
                    press(6);
                    if (!HideModels.profiles().isEmpty()) {
                        throw new AssertionError("the profile was not deleted before pasting");
                    }
                    // Nothing unsaved and no profiles: Unsaved, its cell, then Paste.
                    press(6);
                    final HideModels.Profile back = HideModels.profiles().isEmpty()
                            ? null : HideModels.profiles().get(0);
                    if (back == null || !back.equals(was)) {
                        throw new AssertionError("pasting gave " + back + " rather than " + was);
                    }
                    // Back on the page, the profile and its count sit above Paste again.
                    press(8);
                    if (HideModels.profiles().size() != 1) {
                        throw new AssertionError("pasting the same profile again added it twice");
                    }
                } finally {
                    client.keyboardHandler.setClipboard(held);
                }
                System.out.println("[hidemodels-gametest] profiles: copied, deleted and pasted back");
            });
            context.waitTicks(5);
            context.takeScreenshot("profile-pasted");

            // 9. THE SETTINGS TAB, which must leave the config exactly as the matching directive
            //     typed into the file would. Buttons there: the tabs, hiding, first person only, radius -, radius +,
            //     position, move panel.
            context.runOnClient(client -> HideModels.setListRadius(30));
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
                press(8);
                if (HideModels.guiX() != 1.0 || HideModels.guiY() != 0.0) {
                    throw new AssertionError("Position did not go to the top right corner");
                }
            });
            context.waitTicks(5);
            context.takeScreenshot("settings-right");
            context.runOnClient(client -> {
                press(8);
                if (HideModels.guiX() != 0.0 || HideModels.guiY() != 0.0) {
                    throw new AssertionError("Position did not go back to the top left corner");
                }
            });
            context.waitTicks(5);

            //     Move panel the way a user does it: pick it up, carry it into the bottom right
            //     corner, click. Picked up with the cursor in the window's top left, so the carry
            //     overshoots and the drop is clamped to 1 1 whatever the panel's size.
            context.getInput().setCursorPos(0, 0);
            context.waitTicks(2);
            context.runOnClient(client -> press(9));
            context.waitTicks(2);
            final double[] corner = new double[2];
            context.runOnClient(client -> {
                final double scale = client.getWindow().getGuiScale();
                corner[0] = (client.getWindow().getGuiScaledWidth() - 1) * scale;
                corner[1] = (client.getWindow().getGuiScaledHeight() - 1) * scale;
            });
            context.getInput().setCursorPos(corner[0], corner[1]);
            context.waitTicks(3);
            context.takeScreenshot("moving");
            context.runOnClient(client -> {
                if (childCount() != 1) {
                    throw new AssertionError("a moving panel should leave one click target, not "
                            + childCount());
                }
                press(1);
                if (HideModels.guiX() != 1.0 || HideModels.guiY() != 1.0) {
                    throw new AssertionError("the panel was put down at " + HideModels.guiX() + " "
                            + HideModels.guiY() + ", not in the bottom right corner");
                }
            });
            context.waitTicks(5);
            context.takeScreenshot("placed-bottom-right");
            //     Esc while carrying it cancels the move; onClose is what Esc calls.
            context.runOnClient(client -> {
                press(9);
                try {
                    ((Screen) screenOf(client)).onClose();
                    if (!(screenOf(client) instanceof HiddenListScreen) || childCount() == 1) {
                        throw new AssertionError("Esc while moving closed the screen or kept moving");
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not read the open screen", e);
                }
                HideModels.setGuiPosition(0, 0);
                System.out.println("[hidemodels-gametest] settings tab: toggles, radius, position "
                        + "and move written");
            });
            context.waitTicks(5);

            // 10. THE KEY. It ships unbound, so it is bound here first; the press then goes
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
                if (!String.valueOf(openKey[0].getCategory()).contains(HideModels.MOD_ID)) {
                    throw new AssertionError("the key is in " + openKey[0].getCategory()
                            + " rather than a section of its own");
                }
                System.out.println("[hidemodels-gametest] key opens the screen");
            });
            context.waitTicks(5);
            //     With Shift held, the same key switches hiding instead and opens nothing.
            final boolean[] wasOn = {false};
            context.runOnClient(client -> {
                try {
                    ((Screen) screenOf(client)).onClose();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not close the screen", e);
                }
                openKey[0].setKey(InputConstants.getKey("key.keyboard.h"));
                KeyMapping.resetMapping();
                wasOn[0] = HideModels.isEnabled();
            });
            context.waitTicks(5);
            context.getInput().holdKey(InputConstants.KEY_LSHIFT);
            context.getInput().pressKey(openKey[0]);
            context.getInput().releaseKey(InputConstants.KEY_LSHIFT);
            context.waitTicks(5);
            context.runOnClient(client -> {
                final Object scr;
                try {
                    scr = screenOf(client);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not read the open screen", e);
                }
                final boolean on = HideModels.isEnabled();
                HideModels.setEnabled(wasOn[0]);
                openKey[0].setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                if (scr != null || on == wasOn[0]) {
                    throw new AssertionError("Shift with the key opened " + scr
                            + " and left hiding " + (on ? "on" : "off"));
                }
                System.out.println("[hidemodels-gametest] Shift with the key switches hiding");
            });
            context.waitTicks(5);
            //     Its section, seen the way a player finds it: Controls, scrolled to the bottom,
            //     where sections from mods go.
            context.runOnClient(client -> io.github.simuciokas.hidemodels.Screens.open(client,
                    new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(
                            null, client.options)));
            context.waitTicks(5);
            hoverFirstRow(context);
            context.getInput().scroll(-400);
            context.waitTicks(5);
            context.takeScreenshot("controls-section");
            context.runOnClient(client -> io.github.simuciokas.hidemodels.Screens.open(client,
                    new HiddenListScreen(null)));
            context.waitTicks(5);

            // 11. WHAT THE HOVER BOX IS BUILT FROM: a model's piece count, the line that covers
            //     it, and how much a line hides. The box itself is only looked at.
            writeConfig("hidemodels:rig/");
            context.waitFor(client -> HideModels.hidden("hidemodels:rig/head"));
            context.runOnClient(client -> {
                final String covering = HideModels.coveredBy("hidemodels:rig/head");
                if (!"hidemodels:rig/".equals(covering)) {
                    throw new AssertionError("a bone of a hidden model reported '" + covering
                            + "' as the line covering it, not hidemodels:rig/");
                }
                NearbyModels.Nearby rig = null;
                for (NearbyModels.Nearby n : NearbyModels.nearby(32.0)) {
                    if ("hidemodels:rig/".equals(n.id())) {
                        rig = n;
                    }
                }
                if (rig == null || rig.pieces() != 2) {
                    throw new AssertionError("the two-bone rig came back as " + rig);
                }
                final int matched = NearbyModels.piecesMatching(32.0, "hidemodels:rig/");
                if (matched != 2) {
                    throw new AssertionError("the rig's line matched " + matched + " pieces, not 2");
                }
                System.out.println("[hidemodels-gametest] hover detail: 2 pieces, covered by its line");
            });
            // The case the box exists for: hidden by a broader line, which a click cannot remove.
            writeConfig("hidemodels:");
            // Something only the new line hides, or this passes on the old config.
            context.waitFor(client -> HideModels.hidden(TEST_MODEL));
            context.runOnClient(client -> {
                if (!"hidemodels:".equals(HideModels.coveredBy("hidemodels:rig/head"))) {
                    throw new AssertionError("a broader line was not reported as the cover");
                }
            });
            hoverFirstRow(context);
            context.takeScreenshot("hover-detail");

            // 12. A MODEL'S BONES, opened from its row so one piece can be hidden on its own. The
            //     area is cleared first, leaving one three-bone model, which fixes where the
            //     buttons fall: the tabs, the model, its cell, close - and once opened, the tabs,
            //     the way back, three bones, close.
            context.runOnClient(client -> {
                try {
                    ((Screen) screenOf(client)).onClose();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not close the screen", e);
                }
            });
            singleplayer.getServer().runCommand("kill @e[type=item_display]");
            singleplayer.getServer().runCommand("kill @e[type=armor_stand]");
            // What was killed would stay listed as gone; this section counts rows from a clean list.
            context.waitTicks(1);
            context.runOnClient(client -> forgetGone());
            final String[] beast = {"hidemodels:beast/head", "hidemodels:beast/body",
                                    "hidemodels:beast/tail"};
            for (String bone : beast) {
                singleplayer.getServer().runCommand(
                        "summon item_display ~ ~1 ~ {item:{id:\"stone\",components:"
                                + "{\"minecraft:item_model\":\"" + bone + "\"}}}");
            }
            writeConfig("# cleared for the bone view");
            context.waitFor(client -> !HideModels.hidden(beast[0]));
            context.waitTicks(20);
            context.runOnClient(client -> client.getConnection().sendCommand(HideModels.MOD_ID));
            context.waitTicks(10);
            context.takeScreenshot("model-with-cell");
            context.runOnClient(client -> {
                if (childCount() != 6) {
                    throw new AssertionError("expected the tabs, one model, its cell and close - "
                            + "got " + childCount() + " buttons");
                }
                press(5);
                if (childCount() != 8) {
                    throw new AssertionError("the model's three bones did not open: "
                            + childCount() + " buttons");
                }
                press(5);
                int hidden = 0;
                for (String bone : beast) {
                    if (HideModels.listed(bone)) {
                        hidden++;
                    }
                }
                if (hidden != 1 || HideModels.listed("hidemodels:beast/")) {
                    throw new AssertionError("clicking one bone hid " + hidden + " of three"
                            + (HideModels.listed("hidemodels:beast/") ? ", and the model" : ""));
                }
            });
            context.waitTicks(5);
            context.takeScreenshot("bone-view");
            context.runOnClient(client -> {
                press(4);
                if (childCount() != 6) {
                    throw new AssertionError("going back did not return to the models: "
                            + childCount() + " buttons");
                }
            });
            hoverFirstRow(context);
            context.takeScreenshot("partly-hidden");
            //     The partly hidden model's own row: one click hides all of it, the next shows all
            //     of it again, taking the single bone's line with it.
            context.runOnClient(client -> {
                press(4);
                for (String bone : beast) {
                    if (!HideModels.listed(bone)) {
                        throw new AssertionError("hiding a partly hidden model left " + bone
                                + " showing");
                    }
                }
                press(4);
                for (String bone : beast) {
                    if (HideModels.listed(bone)) {
                        throw new AssertionError("unhiding the model left " + bone + " hidden");
                    }
                }
                System.out.println("[hidemodels-gametest] bone view: one bone of three hidden, "
                        + "then all of it, then none");
            });
            //     A model a profile hides opens that profile when clicked, rather than editing it -
            //     shown by deleting the profile from there: in Nearby the same buttons would only
            //     have opened the model's bones.
            context.runOnClient(client -> {
                HideModels.add("hidemodels:beast/");
                HideModels.saveUnsavedAsProfile();
            });
            context.waitTicks(5);
            context.runOnClient(client -> client.getConnection().sendCommand(HideModels.MOD_ID));
            context.waitTicks(10);
            context.runOnClient(client -> {
                press(4);
                press(6);
                press(6);
                if (!HideModels.profiles().isEmpty() || HideModels.listed("hidemodels:beast/")) {
                    throw new AssertionError("clicking a model hidden by a profile did not open it "
                            + "- the profile is still there: " + HideModels.profiles());
                }
                System.out.println("[hidemodels-gametest] nearby: a profile's model opens the profile");
            });

            // 13. THE DEMO SCENE that runDemo places, checked model by model: its summons fail
            //     silently, and a broken one would only show as an empty world in the demo.
            final double[] at = new double[4];
            context.runOnClient(client -> {
                at[0] = client.player.getX();
                at[1] = client.player.getY();
                at[2] = client.player.getZ();
                at[3] = client.player.getYRot();
            });
            singleplayer.getServer().runOnServer(server ->
                    DemoModels.place(server, at[0], at[1], at[2], (float) at[3]));
            context.waitTicks(20);
            context.runOnClient(client -> {
                final java.util.Map<String, Integer> placed = new java.util.TreeMap<>();
                for (NearbyModels.Nearby n : NearbyModels.nearby(16.0)) {
                    if (!n.gone() && n.id().startsWith("hidemodels_demo:")) {
                        placed.put(n.id(), n.pieces());
                    }
                }
                final java.util.Map<String, Integer> expected = new java.util.TreeMap<>(java.util.Map.of(
                        "hidemodels_demo:dragon/", 5, "hidemodels_demo:golem/", 4,
                        "hidemodels_demo:statue/", 1, "hidemodels_demo:lantern", 1));
                if (!expected.equals(placed)) {
                    throw new AssertionError("the demo scene placed " + placed + ", not " + expected);
                }
                System.out.println("[hidemodels-gametest] demo scene: " + placed);
            });
            context.takeScreenshot("demo-scene");

            //     The model in front of you, whichever way you turn: the golem stands 4.5 right and
            //     6 ahead, the statue 2.5 left and 5 ahead, the dragon straight on. Facing the
            //     golem, the screen lists it first, so its row is the first button after the tabs.
            final Object[][] looks = {
                    {Math.toDegrees(Math.atan2(4.5, 6.0)), "hidemodels_demo:golem/"},
                    {Math.toDegrees(Math.atan2(-2.5, 5.0)), "hidemodels_demo:statue/"},
                    {0.0, "hidemodels_demo:dragon/"},
                    {180.0, null}};
            for (Object[] look : looks) {
                context.runOnClient(client -> {
                    client.player.setYRot((float) (at[3] + (double) look[0]));
                    client.player.setXRot(0f);
                    final String seen = NearbyModels.lookedAt(16.0);
                    if (!java.util.Objects.equals(seen, look[1])) {
                        throw new AssertionError("turned " + look[0] + " degrees, the model in front "
                                + "was " + seen + " rather than " + look[1]);
                    }
                });
            }
            context.runOnClient(client -> {
                client.player.setYRot((float) (at[3] + (double) looks[0][0]));
                io.github.simuciokas.hidemodels.Screens.open(client, new HiddenListScreen(null));
            });
            context.waitTicks(5);
            hoverFirstRow(context);
            context.takeScreenshot("looked-at");
            context.runOnClient(client -> {
                press(4);
                if (!HideModels.listed("hidemodels_demo:golem/")) {
                    throw new AssertionError("the first row was not the golem in front of you");
                }
                HideModels.removeModel("hidemodels_demo:golem/");
                client.player.setYRot((float) at[3]);
                System.out.println("[hidemodels-gametest] nearby: the model in front of you is "
                        + "found whichever way you face, and listed first");
            });

            // 14. MODELS TOLD APART BY A NUMBER, as some servers draw theirs: plain oak boats whose
            //     custom_model_data picks the model. A line ending in a number matches it whole,
            //     so #12 is not #1234. Then a model gone before the screen opens - an effect -
            //     still listed, dimmed, and hidden from there.
            context.runOnClient(client -> {
                try {
                    ((Screen) screenOf(client)).onClose();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("could not close the screen", e);
                }
            });
            singleplayer.getServer().runCommand("kill @e[type=item_display]");
            singleplayer.getServer().runCommand("kill @e[type=armor_stand]");
            singleplayer.getServer().runCommand("summon item_display ~ ~1 ~ {item:{id:\"oak_boat\","
                    + "components:{\"minecraft:custom_model_data\":{floats:[1234f]}}}}");
            writeConfig("minecraft:oak_boat#12");
            context.waitFor(client -> HideModels.unsaved().contains("minecraft:oak_boat#12"));
            context.waitTicks(10);
            context.runOnClient(client -> {
                String boat = null;
                for (NearbyModels.Nearby n : NearbyModels.nearby(16.0)) {
                    if (!n.gone() && n.id().startsWith("minecraft:oak_boat")) {
                        boat = n.id();
                    }
                }
                if (!"minecraft:oak_boat#1234".equals(boat)) {
                    throw new AssertionError("an oak boat with custom_model_data 1234 read as "
                            + boat);
                }
                if (HideModels.listed(boat)) {
                    throw new AssertionError("the line #12 hid #1234");
                }
            });
            writeConfig("minecraft:oak_boat#1234");
            context.waitFor(client -> HideModels.listed("minecraft:oak_boat#1234"));
            writeConfig("oak_boat");
            context.waitFor(client -> HideModels.listed("minecraft:oak_boat#1234"));
            singleplayer.getServer().runCommand("summon item_display ~2 ~1 ~ {Tags:[\"hm_effect\"],"
                    + "item:{id:\"stone\",components:{\"minecraft:item_model\":"
                    + "\"hidemodels:effect/flash\"}}}");
            context.waitTicks(10);
            singleplayer.getServer().runCommand("kill @e[tag=hm_effect]");
            context.waitTicks(10);
            context.runOnClient(client -> {
                NearbyModels.Nearby effect = null;
                for (NearbyModels.Nearby n : NearbyModels.nearby(16.0)) {
                    if (n.id().equals("hidemodels:effect/")) {
                        effect = n;
                    }
                }
                if (effect == null || !effect.gone()) {
                    throw new AssertionError("a model gone a moment ago is listed as " + effect);
                }
                io.github.simuciokas.hidemodels.Screens.open(client, new HiddenListScreen(null));
            });
            context.waitTicks(5);
            context.takeScreenshot("recently-seen");
            context.runOnClient(client -> {
                // The boat, still here, then what has gone, newest first: the effect and its cell.
                press(5);
                if (!HideModels.listed("hidemodels:effect/flash")) {
                    throw new AssertionError("clicking the gone effect's row did not hide it");
                }
                System.out.println("[hidemodels-gametest] model numbers matched whole, and a model "
                        + "already gone listed and hidden");
            });

            // Kept for a human to look at when a run fails; asserts nothing by itself, because a
            // screenshot comparison would fail on every unrelated resource-pack or lighting change.
            context.takeScreenshot("hidemodels-after-hide");
        }

        // 15. PROFILES FOR A SERVER, on a real one: the harness starts a dedicated server and
        //     joins it, the only way to have an address to link to. Joining switches a profile
        //     linked elsewhere off and leaves one switched by hand alone; Use on this server links
        //     one; and only a fresh join switches again, so a hand switch holds until then.
        writeConfig("[Elsewhere] @other.example.net\nhidemodels:elsewhere/\n[Manual]\nhidemodels:manual/");
        context.waitFor(client -> HideModels.profiles().size() == 2);
        try (var server = context.worldBuilder().createServer()) {
            final String[] here = {null};
            try (var connection = server.connect()) {
                context.waitTicks(20);
                context.runOnClient(client -> {
                    here[0] = HideModels.currentServer();
                    if (here[0] == null || profile("Elsewhere").on() || !profile("Manual").on()) {
                        throw new AssertionError("joining " + here[0] + " left "
                                + HideModels.profiles());
                    }
                    io.github.simuciokas.hidemodels.Screens.open(client, new HiddenListScreen(null));
                });
                context.waitTicks(5);
                context.runOnClient(client -> {
                    // The lists: Unsaved, its cell, then Manual and its count - switched by hand,
                    // so above Elsewhere, which is for another server.
                    press(2);
                    press(7);
                    // Open: the way back, the name box, Use on this server, Copy, Delete.
                    press(5);
                    if (!profile("Manual").servers().equals(java.util.List.of(here[0]))
                            || !profile("Manual").on()) {
                        throw new AssertionError("Use on this server gave " + profile("Manual"));
                    }
                });
                context.waitTicks(5);
                context.takeScreenshot("use-on-this-server");
                context.runOnClient(client -> {
                    // Back on the lists, Manual is now this server's and comes first.
                    press(4);
                    press(6);
                    if (profile("Manual").on()) {
                        throw new AssertionError("switching the profile off by hand did nothing");
                    }
                });
                context.waitTicks(5);
                context.takeScreenshot("server-profiles");
                context.waitTicks(20);
                context.runOnClient(client -> {
                    if (profile("Manual").on()) {
                        throw new AssertionError("a profile switched off by hand came back on "
                                + "without a new join");
                    }
                });
            }
            // A beat for the server to let the last session go: joining again in the same tick
            // stalls the login on 1.21.11.
            context.waitTicks(20);
            try (var connection = server.connect()) {
                context.waitTicks(20);
                context.runOnClient(client -> {
                    if (!profile("Manual").on() || profile("Elsewhere").on()) {
                        throw new AssertionError("joining again left " + HideModels.profiles());
                    }
                    System.out.println("[hidemodels-gametest] server profiles: switched on joining "
                            + here[0] + ", linked from the screen, held until the next join");
                });
            }
        }
    }

    /**
     * Empties the Nearby tab's recently seen list, through its private field rather than API the
     * mod would carry only for this.
     */
    private static void forgetGone() {
        try {
            final java.lang.reflect.Field seen = NearbyModels.class.getDeclaredField("seen");
            seen.setAccessible(true);
            ((java.util.Map<?, ?>) seen.get(null)).clear();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not clear the recently seen list", e);
        }
    }

    private static HideModels.Profile profile(String name) {
        for (HideModels.Profile p : HideModels.profiles()) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        throw new AssertionError("no profile " + name + " in " + HideModels.profiles());
    }
}
