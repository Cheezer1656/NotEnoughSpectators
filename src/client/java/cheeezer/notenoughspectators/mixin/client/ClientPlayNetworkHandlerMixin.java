package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PlayerTaskQueue;
import cheeezer.notenoughspectators.event.PacketCallback;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onGameJoin", at = @At("TAIL"))
    public void onGameJoin(CallbackInfo ci) {
        PlayerTaskQueue.processTasks(MinecraftClient.getInstance().player);
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    public void onInventoryTail(CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ItemStack handStack = player.getMainHandStack();
        PacketCallback.EVENT.invoker().onPacketReceived(new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, handStack))));
    }
}
