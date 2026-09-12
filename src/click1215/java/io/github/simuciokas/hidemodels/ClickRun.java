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

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

/**
 * Click-to-run styling for 1.21.5 and later, 26.x included. See src/click121 for the other copy.
 *
 * <p>ClickEvent became a sealed interface in 1.21.5, with one record per action, so the action is
 * now the type rather than an argument. The command carries its leading slash: the game trims it
 * with Commands.trimOptionalPrefix before sending, and every vanilla use includes it.
 */
public final class ClickRun {

    private ClickRun() {
    }

    /** A style that runs {@code command} (leading slash included) when the text is clicked. */
    public static Style style(String command) {
        return Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command));
    }
}
