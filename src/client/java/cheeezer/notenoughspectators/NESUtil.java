package cheeezer.notenoughspectators;

import cheeezer.notenoughspectators.event.PacketCallback;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;

import java.util.List;

public class NESUtil {
    public static boolean isEquipmentSlot(int slot) {
        return (slot >= 5 && slot <= 8) || (slot >= 36 && slot <= 45) || slot == 0; // /clear command seems to only update slot 0
    }
    public static void updatePlayerEquipment() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, player.getMainHandStack()))));
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.OFFHAND, player.getOffHandStack()))));
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.HEAD, player.getEquippedStack(EquipmentSlot.HEAD)))));
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.CHEST, player.getEquippedStack(EquipmentSlot.CHEST)))));
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.LEGS, player.getEquippedStack(EquipmentSlot.LEGS)))));
            PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.FEET, player.getEquippedStack(EquipmentSlot.FEET)))));
        }
    }
}
