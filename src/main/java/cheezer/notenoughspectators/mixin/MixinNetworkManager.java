package cheezer.notenoughspectators.mixin;

import cheezer.notenoughspectators.event.MovementEvent;
import cheezer.notenoughspectators.event.PacketEvent;
import cheezer.notenoughspectators.PacketStore;
import cheezer.notenoughspectators.server.SpectatorServerNetworkHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.*;
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
            if (packet instanceof S08PacketPlayerPosLook) {
                MinecraftForge.EVENT_BUS.post(new MovementEvent());
            } else if (packet instanceof S07PacketRespawn) {
                MinecraftForge.EVENT_BUS.post(new PacketEvent(new S0CPacketSpawnPlayer(Minecraft.getMinecraft().thePlayer)));
            }
            else if (phase == EnumConnectionState.PLAY && !(packet instanceof S39PacketPlayerAbilities || packet instanceof S2DPacketOpenWindow || packet instanceof S2EPacketCloseWindow || packet instanceof S2FPacketSetSlot || packet instanceof S30PacketWindowItems || (packet instanceof S2BPacketChangeGameState && ((S2BPacketChangeGameState) packet).getGameState() == 3))) {
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

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (this.channel.isOpen()) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (packet instanceof C03PacketPlayer) MinecraftForge.EVENT_BUS.post(new MovementEvent());
            else if (packet instanceof C09PacketHeldItemChange) MinecraftForge.EVENT_BUS.post(new PacketEvent(new S04PacketEntityEquipment(player.getEntityId(), 0, player.inventory.getCurrentItem())));
            else if (packet instanceof C0EPacketClickWindow) SpectatorServerNetworkHandler.updateEquipment();
        }
    }

    @Unique
    private EnumConnectionState getNetworkPhase() {
        return this.channel.attr(attrKeyConnectionState).get();
    }
}
