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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The key that opens the screen, in a Hide Models section of its own in Controls. Unbound until the
 * player picks one, so it can never take a key another mod or the player already uses.
 */
public final class Keys {

    public static final String OPEN = "key.hidemodels.open";
    /** The section up to 1.21.8, where a category is its own heading's translation key. */
    private static final String CATEGORY = "key.categories." + HideModels.MOD_ID;
    /** The section's id path from 1.21.9; its heading is then key.category.hidemodels.main. */
    private static final String CATEGORY_PATH = "main";

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
     *
     * @param registrar takes the new KeyMapping.Category on a loader that registers categories
     *                  itself, as NeoForge does; null registers it with vanilla, as Fabric needs
     */
    public static KeyMapping create(Consumer<Object> registrar) {
        for (Constructor<?> c : KeyMapping.class.getConstructors()) {
            final Class<?>[] p = c.getParameterTypes();
            if (p.length != 3 || p[0] != String.class || p[1] != int.class) {
                continue;
            }
            final Object category = p[2] == String.class ? CATEGORY : category(p[2], registrar);
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
     * Our KeyMapping.Category, its id and its factory found by shape: the id is a ResourceLocation
     * up to 1.21.10 and an Identifier from 1.21.11, and production renames all of it.
     */
    private static Object category(Class<?> type, Consumer<Object> registrar) {
        final RecordComponent[] parts = type.getRecordComponents();
        if (parts == null || parts.length != 1) {
            return null;
        }
        final Class<?> idType = parts[0].getType();
        try {
            final Object id = id(idType);
            if (id == null) {
                return null;
            }
            if (registrar != null) {
                final Object category = type.getConstructor(idType).newInstance(id);
                registrar.accept(category);
                return category;
            }
            for (Method m : type.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == type
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == idType) {
                    return m.invoke(null, id);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // no category of our own can be made on this version
        }
        return null;
    }

    /** hidemodels:main, from whichever static (String, String) factory the id class has. */
    private static Object id(Class<?> idType) throws ReflectiveOperationException {
        for (Method m : idType.getMethods()) {
            final Class<?>[] p = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == idType && p.length == 2
                    && p[0] == String.class && p[1] == String.class) {
                final Object id = m.invoke(null, HideModels.MOD_ID, CATEGORY_PATH);
                if (id != null) {
                    return id;
                }
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
