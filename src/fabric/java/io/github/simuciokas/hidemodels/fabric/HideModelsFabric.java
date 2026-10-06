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

import io.github.simuciokas.hidemodels.CommandTree;
import io.github.simuciokas.hidemodels.HideModels;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * What the mod needs from the loader, on Fabric.
 *
 * <p>A registered command rather than an intercepted one: it gets tab completion, and it needs no
 * injection point for the clicked-command path that only exists from 1.21.6.
 *
 * <p>What stays a mixin is what has no event behind it: the render hook and the stack accessor.
 */
public final class HideModelsFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(CommandTree.build(Cmd.INSTANCE)));

        // The config poll. Once a tick rather than once per model piece per frame - see
        // HideModels.tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> HideModels.tick());

        // A server's opt-out lasts for one connection.
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> HideModels.clearServerOverride());
    }
}
