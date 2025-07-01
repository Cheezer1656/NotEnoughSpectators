package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import cheeezer.notenoughspectators.event.MovementCallback;
import cheeezer.notenoughspectators.event.PacketCallback;
import com.mojang.datafixers.util.Pair;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

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

    @Inject(method = "channelRead0", at = @At("RETURN"))
    private void hookChannelReadEnd(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.channel.isOpen() && packetListener != null && packetListener.accepts(packet) && side == NetworkSide.CLIENTBOUND) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (packet instanceof ScreenHandlerSlotUpdateS2CPacket slotUpdatePacket && slotUpdatePacket.getSlot() == player.getInventory().getSelectedSlot() + 36) {
                System.out.println("Received slot update packet for main hand: " + slotUpdatePacket.getStack());
                PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, slotUpdatePacket.getStack()))));
            }
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean flush, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (packet instanceof PlayerMoveC2SPacket) {
            MovementCallback.MovementType movementType = switch (packet) {
                case PlayerMoveC2SPacket.PositionAndOnGround ignored -> MovementCallback.MovementType.POSITION;
                case PlayerMoveC2SPacket.LookAndOnGround ignored -> MovementCallback.MovementType.ROTATION;
                case PlayerMoveC2SPacket.Full ignored -> MovementCallback.MovementType.POSITION_AND_ROTATION;
                default -> MovementCallback.MovementType.UNKNOWN;
            };
            MovementCallback.EVENT.invoker().onMovementPacket(movementType);
        } else if (packet instanceof HandSwingC2SPacket handSwingPacket) {
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityAnimationS2CPacket(player, handSwingPacket.getHand() == Hand.MAIN_HAND ? 0 : 3));
        } else if (packet instanceof PlayerInteractItemC2SPacket useItemPacket) {
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityAnimationS2CPacket(player, useItemPacket.getHand() == Hand.MAIN_HAND ? 0 : 3));
        } else if (packet instanceof UpdateSelectedSlotC2SPacket || packet instanceof CreativeInventoryActionC2SPacket) {
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, player.getMainHandStack()))));
        }
    }

}