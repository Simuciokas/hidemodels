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

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.simuciokas.hidemodels.Fade;
import io.github.simuciokas.hidemodels.Faded;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the model an armor stand wears on its head see-through: the head layer draws it as an
 * item, so that draw is handed a fading collector, as an item display's is. Only an armor stand's
 * state carries an opacity, so any other wearer's head is drawn as it is.
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerFadeMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"))
    private void hidemodels$beginFade(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      LivingEntityRenderState state, float yRot, float xRot,
                                      CallbackInfo ci) {
        Fade.begin(state instanceof Faded faded ? faded.hidemodels$opacity() : 1f);
    }

    @ModifyArg(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/"
                    + "ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            index = 1)
    private SubmitNodeCollector hidemodels$fade(SubmitNodeCollector collector) {
        return Fade.wrap(collector);
    }
}
