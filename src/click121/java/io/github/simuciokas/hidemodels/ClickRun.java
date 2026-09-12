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
 * Click-to-run styling for 1.21.2 through 1.21.4. See src/click1215 for the other copy.
 *
 * <p>THE SECOND FILE THAT DIFFERS BETWEEN VERSIONS, on a different boundary from {@link ChatOut}.
 * ClickEvent was a plain class with a constructor up to 1.21.4 and became a sealed interface whose
 * cases are records in 1.21.5 - so {@code new ClickEvent(Action.RUN_COMMAND, cmd)} does not compile
 * after, and {@code new ClickEvent.RunCommand(cmd)} does not compile before. Two three-line files
 * beat a preprocessor, and beat giving up clickable output on either half of the range.
 */
public final class ClickRun {

    private ClickRun() {
    }

    /** A style that runs {@code command} (leading slash included) when the text is clicked. */
    public static Style style(String command) {
        return Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
    }
}
