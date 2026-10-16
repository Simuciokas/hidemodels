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
 * What the 1.21.x fade mixins hook, by intermediary name, as a Fabric install runs. Constants, so
 * the compiler writes them into the copies of the mixins the Fabric jar carries.
 */
public final class FadeTargets {

    private FadeTargets() {
    }

    public static final String DISPLAY_EXTRACT =
            "method_62408(Lnet/minecraft/class_8113$class_8122;Lnet/minecraft/class_10038;F)V";

    public static final String DISPLAY_SUBMIT = "method_49055(Lnet/minecraft/class_10038;"
            + "Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;IF)V";

    public static final String STAND_EXTRACT =
            "method_62375(Lnet/minecraft/class_1531;Lnet/minecraft/class_9998;F)V";

    public static final String HEAD_SUBMIT = "method_17159(Lnet/minecraft/class_4587;"
            + "Lnet/minecraft/class_11659;ILnet/minecraft/class_10042;FF)V";

    public static final String SUBMIT_ITEM = "Lnet/minecraft/class_10444;method_65604("
            + "Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;III)V";

    // Before 1.21.9 the item is drawn into a MultiBufferSource: from the entity itself up to
    // 1.21.1, from its render state after.

    public static final String DISPLAY_RENDER_ENTITY = "method_49055("
            + "Lnet/minecraft/class_8113$class_8122;"
            + "Lnet/minecraft/class_8113$class_8122$class_8226;"
            + "Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;IF)V";

    public static final String DISPLAY_RENDER_STATE = "method_49055(Lnet/minecraft/class_10038;"
            + "Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;IF)V";

    public static final String HEAD_RENDER_ENTITY = "method_17159(Lnet/minecraft/class_4587;"
            + "Lnet/minecraft/class_4597;ILnet/minecraft/class_1309;FFFFFF)V";

    public static final String HEAD_RENDER_STATE = "method_17159(Lnet/minecraft/class_4587;"
            + "Lnet/minecraft/class_4597;ILnet/minecraft/class_10042;FF)V";

    /** Up to 1.21.1, the item display's draw; its buffers are argument 5. */
    public static final String RENDER_STATIC = "Lnet/minecraft/class_918;method_23178("
            + "Lnet/minecraft/class_1799;Lnet/minecraft/class_811;IILnet/minecraft/class_4587;"
            + "Lnet/minecraft/class_4597;Lnet/minecraft/class_1937;I)V";

    /** Up to 1.21.1, the head layer's draw; its buffers are argument 5. */
    public static final String RENDER_IN_HAND = "Lnet/minecraft/class_759;method_3233("
            + "Lnet/minecraft/class_1309;Lnet/minecraft/class_1799;Lnet/minecraft/class_811;Z"
            + "Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V";

    /** 1.21.2 and 1.21.3, both draws; their buffers are argument 4. */
    public static final String RENDER_MODEL = "Lnet/minecraft/class_918;method_23179("
            + "Lnet/minecraft/class_1799;Lnet/minecraft/class_811;ZLnet/minecraft/class_4587;"
            + "Lnet/minecraft/class_4597;IILnet/minecraft/class_1087;)V";

    /** 1.21.4 to 1.21.8, both draws; their buffers are argument 1. */
    public static final String RENDER_ITEM = "Lnet/minecraft/class_10444;method_65604("
            + "Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;II)V";
}
