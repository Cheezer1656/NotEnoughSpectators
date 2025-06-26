package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.handler.PacketInflater;
import net.minecraft.network.handler.PacketSizeLogger;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.text.Text;
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

    @Shadow private Channel channel;

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
        if (this.channel.isOpen() && this.packetListener != null && packetListener.accepts(packet) && channel.pipeline().get(PacketSniffer.class) != null && PacketSniffer.packetCount <= 2) {
            System.out.println("Received packet: " + packet.getClass().getSimpleName() + " on side: " + side);
            if (packet instanceof HandshakeC2SPacket handshakeC2SPacket) {
                System.out.println("HandshakeC2SPacket details: " + handshakeC2SPacket);
            }
        }
    }

    @Inject(method = "disconnect*", at = @At("HEAD"))
    private void hookDisconnect(Text disconnectReason, CallbackInfo ci) {
        System.out.println("Disconnected from server: " + disconnectReason.getString());
    }

}