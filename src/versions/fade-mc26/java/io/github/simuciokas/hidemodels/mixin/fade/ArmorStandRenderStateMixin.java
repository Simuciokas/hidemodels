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
package io.github.simuciokas.hidemodels.mixin.fade;

import io.github.simuciokas.hidemodels.Faded;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries an armor stand's opacity from extracting its state to drawing its head. */
@Mixin(ArmorStandRenderState.class)
public abstract class ArmorStandRenderStateMixin implements Faded {

    @Unique
    private float hidemodels$opacity = 1f;

    @Override
    public float hidemodels$opacity() {
        return hidemodels$opacity;
    }

    @Override
    public void hidemodels$setOpacity(float opacity) {
        hidemodels$opacity = opacity;
    }
}
