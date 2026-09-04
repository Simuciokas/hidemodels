package io.github.simuciokas.hidemodels.mixin;

import io.github.simuciokas.hidemodels.HideModels;
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
}
