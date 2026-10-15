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
import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes an armor stand's opacity on its render state, for the head layer that draws its model. */
@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandFadeMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;"
            + "Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
            at = @At("TAIL"))
    private void hidemodels$noteOpacity(ArmorStand stand, ArmorStandRenderState state,
                                        float partialTick, CallbackInfo ci) {
        ((Faded) state).hidemodels$setOpacity(HideModels.opacityOf(stand));
    }
}
