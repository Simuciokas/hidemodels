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
package com.terraformersmc.modmenu.api;

/**
 * Mod Menu's entrypoint interface, as much of it as this mod implements, with Mod Menu's exact
 * names and signature. Compiled against and left out of the jar: Mod Menu supplies the real one,
 * so there is no Mod Menu build to pick for each Minecraft version.
 */
public interface ModMenuApi {

    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }
}
