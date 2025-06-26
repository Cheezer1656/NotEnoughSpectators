package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.server.SpectatorServer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(CallbackInfo info) {
        // Start the spectator server ASAP for testing purposes
        SpectatorServer server = new SpectatorServer(25565);
        server.setDaemon(true);
        server.start();
    }
}
