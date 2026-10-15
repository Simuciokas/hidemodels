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

/**
 * Whether a profile's opacity changes with distance: not at all, fading out to nothing far away,
 * or fading in from nothing close by.
 */
public enum DistanceFade {
    OFF, FAR, NEAR;

    /** The next one, as the screen's Distance fade row steps through them. */
    public DistanceFade next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
