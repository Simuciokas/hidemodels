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
 * <p>Its Fabric twin is HideModelsSmokeTest, and the checks they both run are in
 * {@link SmokeChecks}. Keeping each entrypoint down to a constructor is the point - what is being
 * tested is the mod, and the mod does not change between loaders.
 *
 * <p>This is what gives the NeoForge jar any runtime coverage at all: the gametest harness is
 * Fabric's and does not exist here, so without this the NeoForge side would be proven only by the
 * fact that it compiles.
 */
@Mod(value = "hidemodels_test", dist = Dist.CLIENT)
public final class HideModelsNeoForgeSmoke {

    public HideModelsNeoForgeSmoke(IEventBus modBus) {
        SmokeChecks.start();
    }
}
