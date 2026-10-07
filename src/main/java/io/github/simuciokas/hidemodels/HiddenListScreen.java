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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * {@code /hidemodels list} as a screen: the models around you, each row hiding or unhiding one.
 *
 * <p>DOWN ONE SIDE, the left unless the settings say otherwise, leaving the rest of the screen
 * clear and unblurred. The point of a screen rather than a chat list is that the world is still
 * there behind it - toggling a row and watching the model go is the whole interaction.
 *
 * <p>DRAWN RATHER THAN ASSEMBLED FROM WIDGETS. Everything here is rectangles and text through
 * {@link Painter}, so the look is ours and this file is still shared: the only per-version code is
 * the painter and the base screen that makes one.
 */
public final class HiddenListScreen extends ClearScreen {

    /** Gap between the panel and the screen edge it sits against. */
    private static final int EDGE = 10;
    private static final int Y = 14;
    private static final int ROW = 16;
    private static final int GAP = 2;
    /** Text inset either side of a label, so a row is never flush against its own edge. */
    private static final int PAD = 7;
    private static final int RADIUS_STEP = 8;

    private static final int PANEL = 0xF00C0C10;
    private static final int ROW_BG = 0xFF1C1C24;
    private static final int ROW_HOVER = 0xFF3A3A48;
    private static final int ACCENT = 0xFF6FCF6F;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFF9A9AA4;

    private static final int NEARBY = 0;
    private static final int HIDDEN = 1;
    private static final int SETTINGS = 2;
    private static final String[] TABS = {"nearby", "hidden", "settings"};

    private static final String PREV = "◀ prev";
    private static final String NEXT = "next ▶";
    private static final String CLOSE = "close";

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private int tab = NEARBY;
    /**
     * The hide list as it was when this tab was opened.
     *
     * <p>TAKEN ONCE, not read live: unhiding something removes it from the config, and a row that
     * vanished under the cursor would make undoing a misclick impossible. The row stays, its accent
     * goes, and the list is only re-read when the tab is.
     */
    private List<String> hiddenSnapshot = List.of();
    /** Set by build: the widest row on this page, which is what the panel is drawn around. */
    private int panelWidth;
    /** Set by build: the panel's left edge, which moves with its width when it sits on the right. */
    private int left = EDGE;
    private boolean onRight;

    /**
     * One line: where it is, what it says, and a hit target.
     *
     * <p>THE BUTTON IS NEVER DRAWN. It is added as a child so vanilla routes the click to it, and
     * the row is painted by hand instead. That matters for more than looks: mouseClicked takes
     * (double, double, int) up to 1.21.8 and (MouseButtonEvent, boolean) from 1.21.10, a boundary
     * inside the range one jar covers - so handling the click ourselves would split that jar.
     * Vanilla absorbs the change on our behalf.
     */
    private static final class Row {
        int x;
        int y;
        int w;
        String label;
        /** Drawn against the right edge, for a setting's current value. */
        String value;
        /** The model or config line the row stands for, which the hover box describes. */
        String id;
        /** What the hover box says for a setting. */
        String hint;
        boolean on;
        boolean muted;
        boolean clickable;
    }

    public HiddenListScreen(Screen parent) {
        super(Component.literal("Hide Models"));
        this.parent = parent;
    }

    /** The gutter below the panel, so the last row never sits flush against the screen edge. */
    private static final int BOTTOM_MARGIN = 12;

    private int perPage() {
        // Room kept for three rows of chrome - the tabs, the pager and close - plus the header.
        final int usable = height - Y - 14 - 3 * (ROW + GAP) - BOTTOM_MARGIN;
        return Math.max(1, usable / (ROW + GAP));
    }

    private int pageCount(int total) {
        return Math.max(1, (total + perPage() - 1) / perPage());
    }

    private List<String> source() {
        if (tab == HIDDEN) {
            return hiddenSnapshot;
        }
        return tab == NEARBY ? NearbyModels.nearbyIds(HideModels.listRadius()) : List.of();
    }

    private void build() {
        rows.clear();
        final List<String> source = source();
        final int perPage = perPage();
        final int pages = pageCount(source.size());
        // A page can vanish under you: hiding the last model on it leaves the index past the end.
        page = Math.min(page, pages - 1);

        final int from = page * perPage;
        final List<String> shown = source.subList(from, Math.min(source.size(), from + perPage));

        final int[] tabW = new int[TABS.length];
        int strip = -GAP;
        for (int i = 0; i < TABS.length; i++) {
            tabW[i] = measure(TABS[i]) + PAD * 2;
            strip += tabW[i] + GAP;
        }

        // WIDTH COMES FROM THIS PAGE'S CONTENT, not from a constant: ids vary from a dozen
        // characters to fifty, and a column sized for the worst case wastes the screen the rest of
        // the time. Capped at a third of the screen so one absurd id cannot swallow the view it is
        // meant to leave clear - but never narrower than the tabs.
        final int cap = Math.max(strip, width / 3);
        int content = measure(header(source.size(), pages));
        for (String id : shown) {
            content = Math.max(content, measure(id));
        }
        final int prevW = measure(PREV) + PAD * 2;
        final int nextW = measure(NEXT) + PAD * 2;
        final int rowWidth = Math.min(cap, Math.max(strip,
                Math.max(content + PAD * 2, pages > 1 ? prevW + GAP + nextW : 0)));
        panelWidth = rowWidth;
        onRight = HideModels.isGuiOnRight();
        left = onRight ? width - EDGE - rowWidth : EDGE;

        int y = Y + 14;
        int x = left;
        for (int i = 0; i < TABS.length; i++) {
            tabButton(x, y, tabW[i], i);
            x += tabW[i] + GAP;
        }
        y += ROW + GAP;

        if (tab == SETTINGS) {
            y = settings(y, rowWidth);
        }

        for (String id : shown) {
            final Row row = row(left, y, rowWidth, id);
            row.id = id;
            row.on = HideModels.listed(id);
            hit(row, () -> {
                if (HideModels.listed(id)) {
                    HideModels.remove(id);
                } else {
                    HideModels.add(id);
                }
                rebuildWidgets();
            });
            y += ROW + GAP;
        }

        if (pages > 1) {
            // The pager takes only what its labels need; the gap between them absorbs the rest.
            chrome(left, y, prevW, PREV, () -> turn(-1, pages));
            chrome(left + rowWidth - nextW, y, nextW, NEXT, () -> turn(1, pages));
            y += ROW + GAP;
        }
        chrome(left, y, measure(CLOSE) + PAD * 2, CLOSE, this::onClose);
    }

    private static int measure(String s) {
        return net.minecraft.client.Minecraft.getInstance().font.width(s);
    }

    private Row row(int x, int y, int w, String label) {
        final Row row = new Row();
        row.x = x;
        row.y = y;
        row.w = w;
        row.label = label;
        rows.add(row);
        return row;
    }

    /** An invisible button over the row, so vanilla delivers the click. */
    private void hit(Row row, Runnable action) {
        row.clickable = true;
        addRenderableWidget(Button.builder(Component.empty(), b -> action.run())
                .bounds(row.x, row.y, row.w, ROW).build());
    }

    /** Switching re-reads the hide list and goes back to page one. */
    private void tabButton(int x, int y, int w, int which) {
        final Row row = row(x, y, w, TABS[which]);
        row.muted = tab != which;
        hit(row, () -> {
            tab = which;
            hiddenSnapshot = List.of(HideModels.patterns());
            page = 0;
            rebuildWidgets();
        });
    }

    /** Each writes the config exactly as its command does, so the screen and the file agree. */
    private int settings(int y, int w) {
        final boolean enabled = HideModels.isEnabled();
        toggle(y, w, "hiding", "off keeps the list but hides nothing", enabled,
                () -> HideModels.setEnabled(!enabled));
        y += ROW + GAP;
        final boolean firstPerson = HideModels.isFirstPersonOnly();
        toggle(y, w, "first person only", "hide only while the camera is in first person",
                firstPerson, () -> HideModels.setFirstPersonOnly(!firstPerson));
        y += ROW + GAP;

        final int sideW = measure("+") + PAD * 2;
        chrome(left, y, sideW, "-", () -> stepRadius(-1));
        final Row radius = row(left + sideW + GAP, y, w - 2 * (sideW + GAP), "list radius");
        radius.value = Integer.toString((int) HideModels.listRadius());
        radius.hint = "how far the nearby tab looks, in blocks";
        chrome(left + w - sideW, y, sideW, "+", () -> stepRadius(1));
        y += ROW + GAP;

        // The panel moves the moment this is clicked, so the row leaves the cursor behind.
        final Row side = row(left, y, w, "panel side");
        side.value = onRight ? "right" : "left";
        side.hint = "which edge of the screen this panel sits against";
        hit(side, () -> {
            HideModels.setGuiOnRight(!onRight);
            rebuildWidgets();
        });
        return y + ROW + GAP;
    }

    private void toggle(int y, int w, String label, String hint, boolean on, Runnable action) {
        final Row row = row(left, y, w, label);
        row.hint = hint;
        row.value = on ? "on" : "off";
        row.on = on;
        hit(row, () -> {
            action.run();
            rebuildWidgets();
        });
    }

    /** To the next multiple of the step, so 30 goes to 32 rather than 38. */
    private void stepRadius(int direction) {
        final double steps = HideModels.listRadius() / RADIUS_STEP;
        final double next = direction > 0 ? Math.floor(steps) + 1 : Math.ceil(steps) - 1;
        HideModels.setListRadius(next * RADIUS_STEP);
        rebuildWidgets();
    }

    private void chrome(int x, int y, int w, String label, Runnable action) {
        final Row row = row(x, y, w, label);
        row.muted = true;
        hit(row, action);
    }

    private void turn(int by, int pages) {
        page = (page + by + pages) % pages;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (hiddenSnapshot.isEmpty()) {
            hiddenSnapshot = List.of(HideModels.patterns());
        }
        build();
    }

    @Override
    protected void paint(Painter p, int mouseX, int mouseY, float partial) {
        final int bottom = rows.isEmpty() ? Y + 28 : rows.get(rows.size() - 1).y + ROW;
        p.fill(left - 4, Y - 6, panelWidth + 8, bottom - Y + 12, PANEL);

        final double radius = HideModels.listRadius();
        final List<NearbyModels.Nearby> around =
                tab == NEARBY ? NearbyModels.nearby(radius) : List.of();
        final int total = tab == HIDDEN ? hiddenSnapshot.size() : around.size();
        p.text(header(total, pageCount(total)), left, Y, DIM);

        Row hovered = null;
        for (Row row : rows) {
            final boolean over = inside(row, mouseX, mouseY);
            if (over) {
                hovered = row;
            }
            final boolean hover = row.clickable && over;
            p.fill(row.x, row.y, row.w, ROW, hover ? ROW_HOVER : ROW_BG);
            // Read live for a model, so a hand edit to the config shows without reopening.
            final boolean on = row.id != null ? HideModels.listed(row.id) : row.on;
            if (on) {
                // A bar down the left edge rather than a tick: it reads at a glance down a column.
                p.fill(row.x, row.y, 2, ROW, ACCENT);
            }
            final int ty = row.y + (ROW - p.lineHeight()) / 2 + 1;
            int room = row.w - PAD * 2;
            if (row.value != null) {
                final int vw = p.textWidth(row.value);
                p.text(row.value, row.x + row.w - PAD - vw, ty,
                        on ? ACCENT : "off".equals(row.value) ? DIM : TEXT);
                room -= vw + PAD;
            }
            p.text(fit(p, row.label, room), row.x + PAD, ty, row.muted ? DIM : TEXT);
        }

        if (hovered != null) {
            final List<String> lines = detail(hovered, around, radius);
            if (!lines.isEmpty()) {
                tip(p, hovered, lines);
            }
        }
    }

    /** What the hover box says about a row. Tabs and chrome get nothing: they say it already. */
    private List<String> detail(Row row, List<NearbyModels.Nearby> around, double radius) {
        if (row.hint != null) {
            return List.of(row.hint);
        }
        if (row.id == null) {
            return List.of();
        }
        final String within = " within " + (int) radius + " blocks";
        final String covering = HideModels.coveredBy(row.id);
        if (tab == HIDDEN) {
            if (covering == null) {
                return List.of("no longer hidden - click to hide again");
            }
            final int pieces = NearbyModels.piecesMatching(radius, row.id);
            return List.of(pieces == 0 ? "hides nothing" + within
                                       : "hides " + pieces + (pieces == 1 ? " piece" : " pieces") + within);
        }
        final List<String> lines = new ArrayList<>();
        for (NearbyModels.Nearby n : around) {
            if (n.id().equals(row.id)) {
                lines.add(n.pieces() + (n.pieces() == 1 ? " piece, " : " pieces, nearest ")
                        + NearbyModels.fmt(n.distance()) + " blocks away");
            }
        }
        if (covering == null) {
            lines.add("click to hide");
        } else if (covering.equals(row.id.toLowerCase(Locale.ROOT))) {
            lines.add("hidden - click to unhide");
        } else {
            // A click would try to remove a line that is not there; say which one is.
            lines.add("hidden by '" + covering + "'");
            lines.add("unhide it from the hidden tab");
        }
        return lines;
    }

    /**
     * Beside the panel on the side facing the middle, level with the row, and pulled back in if it
     * would leave the screen.
     */
    private void tip(Painter p, Row row, List<String> lines) {
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, p.textWidth(line));
        }
        w += PAD * 2;
        final int lineH = p.lineHeight() + 2;
        final int h = lines.size() * lineH + 6;
        final int x = onRight
                ? Math.max(0, left - 8 - w)
                : Math.max(0, Math.min(left + panelWidth + 8, width - w - 4));
        final int y = Math.max(0, Math.min(row.y, height - h - 4));
        p.fill(x, y, w, h, PANEL);
        for (int i = 0; i < lines.size(); i++) {
            p.text(lines.get(i), x + PAD, y + 4 + i * lineH, i == 0 ? TEXT : DIM);
        }
    }

    /** Ids run long, and the end is the part that identifies a bone, so the middle goes. */
    private static String fit(Painter p, String s, int max) {
        if (p.textWidth(s) <= max) {
            return s;
        }
        int head = s.length();
        int tail = 0;
        while (head > 3 && p.textWidth(s.substring(0, head) + ".." + s.substring(s.length() - tail)) > max) {
            if (tail < head / 2) {
                tail++;
            } else {
                head--;
            }
        }
        return s.substring(0, head) + ".." + s.substring(s.length() - tail);
    }

    private String header(int total, int pages) {
        if (tab == SETTINGS) {
            return HideModels.isServerDisabled()
                    ? "this server has hiding off"
                    : "config/" + HideModels.MOD_ID + ".txt";
        }
        final String page = pages > 1 ? "    " + (this.page + 1) + "/" + pages : "";
        if (tab == HIDDEN) {
            return (total == 0 ? "nothing hidden" : total + " hidden") + page;
        }
        final int radius = (int) HideModels.listRadius();
        if (total == 0) {
            return "nothing within " + radius + " blocks";
        }
        return total + " models within " + radius + page;
    }

    private static boolean inside(Row row, double mx, double my) {
        return mx >= row.x && mx < row.x + row.w && my >= row.y && my < row.y + ROW;
    }

    /**
     * Single player keeps running while this is open. The screen exists to watch a model appear or
     * go, and a paused world is one where animated models stand still.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Screens.open(minecraft, parent);
    }
}
