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
package io.github.simuciokas.hidemodels;

/**
 * What the 1.21.x fade mixins hook, by Mojang's name, as NeoForge and a Fabric development run
 * have them. Constants, so the compiler writes them into the mixins' annotations.
 */
public final class FadeTargets {

    private FadeTargets() {
    }

    public static final String DISPLAY_EXTRACT = "extractRenderState("
            + "Lnet/minecraft/world/entity/Display$ItemDisplay;"
            + "Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V";

    public static final String DISPLAY_SUBMIT = "submitInner("
            + "Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V";

    public static final String STAND_EXTRACT = "extractRenderState("
            + "Lnet/minecraft/world/entity/decoration/ArmorStand;"
            + "Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V";

    public static final String HEAD_SUBMIT = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V";

    public static final String SUBMIT_ITEM = "Lnet/minecraft/client/renderer/item/"
            + "ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    // Before 1.21.9 the item is drawn into a MultiBufferSource: from the entity itself up to
    // 1.21.1, from its render state after.

    public static final String DISPLAY_RENDER_ENTITY = "renderInner("
            + "Lnet/minecraft/world/entity/Display$ItemDisplay;"
            + "Lnet/minecraft/world/entity/Display$ItemDisplay$ItemRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;IF)V";

    public static final String DISPLAY_RENDER_STATE = "renderInner("
            + "Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;IF)V";

    public static final String HEAD_RENDER_ENTITY = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V";

    public static final String HEAD_RENDER_STATE = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V";

    /** Up to 1.21.1, the item display's draw; its buffers are argument 5. */
    public static final String RENDER_STATIC = "Lnet/minecraft/client/renderer/entity/ItemRenderer;"
            + "renderStatic(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;II"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/world/level/Level;I)V";

    /** Up to 1.21.1, the head layer's draw; its buffers are argument 5. */
    public static final String RENDER_IN_HAND = "Lnet/minecraft/client/renderer/ItemInHandRenderer;"
            + "renderItem(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /** 1.21.2 and 1.21.3, both draws; their buffers are argument 4. */
    public static final String RENDER_MODEL = "Lnet/minecraft/client/renderer/entity/ItemRenderer;"
            + "render(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lnet/minecraft/client/resources/model/BakedModel;)V";

    /** 1.21.4 to 1.21.8, both draws; their buffers are argument 1. */
    public static final String RENDER_ITEM = "Lnet/minecraft/client/renderer/item/"
            + "ItemStackRenderState;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V";
}
