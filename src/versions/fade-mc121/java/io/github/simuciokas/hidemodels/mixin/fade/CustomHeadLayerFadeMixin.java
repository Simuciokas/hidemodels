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
import io.github.simuciokas.hidemodels.FadeTargets;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Draws the model an armor stand wears on its head see-through on 1.21.9 to 1.21.11: the head
 * layer draws it as an item, so that draw is handed a fading collector, as an item display's is.
 * Only an armor stand's state has an opacity noted, so any other wearer's head is drawn as it is.
 *
 * <p>Written as {@link ItemDisplayFadeMixin} is, for the same reasons. Before 1.21.9 the layer
 * draws through another method, and this does nothing.
 */
@Mixin(value = CustomHeadLayer.class, remap = false)
public abstract class CustomHeadLayerFadeMixin {

    @Inject(method = FadeTargets.HEAD_SUBMIT, at = @At("HEAD"), require = 0)
    private void hidemodels$beginFade(PoseStack poseStack, @Coerce Object collector, int light,
                                      @Coerce Object state, float yRot, float xRot,
                                      CallbackInfo ci) {
        Fade.begin(state);
    }

    @ModifyArgs(method = FadeTargets.HEAD_SUBMIT,
            at = @At(value = "INVOKE", target = FadeTargets.SUBMIT_ITEM), require = 0)
    private void hidemodels$fade(Args args) {
        args.set(1, Fade.wrap(args.get(1)));
    }
}
