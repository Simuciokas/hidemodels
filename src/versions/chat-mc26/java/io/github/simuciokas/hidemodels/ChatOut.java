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
package io.github.simuciokas.hidemodels;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side chat for 26.x. See the copy in ../chat-mc121 for why there are two.
 *
 * <p>The chat call is named {@code sendSystemMessage} here and {@code displayClientMessage}
 * before 26.x. Reflection cannot bridge it: a 1.21.x build runs against intermediary, where the
 * method answers to something like {@code method_7353}.
 *
 * <p>It must be the one LocalPlayer itself declares. CommandSource declares
 * {@code sendSystemMessage} on every version, but on 1.21.x the player does not override it and
 * the inherited implementation is the server's, which prints nothing.
 */
public final class ChatOut {

    private ChatOut() {
    }

    public static void say(Component text) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.sendSystemMessage(text);
    }

    /** Over the hotbar for a few seconds, for a change made without the screen open. */
    public static void actionBar(Component text) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.sendOverlayMessage(text);
    }
}
