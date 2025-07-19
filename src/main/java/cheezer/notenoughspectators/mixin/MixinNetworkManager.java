package cheezer.notenoughspectators.mixin;

import cheezer.notenoughspectators.PacketEvent;
import cheezer.notenoughspectators.PacketStore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import net.minecraft.network.*;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
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
    private void onChannelRead(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) throws Exception {
        if (this.channel.isOpen() && this.direction == EnumPacketDirection.CLIENTBOUND) {
            EnumConnectionState phase = getNetworkPhase();
            if (phase == EnumConnectionState.PLAY && !(packet instanceof S08PacketPlayerPosLook)) {
                if (packet instanceof S01PacketJoinGame) {
                    // Create copy of the packet to prevent modification of the original packet
                    PacketBuffer data = new PacketBuffer(Unpooled.buffer());
                    packet.writePacketData(data);
                    packet = packet.getClass().getConstructor().newInstance();
                    packet.readPacketData(data);
                    ((AccessorS01PacketJoinGame) packet).setEntityId_notenoughspectators(Integer.MAX_VALUE); // Prevents player entity ID conflicts
                }
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
