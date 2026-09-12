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

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.simuciokas.hidemodels.HideModels;
import io.github.simuciokas.hidemodels.NearbyModels;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The {@code /hidemodels} tree.
 *
 * <p>WHY THIS IS BETTER THAN THE STRING PARSING IT REPLACED. The old handler took the raw command
 * line and split it on spaces, which worked but gave the player nothing: no completion, no
 * argument checking, and a typo reported only after the fact. Brigadier knows the shape of the
 * command, so the client completes it as you type - and the two arguments worth completing are
 * exactly the two that are tedious to type by hand:
 *
 * <ul>
 *   <li>{@code add} suggests the ids of models AROUND YOU, straight from the same scan the list
 *       command prints. An id is a resource path nobody memorises; now it is a Tab away.
 *   <li>{@code remove} suggests the patterns actually in the config, so it cannot be misspelled.
 * </ul>
 *
 * <p>Built once, here, against the two-method shim in {@link Cmd} - which is the only part that
 * differs between 1.21.x and 26.x, where Fabric API renamed the class holding those methods.
 */
public final class HideModelsCommand {

    private HideModelsCommand() {
    }

    /** Ids of item_display models near the player, as completion for {@code add}. */
    private static final SuggestionProvider<FabricClientCommandSource> NEARBY = (context, builder) -> {
        final String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (String id : NearbyModels.nearbyIds(HideModels.listRadius())) {
            if (id.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    /** What is currently hidden, as completion for {@code remove}. */
    private static final SuggestionProvider<FabricClientCommandSource> LISTED = (context, builder) -> {
        final String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (String pattern : HideModels.patterns()) {
            if (pattern.startsWith(prefix)) {
                builder.suggest(pattern);
            }
        }
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return Cmd.<FabricClientCommandSource>literal(HideModels.MOD_ID)
                .executes(ctx -> {
                    HideModels.status();
                    return 1;
                })
                .then(Cmd.literal("help").executes(ctx -> {
                    HideModels.status();
                    return 1;
                }))
                .then(Cmd.literal("list")
                        .executes(ctx -> list(HideModels.listRadius(), false))
                        .then(Cmd.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                .executes(ctx -> list(DoubleArgumentType.getDouble(ctx, "radius"), false)))
                        .then(Cmd.literal("bones")
                                .executes(ctx -> list(HideModels.listRadius(), true))
                                .then(Cmd.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                        .executes(ctx -> list(DoubleArgumentType.getDouble(ctx, "radius"), true)))))
                .then(Cmd.literal("add")
                        // greedyString: an id is one token today, but a pattern is a free-form
                        // fragment and nothing stops someone pasting one with a space in it.
                        .then(Cmd.argument("id", StringArgumentType.greedyString())
                                .suggests(NEARBY)
                                .executes(ctx -> {
                                    HideModels.add(StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Cmd.literal("remove")
                        .then(Cmd.argument("id", StringArgumentType.greedyString())
                                .suggests(LISTED)
                                .executes(ctx -> {
                                    HideModels.remove(StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })));
    }

    private static int list(double radius, boolean bones) {
        NearbyModels.report(radius, bones);
        return 1;
    }
}
