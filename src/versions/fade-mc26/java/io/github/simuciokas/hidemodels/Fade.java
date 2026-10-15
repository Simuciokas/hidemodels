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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * Draws a model see-through, on 26.x: an item display, or the piece an armor stand wears on its
 * head.
 *
 * <p>The model's draw goes through a collector that stands in for the game's: each item it is
 * given comes out with its faces swapped for copies that use the translucent item render type and
 * a tint carrying the opacity. A face takes its colour from a tint only when it is tinted, so a
 * face that was not is pointed at a tint added for it.
 *
 * <p>By reflection where the 26.x versions differ: the collector's items arrive as a list of faces
 * up to 26.2 and as ItemQuads from 26.3, and a face's material record changed shape at 26.3. A
 * proxy stands in for the collector, so whatever else the interface holds on a version passes
 * through untouched.
 */
public final class Fade {

    private Fade() {
    }

    /** The opacity of the model being drawn, set as its draw starts and taken by wrap. */
    private static float pending = 1f;

    public static void begin(float opacity) {
        pending = opacity;
    }

    /** The collector to draw the current model with: the game's, or a fading stand-in. */
    public static SubmitNodeCollector wrap(SubmitNodeCollector collector) {
        final float opacity = pending;
        pending = 1f;
        return opacity < 1f ? proxy(SubmitNodeCollector.class, collector, opacity) : collector;
    }

    private static <T> T proxy(Class<T> type, Object delegate, float opacity) {
        final InvocationHandler handler = (self, method, args) -> {
            final Object[] call = method.getName().equals("submitItem") && args != null
                                  ? fadeItem(args, opacity) : args;
            final Object result;
            try {
                result = method.invoke(delegate, call);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            // order() hands back the collector the next items go to; it fades them too.
            if (method.getName().equals("order") && result != null
                    && method.getReturnType().isInterface()) {
                return proxy(method.getReturnType(), result, opacity);
            }
            return result;
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                handler));
    }

    /** submitItem's arguments with its tints and faces swapped for faded ones. */
    private static Object[] fadeItem(Object[] args, float opacity) {
        final Object[] out = args.clone();
        int tintsAt = -1;
        for (int i = 0; i < out.length; i++) {
            if (out[i] instanceof int[]) {
                tintsAt = i;
            }
        }
        if (tintsAt < 0) {
            return args;
        }
        final int[] tints = (int[]) out[tintsAt];
        final int added = tints.length;
        final int[] faded = Arrays.copyOf(tints, added + 1);
        for (int t = 0; t < added; t++) {
            faded[t] = withAlpha(tints[t], opacity);
        }
        faded[added] = withAlpha(0xFFFFFFFF, opacity);
        out[tintsAt] = faded;
        for (int i = 0; i < out.length; i++) {
            if (i != tintsAt && out[i] != null && isFaces(out[i])) {
                out[i] = fadedFaces(out[i], added);
            }
        }
        return out;
    }

    private static int withAlpha(int argb, float opacity) {
        final int alpha = Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** A list of faces, or 26.3's ItemQuads holding them. */
    private static boolean isFaces(Object value) {
        if (value instanceof List<?> list) {
            return list.isEmpty() || list.get(0) instanceof BakedQuad;
        }
        return value.getClass().getSimpleName().equals("ItemQuads");
    }

    /**
     * Faded copies, kept by what they were copied from and the tint index added for them. A list
     * of faces is built afresh each frame up to 26.2, so copies are kept per face, which comes from
     * the model and stays; 26.3's ItemQuads comes from the model itself, so it is kept whole.
     */
    private static final Map<Object, Map<Integer, Object>> FACES = new IdentityHashMap<>();
    private static final Map<Object, Map<Integer, Object>> MATERIALS = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    private static Object fadedFaces(Object faces, int addedTint) {
        if (FACES.size() > 16384) {
            FACES.clear();
            MATERIALS.clear();
        }
        try {
            if (faces instanceof List<?> list) {
                return copyList((List<BakedQuad>) list, addedTint);
            }
            final Map<Integer, Object> byTint = FACES.computeIfAbsent(faces, k -> new HashMap<>());
            Object made = byTint.get(addedTint);
            if (made == null) {
                final List<BakedQuad> all =
                        (List<BakedQuad>) faces.getClass().getMethod("all").invoke(faces);
                made = faces.getClass().getMethod("split", List.class)
                        .invoke(null, copyList(all, addedTint));
                byTint.put(addedTint, made);
            }
            return made;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return faces;
        }
    }

    private static List<BakedQuad> copyList(List<BakedQuad> faces, int addedTint)
            throws ReflectiveOperationException {
        final List<BakedQuad> out = new ArrayList<>(faces.size());
        for (BakedQuad q : faces) {
            final Map<Integer, Object> byTint = FACES.computeIfAbsent(q, k -> new HashMap<>());
            Object made = byTint.get(addedTint);
            if (made == null) {
                made = new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                        q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), q.direction(),
                        (BakedQuad.MaterialInfo) material(q.materialInfo(), addedTint));
                byTint.put(addedTint, made);
            }
            out.add((BakedQuad) made);
        }
        return out;
    }

    /**
     * A face's material made translucent: its render types swapped for the translucent ones, its
     * section layer too, and a face without a tint of its own - none, or one past the item's - is
     * pointed at the added tint. Read and built through the record's components, whose shape
     * differs between 26.2 and 26.3.
     */
    private static Object material(Object info, int addedTint) throws ReflectiveOperationException {
        final Map<Integer, Object> byTint = MATERIALS.computeIfAbsent(info, k -> new HashMap<>());
        final Object done = byTint.get(addedTint);
        if (done != null) {
            return done;
        }
        final RecordComponent[] parts = info.getClass().getRecordComponents();
        final Class<?>[] types = new Class<?>[parts.length];
        final Object[] values = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            Object value = parts[i].getAccessor().invoke(info);
            if (parts[i].getName().equals("tintIndex") && value instanceof Integer tint
                    && (tint < 0 || tint >= addedTint)) {
                value = addedTint;
            } else if (value instanceof Enum<?> layer) {
                value = translucentConstant(layer);
            } else if (value != null && translucent().containsKey(value)) {
                value = translucent().get(value);
            }
            values[i] = value;
        }
        final Constructor<?> make = info.getClass().getDeclaredConstructor(types);
        final Object made = make.newInstance(values);
        byTint.put(addedTint, made);
        return made;
    }

    private static Object translucentConstant(Enum<?> value) {
        for (Object constant : value.getDeclaringClass().getEnumConstants()) {
            if (((Enum<?>) constant).name().equals("TRANSLUCENT")) {
                return constant;
            }
        }
        return value;
    }

    /** Each cutout item render type in Sheets, to its translucent namesake. */
    private static Map<Object, Object> translucent;

    private static Map<Object, Object> translucent() {
        if (translucent == null) {
            final Map<Object, Object> map = new IdentityHashMap<>();
            for (Method cutout : Sheets.class.getMethods()) {
                if (!Modifier.isStatic(cutout.getModifiers()) || cutout.getParameterCount() != 0
                        || !cutout.getName().startsWith("cutout")) {
                    continue;
                }
                try {
                    final Method see = Sheets.class.getMethod(
                            "translucent" + cutout.getName().substring("cutout".length()));
                    map.put(cutout.invoke(null), see.invoke(null));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // no translucent namesake on this version
                }
            }
            translucent = map;
        }
        return translucent;
    }
}
