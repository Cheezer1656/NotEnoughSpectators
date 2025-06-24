package cheeezer.notenoughspectators.server;

import com.google.common.collect.Lists;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.QueryableServer;
import net.minecraft.network.RateLimitedConnection;
import net.minecraft.network.handler.LegacyQueryHandler;
import net.minecraft.server.ServerMetadata;

import java.util.Collections;
import java.util.List;

public class SpectatorServer extends Thread implements QueryableServer {
    private final int port;
    private final int rateLimit;
    final List<ClientConnection> connections = Collections.synchronizedList(Lists.newArrayList());

    public SpectatorServer(int port) {
        this.port = port;
        this.rateLimit = 0;
    }

    public SpectatorServer(int port, int rateLimit) {
        this.port = port;
        this.rateLimit = rateLimit;
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

                            ChannelPipeline channelPipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
//                            channelPipeline.addLast("legacy_query", new LegacyQueryHandler(SpectatorServer.this));

                            ClientConnection.addHandlers(channelPipeline, NetworkSide.SERVERBOUND, false, null);
                            ClientConnection clientConnection = rateLimit > 0 ? new RateLimitedConnection(rateLimit) : new ClientConnection(NetworkSide.SERVERBOUND);
                            connections.add(clientConnection);
                            clientConnection.addFlowControlHandler(channelPipeline);
                            clientConnection.setInitialPacketListener(new ServerHandshakeNetworkHandler(SpectatorServer.this, clientConnection));
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

    public ServerMetadata getServerMetadata() {
        return null;//new ServerMetadata();
    }

    public int getNetworkCompressionThreshold() {
        return 256; // Default compression threshold
    }

    @Override
    public String getServerMotd() {
        return "A NotEnoughSpectators server";
    }

    @Override
    public String getVersion() {
        return "1.21.6";
    }

    @Override
    public int getCurrentPlayerCount() {
        return 0;
    }

    @Override
    public int getMaxPlayerCount() {
        return 1000;
    }
}
