package cheezer.notenoughspectators;

import cheezer.notenoughspectators.server.SpectatorServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "notenoughspectators", useMetadata=true)
public class NotEnoughSpectators {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        SpectatorServer server = new SpectatorServer(25565);
        server.start();
    }
}
