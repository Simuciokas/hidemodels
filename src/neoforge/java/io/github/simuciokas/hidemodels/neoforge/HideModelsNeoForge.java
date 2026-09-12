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
package io.github.simuciokas.hidemodels.neoforge;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.simuciokas.hidemodels.CommandTree;
import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What the mod needs from the loader, on NeoForge. The counterpart of HideModelsFabric.
 *
 * <p>The same two things, under different names: a client command to register, and a disconnect to
 * hear about. Everything the command DOES lives in CommandTree, shared with Fabric - the only part
 * that differs is the source type brigadier builds against, which here is vanilla's own
 * CommandSourceStack rather than a Fabric interface.
 *
 * <p>NOTHING ELSE IN THE MOD KNOWS WHICH LOADER IT IS ON. The render hook is a mixin against a
 * vanilla class, and NeoForge runs with Mojang's official names, so the same mixin applies
 * unchanged - which is the whole reason this port is two small classes rather than a second
 * implementation.
 */
@Mod(value = HideModels.MOD_ID, dist = Dist.CLIENT)
public final class HideModelsNeoForge implements CommandTree.Builders<CommandSourceStack> {

    public HideModelsNeoForge(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
                event -> event.getDispatcher().register(CommandTree.build(this)));

        // A server's opt-out lasts one connection, exactly as on Fabric.
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> HideModels.clearServerOverride());
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return Commands.literal(name);
    }

    @Override
    public <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(
            String name, ArgumentType<T> type) {
        return Commands.argument(name, type);
    }
}
