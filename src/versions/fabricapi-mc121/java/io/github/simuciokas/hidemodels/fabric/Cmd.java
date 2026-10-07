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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Fabric's client-command literal(), for 1.21.x. See ../fabricapi-mc26.
 *
 * <p>Fabric API's rename, not Minecraft's: ClientCommandManager through 1.21.11, ClientCommands
 * from 26.1. The method and the source type are identical; only the class name differs.
 */
public final class Cmd implements CommandTree.Builders<FabricClientCommandSource> {

    public static final Cmd INSTANCE = new Cmd();

    private Cmd() {
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommandManager.literal(name);
    }
}
