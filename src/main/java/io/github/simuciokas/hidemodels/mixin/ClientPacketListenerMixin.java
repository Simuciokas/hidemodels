package io.github.simuciokas.hidemodels.mixin;

import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@code /hidemodels ...} before it leaves the client.
 *
 * <p>WHY NOT A REGISTERED COMMAND. Vanilla builds its client command tree from what the SERVER
 * advertises, so there is no vanilla way to add a local command; Fabric API has one, but this mod
 * has no Fabric API dependency and adding one would change what people must install for a 6 KB
 * mod. Catching the outgoing command instead needs nothing but the mixin already present, and
 * cancelling the send means the server never sees it - no "Unknown command" reply, and no chance
 * of the text being logged somewhere as a normal message.
 *
 * <p>The string arrives WITHOUT its leading slash, which is how the chat screen hands it over.
 * Anything that is not ours is left completely alone.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void hidemodels$interceptOwnCommand(String command, CallbackInfo ci) {
        if (HideModels.handleCommand(command)) {
            ci.cancel();
        }
    }

    /**
     * The same interception for a CLICKED command, which does not come through sendCommand.
     *
     * <p>Clicking a run_command component calls Screen.clickCommandAction, and from 1.21.6 that
     * routes to {@code sendUnattendedCommand} instead - "unattended" meaning nobody typed it. Miss
     * this and the clickable ids in {@code /hidemodels list} would sail past the mod and land on
     * the server as an unknown command, which is both useless and rude: the text a user clicked
     * would be broadcast to someone else's log.
     *
     * <p>{@code require = 0} because the method simply does not exist before 1.21.6, and this one
     * source tree compiles for 1.21.2 as well. An injector that matches nothing there is correct,
     * not a failure - on those versions a clicked command goes through sendCommand above, which is
     * why clicking works across the whole range. tools/verify_targets.py knows to treat a
     * require = 0 target as optional rather than missing.
     */
    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void hidemodels$interceptClickedCommand(String command, Screen screen, CallbackInfo ci) {
        if (HideModels.handleCommand(command)) {
            ci.cancel();
        }
    }
}
