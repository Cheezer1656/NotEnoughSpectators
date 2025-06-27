package cheeezer.notenoughspectators.mixin.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.state.NetworkState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EncoderHandler.class)
public class EncoderHandlerMixin {
    @Shadow @Final public NetworkState state;

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void encode(ChannelHandlerContext context, Packet packet, ByteBuf buf, CallbackInfo ci) {
//        System.out.print("Encoded " + this.state.id() + " packet: " + packet.getClass().getSimpleName());
//        System.out.printf(" (%02X)\n", buf.getByte(0));
    }
}
