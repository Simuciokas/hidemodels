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
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mod's screen, opened by /hidemodels or its key: the models around you, each row hiding or
 * unhiding one.
 *
 * <p>A PANEL, in the top left unless moved, leaving the rest of the screen clear and unblurred.
 * The point of a screen rather than a chat list is that the world is still there behind it -
 * toggling a row and watching the model go is the whole interaction.
 *
 * <p>DRAWN RATHER THAN ASSEMBLED FROM WIDGETS. Everything here is rectangles and text through
 * {@link Painter}, so the look is ours and this file is still shared: the only per-version code is
 * the painter and the base screen that makes one.
 */
public final class HiddenListScreen extends ClearScreen {

    /** Gap between the panel and the screen edge it is pushed against. */
    private static final int EDGE = 10;
    /** The highest the header can sit. */
    private static final int Y = 14;
    private static final int ROW = 16;
    private static final int GAP = 2;
    /** Text inset either side of a label, so a row is never flush against its own edge. */
    private static final int PAD = 7;
    /** The gutter below the panel, so the last row never sits flush against the screen edge. */
    private static final int BOTTOM_MARGIN = 12;
    private static final int RADIUS_STEP = 8;

    private static final int PANEL = 0xF00C0C10;
    private static final int ROW_BG = 0xFF1C1C24;
    private static final int ROW_HOVER = 0xFF3A3A48;
    private static final int ACCENT = 0xFF6FCF6F;
    /** A model with some of its bones hidden, but not all of it. */
    private static final int PARTIAL = 0xFFE8A33D;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFF9A9AA4;

    private static final int NEARBY = 0;
    private static final int HIDDEN = 1;
    private static final int SETTINGS = 2;
    private static final String[] TABS = {"Nearby", "Hidden", "Settings"};

    private static final String PREV = "◀ Prev";
    private static final String NEXT = "Next ▶";
    private static final String CLOSE = "×";
    private static final String MOVING = "Click to place, Esc cancels";
    private static final String RADIUS_LABEL = "List radius";
    private static final String OPEN = " ▶";
    /** Before the model you were looking at when the screen opened. */
    private static final String LOOKING = "» ";
    private static final String UNSAVED = "Unsaved";
    private static final String SAVE_UNSAVED = "Save unsaved as a profile";
    private static final String DELETE = "Delete profile";
    private static final String DELETE_SURE = "Click again to delete";
    private static final String COPY = "Copy profile";
    private static final String COPIED = "Copied to the clipboard";
    private static final String PASTE = "Paste a profile";
    private static final String OPACITY = "Opacity";
    private static final String DISTANCE = "Distance fade";
    private static final String FROM = "From";
    private static final String TO = "To";
    /** A fade's distances, in blocks: finer close by, where half a block shows. */
    private static final double[] DISTANCE_STOPS =
            {0, 0.5, 1, 1.5, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32};
    /** The side panel's narrowest, so its sliders have room to drag along. */
    private static final int SIDE_MIN = 100;
    /** From the panel's rows to the side panel's: room for the arrow on the strip joining them. */
    private static final int SIDE_GAP = 14;
    private static final String NAME = "Name  ";

    private final Screen parent;
    /** The model in front of you as the screen opened, listed first; null if there was none. */
    private final String lookedAt;
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private int tab = NEARBY;
    /**
     * The list the Hidden tab has open: "" for the unsaved lines, a profile's name, or null while
     * the tab shows the lists themselves.
     */
    private String openList;
    /**
     * The open list's lines as they were when it was opened.
     *
     * <p>TAKEN ONCE, not read live: removing a line edits the config, and a row that vanished
     * under the cursor would make undoing a misclick impossible. The row stays, its accent goes,
     * and the lines are only re-read when the list is opened again.
     */
    private List<String> openLines = List.of();
    /** The servers the open profile was used on when it opened; kept like its lines. */
    private List<String> openServers = List.of();
    /** The page of lists to go back to from an open one. */
    private int listsPage;
    /** Set by the first click on Delete profile; the second deletes. */
    private boolean confirmDelete;
    /** Set by Copy profile, so its row can say it worked. */
    private boolean copied;
    /** What the last Paste found, shown on its row; null before one. */
    private String pasted;
    /**
     * The open profile's name, typed into. Vanilla's text box, never drawn but added like the
     * buttons, so vanilla hands it the clicks and the keys - and handling keys ourselves would split
     * the 1.21.x jar the same way clicks would.
     */
    private EditBox nameBox;
    /**
     * The name box's text across a rebuild while it has the keyboard. Vanilla clears focus before
     * the screen is built again, so the new box cannot ask the old one.
     */
    private String stillTyping;
    /** The model whose bones the Nearby tab lists, or null while it lists models. */
    private String bonesOf;
    /** The models page to go back to from a model's bones. */
    private int modelPage;
    /** Set by build: the widest row on this page, which is what the panel is drawn around. */
    private int panelWidth;
    /** Set by build: from the header down to the bottom of the last row. */
    private int panelHeight;
    /** Set by build: the header's corner, placed from the saved position. */
    private int left = EDGE;
    private int top = Y;
    /** The open profile's Distance fade row while it fades with distance, for the side panel. */
    private Row distanceRow;
    /** Set by build: the side panel beside the Distance fade row; no width when there is none. */
    private int sideX;
    private int sideY;
    private int sideW;
    private int sideH;
    /**
     * Picked up by Move panel: drawn under the cursor until a click puts it down. The grab is the
     * point on the panel it was picked up by, so it does not jump to put its corner on the cursor.
     */
    private boolean moving;
    private int grabX;
    private int grabY;
    /** The cursor as of the last frame, because a button press does not say where it happened. */
    private int lastMouseX;
    private int lastMouseY;

    /**
     * One line: where it is, what it says, and what a click does.
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
        Runnable action;
        boolean on;
        boolean muted;
        /** Text in the middle of the row rather than at its left, for a number in a small box. */
        boolean centered;
        /** The open profile's name, typed into: drawn from the name box rather than its label. */
        boolean field;
        /** A value set by dragging: the slider's range, and the widget over the row, once built. */
        SliderSpec slider;
        RowSlider sliderWidget;
    }

    /**
     * A slider's stops, evenly spaced along it, where it stands, how its value reads, and what a
     * new value does. Stops rather than a range, so a scale can be finer where small values matter.
     */
    private record SliderSpec(double[] stops, double current, DoubleFunction<String> text,
                              DoubleConsumer onChange) {

        /** How far along the slider a value sits, 0 to 1 - between stops for one off them. */
        double positionOf(double v) {
            if (v <= stops[0]) {
                return 0;
            }
            for (int i = 1; i < stops.length; i++) {
                if (v <= stops[i]) {
                    return (i - 1 + (v - stops[i - 1]) / (stops[i] - stops[i - 1]))
                            / (stops.length - 1);
                }
            }
            return 1;
        }
    }

    /**
     * A vanilla slider, never drawn, over a row - so vanilla hands it the clicks, drags and arrow
     * keys, as the buttons and the name box get theirs. The row is painted from its value, and a
     * value is passed on only when it lands on a new stop, so a drag writes the config a few times
     * rather than every frame. Until it is moved it shows the value it was opened with, which a
     * hand-edited config can have put between stops.
     */
    private static final class RowSlider extends AbstractSliderButton {

        private final SliderSpec spec;
        private double sent;
        private boolean moved;

        RowSlider(Row row, SliderSpec spec) {
            super(row.x, row.y, row.w, ROW, Component.literal(row.label),
                  spec.positionOf(spec.current()));
            this.spec = spec;
            this.sent = spec.current();
        }

        private int stop() {
            return (int) Math.round(value * (spec.stops().length - 1));
        }

        /** How far along the row the value sits, 0 to 1. */
        double fraction() {
            return moved ? (double) stop() / (spec.stops().length - 1)
                         : spec.positionOf(spec.current());
        }

        String text() {
            return spec.text().apply(moved ? spec.stops()[stop()] : spec.current());
        }

        @Override
        protected void updateMessage() {
            // painted by the screen
        }

        @Override
        protected void applyValue() {
            moved = true;
            final double v = spec.stops()[stop()];
            if (v != sent) {
                sent = v;
                spec.onChange().accept(v);
            }
        }
    }

    /** Opacity's stops: hidden, then every five percent. */
    private static double[] opacityStops() {
        final double[] stops = new double[21];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = i * 5;
        }
        return stops;
    }

    public HiddenListScreen(Screen parent) {
        super(Component.literal("Hide Models"));
        this.parent = parent;
        this.lookedAt = NearbyModels.lookedAt(HideModels.listRadius());
    }

    private int perPage() {
        // Room kept for the header and the chrome rows: the tabs, the pager, the way back when a
        // model's bones are showing, and the Hidden tab's rows above its list.
        final int chrome = 2 + (showingBones() ? 1 : 0) + hiddenFixedRows();
        final int usable = height - Y - 14 - chrome * (ROW + GAP) - BOTTOM_MARGIN;
        return Math.max(1, usable / (ROW + GAP));
    }

    private boolean showingBones() {
        return tab == NEARBY && bonesOf != null;
    }

    /** The rows above an open list, and above and below the profiles. */
    private int hiddenFixedRows() {
        if (tab != HIDDEN) {
            return 0;
        }
        if (openList != null) {
            // A profile's name, Opacity, Distance fade, Copy and Delete, and its servers - and
            // From and To, when they have no room beside the panel.
            return openList.isEmpty() ? 0 : 5 + serverRows().size()
                    + (distancesInColumn(profileNamed(openList)) ? 2 : 0);
        }
        return HideModels.unsaved().isEmpty() ? 2 : 3;
    }

    /**
     * The open profile's server rows: the server being played on first, if there is one, then each
     * other server it was used on when it opened.
     */
    private List<String> serverRows() {
        final String here = HideModels.currentServer();
        final List<String> out = new ArrayList<>();
        if (here != null) {
            out.add(here);
        }
        for (String server : openServers) {
            if (!server.equals(here)) {
                out.add(server);
            }
        }
        return out;
    }

    /**
     * Which group a profile is listed in: those for the server being played on, then those switched
     * by hand, then those for other servers - still there to open, rename and edit.
     */
    private static int serverRank(HideModels.Profile p, String here) {
        if (p.servers().isEmpty()) {
            return 1;
        }
        return here != null && p.servers().contains(here) ? 0 : 2;
    }

    private static HideModels.Profile profileNamed(String name) {
        for (HideModels.Profile p : HideModels.profiles()) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /** Whether a line is still in the open list - what the accent on its row shows. */
    private boolean inOpenList(String line) {
        if (openList == null) {
            return false;
        }
        if (openList.isEmpty()) {
            return HideModels.unsaved().contains(line);
        }
        final HideModels.Profile p = profileNamed(openList);
        return p != null && p.patterns().contains(line);
    }

    /** Opens a list in the Hidden tab, from wherever the screen is. */
    private void openList(String list) {
        listsPage = tab == HIDDEN && openList == null ? page : 0;
        nameBox = null;
        tab = HIDDEN;
        bonesOf = null;
        openList = list;
        final HideModels.Profile p = list.isEmpty() ? null : profileNamed(list);
        openLines = List.copyOf(list.isEmpty() ? HideModels.unsaved()
                : p == null ? List.of() : p.patterns());
        openServers = p == null ? List.of() : List.copyOf(p.servers());
        confirmDelete = false;
        copied = false;
        pasted = null;
        page = 0;
        rebuildWidgets();
    }

    private int pageCount(int total) {
        return Math.max(1, (total + perPage() - 1) / perPage());
    }

    /** The Nearby tab's rows: the models around you, or one model's bones. */
    private List<NearbyModels.Nearby> nearbyRows() {
        if (tab != NEARBY) {
            return List.of();
        }
        final double radius = HideModels.listRadius();
        if (bonesOf != null) {
            return NearbyModels.bones(radius, bonesOf);
        }
        final List<NearbyModels.Nearby> models = new ArrayList<>(NearbyModels.nearby(radius));
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id().equals(lookedAt)) {
                models.add(0, models.remove(i));
                break;
            }
        }
        return models;
    }

    /** The row of the model you were looking at, in the models list. */
    private boolean isLookedAt(String id) {
        return tab == NEARBY && bonesOf == null && id != null && id.equals(lookedAt);
    }

    private void build() {
        rows.clear();
        distanceRow = null;
        sideW = 0;
        final List<NearbyModels.Nearby> found = nearbyRows();
        final List<String> source = new ArrayList<>();
        if (tab == HIDDEN && openList == null) {
            final String here = HideModels.currentServer();
            final List<HideModels.Profile> ranked = new ArrayList<>(HideModels.profiles());
            ranked.sort(java.util.Comparator.comparingInt(p -> serverRank(p, here)));
            for (HideModels.Profile p : ranked) {
                source.add(p.name());
            }
        } else if (tab == HIDDEN) {
            source.addAll(openLines);
        }
        for (NearbyModels.Nearby n : found) {
            source.add(n.id());
        }
        final int perPage = perPage();
        final int pages = pageCount(source.size());
        // A page can vanish under you: hiding the last model on it leaves the index past the end.
        page = Math.min(page, pages - 1);

        final int from = page * perPage;
        List<String> shown = source.subList(from, Math.min(source.size(), from + perPage));

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
        final int cap = panelCap();
        // The header shares its line with the close mark, at the right end.
        final int headerW = Math.max(measure(header(source.size(), goneCount(found), pages)),
                moving ? measure(MOVING) : 0) + GAP * 2 + ROW;
        int content = 0;
        if (tab == SETTINGS) {
            content = Math.max(content, measure(RADIUS_LABEL) + GAP + stepperWidth());
        }
        if (showingBones()) {
            content = Math.max(content, measure("◀ " + bonesOf));
        }
        // A model can be opened up into its bones, from a cell on the right of its row.
        int cellW = 0;
        for (int i = 0; i < shown.size(); i++) {
            if (opensUp(shown.get(i))) {
                cellW = Math.max(cellW, measure(found.get(from + i).pieces() + OPEN) + PAD * 2);
            }
        }
        for (String id : shown) {
            content = Math.max(content, measure((isLookedAt(id) ? LOOKING : "") + id)
                    + (opensUp(id) ? GAP + cellW : 0));
        }
        // The lists: each profile's name with its cells beside it, and the fixed rows above.
        final int unsavedCount = HideModels.unsaved().size();
        final int plusW = measure("+") + PAD * 2;
        int listCellW = 0;
        if (tab == HIDDEN && openList == null) {
            listCellW = measure(unsavedCount + OPEN) + PAD * 2;
            for (String name : shown) {
                final HideModels.Profile p = profileNamed(name);
                listCellW = Math.max(listCellW,
                        measure((p == null ? 0 : p.patterns().size()) + OPEN) + PAD * 2);
            }
            content = Math.max(content, measure(UNSAVED) + GAP + listCellW);
            content = Math.max(content, measure(pasted != null ? pasted : PASTE));
            if (unsavedCount > 0) {
                content = Math.max(content, measure(SAVE_UNSAVED));
            }
            for (String name : shown) {
                content = Math.max(content, measure(name) + GAP + listCellW
                        + (unsavedCount > 0 ? plusW + GAP : 0));
            }
        } else if (tab == HIDDEN) {
            if (!openList.isEmpty()) {
                content = Math.max(content, measure(NAME + openList));
                content = Math.max(content, Math.max(measure(DELETE_SURE), measure(COPIED)));
                content = Math.max(content, measure(OPACITY) + PAD + measure("Hidden"));
                content = Math.max(content, measure(DISTANCE) + PAD + measure("Near"));
                if (distancesInColumn(profileNamed(openList))) {
                    content = Math.max(content, measure(FROM) + PAD + measure("32 blocks"));
                }
                for (String server : serverRows()) {
                    content = Math.max(content, measure(serverLabel(server)) + PAD + measure("Off"));
                }
            }
        }
        final int prevW = measure(PREV) + PAD * 2;
        final int nextW = measure(NEXT) + PAD * 2;
        final int rowWidth = Math.min(cap, Math.max(Math.max(strip, headerW),
                Math.max(content + PAD * 2, pages > 1 ? prevW + GAP + nextW : 0)));
        panelWidth = rowWidth;

        // Laid out in the top left, then moved as a whole to where it was put.
        left = EDGE;
        top = Y;
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

        if (showingBones()) {
            chrome(left, y, rowWidth, "◀ " + bonesOf, () -> {
                bonesOf = null;
                page = modelPage;
                rebuildWidgets();
            });
            y += ROW + GAP;
        }

        if (tab == HIDDEN && openList == null) {
            y = lists(y, rowWidth, shown, listCellW, plusW);
            shown = List.of();
        } else if (tab == HIDDEN) {
            y = openListHead(y, rowWidth);
        }

        for (int i = 0; i < shown.size(); i++) {
            final String id = shown.get(i);
            final boolean opens = opensUp(id);
            final Row row = row(left, y, opens ? rowWidth - cellW - GAP : rowWidth,
                    (isLookedAt(id) ? LOOKING : "") + id);
            row.id = id;
            row.on = tab == HIDDEN ? inOpenList(id) : HideModels.listed(id);
            // Dimmed: seen lately, and gone - still there to hide before it comes back.
            row.muted = tab == NEARBY && found.get(from + i).gone();
            hit(row, () -> {
                if (tab == HIDDEN) {
                    toggleOpenLine(id);
                    rebuildWidgets();
                    return;
                }
                // A profile is changed by opening it, so a stray click here never edits one.
                final String list = HideModels.hidingList(id);
                if (list != null && !list.isEmpty()) {
                    openList(list);
                    return;
                }
                // A model hides whole; unhiding it takes its bones' lines too, so a model that
                // had single bones hidden goes all hidden on one click and all shown on the next.
                if (!HideModels.listed(id)) {
                    HideModels.add(id);
                } else if (opens) {
                    HideModels.removeModel(id);
                } else {
                    HideModels.remove(id);
                }
                rebuildWidgets();
            });
            if (opens) {
                final Row cell = row(left + rowWidth - cellW, y, cellW,
                        found.get(from + i).pieces() + OPEN);
                cell.muted = true;
                cell.hint = "Show its bones, to hide just one";
                hit(cell, () -> {
                    modelPage = page;
                    bonesOf = id;
                    page = 0;
                    rebuildWidgets();
                });
            }
            y += ROW + GAP;
        }

        if (pages > 1) {
            // The pager takes only what its labels need; the gap between them absorbs the rest.
            chrome(left, y, prevW, PREV, () -> turn(-1, pages));
            chrome(left + rowWidth - nextW, y, nextW, NEXT, () -> turn(1, pages));
            y += ROW + GAP;
        }
        panelHeight = y - GAP - top;
        // Level with the header's text, which it shares the top line with.
        final Row close = row(left + rowWidth - ROW, top - 4, ROW, CLOSE);
        close.muted = true;
        close.centered = true;
        hit(close, this::onClose);

        final int dx = (int) Math.round(HideModels.guiX() * roomX());
        final int dy = (int) Math.round(HideModels.guiY() * roomY());
        for (Row row : rows) {
            row.x += dx;
            row.y += dy;
        }
        left += dx;
        top += dy;
        if (distanceRow != null) {
            fadeSidePanel();
        }

        if (moving) {
            // The next click anywhere puts the panel down, so one target covers the screen.
            addRenderableWidget(Button.builder(Component.empty(), b -> place())
                    .bounds(0, 0, width, height).build());
            return;
        }
        for (Row row : rows) {
            if (row.field) {
                addNameBox(row);
            } else if (row.slider != null) {
                row.sliderWidget = new RowSlider(row, row.slider);
                addRenderableWidget(row.sliderWidget);
            } else if (row.action != null) {
                final Runnable action = row.action;
                addRenderableWidget(Button.builder(Component.empty(), b -> action.run())
                        .bounds(row.x, row.y, row.w, ROW).build());
            }
        }
    }

    /**
     * Over the name row. A rebuild makes a new box, so one that had the keyboard hands its text
     * and the keyboard on - including a name not yet taken because it is empty or in use.
     */
    private void addNameBox(Row row) {
        final boolean typing = stillTyping != null;
        final String text = typing ? stillTyping : openList;
        stillTyping = null;
        nameBox = new EditBox(font, row.x, row.y, row.w, ROW, Component.literal(row.label));
        nameBox.setMaxLength(48);
        nameBox.setValue(text);
        nameBox.setResponder(this::rename);
        addRenderableWidget(nameBox);
        if (typing) {
            setFocused(nameBox);
            nameBox.setFocused(true);
        }
    }

    /**
     * As it is typed, so there is nothing to confirm. A name that is empty or another profile's is
     * not taken; the profile keeps its last good one until the text becomes one.
     */
    private void rename(String typed) {
        if (openList != null && !openList.isEmpty() && HideModels.renameProfile(openList, typed)) {
            openList = typed.trim();
            minecraft.execute(() -> {
                stillTyping = nameBox != null && nameBox.isFocused() ? nameBox.getValue() : null;
                rebuildWidgets();
            });
        }
    }

    /** How far the panel can move across without leaving the screen. */
    private int roomX() {
        return Math.max(0, width - 2 * EDGE - panelWidth);
    }

    /** How far it can move down; none when a long list already fills the height. */
    private int roomY() {
        return Math.max(0, height - BOTTOM_MARGIN - Y - panelHeight);
    }

    /**
     * The Hidden tab's front page: the unsaved lines, the way to save them, then this page of
     * profiles - each switched on or off by a click, with + to add the unsaved lines to it and
     * its count to open it.
     */
    private int lists(int y, int w, List<String> names, int cellW, int plusW) {
        final int unsavedCount = HideModels.unsaved().size();
        final Row unsaved = row(left, y, w - cellW - GAP, UNSAVED);
        unsaved.hint = "Hidden by a click and in no profile, so always applied";
        hit(unsaved, () -> openList(""));
        final Row unsavedCell = row(left + w - cellW, y, cellW, unsavedCount + OPEN);
        unsavedCell.muted = true;
        unsavedCell.hint = "Show its lines";
        hit(unsavedCell, () -> openList(""));
        y += ROW + GAP;

        if (unsavedCount > 0) {
            final Row save = row(left, y, w, SAVE_UNSAVED);
            save.hint = "Moves the " + unsavedCount + " unsaved line" + (unsavedCount == 1 ? "" : "s")
                    + " into a new profile, switched on";
            hit(save, () -> {
                HideModels.saveUnsavedAsProfile();
                rebuildWidgets();
            });
            y += ROW + GAP;
        }

        for (String name : names) {
            final HideModels.Profile p = profileNamed(name);
            if (p == null) {
                continue;
            }
            final int cells = cellW + (unsavedCount > 0 ? plusW + GAP : 0);
            final Row row = row(left, y, w - cells - GAP, name);
            row.on = p.on();
            if (p.opacity() > 0) {
                row.value = p.opacity() + "%";
            }
            final int rank = serverRank(p, HideModels.currentServer());
            row.muted = rank == 2;
            row.hint = (rank == 0 ? "Used on this server\n"
                        : rank == 2 ? "Used on " + p.servers().get(0)
                                      + (p.servers().size() > 1 ? " and " + (p.servers().size() - 1)
                                         + " more" : "") + "\n"
                        : "")
                    + (p.on() ? "On - click to switch it off" : "Off - click to switch it on");
            hit(row, () -> {
                HideModels.setProfileOn(name, !p.on());
                rebuildWidgets();
            });
            int x = left + w - cells;
            if (unsavedCount > 0) {
                final Row plus = row(x, y, plusW, "+");
                plus.muted = true;
                plus.hint = "Add the " + unsavedCount + " unsaved line"
                        + (unsavedCount == 1 ? "" : "s") + " to this profile";
                hit(plus, () -> {
                    HideModels.addUnsavedToProfile(name);
                    rebuildWidgets();
                });
                x += plusW + GAP;
            }
            final Row open = row(x, y, cellW, p.patterns().size() + OPEN);
            open.muted = true;
            open.hint = "Show its lines";
            hit(open, () -> openList(name));
            y += ROW + GAP;
        }

        final Row paste = row(left, y, w, pasted != null ? pasted : PASTE);
        paste.hint = "Adds a profile someone copied, from your clipboard";
        hit(paste, () -> {
            final HideModels.Pasted got =
                    HideModels.pasteProfiles(minecraft.keyboardHandler.getClipboard());
            pasted = got.added().size() > 1 ? "Pasted " + got.added().size() + " profiles"
                    : got.added().size() == 1 ? "Pasted '" + got.added().get(0) + "'"
                    : !got.already().isEmpty() ? "Already have '" + got.already().get(0) + "'"
                    : "No profile on the clipboard";
            rebuildWidgets();
        });
        y += ROW + GAP;
        return y;
    }

    /**
     * Above an open profile's lines: its name, how it draws, its servers, Copy, and Delete - asked
     * twice. The Hidden tab is the way back to the lists.
     */
    private int openListHead(int y, int w) {
        if (!openList.isEmpty()) {
            final Row name = row(left, y, w, openList);
            name.field = true;
            name.hint = "Click to rename it, then type";
            y += ROW + GAP;
            final HideModels.Profile profile = profileNamed(openList);
            // How its models are drawn: hidden, or see-through, and whether that changes with
            // distance - from where to where.
            final String list = openList;
            final int opacity = profile == null ? 0 : profile.opacity();
            final Row fade = row(left, y, w, OPACITY);
            fade.slider = new SliderSpec(opacityStops(), opacity,
                    v -> v <= 0 ? "Hidden" : Math.round(v) + "%",
                    v -> HideModels.setProfileOpacity(list, (int) Math.round(v)));
            fade.hint = "0 hides its models; more draws them see-through, 100 as they are"
                    + "\nClick or drag"
                    + (HideModels.fadingAvailable() ? "" : "\nThis Minecraft version can only hide");
            y += ROW + GAP;
            final DistanceFade mode = profile == null ? DistanceFade.OFF : profile.fade();
            final double from = profile == null ? ConfigText.FADE_FROM : profile.fadeFrom();
            final double to = profile == null ? ConfigText.FADE_TO : profile.fadeTo();
            final Row distance = row(left, y, w, DISTANCE);
            distance.value = mode == DistanceFade.FAR ? "Far"
                    : mode == DistanceFade.NEAR ? "Near" : "Off";
            distance.on = mode != DistanceFade.OFF;
            distance.hint = "Off: the opacity everywhere"
                    + "\nFar: as set close by, fading out to nothing by To"
                    + "\nNear: nothing close by, fading in to as set by To";
            hit(distance, () -> {
                HideModels.setProfileFade(list, mode.next(), from, to);
                rebuildWidgets();
            });
            y += ROW + GAP;
            if (distancesInColumn(profile)) {
                distanceRows(left, y, w, list, mode, from, to);
                y += 2 * (ROW + GAP);
            } else if (mode != DistanceFade.OFF) {
                distanceRow = distance;
            }
            final String here = HideModels.currentServer();
            // Switches, as in Settings: on means joining that server switches the profile on.
            for (String server : serverRows()) {
                final boolean used = profile != null && profile.servers().contains(server);
                toggle(y, w, serverLabel(server), used
                        ? "Switched on when you join " + server + ",\nand off on any other"
                        : "Switch it on whenever you join " + server
                          + (server.equals(here) ? "" : "\nYou are not on it now"), used,
                        () -> HideModels.setProfileServer(openList, server, !used));
                y += ROW + GAP;
            }
            final Row copy = row(left, y, w, copied ? COPIED : COPY);
            copy.hint = "Copies it as text, for someone to paste into theirs";
            hit(copy, () -> {
                final String text = HideModels.profileText(openList);
                if (text != null) {
                    minecraft.keyboardHandler.setClipboard(text);
                    copied = true;
                }
                rebuildWidgets();
            });
            y += ROW + GAP;
            final Row delete = row(left, y, w, confirmDelete ? DELETE_SURE : DELETE);
            delete.muted = !confirmDelete;
            delete.hint = "Deletes the profile and every line in it";
            hit(delete, () -> {
                if (confirmDelete) {
                    HideModels.deleteProfile(openList);
                    openList = null;
                    copied = false;
                    page = listsPage;
                }
                confirmDelete = !confirmDelete;
                rebuildWidgets();
            });
            y += ROW + GAP;
        }
        return y;
    }

    /**
     * A distance fade's From and To, in a panel jutting out level with its Distance fade row, on
     * the side facing the middle of the screen - or the other, when that one has no room. Laid
     * out once the panel is where it was put, as it hangs beside the panel rather than in it.
     */
    private void fadeSidePanel() {
        final HideModels.Profile profile = profileNamed(openList);
        if (profile == null) {
            return;
        }
        sideW = sideWidth();
        sideH = ROW * 2 + GAP;
        // A side had room for the widest the panel gets, so it has room for this one.
        final int side = sideRoom(left, panelWidth);
        final boolean onLeft = side == 0 ? sideOnLeft() : side < 0;
        sideX = onLeft ? left - SIDE_GAP - sideW : left + panelWidth + SIDE_GAP;
        sideY = distanceRow.y;
        distanceRows(sideX, sideY, sideW, openList, profile.fade(), profile.fadeFrom(),
                profile.fadeTo());
    }

    /** A fade's From and To sliders, as two rows from (x, y). */
    private void distanceRows(int x, int y, int w, String list, DistanceFade mode, double from,
                              double to) {
        final Row near = row(x, y, w, FROM);
        near.slider = new SliderSpec(DISTANCE_STOPS, from, HiddenListScreen::blocksText, v -> {
            final HideModels.Profile now = profileNamed(list);
            HideModels.setProfileFade(list, mode, v, now == null ? to : now.fadeTo());
        });
        near.hint = "Where the fade starts, in blocks from your eyes";
        final Row far = row(x, y + ROW + GAP, w, TO);
        far.slider = new SliderSpec(DISTANCE_STOPS, to, HiddenListScreen::blocksText, v -> {
            final HideModels.Profile now = profileNamed(list);
            HideModels.setProfileFade(list, mode, now == null ? from : now.fadeFrom(), v);
        });
        far.hint = "Where it ends";
    }

    private static int sideWidth() {
        return Math.max(SIDE_MIN,
                Math.max(measure(FROM), measure(TO)) + PAD * 3 + measure("32 blocks"));
    }

    /** The tabs side by side, which no panel is narrower than. */
    private static int tabStrip() {
        int strip = -GAP;
        for (String tab : TABS) {
            strip += measure(tab) + PAD * 2 + GAP;
        }
        return strip;
    }

    /** The widest the panel gets: a third of the screen, or the tabs. */
    private int panelCap() {
        return Math.max(tabStrip(), width / 3);
    }

    /**
     * Which side of a panel there is room for the side panel on: -1 its left, 1 its right, 0
     * neither. The side facing the screen's middle is tried first.
     */
    private int sideRoom(int panelLeft, int panelW) {
        final int need = SIDE_GAP + sideWidth() + 4;
        final boolean leftFits = panelLeft - need >= 0;
        final boolean rightFits = panelLeft + panelW + need <= width;
        final boolean towardLeft = panelLeft + panelW / 2 > width / 2;
        if (towardLeft ? leftFits : rightFits) {
            return towardLeft ? -1 : 1;
        }
        if (towardLeft ? rightFits : leftFits) {
            return towardLeft ? 1 : -1;
        }
        return 0;
    }

    /**
     * Whether a profile's distances go in the column, under Distance fade: when it fades with
     * distance and neither side of the panel has room for them, even at the widest the panel gets
     * where it was put. A narrower panel only has more room either side, so the answer holds.
     */
    private boolean distancesInColumn(HideModels.Profile profile) {
        if (profile == null || profile.fade() == DistanceFade.OFF) {
            return false;
        }
        final int cap = panelCap();
        final int at = EDGE
                + (int) Math.round(HideModels.guiX() * Math.max(0, width - 2 * EDGE - cap));
        return sideRoom(at, cap) == 0;
    }

    /**
     * A solid triangle four pixels deep and seven tall, centered on y, pointing out of the panel.
     * Drawn from fills, so it is the same at any GUI scale and in any font.
     */
    private static void arrow(Painter p, int x, int y, boolean pointsLeft) {
        for (int i = 0; i < 4; i++) {
            final int half = 3 - i;
            p.fill(pointsLeft ? x + 3 - i : x + i, y - half, 1, half * 2 + 1, ACCENT);
        }
    }

    /** Whether what goes beside the panel goes to its left: the side facing the screen's middle. */
    private boolean sideOnLeft() {
        return left + panelWidth / 2 > width / 2;
    }

    private static String blocksText(double blocks) {
        return ConfigText.number(blocks) + (blocks == 1 ? " block" : " blocks");
    }

    /** A server row's label: the server being played on as such, any other by its address. */
    private static String serverLabel(String server) {
        return server.equals(HideModels.currentServer()) ? "Use on this server" : "Use on " + server;
    }

    /** Takes a line out of the open list, or puts it back. */
    private void toggleOpenLine(String line) {
        final boolean in = inOpenList(line);
        if (openList.isEmpty()) {
            if (in) {
                HideModels.remove(line);
            } else {
                HideModels.add(line);
            }
        } else if (in) {
            HideModels.removeFromProfile(openList, line);
        } else {
            HideModels.addToProfile(openList, line);
        }
    }

    /** Only a model can be opened, and only one whose id has bones under it. */
    private boolean opensUp(String id) {
        return tab == NEARBY && bonesOf == null && id.endsWith("/");
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

    /** Its invisible button is added by build, once the panel is where it belongs. */
    private void hit(Row row, Runnable action) {
        row.action = action;
    }

    /**
     * Switching goes back to page one, and to the lists rather than one of them. The Hidden tab,
     * clicked in an open list, goes back to the page of lists it was opened from.
     */
    private void tabButton(int x, int y, int w, int which) {
        final Row row = row(x, y, w, TABS[which]);
        row.muted = tab != which;
        hit(row, () -> {
            final boolean backToLists = which == HIDDEN && tab == HIDDEN && openList != null;
            tab = which;
            openList = null;
            nameBox = null;
            confirmDelete = false;
            copied = false;
            pasted = null;
            bonesOf = null;
            page = backToLists ? listsPage : 0;
            rebuildWidgets();
        });
    }

    /** Each writes the config exactly as its command does, so the screen and the file agree. */
    private int settings(int y, int w) {
        final boolean enabled = HideModels.isEnabled();
        toggle(y, w, "Hiding", "Off keeps the list but hides nothing\n"
                + "Shift with the Hide Models key switches it anywhere", enabled,
                () -> HideModels.setEnabled(!enabled));
        y += ROW + GAP;
        final boolean firstPerson = HideModels.isFirstPersonOnly();
        toggle(y, w, "First person only", "Hide only while the camera is in first person",
                firstPerson, () -> HideModels.setFirstPersonOnly(!firstPerson));
        y += ROW + GAP;

        // - and + either side of the number, so both buttons sit by what they change.
        final int sideW = measure("+") + PAD * 2;
        final int stepX = left + w - stepperWidth();
        final Row radius = row(left, y, w - stepperWidth() - GAP, RADIUS_LABEL);
        radius.hint = "How far the Nearby tab looks, in blocks";
        chrome(stepX, y, sideW, "-", () -> stepRadius(-1));
        final Row number = row(stepX + sideW + GAP, y, numberWidth(),
                Integer.toString((int) HideModels.listRadius()));
        number.hint = radius.hint;
        number.centered = true;
        chrome(stepX + sideW + GAP + numberWidth() + GAP, y, sideW, "+", () -> stepRadius(1));
        y += ROW + GAP;

        // The two top corners as presets; anywhere else came from Move panel. The panel moves the
        // moment either row is clicked, so it leaves the cursor behind.
        final boolean topLeft = HideModels.guiX() == 0 && HideModels.guiY() == 0;
        final boolean topRight = HideModels.guiX() == 1 && HideModels.guiY() == 0;
        final Row position = row(left, y, w, "Position");
        position.value = topLeft ? "Top left" : topRight ? "Top right" : "Moved";
        position.hint = "Click to switch between the top corners";
        hit(position, () -> {
            HideModels.setGuiPosition(topLeft ? 1 : 0, 0);
            rebuildWidgets();
        });
        y += ROW + GAP;

        final Row move = row(left, y, w, "Move panel");
        move.hint = "Click, then click where the panel should go";
        hit(move, () -> {
            moving = true;
            grabX = lastMouseX - left;
            grabY = lastMouseY - top;
            rebuildWidgets();
        });
        return y + ROW + GAP;
    }

    private void toggle(int y, int w, String label, String hint, boolean on, Runnable action) {
        final Row row = row(left, y, w, label);
        row.hint = hint;
        row.value = on ? "On" : "Off";
        row.on = on;
        hit(row, () -> {
            action.run();
            rebuildWidgets();
        });
    }

    /** Wide enough for the largest radius, so the buttons stay put as the number changes. */
    private static int numberWidth() {
        return measure("256") + PAD * 2;
    }

    private static int stepperWidth() {
        return 2 * (measure("+") + PAD * 2) + numberWidth() + 2 * GAP;
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

    /** Puts a moving panel down where the cursor has it, saved as fractions of the room. */
    private void place() {
        final int x = clamp(lastMouseX - grabX, EDGE, EDGE + roomX());
        final int y = clamp(lastMouseY - grabY, Y, Y + roomY());
        moving = false;
        HideModels.setGuiPosition(
                roomX() == 0 ? HideModels.guiX() : (double) (x - EDGE) / roomX(),
                roomY() == 0 ? HideModels.guiY() : (double) (y - Y) / roomY());
        rebuildWidgets();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void init() {
        build();
    }

    @Override
    protected void paint(Painter p, int mouseX, int mouseY, float partial) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        // A moving panel is drawn where the cursor would put it; nothing is laid out again.
        final int ox = moving ? clamp(mouseX - grabX, EDGE, EDGE + roomX()) - left : 0;
        final int oy = moving ? clamp(mouseY - grabY, Y, Y + roomY()) - top : 0;

        p.fill(left - 4 + ox, top - 6 + oy, panelWidth + 8, panelHeight + 12, PANEL);
        if (sideW > 0) {
            // Its background reaches back to the panel's, without overlapping it, and a strip of
            // row with an arrow on it joins it to Distance fade - the row it opened from.
            final boolean onLeft = sideX < left;
            final int fillX = onLeft ? sideX - 4 : left + panelWidth + 4;
            final int fillEnd = onLeft ? left - 4 : sideX + sideW + 4;
            p.fill(fillX + ox, sideY - 4 + oy, fillEnd - fillX, sideH + 8, PANEL);
            final int stripX = onLeft ? sideX + sideW : left + panelWidth;
            final int stripW = Math.abs((onLeft ? left : sideX) - stripX);
            p.fill(stripX + ox, distanceRow.y + oy, stripW, ROW, ROW_BG);
            arrow(p, stripX + (stripW - 4) / 2 + ox, distanceRow.y + ROW / 2 + oy, onLeft);
        }

        final double radius = HideModels.listRadius();
        final List<NearbyModels.Nearby> around = nearbyRows();
        final int total = tab != HIDDEN ? around.size()
                : openList == null ? HideModels.profiles().size() : openLines.size();
        p.text(fit(p, moving ? MOVING : header(total, goneCount(around), pageCount(total)),
                        panelWidth - ROW - GAP * 2),
                left + ox, top + oy, moving ? TEXT : DIM);

        final Set<String> partly = tab == NEARBY && bonesOf == null
                ? NearbyModels.withHiddenBones(radius) : Set.of();
        Row hovered = null;
        for (Row row : rows) {
            final boolean over = !moving && inside(row, mouseX, mouseY);
            if (over) {
                hovered = row;
            }
            final int x = row.x + ox;
            final int y = row.y + oy;
            final boolean typing = row.field && nameBox != null && nameBox.isFocused();
            p.fill(x, y, row.w, ROW, typing || (over && (row.action != null || row.field))
                    ? ROW_HOVER : ROW_BG);
            if (row.sliderWidget != null) {
                final int lit = (int) Math.round(row.w * row.sliderWidget.fraction());
                p.fill(x, y, lit, ROW, ROW_HOVER);
                p.fill(x + Math.max(0, lit - 2), y, 2, ROW, ACCENT);
                row.value = row.sliderWidget.text();
            }
            // Read live, so a hand edit to the config shows without reopening. In an open list
            // the bar means the line is still in it; elsewhere, that something hides the model.
            final boolean on = row.id == null ? row.on
                    : tab == HIDDEN ? inOpenList(row.id) : HideModels.listed(row.id);
            if (on || (row.id != null && (partly.contains(row.id)
                    || (tab == NEARBY && HideModels.opacityOfId(row.id) < 1f)))) {
                // A bar down the left edge rather than a tick: it reads at a glance down a column.
                p.fill(x, y, 2, ROW, on ? ACCENT : PARTIAL);
            }
            final int ty = y + (ROW - p.lineHeight()) / 2 + 1;
            // A centered label sits in a small box, the close mark's or a number's: no inset.
            int room = row.centered ? row.w - 2 : row.w - PAD * 2;
            if (row.value != null) {
                final int vw = p.textWidth(row.value);
                p.text(row.value, x + row.w - PAD - vw, ty,
                        on ? ACCENT : "Off".equals(row.value) ? DIM : TEXT);
                room -= vw + PAD;
            }
            int tx = x + PAD;
            String text = row.label;
            if (row.field && nameBox != null) {
                // Labelled, so the row reads as somewhere to type rather than as one more line.
                p.text(NAME, tx, ty, DIM);
                tx += p.textWidth(NAME);
                room -= p.textWidth(NAME);
                text = nameBox.getValue() + (typing && System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
            }
            final String label = fit(p, text, room);
            if (row.centered) {
                tx = x + (row.w - p.textWidth(label)) / 2;
            }
            p.text(label, tx, ty, row.muted ? DIM : TEXT);
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
            return List.of(row.hint.split("\n"));
        }
        if (row.id == null) {
            return List.of();
        }
        final String within = " within " + (int) radius + " blocks";
        final String covering = HideModels.coveredBy(row.id);
        if (tab == HIDDEN) {
            if (!inOpenList(row.id)) {
                return List.of("Taken out - click to put it back");
            }
            final HideModels.Profile profile = openList.isEmpty() ? null : profileNamed(openList);
            if (profile != null && !profile.on()) {
                return List.of("Hides nothing while the profile is off");
            }
            final int pieces = NearbyModels.piecesMatching(radius, row.id);
            return List.of(pieces == 0 ? "Hides nothing" + within
                                       : "Hides " + pieces + (pieces == 1 ? " piece" : " pieces") + within);
        }
        final List<String> lines = new ArrayList<>();
        if (isLookedAt(row.id)) {
            lines.add("In front of you");
        }
        for (NearbyModels.Nearby n : around) {
            if (n.id().equals(row.id)) {
                if (n.gone()) {
                    lines.add("Gone - last seen " + Math.max(1, n.goneFor() / 1000) + "s ago");
                }
                lines.add(n.pieces() + (n.pieces() == 1 ? " piece, " : " pieces, nearest ")
                        + NearbyModels.fmt(n.distance()) + " blocks away");
            }
        }
        // Hiding single bones only marks a model's row orange, so say how much of it is gone.
        int hidden = 0;
        if (covering == null && opensUp(row.id)) {
            int pieces = 0;
            for (NearbyModels.Nearby bone : NearbyModels.bones(radius, row.id)) {
                pieces += bone.pieces();
                if (HideModels.listed(bone.id())) {
                    hidden += bone.pieces();
                }
            }
            if (hidden > 0) {
                lines.add(hidden + " of its " + pieces + " pieces hidden");
            }
        }
        final String list = HideModels.hidingList(row.id);
        final float opacity = HideModels.opacityOfId(row.id);
        if (covering == null && opacity < 1f && list != null && !list.isEmpty()) {
            lines.add("Drawn at " + Math.round(opacity * 100) + "% by the profile '" + list + "'");
            final HideModels.Profile fading = profileNamed(list);
            if (fading != null && fading.fade() != DistanceFade.OFF) {
                lines.add((fading.fade() == DistanceFade.FAR ? "Fading out" : "Fading in") + " from "
                        + blocksText(fading.fadeFrom()) + " to " + blocksText(fading.fadeTo()));
            }
            lines.add("Click to open it");
        } else if (covering == null) {
            lines.add(hidden > 0 ? "Click to hide all of it" : "Click to hide");
        } else if (list != null && !list.isEmpty()) {
            lines.add("Hidden by the profile '" + list + "'");
            lines.add("Click to open it");
        } else if (covering.equals(row.id.toLowerCase(Locale.ROOT))) {
            lines.add("Hidden - click to unhide");
        } else {
            // A click would try to remove a line that is not there; say which one is.
            lines.add("Hidden by '" + covering + "'");
            lines.add("Unhide it from the Hidden tab");
        }
        return lines;
    }

    /**
     * Beside the panel on the side facing the middle - beyond the side panel, when there is one -
     * level with the row, and pulled back in if it would leave the screen.
     */
    private void tip(Painter p, Row row, List<String> lines) {
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, p.textWidth(line));
        }
        w += PAD * 2;
        final int lineH = p.lineHeight() + 2;
        final int h = lines.size() * lineH + 6;
        final boolean towardLeft = sideOnLeft();
        // The outer edge of what is drawn on that side: the panel's, or the side panel's.
        final int edge = towardLeft ? (sideW > 0 ? Math.min(left, sideX) : left) - 4
                                    : (sideW > 0 ? Math.max(left + panelWidth, sideX + sideW)
                                                 : left + panelWidth) + 4;
        final int x = towardLeft
                ? Math.max(0, edge - 4 - w)
                : Math.max(0, Math.min(edge + 4, width - w - 4));
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

    private static int goneCount(List<NearbyModels.Nearby> rows) {
        int gone = 0;
        for (NearbyModels.Nearby n : rows) {
            gone += n.gone() ? 1 : 0;
        }
        return gone;
    }

    /** {@code gone} of the {@code total} Nearby rows are models seen lately that have gone. */
    private String header(int total, int gone, int pages) {
        if (tab == SETTINGS) {
            return HideModels.isServerDisabled()
                    ? "This server has hiding off"
                    : "config/" + HideModels.MOD_ID + ".txt";
        }
        final String page = pages > 1 ? "    " + (this.page + 1) + "/" + pages : "";
        if (tab == HIDDEN && openList == null) {
            final List<HideModels.Profile> all = HideModels.profiles();
            int on = 0;
            for (HideModels.Profile p : all) {
                on += p.on() ? 1 : 0;
            }
            return HideModels.unsaved().size() + " unsaved" + (all.isEmpty() ? ""
                    : ", " + on + " of " + all.size() + " profile" + (all.size() == 1 ? "" : "s")
                      + " on") + page;
        }
        if (tab == HIDDEN) {
            final HideModels.Profile profile = openList.isEmpty() ? null : profileNamed(openList);
            return (openList.isEmpty() ? UNSAVED : openList) + ": " + total
                    + (total == 1 ? " line" : " lines")
                    + (profile == null ? "" : profile.on() ? ", on" : ", off") + page;
        }
        final int radius = (int) HideModels.listRadius();
        final int here = total - gone;
        final String lately = gone > 0 ? ", " + gone + " gone" : "";
        if (here == 0) {
            return (showingBones() ? "No bones" : "Nothing") + " within " + radius + " blocks"
                    + lately + page;
        }
        final String noun = showingBones() ? (here == 1 ? " bone" : " bones")
                                           : (here == 1 ? " model" : " models");
        return here + noun + " within " + radius + lately + page;
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

    /** Esc while moving puts the panel back where it was rather than closing the screen. */
    @Override
    public void onClose() {
        if (moving) {
            moving = false;
            rebuildWidgets();
            return;
        }
        Screens.open(minecraft, parent);
    }
}
