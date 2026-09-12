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

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;

/**
 * The {@code /hidemodels} tree, defined once for every loader.
 *
 * <p>GENERIC IN THE COMMAND SOURCE, which is the only thing the loaders disagree about: Fabric's
 * client commands are built against FabricClientCommandSource and NeoForge's against vanilla's
 * CommandSourceStack. Everything else - the shape of the tree, what each branch does, what gets
 * suggested - is identical, and duplicating it per loader would be duplicating the part that
 * actually changes when the command grows.
 *
 * <p>So the loader hands in a {@link Builders} of two methods and gets a finished tree back.
 *
 * <p>WHY BRIGADIER AT ALL, rather than the string splitting this replaced: completion. The two
 * arguments worth completing are the two nobody wants to type - an id from the world around you,
 * and a pattern already in your config - and both come from the mod's own state, so the client can
 * offer them without asking the server anything.
 */
public final class CommandTree {

    /**
     * The two brigadier entry points, supplied by whichever loader is registering the command.
     *
     * <p>Fabric's live on a class Fabric API renamed between 1.21.x and 26.x, and NeoForge's are
     * vanilla's own. Both are static methods that cannot be named generically, hence an interface.
     */
    public interface Builders<S> {
        LiteralArgumentBuilder<S> literal(String name);

        <T> RequiredArgumentBuilder<S, T> argument(String name, ArgumentType<T> type);
    }

    private CommandTree() {
    }

    public static <S> LiteralArgumentBuilder<S> build(Builders<S> b) {
        final SuggestionProvider<S> nearby = (context, builder) -> {
            final String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String id : NearbyModels.nearbyIds(HideModels.listRadius())) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(id);
                }
            }
            return builder.buildFuture();
        };
        final SuggestionProvider<S> listed = (context, builder) -> {
            final String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String pattern : HideModels.patterns()) {
                if (pattern.startsWith(prefix)) {
                    builder.suggest(pattern);
                }
            }
            return builder.buildFuture();
        };

        return b.literal(HideModels.MOD_ID)
                .executes(ctx -> status())
                .then(b.literal("help").executes(ctx -> status()))
                .then(b.literal("list")
                        .executes(ctx -> list(HideModels.listRadius(), false))
                        .then(b.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                .executes(ctx -> list(DoubleArgumentType.getDouble(ctx, "radius"), false)))
                        .then(b.literal("bones")
                                .executes(ctx -> list(HideModels.listRadius(), true))
                                .then(b.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                        .executes(ctx -> list(DoubleArgumentType.getDouble(ctx, "radius"), true)))))
                .then(b.literal("add")
                        // greedyString: an id is one token today, but a pattern is a free-form
                        // fragment and nothing stops someone pasting one with a space in it.
                        .then(b.argument("id", StringArgumentType.greedyString())
                                .suggests(nearby)
                                .executes(ctx -> {
                                    HideModels.add(StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                // THE DIRECTIVES, as commands. Each writes the config and reloads, so the file
                // stays the single description of what this mod is doing - a setting changed here
                // and a setting typed into the file can never disagree.
                .then(b.literal("on").executes(ctx -> {
                    HideModels.setEnabled(true);
                    return 1;
                }))
                .then(b.literal("off").executes(ctx -> {
                    HideModels.setEnabled(false);
                    return 1;
                }))
                .then(b.literal("first-person")
                        .then(b.literal("on").executes(ctx -> {
                            HideModels.setFirstPersonOnly(true);
                            return 1;
                        }))
                        .then(b.literal("off").executes(ctx -> {
                            HideModels.setFirstPersonOnly(false);
                            return 1;
                        })))
                .then(b.literal("radius")
                        .then(b.argument("blocks", DoubleArgumentType.doubleArg(1.0, 256.0))
                                .executes(ctx -> {
                                    HideModels.setListRadius(
                                            DoubleArgumentType.getDouble(ctx, "blocks"));
                                    return 1;
                                })))
                .then(b.literal("remove")
                        .then(b.argument("id", StringArgumentType.greedyString())
                                .suggests(listed)
                                .executes(ctx -> {
                                    HideModels.remove(StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })));
    }

    private static int status() {
        HideModels.status();
        return 1;
    }

    private static int list(double radius, boolean bones) {
        NearbyModels.report(radius, bones);
        return 1;
    }
}
