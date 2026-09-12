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
package io.github.simuciokas.hidemodels.mixin;

import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses to render listed model pieces.
 *
 * <p>{@code shouldRender} is the ideal hook: returning false skips the entity before its render
 * state is even extracted, so a hidden piece costs almost nothing. Crucially this only affects
 * DRAWING - the entity still exists, keeps its hitbox, and the server is none the wiser, which is
 * what makes it safe where discarding the entity outright would not be.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void hidemodels$skipHiddenModels(
            E entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {

        if (!(entity instanceof Display.ItemDisplay display)) {
            return;
        }
        final ItemStack stack = ((ItemDisplayAccessor) display).hidemodels$getItemStack();
        final String model = HideModels.modelIdOf(stack);
        if (model == null) {
            return;
        }
        if (HideModels.hidden(model)) {
            cir.setReturnValue(false);
        }
    }
}
