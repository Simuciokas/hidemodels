package io.github.simuciokas.hidemodels;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side chat for 1.21.x. See the 26.x copy under src/mc26 for why there are two.
 *
 * <p>THE ONLY FILE THAT DIFFERS BETWEEN VERSIONS. Everything else in this mod compiles unchanged
 * from 1.21.2 to 26.2; this one method does not, because the client-facing chat call was renamed:
 * {@code LocalPlayer.displayClientMessage} here, {@code sendSystemMessage} after. Reflection
 * cannot paper over it - a 1.21.x build is remapped to intermediary, so the runtime method is
 * called something like {@code method_7353} and no name-based lookup would find it.
 *
 * <p>Picking the one LocalPlayer itself declares matters: CommandSource declares
 * {@code sendSystemMessage} on every version, but on 1.21.x the player does not override it and
 * the inherited implementation is the server's, which would print nothing at all.
 */
public final class ChatOut {

    private ChatOut() {
    }

    public static void say(Component text) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(text, false);
    }
}
