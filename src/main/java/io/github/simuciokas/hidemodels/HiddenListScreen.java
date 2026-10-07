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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * {@code /hidemodels list} as a screen: the models around you, each row hiding or unhiding one.
 *
 * <p>DOWN THE LEFT, leaving the rest of the screen clear and unblurred. The point of a screen
 * rather than a chat list is that the world is still there behind it - toggling a row and watching
 * the model go is the whole interaction.
 *
 * <p>DRAWN RATHER THAN ASSEMBLED FROM WIDGETS. Everything here is rectangles and text through
 * {@link Painter}, so the look is ours and this file is still shared: the only per-version code is
 * the painter and the base screen that makes one.
 */
public final class HiddenListScreen extends ClearScreen {

    private static final int X = 10;
    private static final int Y = 14;
    private static final int ROW = 16;
    private static final int GAP = 2;
    /** Text inset either side of a label, so a row is never flush against its own edge. */
    private static final int PAD = 7;
    private static final int MIN_WIDTH = 90;

    private static final int PANEL = 0xF00C0C10;
    private static final int ROW_BG = 0xFF1C1C24;
    private static final int ROW_HOVER = 0xFF3A3A48;
    private static final int ACCENT = 0xFF6FCF6F;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFF9A9AA4;

    private static final String NEARBY_TAB = "nearby";
    private static final String HIDDEN_TAB = "hidden";
    private static final String PREV = "◀ prev";
    private static final String NEXT = "next ▶";
    private static final String CLOSE = "close";

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private boolean showHidden;
    /**
     * The hide list as it was when this tab was opened.
     *
     * <p>TAKEN ONCE, not read live: unhiding something removes it from the config, and a row that
     * vanished under the cursor would make undoing a misclick impossible. The row stays, its accent
     * goes, and the list is only re-read when the tab is.
     */
    private List<String> hiddenSnapshot = List.of();
    /** Set by build: the widest row on this page, which is what the panel is drawn around. */
    private int panelWidth = MIN_WIDTH;

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
        boolean on;
        boolean muted;
    }

    public HiddenListScreen(Screen parent) {
        super(Component.literal("Hide Models"));
        this.parent = parent;
    }

    /** The gutter below the panel, so the last row never sits flush against the screen edge. */
    private static final int BOTTOM_MARGIN = 12;

    private int perPage() {
        // Room kept for two rows of chrome at the bottom - the pager and close - plus the header.
        final int usable = height - Y - 14 - 3 * (ROW + GAP) - BOTTOM_MARGIN;
        return Math.max(1, usable / (ROW + GAP));
    }

    private int pageCount(int total) {
        return Math.max(1, (total + perPage() - 1) / perPage());
    }

    private void build() {
        rows.clear();
        final List<String> source = showHidden
                ? hiddenSnapshot
                : NearbyModels.nearbyIds(HideModels.listRadius());
        final int perPage = perPage();
        final int pages = pageCount(source.size());
        // A page can vanish under you: hiding the last model on it leaves the index past the end.
        page = Math.min(page, pages - 1);

        final int from = page * perPage;
        final List<String> shown = source.subList(from, Math.min(source.size(), from + perPage));

        // WIDTH COMES FROM THIS PAGE'S CONTENT, not from a constant: ids vary from a dozen
        // characters to fifty, and a column sized for the worst case wastes the screen the rest of
        // the time. Capped at a third of the screen so one absurd id cannot swallow the view it is
        // meant to leave clear.
        final int cap = Math.max(MIN_WIDTH, width / 3);
        int content = measure(header(source.size(), pages));
        for (String id : shown) {
            content = Math.max(content, measure(id));
        }
        final int prevW = measure(PREV) + PAD * 2;
        final int nextW = measure(NEXT) + PAD * 2;
        final int rowWidth = Math.min(cap, Math.max(MIN_WIDTH,
                Math.max(content + PAD * 2, pages > 1 ? prevW + GAP + nextW : 0)));
        panelWidth = rowWidth;

        int y = Y + 14;
        // The two tabs, the active one lit. Switching re-reads the hide list and goes to page one.
        final int tabW = Math.max(measure(NEARBY_TAB), measure(HIDDEN_TAB)) + PAD * 2;
        tab(X, y, tabW, NEARBY_TAB, !showHidden, false);
        tab(X + tabW + GAP, y, tabW, HIDDEN_TAB, showHidden, true);
        y += ROW + GAP;

        for (String id : shown) {
            final Row row = new Row();
            row.x = X;
            row.y = y;
            row.w = rowWidth;
            row.label = id;
            row.on = HideModels.listed(id);
            rows.add(row);
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
            chrome(X, y, prevW, PREV, () -> turn(-1, pages));
            chrome(X + rowWidth - nextW, y, nextW, NEXT, () -> turn(1, pages));
            y += ROW + GAP;
        }
        chrome(X, y, measure(CLOSE) + PAD * 2, CLOSE, this::onClose);
    }

    private static int measure(String s) {
        return net.minecraft.client.Minecraft.getInstance().font.width(s);
    }

    /** An invisible button over the row, so vanilla delivers the click. */
    private void hit(Row row, Runnable action) {
        addRenderableWidget(Button.builder(Component.empty(), b -> action.run())
                .bounds(row.x, row.y, row.w, ROW).build());
    }

    private void tab(int x, int y, int w, String label, boolean active, boolean hidden) {
        final Row row = new Row();
        row.x = x;
        row.y = y;
        row.w = w;
        row.label = label;
        row.muted = !active;
        rows.add(row);
        hit(row, () -> {
            showHidden = hidden;
            hiddenSnapshot = List.of(HideModels.patterns());
            page = 0;
            rebuildWidgets();
        });
    }

    private void chrome(int x, int y, int w, String label, Runnable action) {
        final Row row = new Row();
        row.x = x;
        row.y = y;
        row.w = w;
        row.label = label;
        row.muted = true;
        rows.add(row);
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
        p.fill(X - 4, Y - 6, panelWidth + 8, bottom - Y + 12, PANEL);

        final int total = showHidden
                ? hiddenSnapshot.size()
                : NearbyModels.nearbyIds(HideModels.listRadius()).size();
        p.text(header(total, pageCount(total)), X, Y, DIM);

        for (Row row : rows) {
            final boolean hover = inside(row, mouseX, mouseY);
            p.fill(row.x, row.y, row.w, ROW, hover ? ROW_HOVER : ROW_BG);
            if (row.on) {
                // A bar down the left edge rather than a tick: it reads at a glance down a column.
                p.fill(row.x, row.y, 2, ROW, ACCENT);
            }
            final int ty = row.y + (ROW - p.lineHeight()) / 2 + 1;
            p.text(fit(p, row.label, row.w - PAD * 2), row.x + PAD, ty, row.muted ? DIM : TEXT);
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
        final String page = pages > 1 ? "    " + (this.page + 1) + "/" + pages : "";
        if (showHidden) {
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
