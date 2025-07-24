package cheezer.notenoughspectators;

import cheezer.notenoughspectators.command.NotEnoughSpectatorsCommand;
import cheezer.notenoughspectators.server.SpectatorServer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "notenoughspectators", useMetadata=true)
public class NotEnoughSpectators {
    private static SpectatorServer server;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new NotEnoughSpectatorsCommand());
    }

    public static void startServer(int port) {
        if (server == null) {
            server = new SpectatorServer(25565);
            server.start();
        } else throw new IllegalStateException("Server is already running");
    }

    public static void stopServer() {
        if (server != null) {
            server.interrupt();
            server = null;
        }
    }
}
