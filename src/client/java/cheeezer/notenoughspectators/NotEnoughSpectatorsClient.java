package cheeezer.notenoughspectators;

import cheeezer.notenoughspectators.server.SpectatorServer;
import cheeezer.notenoughspectators.tunnel.TunnelClient;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NotEnoughSpectatorsClient implements ClientModInitializer {
    private SpectatorServer server;
    private TunnelClient tunnelClient;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            final LiteralCommandNode<FabricClientCommandSource> nesNode = dispatcher.register(literal("notenoughspectators").then(literal("share").executes(this::runShareCommand).then(argument("localPort", IntegerArgumentType.integer(1, 65535)).executes(this::runShareCommand))).then(literal("stop").executes(context -> {
                if (server == null) {
                    context.getSource().sendError(Text.literal("No server is currently running!"));
                    return 0;
                }
                stopServer();
                stopTunnelClient();
                context.getSource().sendFeedback(Text.literal("Server stopped!"));
                return 1;
            })));
            dispatcher.register(literal("nes").redirect(nesNode));
        });
    }

    private int runShareCommand(CommandContext<FabricClientCommandSource> context) {
        new Thread(() -> {
            if (server != null) {
                context.getSource().sendError(Text.literal("A server is already running!"));
            }
            try {
                int localPort;
                try {
                    localPort = IntegerArgumentType.getInteger(context, "localPort");
                } catch (IllegalArgumentException ignored) {
                    localPort = 25566; // Default port if not specified
                }
                startServer(localPort);
                int port = startTunnelClient(localPort);

                String address = String.format("%s:%d", TunnelClient.REMOTE_HOST, port);
                context.getSource().sendFeedback(Text.literal("Server started! Join at ").append(Text.literal(address).setStyle(Style.EMPTY.withUnderline(true).withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to copy"))).withClickEvent(new ClickEvent.CopyToClipboard(address)))));
            } catch (Exception e) {
                context.getSource().sendError(Text.literal("Failed to start server: " + e.getMessage()));
            }
        }).start();
        return 1;
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

    private int startTunnelClient(int port) throws Exception {
        if (tunnelClient == null) {
            tunnelClient = new TunnelClient(port);
            tunnelClient.setDaemon(true);
            tunnelClient.start();
            return tunnelClient.getRemotePort();
        }
        return 0;
    }

    private void stopTunnelClient() {
        if (tunnelClient != null) {
            tunnelClient.interrupt();
            tunnelClient = null;
        }
    }
}