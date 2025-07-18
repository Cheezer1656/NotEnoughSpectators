package cheeezer.notenoughspectators;

import cheeezer.notenoughspectators.server.SpectatorServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

public class NotEnoughSpectatorsClient implements ClientModInitializer {
	private SpectatorServer server;

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("share")
				.executes(context -> {
					if (server != null) {
						context.getSource().sendError(Text.literal("A server is already running!"));
						return 0;
					}
					context.getSource().sendFeedback(Text.literal("Starting server..."));
							startServer();
							context.getSource().sendFeedback(Text.literal("Server started!"));
							return 1;
						}
				)));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("stop_sharing")
				.executes(context -> {
					if (server == null) {
						context.getSource().sendError(Text.literal("No server is currently running!"));
						return 0;
					}
					context.getSource().sendFeedback(Text.literal("Stopping server..."));
							stopServer();
							context.getSource().sendFeedback(Text.literal("Server stopped!"));
							return 1;
						}
				)));
	}

	private void startServer() {
		if (server == null) {
			server = new SpectatorServer(25565);
			server.setDaemon(true);
			server.start();
		}
	}

	private void stopServer() {
		if (server != null) {
			server.interrupt();
			server = null;
		}
	}
}