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
package io.github.simuciokas.hidemodels.test;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The NeoForge end of the smoke test: an entrypoint, and nothing else.
 *
 * <p>Its Fabric twin is HideModelsSmokeTest; the checks are in {@link SmokeChecks}. This is the
 * NeoForge jar's only runtime coverage - the gametest harness is Fabric's.
 */
@Mod(value = "hidemodels_test", dist = Dist.CLIENT)
public final class HideModelsNeoForgeSmoke {

    public HideModelsNeoForgeSmoke(IEventBus modBus) {
        SmokeChecks.start();
    }
}
