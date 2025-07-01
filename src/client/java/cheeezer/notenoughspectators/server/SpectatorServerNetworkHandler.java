package cheeezer.notenoughspectators.server;

import cheeezer.notenoughspectators.NotEnoughSpectators;
import cheeezer.notenoughspectators.PacketSniffer;
import cheeezer.notenoughspectators.event.MovementCallback;
import cheeezer.notenoughspectators.event.PacketCallback;
import cheeezer.notenoughspectators.event.RawPacketCallback;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.*;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.EnterConfigurationC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.config.ReadyS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket;
import net.minecraft.network.state.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.util.Collections;
import java.util.Optional;
import java.util.random.RandomGenerator;

public class SpectatorServerNetworkHandler extends SimpleChannelInboundHandler<Packet<?>> {
    private static final Logger LOGGER = NotEnoughSpectators.LOGGER;
    SpectatorServer server;
    NetworkPhase phase = NetworkPhase.HANDSHAKING;
    private Channel channel;
    private PacketCodec codec;
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

                    codec = context.channel().pipeline().get(EncoderHandler.class).state.codec();
                    context.channel().pipeline().remove("encoder");
                    for (ByteBuf byteBuf1 : PacketSniffer.getPlayPackets()) {
                        ByteBuf byteBuf = ReferenceCountUtil.retain(byteBuf1.copy());
                        if (byteBuf.getByte(0) == 0x2B) {
                            // Modify the packet to give the spectator an entity ID that is not used by any other player
                            // TODO - This is a hacky way to do this, find a better way
                            byteBuf.setByte(1, Integer.MAX_VALUE);
                        }
                        context.writeAndFlush(byteBuf.copy());
                    }

                    configureClient();

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
                            sendPacket(new KeepAliveS2CPacket(rand.nextLong()));
                        }
                    }).start();

                    ClientPlayerEntity oldPlayer = MinecraftClient.getInstance().player; // Retains previous player instance during transition
                    RawPacketCallback.EVENT.register((buf1) -> {
                        ByteBuf buf = ReferenceCountUtil.retain(buf1.copy());
                        if (buf.getByte(0) == 0x2B) {
                            // Modify the packet to give the spectator an entity ID that is not used by any other player
                            // TODO - This is a hacky way to do this, find a better way
                            buf.setByte(1, Integer.MAX_VALUE);
                            // Alert the spectator player that they are switching servers
                            sendPacket(new ProfilelessChatMessageS2CPacket(Text.of("Switching server..."), MessageType.params(MessageType.SAY_COMMAND, oldPlayer.getWorld().getRegistryManager(), Text.of("NotEnoughSpectators"))));
                            // Respawn spectator player
                            World world = oldPlayer.getWorld();
                            if (world == null) {
                                NotEnoughSpectators.LOGGER.warn("World is null, cannot respawn spectator player");
                                return;
                            }
                            sendPacket(new PlayerRespawnS2CPacket(new CommonPlayerSpawnInfo(world.getDimensionEntry(), world.getRegistryKey(), 0, GameMode.CREATIVE, null, world.isDebugWorld(), false, Optional.empty(), 0, 0), (byte) 0));

                            new Thread(() -> {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                configureClient();
                            }).start();
                        }
                        context.channel().writeAndFlush(buf);
                    });
                    PacketCallback.EVENT.register(this::sendPacket);

                    MovementCallback.EVENT.register((movementType) -> {
                        ClientPlayerEntity player = MinecraftClient.getInstance().player;
                        if (player == null) {
                            return;
                        }
                        switch (movementType) {
                            case MovementCallback.MovementType.POSITION_AND_ROTATION -> {
                                Vec3d delta = player.getPos().subtract(player.lastX, player.lastY, player.lastZ);
                                if (delta.lengthSquared() != 0.0) {
                                    delta = delta.multiply(4096.0);
                                    sendPacket(new EntityS2CPacket.RotateAndMoveRelative(player.getId(), (short) delta.x, (short) delta.y, (short) delta.z, MathHelper.packDegrees(player.getYaw()), MathHelper.packDegrees(player.getPitch()), player.isOnGround()));
                                } else {
                                    sendPacket(new EntityPositionSyncS2CPacket(player.getId(), PlayerPosition.fromEntity(player), player.isOnGround()));
                                }
                            }
                            case MovementCallback.MovementType.POSITION -> {
                                Vec3d delta = player.getPos().subtract(player.lastX, player.lastY, player.lastZ).multiply(4096.0);
                                sendPacket(new EntityS2CPacket.MoveRelative(player.getId(), (short) delta.x, (short) delta.y, (short) delta.z, player.isOnGround()));
                            }
                            case MovementCallback.MovementType.ROTATION -> {
                                sendPacket(new EntityS2CPacket.Rotate(player.getId(), MathHelper.packDegrees(player.getYaw()), MathHelper.packDegrees(player.getPitch()), player.isOnGround()));
                                sendPacket(new EntitySetHeadYawS2CPacket(player, MathHelper.packDegrees(player.headYaw)));
                            }
                        }
                    });

                    transitionInbound(PlayStateFactories.C2S.bind(RegistryByteBuf.makeFactory(registryManager), new PlayStateFactories.PacketCodecModifierContext() {
                        @Override
                        public boolean isInCreativeMode() {
                            return true;
                        }
                    }));
                }
                break;
            case NetworkPhase.PLAY:
                if (packet instanceof ChatMessageC2SPacket chatMessagePacket && chatMessagePacket.chatMessage().equals("tp")) {
                    // Teleport the spectator to the host player
                    sendPacket(new PlayerPositionLookS2CPacket(Integer.MAX_VALUE, PlayerPosition.fromEntity(MinecraftClient.getInstance().player), Collections.emptySet()));
                } else if (packet instanceof PlayerInteractItemC2SPacket) {
                    // Teleport the spectator to the host player
                    sendPacket(new PlayerPositionLookS2CPacket(Integer.MAX_VALUE, PlayerPosition.fromEntity(MinecraftClient.getInstance().player), Collections.emptySet()));
                }
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

    private void sendPacket(Packet<?> packet) {
        if (channel.isOpen()) {
            if (codec == null) {
                channel.writeAndFlush(packet);
            } else {
                ByteBuf byteBuf = Unpooled.buffer();
                codec.encode(byteBuf, packet);
                channel.writeAndFlush(byteBuf);
            }
        }
    }

    private void configureClient() {
        // Spawn the host player
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;
        sendPacket(new EntitySpawnS2CPacket(player, 0, BlockPos.ofFloored(player.getPos())));
        sendPacket(new EntityPositionSyncS2CPacket(player.getId(), PlayerPosition.fromEntity(player), true));

        // Set spectator attributes
        PlayerAbilities playerAbilities = new PlayerAbilities();
        playerAbilities.unpack(new PlayerAbilities.Packed(true, true, true, true, true, 0.05F, 0.1F));
        sendPacket(new PlayerAbilitiesS2CPacket(playerAbilities));
        sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, 1.0F));

        // Teleport the spectator to the host player
        sendPacket(new PlayerPositionLookS2CPacket(Integer.MAX_VALUE, PlayerPosition.fromEntity(player), Collections.emptySet()));

        // Give the spectator a compass
        sendPacket(new SetPlayerInventoryS2CPacket(EquipmentSlot.MAINHAND.getIndex(), new ItemStack(RegistryEntry.of(Items.COMPASS), 1, ComponentChanges.builder().add(DataComponentTypes.ITEM_NAME, Text.of("Teleport to Host")).build())));
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

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable ex) {
        if (ex instanceof PacketException) {
            LOGGER.debug("Skipping buf due to errors", ex.getCause());
        } else {
            boolean bl = !this.errored;
            this.errored = true;
            if (this.channel.isOpen()) {
                if (ex instanceof TimeoutException) {
                    LOGGER.debug("Timeout", ex);
                    this.disconnect(new DisconnectionInfo(Text.translatable("disconnect.timeout")));
                } else {
                    Text text = Text.translatable("disconnect.genericReason", "Internal Exception: " + ex);
                    DisconnectionInfo disconnectionInfo = new DisconnectionInfo(text);

                    if (bl) {
                        LOGGER.debug("Failed to sent buf", ex);
                        Packet<?> buf = this.duringLogin ? new LoginDisconnectS2CPacket(text) : new DisconnectS2CPacket(text);
                        sendPacket(buf);
                        this.disconnect(disconnectionInfo);

                        if (this.channel != null) {
                            this.channel.config().setAutoRead(false);
                        }
                    } else {
                        LOGGER.debug("Double fault", ex);
                        this.disconnect(disconnectionInfo);
                    }
                }
            }
        }
    }

    private void disconnect(DisconnectionInfo disconnectionInfo) {
        LOGGER.debug("Disconnecting due to: {}", disconnectionInfo.reason());
        if (this.channel.isOpen()) {
            this.channel.close().awaitUninterruptibly();
        } else {
            LOGGER.debug("Channel already closed, not sending disconnect packet");
        }
    }

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
