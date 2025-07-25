package cheeezer.notenoughspectators;

import cheeezer.notenoughspectators.server.SpectatorServer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NotEnoughSpectatorsClient implements ClientModInitializer {
	private SpectatorServer server;

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			final LiteralCommandNode<FabricClientCommandSource> nesNode = dispatcher.register(literal("notenoughspectators")
					.then(literal("share").then(argument("port", IntegerArgumentType.integer(1, 65535))
							.executes(context -> {
										if (server != null) {
											context.getSource().sendError(Text.literal("A server is already running!"));
											return 0;
										}
										try {
											startServer(IntegerArgumentType.getInteger(context, "port"));
										} catch (Exception e) {
											context.getSource().sendError(Text.literal("Failed to start server: " + e.getMessage()));
											return 0;
										}
										context.getSource().sendFeedback(Text.literal("Server started!"));
										return 1;
									}
							)))
					.then(literal("stop").executes(context -> {
						if (server == null) {
							context.getSource().sendError(Text.literal("No server is currently running!"));
							return 0;
						}
						stopServer();
						context.getSource().sendFeedback(Text.literal("Server stopped!"));
						return 1;
					})));
			dispatcher.register(literal("nes")
					.redirect(nesNode));
		});
	}

	private void startServer(int port) throws Exception {
		if (server == null) {
			SpectatorServer newServer = new SpectatorServer(port);
			newServer.setup();
			newServer.setDaemon(true);
			newServer.start();
			server = newServer;
		}
	}

	private void stopServer() {
		if (server != null) {
			server.interrupt();
			server = null;
		}
	}
}