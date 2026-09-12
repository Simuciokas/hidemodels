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

import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's own version, read from the jar metadata - the Fabric copy. See src/neoforge for the
 * other one.
 *
 * <p>A SMALL THING THAT CANNOT BE SHARED. Every loader knows what version it loaded, and every
 * loader has its own way of being asked. This is the whole of what src/main needed from the loader
 * once the command and the disconnect event moved out, and keeping it behind one method is what
 * lets the rest of src/main compile against nothing but Minecraft.
 */
public final class LoaderInfo {

    private LoaderInfo() {
    }

    /** "1.5.0 ", or "" when the metadata cannot be read - the caller appends it to a status line. */
    public static String modVersion() {
        return FabricLoader.getInstance().getModContainer(HideModels.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString() + " ")
                .orElse("");
    }
}
