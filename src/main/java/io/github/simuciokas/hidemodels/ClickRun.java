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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.network.chat.Style;

/**
 * Click-to-run styling, built reflectively because the type changed shape mid-range.
 *
 * <p>Up to 1.21.4 a ClickEvent is a class taking (Action, String); from 1.21.5 it is a sealed
 * interface whose RunCommand record takes (String). Neither spelling compiles against the other
 * half, so naming either one in source would split this file - and the jar with it.
 *
 * <p>BOTH NAMESPACES ARE TRIED. A Fabric jar for 1.21.x runs against intermediary, where these are
 * class_2558 and class_2558$class_10609; NeoForge and 26.x run against official names. The
 * candidate lists below cover both, so one compiled class serves every version and loader.
 *
 * <p>Resolution happens once. If it fails the style is returned unstyled: an id that cannot be
 * clicked is a smaller loss than a chat line that throws while rendering.
 */
public final class ClickRun {

    /** Set on first use; null once resolution has been tried and failed. */
    private static Constructor<?> runCommand;      // (String)
    private static Constructor<?> legacyEvent;     // (Action, String)
    private static Object legacyRunAction;
    private static boolean resolved;

    private static final String[] MODERN = {
        "net.minecraft.network.chat.ClickEvent$RunCommand",
        "net.minecraft.class_2558$class_10609",
    };
    private static final String[] LEGACY = {
        "net.minecraft.network.chat.ClickEvent",
        "net.minecraft.class_2558",
    };

    private ClickRun() {
    }

    /** {@code command} carries its leading slash; the game trims it before sending. */
    public static Style style(String command) {
        final Object event = clickEvent(command);
        return event == null ? Style.EMPTY : withClickEvent(Style.EMPTY, event);
    }

    private static synchronized Object clickEvent(String command) {
        if (!resolved) {
            resolve();
            resolved = true;
        }
        try {
            if (runCommand != null) {
                return runCommand.newInstance(command);
            }
            if (legacyEvent != null && legacyRunAction != null) {
                return legacyEvent.newInstance(legacyRunAction, command);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static void resolve() {
        for (String name : MODERN) {
            final Class<?> type = find(name);
            if (type != null) {
                try {
                    runCommand = type.getConstructor(String.class);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // fall through to the legacy shape
                }
            }
        }
        for (String name : LEGACY) {
            final Class<?> type = find(name);
            if (type == null) {
                continue;
            }
            for (Constructor<?> c : type.getConstructors()) {
                final Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0].isEnum() && p[1] == String.class) {
                    legacyEvent = c;
                    // The Action type comes from the constructor rather than from a name of its
                    // own, so the enum never has to be found by a spelling that also moved.
                    legacyRunAction = runCommandConstant(p[0]);
                    return;
                }
            }
        }
    }

    /**
     * RUN_COMMAND among the Action constants, matched on the serialized name the codec uses.
     *
     * <p>The field name is remapped and the enum's own name() is obfuscated, but "run_command" is a
     * string constant in the class either way. Ordinal 2 is the fallback, which is where it sits on
     * every version this runs against.
     */
    private static Object runCommandConstant(Class<?> action) {
        final Object[] constants = action.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            for (Method m : action.getMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() != String.class) {
                    continue;
                }
                try {
                    if ("run_command".equals(m.invoke(constant))) {
                        return constant;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // a getter that throws on this constant tells us nothing; try the next
                }
            }
        }
        return constants.length > 2 ? constants[2] : null;
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** Style.withClickEvent takes the event type, which is exactly what cannot be named here. */
    private static Style withClickEvent(Style style, Object event) {
        for (Method m : Style.class.getMethods()) {
            final Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() == Style.class && p.length == 1
                    && p[0].isInstance(event) && p[0] != Object.class) {
                try {
                    return (Style) m.invoke(style, event);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return style;
                }
            }
        }
        return style;
    }
}
