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
import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Draws the model an armor stand wears on its head see-through on 1.20.5 to 1.21.11: the head
 * layer draws it as an item, so that draw is handed a fading collector or fading buffers, as an
 * item display's is. Only an armor stand's model fades - its state has an opacity noted, or, up to
 * 1.21.1, the layer is handed the stand itself - so any other wearer's head is drawn as it is.
 * Skulls are drawn by another call, which is left alone.
 *
 * <p>Written as {@link ItemDisplayFadeMixin} is, for the same reasons.
 */
@Mixin(value = CustomHeadLayer.class, remap = false)
public abstract class CustomHeadLayerFadeMixin {

    @Inject(method = {FadeTargets.HEAD_SUBMIT, FadeTargets.HEAD_RENDER_STATE},
            at = @At("HEAD"), require = 0)
    private void hidemodels$beginFade(PoseStack poseStack, @Coerce Object collector, int light,
                                      @Coerce Object state, float yRot, float xRot,
                                      CallbackInfo ci) {
        Fade.begin(state);
    }

    @Inject(method = FadeTargets.HEAD_RENDER_ENTITY, at = @At("HEAD"), require = 0)
    private void hidemodels$beginFadeOf(PoseStack poseStack, MultiBufferSource buffers, int light,
                                        LivingEntity wearer, float limbSwing, float limbSwingAmount,
                                        float partialTick, float ageInTicks, float headYaw,
                                        float headPitch, CallbackInfo ci) {
        Fade.beginAt(wearer instanceof ArmorStand ? HideModels.opacityOf(wearer) : 1f);
    }

    @ModifyArgs(method = FadeTargets.HEAD_SUBMIT,
            at = @At(value = "INVOKE", target = FadeTargets.SUBMIT_ITEM), require = 0)
    private void hidemodels$fade(Args args) {
        args.set(1, Fade.wrap(args.get(1)));
    }

    @ModifyArg(method = FadeTargets.HEAD_RENDER_ENTITY,
            at = @At(value = "INVOKE", target = FadeTargets.RENDER_IN_HAND), index = 5,
            require = 0)
    private MultiBufferSource hidemodels$fadeInHand(MultiBufferSource buffers) {
        return Fade.wrapBuffers(buffers);
    }

    @ModifyArg(method = FadeTargets.HEAD_RENDER_STATE,
            at = @At(value = "INVOKE", target = FadeTargets.RENDER_MODEL), index = 4,
            require = 0)
    private MultiBufferSource hidemodels$fadeModel(MultiBufferSource buffers) {
        return Fade.wrapBuffers(buffers);
    }

    @ModifyArg(method = FadeTargets.HEAD_RENDER_STATE,
            at = @At(value = "INVOKE", target = FadeTargets.RENDER_ITEM), index = 1,
            require = 0)
    private MultiBufferSource hidemodels$fadeItem(MultiBufferSource buffers) {
        return Fade.wrapBuffers(buffers);
    }
}
