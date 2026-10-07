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

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen that paints itself, for 26.x. See the other copy beside this one.
 *
 * <p>Vanilla blurs and dims whatever is behind a screen; this one is for judging what is in front
 * of you, so the background is left alone.
 *
 * <p>Everything drawn goes through {@link Painter}, so the screen itself is shared code. This file
 * exists only because the draw context in these two signatures is the one type that changed.
 */
public abstract class ClearScreen extends Screen {

    protected ClearScreen(Component title) {
        super(title);
    }

    /** Draw the screen. Called with the version's context already wrapped. */
    protected abstract void paint(Painter painter, int mouseX, int mouseY, float partial);

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        paint(new Painter(graphics), mouseX, mouseY, partial);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        // deliberately nothing
    }
}
