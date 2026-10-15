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
}
