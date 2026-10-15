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
import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws an item display see-through when a config line gives its model an opacity: noted on its
 * render state as the state is extracted, then applied by handing its item a fading collector.
 */
@Mixin(DisplayRenderer.ItemDisplayRenderer.class)
public abstract class ItemDisplayFadeMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$ItemDisplay;"
            + "Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V",
            at = @At("TAIL"))
    private void hidemodels$noteOpacity(Display.ItemDisplay display,
                                        ItemDisplayEntityRenderState state, float partialTick,
                                        CallbackInfo ci) {
        ((Faded) state).hidemodels$setOpacity(HideModels.opacityOf(display));
    }

    @Inject(method = "submitInner(Lnet/minecraft/client/renderer/entity/state/"
            + "ItemDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V", at = @At("HEAD"))
    private void hidemodels$beginFade(ItemDisplayEntityRenderState state, PoseStack poseStack,
                                      SubmitNodeCollector collector, int light, float partialTick,
                                      CallbackInfo ci) {
        Fade.begin(((Faded) state).hidemodels$opacity());
    }

    @ModifyArg(method = "submitInner(Lnet/minecraft/client/renderer/entity/state/"
            + "ItemDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/"
                    + "ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            index = 1)
    private SubmitNodeCollector hidemodels$fade(SubmitNodeCollector collector) {
        return Fade.wrap(collector);
    }
}
