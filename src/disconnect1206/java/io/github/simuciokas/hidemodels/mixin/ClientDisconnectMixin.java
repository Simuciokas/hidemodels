package io.github.simuciokas.hidemodels.mixin;

import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the server's opt-out when the connection ends. The 1.20.5 - 1.20.6 copy; see
 * src/disconnect121 for the other one and for why there are two.
 *
 * <p>Same hook, same moment, one parameter different: here the reason for the disconnect arrives
 * as the Component that gets shown on the disconnect screen, rather than the DisconnectionDetails
 * record that replaced it in 1.21. Neither copy reads it - the mod only cares that the connection
 * ended - but a Mixin handler still has to declare it.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientDisconnectMixin {

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void hidemodels$clearServerControl(Component reason, CallbackInfo ci) {
        HideModels.clearServerOverride();
    }
}
