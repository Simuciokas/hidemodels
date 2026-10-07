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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drawing primitives, for 26.x. See ../screen-mc121 for the other copy.
 *
 * <p>The draw context is the one type a screen cannot name on both halves of the range, so it is
 * wrapped here and nowhere else: every widget this mod draws is shared code holding a Painter.
 */
public final class Painter {

    private final GuiGraphicsExtractor graphics;

    public Painter(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    public void fill(int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + h, argb);
    }

    public void text(String s, int x, int y, int argb) {
        graphics.text(Minecraft.getInstance().font, s, x, y, argb);
    }

    public int textWidth(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    public int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }
}
