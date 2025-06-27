package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.state.NetworkState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DecoderHandler.class)
public class DecoderHandlerMixin {
    @Shadow @Final public NetworkState<?> state;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(NetworkState<?> state, CallbackInfo ci) {
        System.out.println("DecoderHandlerMixin initialized with state: " + state);
    }

    @Inject(method = "decode", at = @At("HEAD"))
    protected void decode(ChannelHandlerContext context, ByteBuf buf, List<Object> objects, CallbackInfo ci) {
        if (this.state.id() == NetworkPhase.PLAY) {
            PacketSniffer.decoderCalls++;
//            System.out.println(this.state.id().getId() + " - Calls to decode(): " + PacketSniffer.decoderCalls);
        }
    }
}
