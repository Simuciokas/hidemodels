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

import io.github.simuciokas.hidemodels.NearbyModels;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Sample models to try the mod on by hand, for {@code ./gradlew runDemo}.
 *
 * <p>Built the way ModelEngine builds them - one item_display per bone, each carrying an
 * item_model id - plus an armor stand wearing a piece on its head, the older plugins' shape. They
 * are placed in front of you every time you join a single-player world, replacing the last set.
 * The pieces show as vanilla blocks through the item definitions under assets/hidemodels_demo.
 *
 * <p>Does nothing unless the demo launch sets the property.
 */
public final class DemoModels implements ClientModInitializer {

    private static final String ENABLE_PROPERTY = "hidemodels.demo";
    private static final String TAG = "hidemodels_demo";
    private static final String NAMESPACE = "hidemodels_demo:";

    @Override
    public void onInitializeClient() {
        if (System.getProperty(ENABLE_PROPERTY) == null) {
            return;
        }
        final Thread watcher = new Thread(DemoModels::watch, "hidemodels-demo");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** Each world joined gets its own integrated server, which is how a fresh join is told apart. */
    private static void watch() {
        MinecraftServer placedIn = null;
        while (true) {
            final Minecraft mc = Minecraft.getInstance();
            final MinecraftServer server = mc == null ? null : mc.getSingleplayerServer();
            if (server != null && server != placedIn && mc.player != null) {
                placedIn = server;
                final double x = mc.player.getX();
                final double y = mc.player.getY();
                final double z = mc.player.getZ();
                final float yaw = mc.player.getYRot();
                server.execute(() -> place(server, x, y, z, yaw));
                mc.execute(() -> NearbyModels.say(Component.literal("hidemodels demo: a dragon, a "
                        + "golem, a statue and a lantern are in front of you. Open /hidemodels, "
                        + "or bind Open Hide Models in Controls > Key Binds.")
                        .withStyle(ChatFormatting.AQUA)));
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * Lays the scene out relative to where you stand and face. Positions are (right, up, forward)
     * from your feet; every piece is turned to your facing, so a scale's z is always the length.
     * The gametest calls this too, so a summon that stops parsing fails a test rather than leaving
     * the demo world empty.
     */
    static void place(MinecraftServer server, double x, double y, double z, float yaw) {
        final Scene scene = new Scene(x, y, z, yaw);
        scene.commands.add("kill @e[tag=" + TAG + "]");

        // Head towards you, tail away.
        scene.piece("dragon/body", 0, 1.4, 7.0, 1.4f, 1.0f, 2.2f);
        scene.piece("dragon/head", 0, 2.0, 5.3, 0.9f, 0.9f, 0.9f);
        scene.piece("dragon/wing_left", -1.6, 1.9, 7.0, 1.8f, 0.15f, 1.4f);
        scene.piece("dragon/wing_right", 1.6, 1.9, 7.0, 1.8f, 0.15f, 1.4f);
        scene.piece("dragon/tail", 0, 1.2, 8.9, 0.5f, 0.5f, 1.6f);

        scene.piece("golem/body", 4.5, 1.3, 6.0, 1.0f, 1.4f, 0.7f);
        scene.piece("golem/head", 4.5, 2.45, 6.0, 0.8f, 0.8f, 0.8f);
        scene.piece("golem/arm_left", 3.65, 1.25, 6.0, 0.35f, 1.3f, 0.35f);
        scene.piece("golem/arm_right", 5.35, 1.25, 6.0, 0.35f, 1.3f, 0.35f);

        // No slash, so a model of one piece: the list shows it without a piece count to open.
        scene.piece("lantern", 2.2, 0.5, 3.5, 0.6f, 0.6f, 0.6f);

        scene.stand("statue/head", -2.5, 5.0);

        final CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        for (String command : scene.commands) {
            server.getCommands().performPrefixedCommand(source, command);
        }
    }

    private static final class Scene {
        final List<String> commands = new ArrayList<>();
        final double x;
        final double y;
        final double z;
        final float yaw;
        final double forwardX;
        final double forwardZ;
        final double rightX;
        final double rightZ;

        Scene(double x, double y, double z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            final double rad = Math.toRadians(yaw);
            forwardX = -Math.sin(rad);
            forwardZ = Math.cos(rad);
            rightX = -Math.cos(rad);
            rightZ = -Math.sin(rad);
        }

        void piece(String bone, double right, double up, double forward,
                   float sx, float sy, float sz) {
            commands.add(String.format(Locale.ROOT,
                    "summon item_display %.2f %.2f %.2f {Tags:[\"%s\"],Rotation:[%.1ff,0f],"
                            + "item:{id:\"stone\",components:{\"minecraft:item_model\":\"%s%s\"}},"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],"
                            + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],"
                            + "scale:[%.2ff,%.2ff,%.2ff]}}",
                    x + rightX * right + forwardX * forward, y + up,
                    z + rightZ * right + forwardZ * forward,
                    TAG, yaw, NAMESPACE, bone, sx, sy, sz));
        }

        /** An armor stand facing you, wearing the piece on its head. */
        void stand(String bone, double right, double forward) {
            final String standTag = TAG + "_stand";
            commands.add(String.format(Locale.ROOT,
                    "summon armor_stand %.2f %.2f %.2f {Tags:[\"%s\",\"%s\"],Rotation:[%.1ff,0f]}",
                    x + rightX * right + forwardX * forward, y,
                    z + rightZ * right + forwardZ * forward,
                    TAG, standTag, yaw + 180f));
            commands.add("item replace entity @e[tag=" + standTag + ",limit=1] armor.head with "
                    + "stone[minecraft:item_model=\"" + NAMESPACE + bone + "\"]");
        }
    }
}
