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

import net.fabricmc.api.ClientModInitializer;

/**
 * The Fabric end of the smoke test: an entrypoint, and nothing else.
 *
 * <p>The checks themselves are in {@link SmokeChecks}, shared with the NeoForge harness, because
 * what they assert - that the component resolves from the registry and that the config drives the
 * matcher - is the mod's behaviour rather than any loader's.
 */
public final class HideModelsSmokeTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SmokeChecks.start();
    }
}
