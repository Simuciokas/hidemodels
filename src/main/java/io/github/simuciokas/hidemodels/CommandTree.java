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

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/**
 * {@code /hidemodels}, which opens the screen; everything else is done there.
 *
 * <p>GENERIC IN THE COMMAND SOURCE, the only thing the loaders disagree about: Fabric builds
 * against FabricClientCommandSource and NeoForge against vanilla's CommandSourceStack.
 */
public final class CommandTree {

    /**
     * Brigadier's literal(), from whichever loader is registering the command. Fabric's lives on a
     * class Fabric API renamed between 1.21.x and 26.x, and NeoForge's is vanilla's own.
     */
    public interface Builders<S> {
        LiteralArgumentBuilder<S> literal(String name);
    }

    private CommandTree() {
    }

    public static <S> LiteralArgumentBuilder<S> build(Builders<S> b) {
        // The chat screen closes after the command runs, so opening ours waits until then, or
        // the client would replace it straight away.
        return b.literal(HideModels.MOD_ID).executes(ctx -> {
            final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> Screens.open(mc, new HiddenListScreen(null)));
            return 1;
        });
    }
}
