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
}
