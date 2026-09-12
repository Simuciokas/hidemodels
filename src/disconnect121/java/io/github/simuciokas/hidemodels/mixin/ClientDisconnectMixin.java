package io.github.simuciokas.hidemodels.mixin;

import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the server's opt-out when the connection ends. The 1.21+ copy; see src/disconnect1206.
 *
 * <p>THE THIRD PER-VERSION SPLIT, and the only one that is a mixin. onDisconnect took a Component
 * up to 1.20.6 and a DisconnectionDetails from 1.21, and a Mixin handler must mirror its target's
 * parameters exactly - there is no signature that satisfies both, and no way to write one class
 * that compiles against a type half the range does not have.
 *
 * <p>Split out of ClientCommonPacketListenerMixin rather than duplicating that whole class: the
 * custom-payload hook beside it is identical everywhere, and copying it per version would be two
 * places to fix the next time the payload API moves.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientDisconnectMixin {

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void hidemodels$clearServerControl(DisconnectionDetails details, CallbackInfo ci) {
        HideModels.clearServerOverride();
    }
}
