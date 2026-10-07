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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The config file as sections: the lines before the first heading, which always apply, then one
 * section per profile, opened by a heading - {@code [Name]}, or {@code [Name] off} for one that is
 * saved but not applied.
 *
 * <p>Every edit goes through here so it lands in the right section, and every line the user wrote
 * - comments, blank lines, their order - comes back out as it went in.
 */
final class ConfigText {

    private static final Pattern HEADING =
            Pattern.compile("^\\s*\\[([^\\]]+)\\]\\s*(off)?\\s*$", Pattern.CASE_INSENSITIVE);

    static final class Section {
        /** Null for the lines before the first heading. */
        String name;
        boolean on = true;
        final List<String> lines = new ArrayList<>();
    }

    /** The first is always the unnamed one, empty or not. */
    final List<Section> sections = new ArrayList<>();

    private ConfigText() {
    }

    static ConfigText parse(List<String> lines) {
        final ConfigText text = new ConfigText();
        Section current = new Section();
        text.sections.add(current);
        for (String line : lines) {
            final Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                current = new Section();
                current.name = m.group(1).trim();
                current.on = m.group(2) == null;
                text.sections.add(current);
            } else {
                current.lines.add(line);
            }
        }
        return text;
    }

    List<String> render() {
        final List<String> out = new ArrayList<>();
        for (Section s : sections) {
            if (s.name != null) {
                out.add("[" + s.name + "]" + (s.on ? "" : " off"));
            }
            out.addAll(s.lines);
        }
        return out;
    }

    Section top() {
        return sections.get(0);
    }

    /** By name, ignoring case; null if there is none. */
    Section profile(String name) {
        for (Section s : sections) {
            if (s.name != null && s.name.equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Adds a line at the end of a section's own lines, ahead of the blank lines that separate it
     * from the next heading, so the gap stays where it was.
     */
    static void append(Section s, String line) {
        int at = s.lines.size();
        while (at > 0 && s.lines.get(at - 1).isBlank()) {
            at--;
        }
        s.lines.add(at, line);
    }

    /** Removes the lines that match; how many went. */
    static int removeIf(Section s, Predicate<String> trimmedLowerCase) {
        int removed = 0;
        for (int i = s.lines.size() - 1; i >= 0; i--) {
            if (trimmedLowerCase.test(s.lines.get(i).trim().toLowerCase(Locale.ROOT))) {
                s.lines.remove(i);
                removed++;
            }
        }
        return removed;
    }

    /** A new section at the end of the file, set off from what is above it by a blank line. */
    Section addProfile(String name) {
        final Section last = sections.get(sections.size() - 1);
        if (!last.lines.isEmpty() && !last.lines.get(last.lines.size() - 1).isBlank()) {
            last.lines.add("");
        }
        final Section s = new Section();
        s.name = name;
        sections.add(s);
        return s;
    }
}
