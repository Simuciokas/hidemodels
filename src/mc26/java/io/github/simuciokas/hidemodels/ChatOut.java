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
 * Client-side chat for 26.x. See the 1.21.x copy under src/mc121 for why there are two.
 *
 * <p>THE ONLY FILE THAT DIFFERS BETWEEN VERSIONS. Everything else in this mod compiles unchanged
 * from 1.21.2 to 26.2; this one method does not, because the client-facing chat call was renamed:
 * {@code LocalPlayer.sendSystemMessage} here, {@code displayClientMessage} before. Reflection
 * cannot paper over it - a 1.21.x build is remapped to intermediary, so the runtime method is
 * called something like {@code method_7353} and no name-based lookup would find it.
 *
 * <p>Picking the one LocalPlayer itself declares matters: CommandSource declares
 * {@code sendSystemMessage} on every version, but on 1.21.x the player does not override it and
 * the inherited implementation is the server's, which would print nothing at all.
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
}
