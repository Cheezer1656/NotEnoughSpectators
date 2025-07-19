package cheezer.notenoughspectators.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.ServerStatusResponse;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;

public class SpectatorServer extends Thread {
    private final ServerStatusResponse statusResponse = new ServerStatusResponse();
    private final int port;
    private final int rateLimit;

    public SpectatorServer(int port) {
        this(port, 0);
    }

    public SpectatorServer(int port, int rateLimit) {
        this.port = port;
        this.rateLimit = rateLimit;

        statusResponse.setServerDescription(new ChatComponentText(getMOTD()));
        statusResponse.setProtocolVersionInfo(new ServerStatusResponse.MinecraftProtocolVersionIdentifier(getMinecraftVersion(), getProtocolVersion()));
    }

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel)
                                throws Exception {
                            try {
                                channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                            } catch (ChannelException ignored) {
                            }

                            channel.pipeline().addLast("timeout", new ReadTimeoutHandler(FMLNetworkHandler.READ_TIMEOUT))
                                    .addLast("legacy_query", new PingResponseHandler(SpectatorServer.this))
                                    .addLast("splitter", new MessageDeserializer2())
                                    .addLast("decoder", new MessageDeserializer(EnumPacketDirection.SERVERBOUND))
                                    .addLast("prepender", new MessageSerializer2())
                                    .addLast("encoder", new MessageSerializer(EnumPacketDirection.CLIENTBOUND))
                                    .addLast("packet_handler", new SpectatorServerNetworkHandler(SpectatorServer.this));
                            channel.config().setAutoRead(false);
                        }
                    }).option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            try {
                ChannelFuture f = b.bind(port).sync();
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                System.err.println("Server interrupted: " + e.getMessage());
            }
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public String getMinecraftVersion() {
        return "1.8.9";
    }

    public int getProtocolVersion() {
        return 47; // Protocol version for Minecraft 1.8.9
    }

    public String getMOTD() {
        return "A NotEnoughSpectators server";
    }

    public int getCurrentPlayerCount() {
        return 0; // Placeholder for current player count
    }

    public int getMaxPlayers() {
        return 20; // Placeholder for max players
    }

    public ServerStatusResponse getServerStatus() {
        statusResponse.setPlayerCountData(new ServerStatusResponse.PlayerCountData(getMaxPlayers(), getCurrentPlayerCount()));
        return statusResponse;
    }
}
