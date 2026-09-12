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

import net.neoforged.fml.ModList;

/**
 * The mod's own version, read from the jar metadata - the NeoForge copy. See src/fabric for the
 * other one and for why this is the only thing src/main asks the loader for.
 */
public final class LoaderInfo {

    private LoaderInfo() {
    }

    /** "1.5.0 ", or "" when the metadata cannot be read - the caller appends it to a status line. */
    public static String modVersion() {
        return ModList.get().getModContainerById(HideModels.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString() + " ")
                .orElse("");
    }
}
