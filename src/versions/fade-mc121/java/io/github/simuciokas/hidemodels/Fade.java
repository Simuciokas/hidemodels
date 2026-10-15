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
import java.util.WeakHashMap;
import net.minecraft.client.renderer.Sheets;

/**
 * Draws a model see-through, on 1.21.9 to 1.21.11 - an item display, or the piece an armor stand
 * wears on its head - inside a jar that also runs back to 1.20.5, where none of the types this
 * works with exist. So nothing here names them: the collector, its faces and their render types
 * are all found by shape or by candidate names, from the objects the draw hands over.
 *
 * <p>The way it fades is 26.x's: the item goes through a collector standing in for the game's,
 * whose submitItem gets the faces with a tint carrying the opacity - a face with no tint pointed at
 * one added for it - and a translucent render type in place of an opaque block sheet.
 */
public final class Fade {

    private Fade() {
    }

    /**
     * Opacities below 1, noted on render states as they are extracted, for their draw. Kept here
     * rather than on the states, whose classes a mixin would have to name. Weak, as the game drops
     * a state once it is drawn.
     */
    private static final Map<Object, Float> NOTED = new WeakHashMap<>();

    /** The opacity of the model being drawn, set as its draw starts and taken by wrap. */
    private static float pending = 1f;

    public static void note(Object state, float opacity) {
        if (opacity < 1f) {
            NOTED.put(state, opacity);
        } else if (!NOTED.isEmpty()) {
            NOTED.remove(state);
        }
    }

    public static void begin(Object state) {
        final Float noted = state == null ? null : NOTED.get(state);
        pending = noted == null ? 1f : noted;
    }

    /**
     * The collector to draw the current model's item with: the game's, or a fading stand-in when
     * the model is faded. Should the stand-in fail to build, the item is drawn as it is.
     */
    public static Object wrap(Object collector) {
        final float opacity = pending;
        pending = 1f;
        if (opacity >= 1f || collector == null) {
            return collector;
        }
        try {
            return proxy(COLLECTOR_TYPES.get(collector.getClass()), collector, opacity);
        } catch (RuntimeException e) {
            return collector;
        }
    }

    /** Every public collector interface the game's collector has, for the stand-in to have too. */
    private static final ClassValue<Class<?>[]> COLLECTOR_TYPES = new ClassValue<>() {
        @Override
        protected Class<?>[] computeValue(Class<?> type) {
            final List<Class<?>> out = new ArrayList<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                for (Class<?> i : c.getInterfaces()) {
                    if (Modifier.isPublic(i.getModifiers()) && takesTints(i) && !out.contains(i)) {
                        out.add(i);
                    }
                }
            }
            return out.toArray(new Class<?>[0]);
        }
    };

    /** A collector: it has a method taking an item's tints as an int[]. */
    private static boolean takesTints(Class<?> type) {
        for (Method m : type.getMethods()) {
            if (Arrays.asList(m.getParameterTypes()).contains(int[].class)) {
                return true;
            }
        }
        return false;
    }

    private static Object proxy(Class<?>[] types, Object delegate, float opacity) {
        final InvocationHandler handler = (self, method, args) -> {
            final Object[] call = args != null && isSubmitItem(method)
                                  ? fadeItem(args, opacity) : args;
            final Object result;
            try {
                result = method.invoke(delegate, call);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            // order(int) hands back the collector the next items go to; it fades them too.
            final Class<?>[] p = method.getParameterTypes();
            if (result != null && p.length == 1 && p[0] == int.class
                    && method.getReturnType().isInterface()) {
                return proxy(new Class<?>[] {method.getReturnType()}, result, opacity);
            }
            return result;
        };
        return Proxy.newProxyInstance(types[0].getClassLoader(), types, handler);
    }

    /** submitItem, by shape: it takes the tints as an int[] and the faces as a list. */
    private static boolean isSubmitItem(Method method) {
        final List<Class<?>> p = Arrays.asList(method.getParameterTypes());
        return p.contains(int[].class) && p.contains(List.class);
    }

    /** submitItem's arguments with its tints, faces and render type swapped for faded ones. */
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
            if (out[i] instanceof List<?> faces) {
                out[i] = fadedFaces(faces, added);
            } else if (out[i] != null && translucent().containsKey(out[i])) {
                out[i] = translucent().get(out[i]);
            }
        }
        return out;
    }

    private static int withAlpha(int argb, float opacity) {
        final int alpha = Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Faded copies, kept by the face they were copied from and the tint index added for them. The
     * list of faces is built afresh each frame; the faces in it come from the model and stay.
     */
    private static final Map<Object, Map<Integer, Object>> FACES = new IdentityHashMap<>();

    private static Object fadedFaces(List<?> faces, int addedTint) {
        if (FACES.size() > 16384) {
            FACES.clear();
        }
        try {
            final List<Object> out = new ArrayList<>(faces.size());
            for (Object face : faces) {
                final Map<Integer, Object> byTint =
                        FACES.computeIfAbsent(face, k -> new HashMap<>());
                Object made = byTint.get(addedTint);
                if (made == null) {
                    made = copyFace(face, addedTint);
                    byTint.put(addedTint, made);
                }
                out.add(made);
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return faces;
        }
    }

    /**
     * A face as a copy whose tint index points at the added tint when it has no tint of its own -
     * none, or one past the item's. A face is a record in both shapes this range has, and its first
     * int is the tint index either way.
     */
    private static Object copyFace(Object face, int addedTint) throws ReflectiveOperationException {
        final RecordComponent[] parts = face.getClass().getRecordComponents();
        final Class<?>[] types = new Class<?>[parts.length];
        final Object[] values = new Object[parts.length];
        boolean tint = false;
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            values[i] = parts[i].getAccessor().invoke(face);
            if (!tint && types[i] == int.class) {
                tint = true;
                final int index = (Integer) values[i];
                if (index < 0 || index >= addedTint) {
                    values[i] = addedTint;
                }
            }
        }
        return face.getClass().getDeclaredConstructor(types).newInstance(values);
    }

    /**
     * Opaque block sheets to the translucent one drawn from the same atlas: on 1.21.11 the block
     * item sheet, before it the item sheet, which shares the blocks' atlas there. Under either
     * name, since production runs intermediary.
     */
    private static Map<Object, Object> translucent;

    private static Map<Object, Object> translucent() {
        if (translucent == null) {
            final Map<Object, Object> map = new IdentityHashMap<>();
            Object see = sheet("method_76545", "translucentBlockItemSheet");
            if (see == null) {
                see = sheet("method_29382", "translucentItemSheet");
            }
            if (see != null) {
                for (Object opaque : new Object[] {sheet("method_24073", "solidBlockSheet"),
                                                   sheet("method_24074", "cutoutBlockSheet")}) {
                    if (opaque != null) {
                        map.put(opaque, see);
                    }
                }
            }
            translucent = map;
        }
        return translucent;
    }

    private static Object sheet(String... names) {
        for (String name : names) {
            try {
                final Method m = Sheets.class.getMethod(name);
                if (Modifier.isStatic(m.getModifiers())) {
                    return m.invoke(null);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // the other namespace, or a version without it
            }
        }
        return null;
    }
}
