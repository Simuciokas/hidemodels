package io.github.simuciokas.hidemodels;

import io.github.simuciokas.hidemodels.mixin.ItemDisplayAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code /hidemodels list} report: every item_model id within a radius, so the ids you need for
 * the config can be read off the screen instead of guessed.
 *
 * <p>Grouped by MODEL by default - {@code modelengine:some_mount/} - because that is what goes in
 * the config; {@code list bones} prints the individual bone ids for hiding just one piece.
 *
 * <p>Nothing here runs on a timer: it is a one-shot scan of the level's render list, triggered by
 * the command, so it costs nothing when unused.
 */
public final class NearbyModels {

    /** Chat gets unreadable long before this; the tail is summarised instead. */
    private static final int MAX_LINES = 40;

    private NearbyModels() {
    }

    private static final class Group {
        int pieces;
        double nearestSq = Double.MAX_VALUE;
    }

    /** Runs the scan and prints it. {@code bones} lists full ids rather than grouping by model. */
    public static void report(double radius, boolean bones) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            say(Component.literal("hidemodels: not in a world").withStyle(ChatFormatting.RED));
            return;
        }
        final Vec3 eye = mc.player.position();
        final double r2 = radius * radius;

        final Map<String, Group> found = new HashMap<>();
        int scanned = 0;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Display.ItemDisplay display)) {
                continue;
            }
            final double dSq = e.distanceToSqr(eye);
            if (dSq > r2) {
                continue;
            }
            final ItemStack stack = ((ItemDisplayAccessor) display).hidemodels$getItemStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            final Identifier model = stack.get(DataComponents.ITEM_MODEL);
            if (model == null) {
                continue;
            }
            scanned++;
            final String id = model.toString();
            // Group by the model, i.e. everything up to and including the last '/', which is
            // exactly the fragment the config wants. Ids with no slash stand alone.
            final int cut = id.lastIndexOf('/');
            final String key = (bones || cut < 0) ? id : id.substring(0, cut + 1);
            final Group g = found.computeIfAbsent(key, k -> new Group());
            g.pieces++;
            if (dSq < g.nearestSq) {
                g.nearestSq = dSq;
            }
        }

        if (found.isEmpty()) {
            say(Component.literal("hidemodels: no item_display models within " + fmt(radius) + " blocks")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        final List<Map.Entry<String, Group>> rows = new ArrayList<>(found.entrySet());
        rows.sort(Comparator.comparingDouble(x -> x.getValue().nearestSq));

        say(Component.literal("hidemodels: " + found.size() + (bones ? " bone" : " model")
                + (found.size() == 1 ? "" : "s") + " within " + fmt(radius) + " blocks"
                + " (" + scanned + " piece" + (scanned == 1 ? "" : "s") + ")")
                .withStyle(ChatFormatting.AQUA));

        final int shown = Math.min(rows.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            final Map.Entry<String, Group> row = rows.get(i);
            final Group g = row.getValue();
            final boolean listed = HideModels.listed(row.getKey());
            final Component line = Component.literal("  x" + g.pieces + "  ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(row.getKey())
                            .withStyle(listed ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .append(Component.literal("  " + fmt(Math.sqrt(g.nearestSq)) + "m")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(listed ? Component.literal("  hidden").withStyle(ChatFormatting.GREEN)
                                   : Component.empty());
            say(line);
        }
        if (rows.size() > shown) {
            say(Component.literal("  ... " + (rows.size() - shown) + " more (narrow the radius)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** One decimal, without dragging in String.format's locale surprises. */
    private static String fmt(double v) {
        return Double.toString(Math.round(v * 10.0) / 10.0);
    }

    /** Client-side chat, so nothing is sent to the server. */
    public static void say(Component text) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return;
        }
        // The 1.21.11 split put chat on Hud rather than Gui, reached as gui.hud.getChat().
        mc.gui.hud.getChat().addClientSystemMessage(text);
    }
}
