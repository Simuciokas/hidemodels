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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * The models around you, as the screen lists them: every item_model id within a radius, grouped by
 * MODEL - {@code modelengine:some_mount/} - because that is what goes in the config, and one
 * model's bones when a single piece is wanted.
 *
 * <p>Nothing here runs on a timer: it is a scan of the level's render list, made while the screen
 * is open, so it costs nothing otherwise.
 */
public final class NearbyModels {

    private NearbyModels() {
    }

    private static final class Group {
        int pieces;
        double nearestSq = Double.MAX_VALUE;
    }

    /** One pass over the render list, grouped by model, or by bone when {@code bones} is set. */
    static Map<String, Group> scan(double radius, boolean bones) {
        final Map<String, Group> found = new HashMap<>();
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return found;
        }
        final double r2 = radius * radius;
        for (Entity e : level.entitiesForRendering()) {
            final String id = HideModels.modelIdOfEntity(e);
            if (id == null) {
                continue;
            }
            // Against the player ENTITY, not its position vector: position() has two different
            // intermediary names across 1.21.5-1.21.11, and this overload has one, so the whole
            // range compiles to the same bytes.
            final double dSq = e.distanceToSqr(mc.player);
            if (dSq > r2) {
                continue;
            }
            // Everything up to the last '/' is the fragment the config wants.
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

    public record Nearby(String id, int pieces, double distance) {
    }

    /** Models within the radius, nearest first. */
    public static List<Nearby> nearby(double radius) {
        final List<Nearby> out = new ArrayList<>();
        for (Map.Entry<String, Group> e : scan(radius, false).entrySet()) {
            out.add(new Nearby(e.getKey(), e.getValue().pieces, Math.sqrt(e.getValue().nearestSq)));
        }
        out.sort(Comparator.comparingDouble(Nearby::distance));
        return out;
    }

    /** One model's bones within the radius, nearest first. */
    public static List<Nearby> bones(double radius, String model) {
        // From the start, not as a substring the way the hide list matches: a substring would pull
        // in another model that merely contains the same word.
        final String prefix = model.toLowerCase(Locale.ROOT);
        final List<Nearby> out = new ArrayList<>();
        for (Map.Entry<String, Group> e : scan(radius, true).entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(new Nearby(e.getKey(), e.getValue().pieces, Math.sqrt(e.getValue().nearestSq)));
            }
        }
        out.sort(Comparator.comparingDouble(Nearby::distance));
        return out;
    }

    /** Model ids within the radius, nearest first: the Nearby tab's rows. */
    public static List<String> nearbyIds(double radius) {
        final List<String> out = new ArrayList<>();
        for (Nearby n : nearby(radius)) {
            out.add(n.id());
        }
        return out;
    }

    /** Pieces within the radius that one config line hides, matched the way the hide list matches. */
    public static int piecesMatching(double radius, String pattern) {
        final String fragment = pattern.toLowerCase(Locale.ROOT);
        int pieces = 0;
        for (Map.Entry<String, Group> e : scan(radius, true).entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).contains(fragment)) {
                pieces += e.getValue().pieces;
            }
        }
        return pieces;
    }

    /** One decimal, without dragging in String.format's locale surprises. */
    static String fmt(double v) {
        return Double.toString(Math.round(v * 10.0) / 10.0);
    }

    /**
     * Client-side chat, so nothing is sent to the server.
     *
     * <p>Delegated to ChatOut, which has a per-version copy - see src/versions/chat-mc121 and -mc26.
     */
    public static void say(Component text) {
        ChatOut.say(text);
    }
}
