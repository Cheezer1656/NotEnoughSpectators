package cheezer.notenoughspectators;

import cheezer.notenoughspectators.command.NotEnoughSpectatorsCommand;
import cheezer.notenoughspectators.server.SpectatorServer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = NotEnoughSpectators.MOD_ID, useMetadata=true)
public class NotEnoughSpectators {
    public static final String MOD_ID = "notenoughspectators";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static SpectatorServer server;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new NotEnoughSpectatorsCommand());
    }

    public static void startServer(int port) {
        if (server == null) {
            server = new SpectatorServer(port);
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
