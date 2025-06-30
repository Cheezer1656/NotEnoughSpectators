package cheeezer.notenoughspectators;

import cheeezer.notenoughspectators.server.SpectatorServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

public class NotEnoughSpectatorsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Start the spectator server ASAP for testing purposes
		startServer();

		// Actual start server command (to be used in the future)
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("share")
				.executes(context -> {
					context.getSource().sendFeedback(Text.literal("Starting server..."));
							startServer();
							context.getSource().sendFeedback(Text.literal("Server started!"));
							return 1;
						}
				)));
	}

	private void startServer() {
		SpectatorServer server = new SpectatorServer(25565);
		server.setDaemon(true);
		server.start();
	}
}