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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing primitives, for 1.21.x and earlier. See ../screen-mc26 for the other copy.
 *
 * <p>The draw context is the one type a screen cannot name on both halves of the range, so it is
 * wrapped here and nowhere else: every widget this mod draws is shared code holding a Painter.
 */
public final class Painter {

    private final GuiGraphics graphics;

    public Painter(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public void fill(int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + h, argb);
    }

    /**
     * CALLED BY NAME, because the return type changed inside this file's own range: drawString
     * gives back an int up to 1.21.4 and nothing from 1.21.8, so a direct call compiles to an extra
     * POP on the older half and splits an otherwise identical jar.
     *
     * <p>Resolved once. The candidates are checked against every supported version by
     * tools/verify_targets.py.
     */
    private static final String[] DRAW_STRING = {
        "net.minecraft.client.gui.GuiGraphics#drawString(net.minecraft.client.gui.Font,"
                + "java.lang.String,int,int,int)",
        "#method_25303",
    };

    private static Method drawString;
    private static boolean resolved;

    public void text(String s, int x, int y, int argb) {
        if (!resolved) {
            for (String candidate : DRAW_STRING) {
                String name = candidate.substring(candidate.indexOf('#') + 1);
                final int args = name.indexOf('(');
                if (args >= 0) {
                    name = name.substring(0, args);
                }
                try {
                    drawString = GuiGraphics.class.getMethod(name, Font.class, String.class,
                            int.class, int.class, int.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // the other namespace
                }
            }
            resolved = true;
        }
        if (drawString == null) {
            return;
        }
        try {
            drawString.invoke(graphics, Minecraft.getInstance().font, s, x, y, argb);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // a line of text is not worth taking the screen down for
        }
    }

    public int textWidth(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    public int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }
}
