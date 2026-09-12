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

import io.github.simuciokas.hidemodels.mixin.ItemDisplayAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code /hidemodels list} report: every item_model id within a radius, so the ids you need for
 * the config can be read off the screen instead of guessed.
 *
 * <p>Grouped by MODEL by default - {@code modelengine:some_mount/} - because that is what goes in
 * the config; {@code list bones} prints the individual bone ids for hiding just one piece.
 *
 * <p>Nothing here runs on a timer: it is a one-shot scan of the level's render list, triggered by
 * the command, so it costs nothing when unused.
 */
public final class NearbyModels {

    /** Chat gets unreadable long before this; the tail is summarised instead. */
    private static final int MAX_LINES = 40;

    private NearbyModels() {
    }

    private static final class Group {
        int pieces;
        double nearestSq = Double.MAX_VALUE;
    }

    /** Runs the scan and prints it. {@code bones} lists full ids rather than grouping by model. */
    public static void report(double radius, boolean bones) {
        report(radius, bones, null);
    }

    /**
     * As above, restricted to ids under one model.
     *
     * <p>This is what the piece count links to: a model row says "x7", and clicking it asks for
     * those seven bones and nothing else. Unfiltered {@code list bones} prints every bone of every
     * model in range, which near a couple of mounts is more lines than chat will show - the filter
     * is what makes "see the mount, pick its head" a two-click job rather than a squint.
     */
    public static void report(double radius, boolean bones, String filter) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            say(Component.literal("hidemodels: not in a world").withStyle(ChatFormatting.RED));
            return;
        }
        final Map<String, Group> found = scan(radius, bones, filter);
        int scanned = 0;
        for (Group g : found.values()) {
            scanned += g.pieces;
        }

        if (found.isEmpty()) {
            say(Component.literal("hidemodels: no item_display models"
                    + (filter == null ? "" : " under '" + filter + "'")
                    + " within " + fmt(radius) + " blocks")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        final List<Map.Entry<String, Group>> rows = new ArrayList<>(found.entrySet());
        rows.sort(Comparator.comparingDouble(x -> x.getValue().nearestSq));

        say(Component.literal("hidemodels: " + found.size() + (bones ? " bone" : " model")
                + (found.size() == 1 ? "" : "s")
                + (filter == null ? "" : " of " + filter)
                + " within " + fmt(radius) + " blocks"
                + " (" + scanned + " piece" + (scanned == 1 ? "" : "s") + ")")
                .withStyle(ChatFormatting.AQUA));

        final int shown = Math.min(rows.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            final Map.Entry<String, Group> row = rows.get(i);
            final Group g = row.getValue();
            final boolean listed = HideModels.listed(row.getKey());

            // CLICK THE ID TO HIDE IT. The whole point of this report is to get ids out of the
            // game and into the config, and the shortest path from "I can see it" to "it is gone"
            // is one click. Only ids that are NOT yet hidden are clickable: a click that silently
            // did nothing would be worse than no click at all, and removal is deliberately not
            // wired to a click, because undoing something by clicking the same place you just
            // clicked is how people hide things by accident.
            final Component id = listed
                    ? Component.literal(row.getKey()).withStyle(ChatFormatting.GREEN)
                    : Component.literal(row.getKey()).withStyle(
                            ClickRun.style("/" + HideModels.MOD_ID + " add " + row.getKey())
                                    .withColor(ChatFormatting.WHITE)
                                    .withUnderlined(true));

            // CLICK THE COUNT TO OPEN THE MODEL UP. The id hides the whole thing; the piece count
            // beside it asks "which pieces?" and answers in place, listing just this model's bones
            // so one of them can be hidden on its own - the "I want to see past the head but keep
            // the mount" case, which otherwise means typing list bones and reading past every other
            // model in range. Only on model rows with pieces to open: in bones mode the row IS a
            // piece, and an id with no slash has nothing underneath it, so both stay plain text
            // rather than offering a click that would just reprint the same line.
            final boolean drillable = !bones && row.getKey().endsWith("/");
            final Style countStyle = drillable
                    ? ClickRun.style("/" + HideModels.MOD_ID + " list bones " + fmt(radius)
                            + " " + row.getKey()).withColor(ChatFormatting.GRAY)
                    : Style.EMPTY.withColor(ChatFormatting.DARK_GRAY);

            final Component line = Component.literal("  x" + g.pieces + "  ")
                    .withStyle(countStyle)
                    .append(id)
                    .append(Component.literal("  " + fmt(Math.sqrt(g.nearestSq)) + "m")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(listed ? Component.literal("  hidden").withStyle(ChatFormatting.GREEN)
                                   : Component.literal("  click to hide")
                                             .withStyle(ChatFormatting.DARK_GRAY));
            say(line);
        }
        if (rows.size() > shown) {
            say(Component.literal("  ... " + (rows.size() - shown) + " more (narrow the radius)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * One pass over the render list, grouped the way the config wants it.
     *
     * <p>Shared with the command's tab completion, which suggests exactly what this report prints -
     * the ids around you. Two scans that could disagree about what is nearby would be a small but
     * infuriating bug: completion offering an id the list does not show, or the reverse.
     */
    static Map<String, Group> scan(double radius, boolean bones) {
        return scan(radius, bones, null);
    }

    /** As above, keeping only ids under {@code filter} - the model a piece count was clicked on. */
    static Map<String, Group> scan(double radius, boolean bones, String filter) {
        final String prefix = (filter == null) ? null : filter.toLowerCase(Locale.ROOT);
        final Map<String, Group> found = new HashMap<>();
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return found;
        }
        final double r2 = radius * radius;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Display.ItemDisplay display)) {
                continue;
            }
            // MEASURED AGAINST THE PLAYER ENTITY, not against its position vector, and that is
            // not a style choice: position() is one of the few members this mod touches whose
            // intermediary name CHANGED mid-range - method_19538 through 1.21.8, method_73189
            // after - which split an otherwise identical jar in two. Taking the entity overload
            // means 1.21.5 through 1.21.11 compile to the same bytes, and it is the shorter way to
            // say the same thing.
            final double dSq = e.distanceToSqr(mc.player);
            if (dSq > r2) {
                continue;
            }
            final ItemStack stack = ((ItemDisplayAccessor) display).hidemodels$getItemStack();
            final String id = HideModels.modelIdOf(stack);
            if (id == null) {
                continue;
            }
            // MATCHED FROM THE START, not as a substring the way the hide list matches: the filter
            // is a model prefix the report itself produced, and "under this model" means exactly
            // that. Substring matching here would pull in another model that happened to contain
            // the same word, which is the one thing a drill-down must not do.
            if (prefix != null && !id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                continue;
            }
            // Group by the model, i.e. everything up to and including the last '/', which is
            // exactly the fragment the config wants. Ids with no slash stand alone.
            final int cut = id.lastIndexOf('/');
            final String key = (bones || cut < 0) ? id : id.substring(0, cut + 1);
            final Group g = found.computeIfAbsent(key, k -> new Group());
            g.pieces++;
            if (dSq < g.nearestSq) {
                g.nearestSq = dSq;
            }
        }
        return found;
    }

    /** Model ids within the radius, nearest first - the completion for {@code /hidemodels add}. */
    public static List<String> nearbyIds(double radius) {
        final List<Map.Entry<String, Group>> rows = new ArrayList<>(scan(radius, false).entrySet());
        rows.sort(Comparator.comparingDouble(x -> x.getValue().nearestSq));
        final List<String> out = new ArrayList<>(rows.size());
        for (Map.Entry<String, Group> row : rows) {
            out.add(row.getKey());
        }
        return out;
    }

    /** One decimal, without dragging in String.format's locale surprises. */
    private static String fmt(double v) {
        return Double.toString(Math.round(v * 10.0) / 10.0);
    }

    /**
     * Client-side chat, so nothing is sent to the server.
     *
     * <p>Delegated to ChatOut, the one class with a per-version copy - see src/mc26 and src/mc121.
     */
    public static void say(Component text) {
        ChatOut.say(text);
    }
}
