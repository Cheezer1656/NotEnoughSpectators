package cheeezer.notenoughspectators.server;

import cheeezer.notenoughspectators.PacketSniffer;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.TimeoutException;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.*;
import net.minecraft.network.handler.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.EnterConfigurationC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.config.ReadyS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket;
import net.minecraft.network.state.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.util.NoSuchElementException;

public class SpectatorServerNetworkHandler extends SimpleChannelInboundHandler<Packet<?>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    SpectatorServer server;
    NetworkPhase phase = NetworkPhase.HANDSHAKING;
    private Channel channel;
    private boolean duringLogin = false;
    private boolean errored = false;

    public SpectatorServerNetworkHandler(SpectatorServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        channel = context.channel();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Packet<?> packet) throws Exception {
        switch (phase) {
            case NetworkPhase.HANDSHAKING:
                if (packet instanceof HandshakeC2SPacket handshakePacket) {
                    if (handshakePacket.intendedState() == ConnectionIntent.STATUS) {
                        phase = NetworkPhase.STATUS;
                        transitionOutbound(QueryStates.S2C);
                        transitionInbound(QueryStates.C2S);
                    } else if (handshakePacket.intendedState() == ConnectionIntent.LOGIN) {
                        phase = NetworkPhase.LOGIN;
                        login(handshakePacket);
                    } else {
                        context.disconnect();
                    }
                }
                break;
            case NetworkPhase.STATUS:
                if (packet instanceof QueryRequestC2SPacket) {
                    context.writeAndFlush(new QueryResponseS2CPacket(server.getServerMetadata()));
                } else if (packet instanceof QueryPingC2SPacket pingPacket) {
                    context.writeAndFlush(new PingResultS2CPacket(pingPacket.getStartTime()));
                }
                break;
            case NetworkPhase.LOGIN:
                if (packet instanceof LoginHelloC2SPacket loginPacket) {
                    context.writeAndFlush(new LoginSuccessS2CPacket(Uuids.getOfflinePlayerProfile(loginPacket.name())));
                } else if (packet instanceof EnterConfigurationC2SPacket) {
                    phase = NetworkPhase.CONFIGURATION;
                    transitionOutbound(ConfigurationStates.S2C);
                    for (Packet packet1 : PacketSniffer.configPackets) {
                        context.write(packet1);
                    }
                    context.flush();
                    transitionInbound(ConfigurationStates.C2S);
                    context.channel().pipeline().writeAndFlush(ReadyS2CPacket.INSTANCE);
                }
                break;
            case NetworkPhase.CONFIGURATION:
                System.out.println(packet);
                if (packet instanceof ReadyC2SPacket) {
                    phase = NetworkPhase.PLAY;
                    System.out.println("MC SERVER: "+ MinecraftClient.getInstance().getServer());
                    DynamicRegistryManager.Immutable registryManager = MinecraftClient.getInstance().getServer().getRegistryManager();
                    transitionOutbound(PlayStateFactories.S2C.bind(RegistryByteBuf.makeFactory(registryManager)));

                    System.out.printf("%d packets and %d raw packets", PacketSniffer.playPackets.size(), PacketSniffer.getPlayPackets().size());

                    context.channel().pipeline().remove("encoder");
                    context.channel().pipeline().remove("prepender");
                    for (ByteBuf byteBuf : PacketSniffer.getPlayPackets()) {
                        context.writeAndFlush(byteBuf.copy());
                    }
                    System.out.println("Sent play packets");
                }
                break;
            case NetworkPhase.PLAY:
                System.out.println("Play: "+packet);
        }
    }

    private void login(HandshakeC2SPacket packet) {
        transitionOutbound(LoginStates.S2C);
        if (packet.protocolVersion() != SharedConstants.getGameVersion().protocolVersion()) {
            Text text;
            if (packet.protocolVersion() < 754) {
                text = Text.translatable("multiplayer.disconnect.outdated_client", SharedConstants.getGameVersion().name());
            } else {
                text = Text.translatable("multiplayer.disconnect.incompatible", SharedConstants.getGameVersion().name());
            }

            channel.writeAndFlush(new LoginDisconnectS2CPacket(text));
            this.channel.close().awaitUninterruptibly();
        } else {
            transitionInbound(LoginStates.C2S);
        }
    }

    public void transitionOutbound(NetworkState<?> newState) {
        if (newState.side() != NetworkSide.CLIENTBOUND) {
            throw new IllegalStateException("Invalid outbound protocol: " + newState.id());
        } else {
            NetworkStateTransitions.EncoderTransitioner encoderTransitioner = NetworkStateTransitions.encoderTransitioner(newState);
            PacketBundleHandler packetBundleHandler = newState.bundleHandler();
            if (packetBundleHandler != null) {
                PacketUnbundler packetUnbundler = new PacketUnbundler(packetBundleHandler);
                encoderTransitioner = encoderTransitioner.andThen(context1 -> context1.pipeline().addAfter("encoder", "unbundler", packetUnbundler));
            }

            boolean bl = newState.id() == NetworkPhase.LOGIN;
            syncUninterruptibly(channel.writeAndFlush(encoderTransitioner.andThen(context1 -> this.duringLogin = bl)));
        }
    }

    public <T extends PacketListener> void transitionInbound(NetworkState<T> state) {
        if (state.side() != NetworkSide.SERVERBOUND) {
            throw new IllegalStateException("Invalid inbound protocol: " + state.id());
        } else {
            NetworkStateTransitions.DecoderTransitioner decoderTransitioner = NetworkStateTransitions.decoderTransitioner(state);
            PacketBundleHandler packetBundleHandler = state.bundleHandler();
            if (packetBundleHandler != null) {
                PacketBundler packetBundler = new PacketBundler(packetBundleHandler);
                decoderTransitioner = decoderTransitioner.andThen(context -> context.pipeline().addAfter("decoder", "bundler", packetBundler));
            }

            syncUninterruptibly(this.channel.writeAndFlush(decoderTransitioner));
        }
    }

//    @Override
//    public void exceptionCaught(ChannelHandlerContext context, Throwable ex) {
//        if (ex instanceof PacketException) {
//            LOGGER.debug("Skipping packet due to errors", ex.getCause());
//        } else {
//            boolean bl = !this.errored;
//            this.errored = true;
//            if (this.channel.isOpen()) {
//                if (ex instanceof TimeoutException) {
//                    LOGGER.debug("Timeout", ex);
//                    this.disconnect(Text.translatable("disconnect.timeout"));
//                } else {
//                    Text text = Text.translatable("disconnect.genericReason", "Internal Exception: " + ex);
//                    PacketListener packetListener = this.packetListener;
//                    DisconnectionInfo disconnectionInfo;
//                    if (packetListener != null) {
//                        disconnectionInfo = packetListener.createDisconnectionInfo(text, ex);
//                    } else {
//                        disconnectionInfo = new DisconnectionInfo(text);
//                    }
//
//                    if (bl) {
//                        LOGGER.debug("Failed to sent packet", ex);
//                        if (this.getOppositeSide() == NetworkSide.CLIENTBOUND) {
//                            Packet<?> packet = (Packet<?>)(this.duringLogin ? new LoginDisconnectS2CPacket(text) : new DisconnectS2CPacket(text));
//                            this.send(packet, PacketCallbacks.always(() -> this.disconnect(disconnectionInfo)));
//                        } else {
//                            this.disconnect(disconnectionInfo);
//                        }
//
//                        this.tryDisableAutoRead();
//                    } else {
//                        LOGGER.debug("Double fault", ex);
//                        this.disconnect(disconnectionInfo);
//                    }
//                }
//            }
//        }
//    }


    private static void syncUninterruptibly(ChannelFuture future) {
        try {
            future.syncUninterruptibly();
        } catch (Exception var2) {
            if (var2 instanceof ClosedChannelException) {
                LOGGER.info("Connection closed during protocol change");
            } else {
                throw var2;
            }
        }
    }
}
