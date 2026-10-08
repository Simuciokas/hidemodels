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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
            final String key = bones ? id : modelOf(id);
            final Group g = found.computeIfAbsent(key, k -> new Group());
            g.pieces++;
            if (dSq < g.nearestSq) {
                g.nearestSq = dSq;
            }
        }
        return found;
    }

    /** Everything up to the last '/' is the fragment the config wants for the whole model. */
    static String modelOf(String id) {
        final int cut = id.lastIndexOf('/');
        return cut < 0 ? id : id.substring(0, cut + 1);
    }

    /** A row of the Nearby tab; {@code goneFor} is how long ago it was last seen, 0 if it is here. */
    public record Nearby(String id, int pieces, double distance, long goneFor) {

        public boolean gone() {
            return goneFor > 0;
        }
    }

    /**
     * How long a model that has gone stays listed. Long enough to cast a spell, or watch an effect
     * play, then open the screen and hide it - by then it has usually gone.
     */
    static final long RECENT_MS = 30_000L;
    /** Ticks between looks at what is around, for the recently seen list. */
    private static final int SAMPLE_TICKS = 5;

    private static final class Seen {
        long at;
        int pieces;
        double nearestSq;
    }

    /** By bone id, what the looks found, kept RECENT_MS after the last time each was there. */
    private static final Map<String, Seen> seen = new HashMap<>();
    private static Object sampledLevel;
    private static int sampleTick;

    /**
     * Notes what is in range, every SAMPLE_TICKS ticks, so the Nearby tab can list a model that
     * has already gone - an effect that lasts a second is otherwise over before the screen opens.
     * The same pass as the tab's own, a few times a second; another world starts it afresh.
     */
    public static void sample() {
        if (++sampleTick % SAMPLE_TICKS != 0) {
            return;
        }
        final ClientLevel level = Minecraft.getInstance().level;
        if (level != sampledLevel) {
            seen.clear();
            sampledLevel = level;
        }
        if (level == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        for (Map.Entry<String, Group> e : scan(HideModels.listRadius(), true).entrySet()) {
            final Seen s = seen.computeIfAbsent(e.getKey(), k -> new Seen());
            s.at = now;
            s.pieces = e.getValue().pieces;
            s.nearestSq = e.getValue().nearestSq;
        }
        seen.values().removeIf(s -> now - s.at > RECENT_MS);
    }

    /**
     * Rows for what was seen lately but is not here now, newest first: grouped by model, or one
     * model's bones when {@code model} is given. {@code here} holds the rows already listed.
     */
    private static List<Nearby> gone(Map<String, Group> here, String model) {
        final long now = System.currentTimeMillis();
        final Map<String, Nearby> rows = new HashMap<>();
        for (Map.Entry<String, Seen> e : seen.entrySet()) {
            final String bone = e.getKey();
            final String key = model == null ? modelOf(bone) : bone;
            if (here.containsKey(key) || now - e.getValue().at > RECENT_MS
                    || (model != null && !bone.toLowerCase(Locale.ROOT).startsWith(model))) {
                continue;
            }
            final Seen s = e.getValue();
            final Nearby was = rows.get(key);
            final long ago = Math.max(1, now - s.at);
            final double distance = Math.sqrt(s.nearestSq);
            rows.put(key, was == null ? new Nearby(key, s.pieces, distance, ago)
                    : new Nearby(key, was.pieces() + s.pieces, Math.min(was.distance(), distance),
                                 Math.min(was.goneFor(), ago)));
        }
        final List<Nearby> out = new ArrayList<>(rows.values());
        out.sort(Comparator.comparingLong(Nearby::goneFor));
        return out;
    }

    /** How far off the line of sight a model can be and still be the one looked at. */
    private static final double LOOK_CONE_DEGREES = 15.0;
    /**
     * How far above a piece the model is taken to reach. Plugins often stand every piece of a model
     * on its base and lift each bone by its display transform, so the piece's own position can sit
     * well below what you are looking at.
     */
    private static final double MODEL_HEIGHT = 3.0;
    /** Degrees a block of distance counts for, so the nearer of two models in line wins. */
    private static final double DEGREES_PER_BLOCK = 0.2;
    /**
     * Nearer than this, a point gives no direction worth reading: the mount you ride stands where
     * you do, and would seem to be ahead of you whichever way you faced.
     */
    private static final double LOOK_MIN_DISTANCE = 1.0;

    /**
     * The model the player is looking at, within the radius: the one closest to the line of sight
     * and no more than LOOK_CONE_DEGREES off it. Null when nothing is.
     *
     * <p>From yaw, pitch and coordinates rather than the view and position vectors - the same
     * reason scan measures against the player entity: those keep one name across the 1.21.x jar.
     */
    public static String lookedAt(double radius) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        final Entity player = mc.player;
        if (level == null || player == null) {
            return null;
        }
        final double yaw = Math.toRadians(player.getYRot());
        final double pitch = Math.toRadians(player.getXRot());
        final double dx = -Math.sin(yaw) * Math.cos(pitch);
        final double dy = -Math.sin(pitch);
        final double dz = Math.cos(yaw) * Math.cos(pitch);
        final double eyeX = player.getX();
        final double eyeY = player.getEyeY();
        final double eyeZ = player.getZ();
        final double r2 = radius * radius;
        String best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : level.entitiesForRendering()) {
            final String id = HideModels.modelIdOfEntity(e);
            if (id == null || e.distanceToSqr(player) > r2) {
                continue;
            }
            final double vx = e.getX() - eyeX;
            final double vz = e.getZ() - eyeZ;
            // The point of the piece's column nearest the line of sight, in a few steps up it.
            double nearest = Double.MAX_VALUE;
            double length = 0;
            for (int step = 0; step <= 6; step++) {
                final double vy = e.getY() + MODEL_HEIGHT * step / 6 - eyeY;
                final double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (len < LOOK_MIN_DISTANCE) {
                    continue;
                }
                final double cos = Math.max(-1, Math.min(1, (vx * dx + vy * dy + vz * dz) / len));
                final double angle = Math.toDegrees(Math.acos(cos));
                if (angle < nearest) {
                    nearest = angle;
                    length = len;
                }
            }
            final double score = nearest + length * DEGREES_PER_BLOCK;
            if (nearest <= LOOK_CONE_DEGREES && score < bestScore) {
                bestScore = score;
                best = modelOf(id);
            }
        }
        return best;
    }

    /** Models within the radius, nearest first, then those seen lately and gone, newest first. */
    public static List<Nearby> nearby(double radius) {
        final Map<String, Group> here = scan(radius, false);
        final List<Nearby> out = new ArrayList<>();
        for (Map.Entry<String, Group> e : here.entrySet()) {
            out.add(new Nearby(e.getKey(), e.getValue().pieces,
                    Math.sqrt(e.getValue().nearestSq), 0));
        }
        out.sort(Comparator.comparingDouble(Nearby::distance));
        out.addAll(gone(here, null));
        return out;
    }

    /** One model's bones within the radius, nearest first, then its bones seen lately and gone. */
    public static List<Nearby> bones(double radius, String model) {
        // From the start, not as a substring the way the hide list matches: a substring would pull
        // in another model that merely contains the same word.
        final String prefix = model.toLowerCase(Locale.ROOT);
        final Map<String, Group> here = new HashMap<>();
        for (Map.Entry<String, Group> e : scan(radius, true).entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                here.put(e.getKey(), e.getValue());
            }
        }
        final List<Nearby> out = new ArrayList<>();
        for (Map.Entry<String, Group> e : here.entrySet()) {
            out.add(new Nearby(e.getKey(), e.getValue().pieces,
                    Math.sqrt(e.getValue().nearestSq), 0));
        }
        out.sort(Comparator.comparingDouble(Nearby::distance));
        out.addAll(gone(here, prefix));
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

    /** Models within the radius that have at least one bone hidden by a line of the config. */
    public static Set<String> withHiddenBones(double radius) {
        final Set<String> models = new HashSet<>();
        for (String bone : scan(radius, true).keySet()) {
            final int cut = bone.lastIndexOf('/');
            if (cut >= 0 && HideModels.listed(bone)) {
                models.add(bone.substring(0, cut + 1));
            }
        }
        return models;
    }

    /** Pieces within the radius that one config line hides, matched the way the hide list matches. */
    public static int piecesMatching(double radius, String pattern) {
        final String fragment = pattern.toLowerCase(Locale.ROOT);
        int pieces = 0;
        for (Map.Entry<String, Group> e : scan(radius, true).entrySet()) {
            if (HideModels.covers(e.getKey().toLowerCase(Locale.ROOT), fragment)) {
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
