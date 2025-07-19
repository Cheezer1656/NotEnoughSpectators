package cheezer.notenoughspectators.mixin;

import cheezer.notenoughspectators.PlayerPosition;
import cheezer.notenoughspectators.PlayerTaskQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {
    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void onHandleJoinGame(CallbackInfo ci) {
        PlayerTaskQueue.processTasks(Minecraft.getMinecraft().thePlayer);
    }

    @Inject(method = "handlePlayerPosLook", at = @At("TAIL"))
    private void onHandlePlayerPosLook(CallbackInfo ci) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        PlayerTaskQueue.processPositionTasks(new PlayerPosition(
                player.posX,
                player.posY,
                player.posZ,
                player.rotationYaw,
                player.rotationPitch
        ));
    }
}
