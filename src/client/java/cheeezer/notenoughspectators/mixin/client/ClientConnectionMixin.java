package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.PacketInflater;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
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

    @Inject(method = "channelActive", at = @At("TAIL"))
    public void channelActive(ChannelHandlerContext context, CallbackInfo ci) {
        if (channel.pipeline().get("decompress") instanceof PacketInflater) {
            channel.pipeline().addAfter("decompress", "sniffer", new PacketSniffer());
        } else {
            channel.pipeline().addAfter("splitter", "sniffer", new PacketSniffer());
        }
    }

    @Inject(method = "handlePacket", at = @At("HEAD"))
    private static <T extends PacketListener> void hookEventPacketReceive(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        System.out.println("Received packet: " + packet.getClass().getSimpleName() + " on side: " + listener.getSide());
    }

    @Inject(method = "disconnect*", at = @At("HEAD"))
    private void hookDisconnect(Text disconnectReason, CallbackInfo ci) {
        System.out.println("Disconnected from server: " + disconnectReason.getString());
    }

}