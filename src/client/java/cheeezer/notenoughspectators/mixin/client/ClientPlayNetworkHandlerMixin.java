package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.NotEnoughSpectators;
import cheeezer.notenoughspectators.event.PacketCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Shadow private ClientWorld world;

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    public void onGameJoin(CallbackInfo ci) {
        // Alert the spectator player that they are switching servers
        PacketCallback.EVENT.invoker().onPacketReceived(new ProfilelessChatMessageS2CPacket(Text.of("Switching server..."), MessageType.params(MessageType.SAY_COMMAND, MinecraftClient.getInstance().player.getWorld().getRegistryManager(), Text.of("NotEnoughSpectators"))));
        // Respawn spectator player
        World world = this.world;
        if (world == null) {
            NotEnoughSpectators.LOGGER.warn("World is null, cannot respawn spectator player");
            return;
        }
        PacketCallback.EVENT.invoker().onPacketReceived(new PlayerRespawnS2CPacket(new CommonPlayerSpawnInfo(world.getDimensionEntry(), world.getRegistryKey(), 0, GameMode.CREATIVE, null, world.isDebugWorld(), false, Optional.empty(), 0, 0), (byte) 0));
    }
}
