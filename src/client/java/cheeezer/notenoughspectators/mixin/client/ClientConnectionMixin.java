package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
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

}