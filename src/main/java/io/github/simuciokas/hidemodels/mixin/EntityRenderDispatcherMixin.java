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
 * <p>It asks about EVERY entity rather than filtering to a type first, because which entity types
 * carry a model is HideModels' business - item displays and armor stands today - and this hook
 * should not need editing when that list grows.
 *
 * <p>TWO HANDLERS FOR ONE HOOK, because 26.3 gave {@code shouldRender} a trailing float and dropped
 * the old overload. A Mixin handler must mirror its target's parameters exactly - a five-argument
 * handler is rejected outright against the six-argument target, which is how this was found - so
 * each signature gets its own, selected by full descriptor, and each is {@code require = 0} so the
 * one that does not apply on a given version is not an error.
 *
 * <p>THE PRICE OF require = 0 IS THE SAFETY NET, and it is bought back in the gametest: with the
 * config's defaultRequire of 1, a hook that stopped matching failed at launch and was impossible to
 * miss. Optional injectors cannot do that, so the gametest calls shouldRender itself and asserts a
 * listed model comes back false - which is the only check that proves this class is applied at all,
 * rather than merely that the matcher it calls works.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    /** 1.20.5 through 26.2. Written out in full rather than assembled from constants: the
     *  targets checker reads these descriptors out of the source, and cannot evaluate an
     *  expression. */
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
