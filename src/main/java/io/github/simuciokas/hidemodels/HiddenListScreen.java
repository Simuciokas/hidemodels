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
import net.minecraft.client.gui.components.Button;
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
    private static final String CLOSE = "Close";
    private static final String MOVING = "Click to place, Esc cancels";
    private static final String RADIUS_LABEL = "List radius";
    private static final String OPEN = " ▶";
    private static final String UNSAVED = "Unsaved";
    private static final String SAVE_UNSAVED = "Save unsaved as a profile";
    private static final String DELETE = "Delete profile";
    private static final String DELETE_SURE = "Click again to delete";

    private final Screen parent;
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
    /** The page of lists to go back to from an open one. */
    private int listsPage;
    /** Set by the first click on Delete profile; the second deletes. */
    private boolean confirmDelete;
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
    }

    public HiddenListScreen(Screen parent) {
        super(Component.literal("Hide Models"));
        this.parent = parent;
    }

    private int perPage() {
        // Room kept for the header and the chrome rows: the tabs, the pager, close, the way back
        // when a model's bones are showing, and the Hidden tab's rows above its list.
        final int chrome = 3 + (showingBones() ? 1 : 0) + hiddenFixedRows();
        final int usable = height - Y - 14 - chrome * (ROW + GAP) - BOTTOM_MARGIN;
        return Math.max(1, usable / (ROW + GAP));
    }

    private boolean showingBones() {
        return tab == NEARBY && bonesOf != null;
    }

    /** Back and Delete above an open list; Unsaved and Save above the profiles. */
    private int hiddenFixedRows() {
        if (tab != HIDDEN) {
            return 0;
        }
        if (openList != null) {
            return openList.isEmpty() ? 1 : 2;
        }
        return HideModels.unsaved().isEmpty() ? 1 : 2;
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
        tab = HIDDEN;
        bonesOf = null;
        openList = list;
        final HideModels.Profile p = list.isEmpty() ? null : profileNamed(list);
        openLines = List.copyOf(list.isEmpty() ? HideModels.unsaved()
                : p == null ? List.of() : p.patterns());
        confirmDelete = false;
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
        return bonesOf == null ? NearbyModels.nearby(radius) : NearbyModels.bones(radius, bonesOf);
    }

    private void build() {
        rows.clear();
        final List<NearbyModels.Nearby> found = nearbyRows();
        final List<String> source = new ArrayList<>();
        if (tab == HIDDEN && openList == null) {
            for (HideModels.Profile p : HideModels.profiles()) {
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
        final int cap = Math.max(strip, width / 3);
        int content = Math.max(measure(header(source.size(), pages)), moving ? measure(MOVING) : 0);
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
            content = Math.max(content, measure(id) + (opensUp(id) ? GAP + cellW : 0));
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
            if (unsavedCount > 0) {
                content = Math.max(content, measure(SAVE_UNSAVED));
            }
            for (String name : shown) {
                content = Math.max(content, measure(name) + GAP + listCellW
                        + (unsavedCount > 0 ? plusW + GAP : 0));
            }
        } else if (tab == HIDDEN) {
            content = Math.max(content, measure("◀ " + (openList.isEmpty() ? UNSAVED : openList)));
            if (!openList.isEmpty()) {
                content = Math.max(content, measure(DELETE_SURE));
            }
        }
        final int prevW = measure(PREV) + PAD * 2;
        final int nextW = measure(NEXT) + PAD * 2;
        final int rowWidth = Math.min(cap, Math.max(strip,
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
            final Row row = row(left, y, opens ? rowWidth - cellW - GAP : rowWidth, id);
            row.id = id;
            row.on = tab == HIDDEN ? inOpenList(id) : HideModels.listed(id);
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
        chrome(left, y, measure(CLOSE) + PAD * 2, CLOSE, this::onClose);
        panelHeight = y + ROW - top;

        final int dx = (int) Math.round(HideModels.guiX() * roomX());
        final int dy = (int) Math.round(HideModels.guiY() * roomY());
        for (Row row : rows) {
            row.x += dx;
            row.y += dy;
        }
        left += dx;
        top += dy;

        if (moving) {
            // The next click anywhere puts the panel down, so one target covers the screen.
            addRenderableWidget(Button.builder(Component.empty(), b -> place())
                    .bounds(0, 0, width, height).build());
            return;
        }
        for (Row row : rows) {
            if (row.action != null) {
                final Runnable action = row.action;
                addRenderableWidget(Button.builder(Component.empty(), b -> action.run())
                        .bounds(row.x, row.y, row.w, ROW).build());
            }
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
            row.hint = p.on() ? "On - click to switch it off" : "Off - click to switch it on";
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
        return y;
    }

    /** Above an open list: the way back, and for a profile, Delete - which asks twice. */
    private int openListHead(int y, int w) {
        chrome(left, y, w, "◀ " + (openList.isEmpty() ? UNSAVED : openList), () -> {
            openList = null;
            confirmDelete = false;
            page = listsPage;
            rebuildWidgets();
        });
        y += ROW + GAP;
        if (!openList.isEmpty()) {
            final Row delete = row(left, y, w, confirmDelete ? DELETE_SURE : DELETE);
            delete.muted = !confirmDelete;
            delete.hint = "Deletes the profile and every line in it";
            hit(delete, () -> {
                if (confirmDelete) {
                    HideModels.deleteProfile(openList);
                    openList = null;
                    page = listsPage;
                }
                confirmDelete = !confirmDelete;
                rebuildWidgets();
            });
            y += ROW + GAP;
        }
        return y;
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

    /** Switching goes back to page one, and to the lists rather than one of them. */
    private void tabButton(int x, int y, int w, int which) {
        final Row row = row(x, y, w, TABS[which]);
        row.muted = tab != which;
        hit(row, () -> {
            tab = which;
            openList = null;
            confirmDelete = false;
            bonesOf = null;
            page = 0;
            rebuildWidgets();
        });
    }

    /** Each writes the config exactly as its command does, so the screen and the file agree. */
    private int settings(int y, int w) {
        final boolean enabled = HideModels.isEnabled();
        toggle(y, w, "Hiding", "Off keeps the list but hides nothing", enabled,
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

        final double radius = HideModels.listRadius();
        final List<NearbyModels.Nearby> around = nearbyRows();
        final int total = tab != HIDDEN ? around.size()
                : openList == null ? HideModels.profiles().size() : openLines.size();
        p.text(moving ? MOVING : header(total, pageCount(total)), left + ox, top + oy,
                moving ? TEXT : DIM);

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
            p.fill(x, y, row.w, ROW, over && row.action != null ? ROW_HOVER : ROW_BG);
            // Read live, so a hand edit to the config shows without reopening. In an open list
            // the bar means the line is still in it; elsewhere, that something hides the model.
            final boolean on = row.id == null ? row.on
                    : tab == HIDDEN ? inOpenList(row.id) : HideModels.listed(row.id);
            if (on || (row.id != null && partly.contains(row.id))) {
                // A bar down the left edge rather than a tick: it reads at a glance down a column.
                p.fill(x, y, 2, ROW, on ? ACCENT : PARTIAL);
            }
            final int ty = y + (ROW - p.lineHeight()) / 2 + 1;
            int room = row.w - PAD * 2;
            if (row.value != null) {
                final int vw = p.textWidth(row.value);
                p.text(row.value, x + row.w - PAD - vw, ty,
                        on ? ACCENT : "Off".equals(row.value) ? DIM : TEXT);
                room -= vw + PAD;
            }
            final String label = fit(p, row.label, room);
            final int tx = row.centered ? x + (row.w - p.textWidth(label)) / 2 : x + PAD;
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
            return List.of(row.hint);
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
        for (NearbyModels.Nearby n : around) {
            if (n.id().equals(row.id)) {
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
        if (covering == null) {
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
        final boolean towardLeft = left + panelWidth / 2 > width / 2;
        final int x = towardLeft
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
        if (total == 0) {
            return (showingBones() ? "No bones" : "Nothing") + " within " + radius + " blocks";
        }
        final String noun = showingBones() ? (total == 1 ? " bone" : " bones")
                                           : (total == 1 ? " model" : " models");
        return total + noun + " within " + radius + page;
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
