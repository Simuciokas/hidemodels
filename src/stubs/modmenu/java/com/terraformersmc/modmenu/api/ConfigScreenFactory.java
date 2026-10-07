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

import net.minecraft.client.gui.screens.Screen;

/** Mod Menu's screen factory, with its exact signature; see ModMenuApi for why it is here. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {

    S create(Screen parent);
}
