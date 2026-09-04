package io.github.simuciokas.hidemodels.mixin;

import io.github.simuciokas.hidemodels.HideModels;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a server switch this mod off for its own players - the server-side opt-out.
 *
 * <p>The protocol is deliberately just a channel name with no body, because an unknown plugin
 * channel reaches the client as a {@code DiscardedPayload}, which in 26.2 is a record holding
 * only the {@code Identifier}: the bytes are thrown away before any mod can see them. So the
 * channel IS the message.
 *
 * <p>A server sends an empty custom payload on {@code hidemodels:disable} to turn hiding off for
 * the rest of the session, or {@code hidemodels:enable} to allow it again. Sending it is safe
 * unconditionally - a client without this mod discards unknown channels silently - so a server
 * can simply fire it at every player on join.
 *
 * <p>The override is per-connection: {@code onDisconnect} clears it, so a server cannot leave a
 * client permanently altered, and reconnecting starts from the user's own config again.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"))
    private void hidemodels$serverControl(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        HideModels.onServerChannel(packet.payload().type().id().toString());
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void hidemodels$clearServerControl(DisconnectionDetails details, CallbackInfo ci) {
        HideModels.clearServerOverride();
    }
}
