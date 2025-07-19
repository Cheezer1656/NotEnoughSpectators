package cheezer.notenoughspectators.mixin;

import cheezer.notenoughspectators.PacketEvent;
import cheezer.notenoughspectators.PacketStore;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {
    @Shadow private Channel channel;

    @Shadow @Final public static AttributeKey<EnumConnectionState> attrKeyConnectionState;

    @Shadow @Final private EnumPacketDirection direction;

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        if (this.direction == EnumPacketDirection.CLIENTBOUND) {
            PacketStore.clearPackets();
        }
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void onChannelRead(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.channel.isOpen() && this.direction == EnumPacketDirection.CLIENTBOUND) {
            EnumConnectionState phase = getNetworkPhase();
            if (phase == EnumConnectionState.PLAY) {
                PacketStore.addPlayPacket(packet);
                MinecraftForge.EVENT_BUS.post(new PacketEvent(packet));
            }
        }
    }

    @Unique
    private EnumConnectionState getNetworkPhase() {
        return this.channel.attr(attrKeyConnectionState).get();
    }
}
