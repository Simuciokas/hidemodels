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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import io.github.simuciokas.hidemodels.CommandTree;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The two builder methods Fabric's client command API exposes, for 26.x. See ../cmd-mc121.
 *
 * <p>FABRIC API'S OWN RENAME, not Minecraft's: the class holding these was ClientCommandManager
 * through 1.21.11 and is ClientCommands from 26.1. The methods and the FabricClientCommandSource
 * are identical, so the whole difference is which name to type - and it splits on the same 1.21.x
 * against 26.x boundary as ChatOut, which is why it lives beside it.
 */
public final class Cmd implements CommandTree.Builders<FabricClientCommandSource> {

    public static final Cmd INSTANCE = new Cmd();

    private Cmd() {
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommands.literal(name);
    }

    @Override
    public <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
            String name, ArgumentType<T> type) {
        return ClientCommands.argument(name, type);
    }

    /**
     * The live client-command dispatcher, for the gametest's tab-completion check.
     *
     * <p>Here rather than in the test because it is the same class that moved between versions.
     */
    public static CommandDispatcher<FabricClientCommandSource> dispatcher() {
        return ClientCommands.getActiveDispatcher();
    }
}
