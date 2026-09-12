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
package io.github.simuciokas.hidemodels.fabric;

import io.github.simuciokas.hidemodels.HideModels;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * What the mod needs from the loader, on Fabric.
 *
 * <p>THIS CLASS REPLACED THREE MIXINS, which is the point of depending on Fabric API at all:
 *
 * <ul>
 *   <li>{@code /hidemodels} was caught by injecting into the client's command-send path. That meant
 *       hooking TWO methods, because a clicked command does not go through the same one as a typed
 *       command from 1.21.6 - and the second hook had to be optional, because it does not exist
 *       before that. A registered command needs neither, and gets tab completion for free.
 *   <li>The disconnect hook cleared the server's opt-out. Its target takes a Component up to 1.20.6
 *       and a DisconnectionDetails after, and a Mixin handler must mirror its target exactly - so
 *       that one mixin needed a per-version copy. The event does not care.
 * </ul>
 *
 * <p>What is left as a mixin is what genuinely has no event behind it: the render hook, and the
 * accessor that reads an item_display's stack.
 */
public final class HideModelsFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(HideModelsCommand.build()));

        // A server's opt-out lasts for one connection. This is the whole of what the disconnect
        // mixin did, minus the version split its signature forced.
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> HideModels.clearServerOverride());
    }
}
