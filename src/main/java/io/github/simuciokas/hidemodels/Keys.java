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

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The key that opens the screen, under Miscellaneous in Controls. Unbound until the player picks
 * one, so it can never take a key another mod or the player already uses.
 */
public final class Keys {

    public static final String OPEN = "key.hidemodels.open";

    private static KeyMapping open;

    private Keys() {
    }

    /**
     * Builds the key. Each loader calls this where it registers keys, and registers the result.
     *
     * <p>BY CONSTRUCTOR SHAPE: the category is a String up to 1.21.8 and a KeyMapping.Category
     * from 1.21.9, both inside the range one jar covers. The (name, key, category) form also keeps
     * the input type out of this class, since 26.3 renamed KEYSYM to KEYBOARD. Null if no
     * constructor fits.
     */
    public static KeyMapping create() {
        for (Constructor<?> c : KeyMapping.class.getConstructors()) {
            final Class<?>[] p = c.getParameterTypes();
            if (p.length != 3 || p[0] != String.class || p[1] != int.class) {
                continue;
            }
            final Object category = p[2] == String.class ? "key.categories.misc" : misc(p[2]);
            if (category == null) {
                continue;
            }
            try {
                open = (KeyMapping) c.newInstance(OPEN, InputConstants.UNKNOWN.getValue(), category);
            } catch (ReflectiveOperationException | RuntimeException e) {
                open = null;
            }
            return open;
        }
        return null;
    }

    /**
     * KeyMapping.Category.MISC, matched by its id because production renames the field.
     */
    private static Object misc(Class<?> category) {
        final RecordComponent[] parts = category.getRecordComponents();
        if (parts == null || parts.length != 1) {
            return null;
        }
        for (Field f : category.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != category) {
                continue;
            }
            try {
                final Object value = f.get(null);
                if ("minecraft:misc".equals(String.valueOf(parts[0].getAccessor().invoke(value)))) {
                    return value;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // not this one
            }
        }
        return null;
    }

    public static void tick() {
        if (open == null) {
            return;
        }
        while (open.consumeClick()) {
            final Minecraft mc = Minecraft.getInstance();
            Screens.open(mc, new HiddenListScreen(null));
        }
    }
}
