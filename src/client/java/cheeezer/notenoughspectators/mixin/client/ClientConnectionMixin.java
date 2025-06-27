package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.s2c.config.ReadyS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.state.NetworkState;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Shadow
    @Final
    private NetworkSide side;

    @Shadow private Channel channel;

    @Shadow private volatile @Nullable PacketListener packetListener;

    @Unique private static int calls = 0;

    @Inject(method = "addHandlers", at = @At("TAIL"))
    private static void addHandlers(ChannelPipeline pipeline, NetworkSide side, boolean local, @Nullable PacketSizeLogger packetSizeLogger, CallbackInfo ci) {
        System.out.println("Side: "+side);
        if (pipeline.get("decompress") instanceof PacketInflater) {
            pipeline.addAfter("decompress", "sniffer", new PacketSniffer());
        } else {
            pipeline.addAfter("splitter", "sniffer", new PacketSniffer());
        }
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    protected void hookChannelRead(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (this.channel.isOpen() && this.packetListener != null && packetListener.accepts(packet) && side == NetworkSide.CLIENTBOUND) {
            NetworkPhase phase = PacketSniffer.getNetworkPhase(channelHandlerContext);
            System.out.println(phase + " | Received packet: " + packet.getClass().getSimpleName() + " on side: " + side + " with id: " + packet.getPacketType().id());
            if (phase == NetworkPhase.CONFIGURATION) {
                PacketSniffer.configPackets.add(packet);
            } else if (phase == NetworkPhase.PLAY) {
                calls++;
//                if (calls != PacketSniffer.decoderCalls) System.out.println("Mismatch in calls: " + calls + " vs " + PacketSniffer.decoderCalls);
//                System.out.println("Adding packet to play packets: " + packet.getClass().getSimpleName());
                PacketSniffer.appendBuffer(packet);
                PacketSniffer.playPackets.add(packet);
            }
        }
    }

    @Inject(method = "disconnect*", at = @At("HEAD"))
    private void hookDisconnect(Text disconnectReason, CallbackInfo ci) {
        System.out.println("Disconnected from server: " + disconnectReason.getString());
    }

}