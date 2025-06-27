package cheeezer.notenoughspectators.server;

import com.google.common.collect.Lists;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.SharedConstants;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.handler.*;
import net.minecraft.network.state.HandshakeStates;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SpectatorServer extends Thread {
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

                            channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30))
                                    .addLast("splitter", new SplitterHandler(null))
                                    .addLast(new FlowControlHandler())
                                    .addLast("decoder", new DecoderHandler<>(HandshakeStates.C2S))
                                    .addLast("prepender", new SizePrepender())
                                    .addLast("outbound_config", new NetworkStateTransitions.OutboundConfigurer())
                                    .addLast("handler", new SpectatorServerNetworkHandler(SpectatorServer.this));
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
        return new ServerMetadata(Text.of("A NotEnoughSpectators server"), Optional.empty(), Optional.of(new ServerMetadata.Version(SharedConstants.getGameVersion().name(), SharedConstants.getProtocolVersion())), Optional.empty(), false);
    }
}
