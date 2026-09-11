package io.github.simuciokas.hidemodels.mixin;

import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.component.DataComponents;
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
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // DELIBERATELY NOT THE CONCRETE TYPE. This is the item_model component's value, whose class
        // is Identifier on 1.21.11+ and ResourceLocation before it - naming either one pins the
        // source to half the supported range for no benefit, because the only thing wanted from it
        // is toString(). Leave it as Object.
        final Object model = stack.get(DataComponents.ITEM_MODEL);
        if (model == null) {
            return;
        }
        if (HideModels.hidden(model.toString())) {
            cir.setReturnValue(false);
        }
    }
}
