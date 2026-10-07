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

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opening a screen, for 26.x. See ../screen-mc121 for the other copy.
 *
 * <p>THE ORDINARY WAY: Minecraft.setScreen up to 26.1.2, Gui.setScreen from 26.2. Found by name,
 * which 26.x keeps at runtime, because the jar covers both. The one spelling all of 26.x shares,
 * setScreenAndShow, also renders a frame on the spot with the world left out of it, and that frame
 * shows as a flash across the whole screen.
 */
public final class Screens {

    private static final Method GUI_SET = method(Gui.class);
    private static final Method MINECRAFT_SET = method(Minecraft.class);

    private Screens() {
    }

    public static void open(Minecraft mc, Screen screen) {
        try {
            if (GUI_SET != null) {
                GUI_SET.invoke(mc.gui, screen);
                return;
            }
            if (MINECRAFT_SET != null) {
                MINECRAFT_SET.invoke(mc, screen);
                return;
            }
        } catch (ReflectiveOperationException e) {
            // the call below exists on every 26.x
        }
        mc.setScreenAndShow(screen);
    }

    private static Method method(Class<?> owner) {
        try {
            return owner.getMethod("setScreen", Screen.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
