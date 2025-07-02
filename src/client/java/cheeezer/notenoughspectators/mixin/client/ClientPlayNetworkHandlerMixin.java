package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.NESUtil;
import cheeezer.notenoughspectators.PlayerTaskQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onGameJoin", at = @At("TAIL"))
    public void onGameJoin(CallbackInfo ci) {
        PlayerTaskQueue.processTasks(MinecraftClient.getInstance().player);
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    public void onInventoryTail(CallbackInfo ci) {
        NESUtil.updatePlayerEquipment();
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    public void onScreenHandlerSlotUpdate(CallbackInfo ci) {
        NESUtil.updatePlayerEquipment();
    }
}
