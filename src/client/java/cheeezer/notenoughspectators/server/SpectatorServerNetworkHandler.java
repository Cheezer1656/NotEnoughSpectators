package cheeezer.notenoughspectators.server;

import cheeezer.notenoughspectators.PacketSniffer;
import cheeezer.notenoughspectators.event.MovementCallback;
import cheeezer.notenoughspectators.event.PacketCallback;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.*;
import net.minecraft.network.handler.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.EnterConfigurationC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.config.ReadyS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket;
import net.minecraft.network.state.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.util.random.RandomGenerator;

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
                    for (Packet packet1 : PacketSniffer.getConfigPackets()) {
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
                    DynamicRegistryManager.Immutable registryManager = MinecraftClient.getInstance().getNetworkHandler().getRegistryManager();
                    System.out.println("Registry Manager: " + registryManager);
                    transitionOutbound(PlayStateFactories.S2C.bind(RegistryByteBuf.makeFactory(registryManager)));

                    NetworkState<?> state = context.channel().pipeline().get(EncoderHandler.class).state;
                    context.channel().pipeline().remove("encoder");
                    for (ByteBuf byteBuf : PacketSniffer.getPlayPackets()) {
                        if (byteBuf.getByte(0) == 0x2B) {
                            // Modify the packet to give the spectator an entity ID that is not used by any other player
                            // TODO - This is a hacky way to do this, find a better way
                            byteBuf.setByte(1, Integer.MAX_VALUE);
                        }
                        context.writeAndFlush(byteBuf.copy());
                    }

                    // Spawn the host player
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    assert player != null;
                    sendPacket(state, new EntitySpawnS2CPacket(player, 0, BlockPos.ofFloored(player.getPos())));
                    sendPacket(state, new EntityPositionSyncS2CPacket(player.getId(), PlayerPosition.fromEntity(player), true));

                    sendPacket(state, new GameStateChangeS2CPacket(new GameStateChangeS2CPacket.Reason(3), 3.0F));

                    // TODO - Don't busy wait
                    new Thread(() -> {
                        RandomGenerator rand = RandomGenerator.getDefault();
                        while (context.channel().isOpen() && context.channel().isActive()) {
                            try {
                                Thread.sleep(20000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            sendPacket(state, new KeepAliveS2CPacket(rand.nextLong()));
                        }
                    }).start();

                    PacketCallback.EVENT.register((buf) -> {
                        context.channel().writeAndFlush(buf);
                    });

                    MovementCallback.EVENT.register((movementPacket) -> {
                        switch (movementPacket) {
                            case MovementCallback.MovementType.POSITION_AND_ROTATION -> {
                                Vec3d delta = player.getPos().subtract(player.lastX, player.lastY, player.lastZ).multiply(4096.0);
                                sendPacket(state, new EntityS2CPacket.RotateAndMoveRelative(player.getId(), (short) delta.x, (short) delta.y, (short) delta.z, MathHelper.packDegrees(player.getYaw()), MathHelper.packDegrees(player.getPitch()), player.isOnGround()));
                            }
                            case MovementCallback.MovementType.POSITION -> {
                                Vec3d delta = player.getPos().subtract(player.lastX, player.lastY, player.lastZ).multiply(4096.0);
                                sendPacket(state, new EntityS2CPacket.MoveRelative(player.getId(), (short) delta.x, (short) delta.y, (short) delta.z, player.isOnGround()));
                            }
                            case MovementCallback.MovementType.ROTATION -> {
                                sendPacket(state, new EntityS2CPacket.Rotate(player.getId(), MathHelper.packDegrees(player.getYaw()), MathHelper.packDegrees(player.getPitch()), player.isOnGround()));
                                sendPacket(state, new EntitySetHeadYawS2CPacket(player, MathHelper.packDegrees(player.headYaw)));
                            }
                        }
                    });

                    transitionInbound(PlayStateFactories.C2S.bind(RegistryByteBuf.makeFactory(registryManager), null));
                }
                break;
        }
        context.fireChannelRead(packet);
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

    private void sendPacket(NetworkState<?> state, Packet packet) {
        if (channel.isOpen()) {
            ByteBuf byteBuf = Unpooled.buffer();
            state.codec().encode(byteBuf, packet);
            channel.writeAndFlush(byteBuf);
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
//            LOGGER.debug("Skipping buf due to errors", ex.getCause());
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
//                        LOGGER.debug("Failed to sent buf", ex);
//                        if (this.getOppositeSide() == NetworkSide.CLIENTBOUND) {
//                            Packet<?> buf = (Packet<?>)(this.duringLogin ? new LoginDisconnectS2CPacket(text) : new DisconnectS2CPacket(text));
//                            this.send(buf, PacketCallbacks.always(() -> this.disconnect(disconnectionInfo)));
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
        } catch (Exception e) {
            if (e instanceof ClosedChannelException) {
                LOGGER.info("Connection closed during protocol change");
            } else {
                throw e;
            }
        }
    }
}
