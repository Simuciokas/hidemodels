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
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Draws an item display see-through on 1.21.9 to 1.21.11, from a jar that also runs back to
 * 1.20.5. Its targets take types those versions lack - the submit collector, the render state -
 * so those are taken as Object, every injector may find nothing, and before 1.21.9, where the
 * draw method has another shape, this does nothing at all.
 *
 * <p>The targets are {@link FadeTargets}': Mojang's names here, intermediary ones in the copy the
 * Fabric jar carries - build.gradle says why.
 *
 * <p>The collector is swapped through ModifyArgs, whose arguments come untyped, rather than a
 * Redirect: MixinExtras 0.5.4, which Quilt bundles, crashes reading a Redirect compiled against
 * this Mixin.
 */
@Mixin(value = DisplayRenderer.ItemDisplayRenderer.class, remap = false)
public abstract class ItemDisplayFadeMixin {

    @Inject(method = FadeTargets.DISPLAY_EXTRACT, at = @At("TAIL"), require = 0)
    private void hidemodels$noteOpacity(Display.ItemDisplay display, @Coerce Object state,
                                        float partialTick, CallbackInfo ci) {
        Fade.note(state, HideModels.opacityOf(display));
    }

    @Inject(method = FadeTargets.DISPLAY_SUBMIT, at = @At("HEAD"), require = 0)
    private void hidemodels$beginFade(@Coerce Object state, PoseStack poseStack,
                                      @Coerce Object collector, int light, float partialTick,
                                      CallbackInfo ci) {
        Fade.begin(state);
    }

    @ModifyArgs(method = FadeTargets.DISPLAY_SUBMIT,
            at = @At(value = "INVOKE", target = FadeTargets.SUBMIT_ITEM), require = 0)
    private void hidemodels$fade(Args args) {
        args.set(1, Fade.wrap(args.get(1)));
    }
}
