package io.github.simuciokas.hidemodels.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code Display.ItemDisplay#getItemStack()} is PRIVATE in 26.2, so the render hook cannot read the
 * displayed item directly. An @Invoker exposes it without touching the class's behaviour.
 */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {

    @Invoker("getItemStack")
    ItemStack hidemodels$getItemStack();
}
