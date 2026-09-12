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

import com.mojang.brigadier.arguments.ArgumentType;
import io.github.simuciokas.hidemodels.CommandTree;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The two builder methods Fabric's client command API exposes, for 1.21.x. See src/fabric-mc26.
 *
 * <p>A FOURTH THING THAT MOVED, and this one is Fabric API's rather than Minecraft's: the class
 * holding these was ClientCommandManager through 1.21.11 and is ClientCommands from 26.1. The
 * methods, and the FabricClientCommandSource they build against, are identical - so the whole
 * difference is which class name to type, and everything that builds the actual command tree is
 * written once against this shim.
 *
 * <p>It splits on the same boundary as ChatOut, 1.21.x against 26.x, which is why it lives beside
 * it rather than inventing a third set of directories.
 */
public final class Cmd implements CommandTree.Builders<FabricClientCommandSource> {

    public static final Cmd INSTANCE = new Cmd();

    private Cmd() {
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommandManager.literal(name);
    }

    @Override
    public <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
            String name, ArgumentType<T> type) {
        return ClientCommandManager.argument(name, type);
    }
}
