package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import cheeezer.notenoughspectators.event.MovementCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Shadow
    @Final
    private NetworkSide side;

    @Shadow
    public Channel channel;

    @Shadow private volatile @Nullable PacketListener packetListener;

    @Inject(method = "addHandlers", at = @At("TAIL"))
    private static void addHandlers(ChannelPipeline pipeline, NetworkSide side, boolean local, @Nullable PacketSizeLogger packetSizeLogger, CallbackInfo ci) {
        if (pipeline.get("decompress") instanceof PacketInflater) {
            pipeline.addAfter("decompress", "sniffer", new PacketSniffer());
        } else {
            pipeline.addAfter("splitter", "sniffer", new PacketSniffer());
        }
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    protected void hookChannelRead(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (this.channel.isOpen() && packetListener != null && packetListener.accepts(packet) && side == NetworkSide.CLIENTBOUND) {
            NetworkPhase phase = PacketSniffer.getNetworkPhase(channelHandlerContext);
            if (phase == NetworkPhase.CONFIGURATION) {
                PacketSniffer.addConfigPacket(packet);
            }
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean flush, CallbackInfo ci) {
        if (packet instanceof PlayerMoveC2SPacket) {
            MovementCallback.MovementType movementType = switch (packet) {
                case PlayerMoveC2SPacket.PositionAndOnGround ignored -> MovementCallback.MovementType.POSITION;
                case PlayerMoveC2SPacket.LookAndOnGround ignored -> MovementCallback.MovementType.ROTATION;
                case PlayerMoveC2SPacket.Full ignored -> MovementCallback.MovementType.POSITION_AND_ROTATION;
                default -> MovementCallback.MovementType.UNKNOWN;
            };
            MovementCallback.EVENT.invoker().onMovementPacket(movementType);
        }
    }

}