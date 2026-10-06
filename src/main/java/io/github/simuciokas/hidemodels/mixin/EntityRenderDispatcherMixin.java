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
import net.minecraft.world.entity.Entity;
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
 *
 * <p>It asks about EVERY entity rather than filtering to a type first: which types carry a model is
 * HideModels' business, and this hook should not need editing when that list grows.
 *
 * <p>TWO HANDLERS FOR ONE HOOK, because 26.3 gave {@code shouldRender} a trailing float and dropped
 * the old overload, and a handler must mirror its target's parameters exactly. Each signature is
 * selected by full descriptor with {@code require = 0}, so the one that does not apply is not an
 * error - which also means a hook matching NOTHING is silent, where defaultRequire would have
 * failed the launch. The gametest buys that back by calling shouldRender and asserting a listed
 * model comes back false.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    /** 1.20.5 through 26.2. Spelled out rather than built from constants - the targets checker
     *  reads these descriptors from the source and cannot evaluate an expression. */
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;"
                   + "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private <T extends Entity> void hidemodels$skipHiddenModels(
            T entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {

        if (HideModels.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }

    /** 26.3 and later: the same hook, plus a partial tick it does not need. */
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;"
                   + "Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private <T extends Entity> void hidemodels$skipHiddenModelsWithPartialTick(
            T entity, Frustum frustum, double camX, double camY, double camZ, float partialTick,
            CallbackInfoReturnable<Boolean> cir) {

        if (HideModels.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}
