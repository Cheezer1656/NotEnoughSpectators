//package cheeezer.notenoughspectators.server;
//
//import cheeezer.notenoughspectators.PacketSniffer;
//import com.google.common.primitives.Ints;
//import com.mojang.authlib.GameProfile;
//import com.mojang.logging.LogUtils;
//import io.netty.buffer.ByteBuf;
//import io.netty.buffer.Unpooled;
//import net.minecraft.network.ClientConnection;
//import net.minecraft.network.DisconnectionInfo;
//import net.minecraft.network.PacketCallbacks;
//import net.minecraft.network.handler.EncoderHandler;
//import net.minecraft.network.listener.ServerLoginPacketListener;
//import net.minecraft.network.listener.TickablePacketListener;
//import net.minecraft.network.packet.BrandCustomPayload;
//import net.minecraft.network.packet.c2s.common.CookieResponseC2SPacket;
//import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
//import net.minecraft.network.packet.c2s.login.EnterConfigurationC2SPacket;
//import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
//import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
//import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
//import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
//import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
//import net.minecraft.network.packet.s2c.config.ReadyS2CPacket;
//import net.minecraft.network.packet.s2c.login.LoginCompressionS2CPacket;
//import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
//import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
//import net.minecraft.network.state.ConfigurationStates;
//import net.minecraft.network.state.NetworkState;
//import net.minecraft.server.network.ConnectedClientData;
//import net.minecraft.text.Text;
//import net.minecraft.util.StringHelper;
//import net.minecraft.util.Uuids;
//import net.minecraft.util.crash.CrashCallable;
//import net.minecraft.util.crash.CrashReport;
//import net.minecraft.util.crash.CrashReportSection;
//import net.minecraft.util.math.random.Random;
//import org.apache.commons.lang3.Validate;
//import org.jetbrains.annotations.Nullable;
//import org.slf4j.Logger;
//
//import java.util.Objects;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class ServerLoginNetworkHandler implements ServerLoginPacketListener, TickablePacketListener {
//    private static final AtomicInteger NEXT_AUTHENTICATOR_THREAD_ID = new AtomicInteger(0);
//    static final Logger LOGGER = LogUtils.getLogger();
//    private static final int TIMEOUT_TICKS = 600;
//    private final byte[] nonce;
//    final SpectatorServer server;
//    final ClientConnection connection;
//    private volatile ServerLoginNetworkHandler.State state = ServerLoginNetworkHandler.State.HELLO;
//    private int loginTicks;
//    @Nullable
//    String profileName;
//    @Nullable
//    private GameProfile profile;
//    private final String serverId = "";
//    private final boolean transferred;
//
//    public ServerLoginNetworkHandler(SpectatorServer server, ClientConnection connection, boolean transferred) {
//        this.server = server;
//        this.connection = connection;
//        this.nonce = Ints.toByteArray(Random.create().nextInt());
//        this.transferred = transferred;
//    }
//
//    @Override
//    public void tick() {
//        if (this.state == ServerLoginNetworkHandler.State.VERIFYING) {
//            this.tickVerify((GameProfile) Objects.requireNonNull(this.profile));
//        }
//
//        if (this.state == ServerLoginNetworkHandler.State.WAITING_FOR_DUPE_DISCONNECT && !this.hasPlayerWithId((GameProfile)Objects.requireNonNull(this.profile))) {
//            this.sendSuccessPacket(this.profile);
//        }
//
//        if (this.loginTicks++ == 600) {
//            this.disconnect(Text.translatable("multiplayer.disconnect.slow_login"));
//        }
//    }
//
//    @Override
//    public boolean isConnectionOpen() {
//        return this.connection.isOpen();
//    }
//
//    public void disconnect(Text reason) {
//        try {
//            LOGGER.info("Disconnecting {}: {}", this.getConnectionInfo(), reason.getString());
//            this.connection.send(new LoginDisconnectS2CPacket(reason));
//            this.connection.disconnect(reason);
//        } catch (Exception var3) {
//            LOGGER.error("Error whilst disconnecting player", (Throwable)var3);
//        }
//    }
//
//    private boolean hasPlayerWithId(GameProfile profile) {
//        return false; // Allow duplicate logins
//    }
//
//    @Override
//    public void onDisconnected(DisconnectionInfo info) {
//        LOGGER.info("{} lost connection: {}", this.getConnectionInfo(), info.reason().getString());
//    }
//
//    public String getConnectionInfo() {
//        String string = this.connection.getAddressAsString(false); // Don't log IP addresses
//        return this.profileName != null ? this.profileName + " (" + string + ")" : string;
//    }
//
//    @Override
//    public void onHello(LoginHelloC2SPacket packet) {
//        Validate.validState(this.state == ServerLoginNetworkHandler.State.HELLO, "Unexpected hello packet");
//        Validate.validState(StringHelper.isValidPlayerName(packet.name()), "Invalid characters in username");
//        this.profileName = packet.name();
//        this.startVerify(Uuids.getOfflinePlayerProfile(this.profileName));
//    }
//
//    void startVerify(GameProfile profile) {
//        this.profile = profile;
//        this.state = ServerLoginNetworkHandler.State.VERIFYING;
//    }
//
//    private void tickVerify(GameProfile profile) {
//        if (this.server.getNetworkCompressionThreshold() >= 0 && !this.connection.isLocal()) {
//            this.connection
//                    .send(
//                            new LoginCompressionS2CPacket(this.server.getNetworkCompressionThreshold()),
//                            PacketCallbacks.always(() -> this.connection.setCompressionThreshold(this.server.getNetworkCompressionThreshold(), true))
//                    );
//        }
//        this.sendSuccessPacket(profile);
//    }
//
//    private void sendSuccessPacket(GameProfile profile) {
//        this.state = ServerLoginNetworkHandler.State.PROTOCOL_SWITCHING;
//        this.connection.send(new LoginSuccessS2CPacket(profile));
//    }
//
//    @Override
//    public void onKey(LoginKeyC2SPacket packet) {}
//
//    @Override
//    public void onQueryResponse(LoginQueryResponseC2SPacket packet) {
//        this.disconnect(ServerCommonNetworkHandler.UNEXPECTED_QUERY_RESPONSE_TEXT);
//    }
//
//    @Override
//    public void onEnterConfiguration(EnterConfigurationC2SPacket packet) {
//            // Run transition to bring back encoder so that we can extract its NetworkState
//            Validate.validState(this.state == ServerLoginNetworkHandler.State.PROTOCOL_SWITCHING, "Unexpected login acknowledgement packet");
//            this.connection.transitionOutbound(ConfigurationStates.S2C);
//
////            EncoderHandler encoder = ((EncoderHandler) this.connection.channel.pipeline().get("encoder"));
////            try {
////                this.connection.channel.pipeline().remove("encoder");
////            } catch (Exception e) {
////                e.printStackTrace();
////            }
////
//////            for (ByteBuf buf : PacketSniffer.getConfigPackets()) {
//////                System.out.println(buf.readByte());
//////                this.connection.channel.writeAndFlush(buf);
//////            }
//////            System.out.println("Sent " + PacketSniffer.getConfigPackets().size() + " configuration packets");
////
//////        Validate.validState(this.state == ServerLoginNetworkHandler.State.PROTOCOL_SWITCHING, "Unexpected login acknowledgement packet");
//////        this.connection.transitionOutbound(ConfigurationStates.S2C);
////
////        try {
////            this.connection.channel.pipeline().addBefore("prepender", "encoder", new EncoderHandler<>(encoder.state));
////        } catch (Exception e) {
////            e.printStackTrace();
////        }
////        System.out.println(this.connection.channel.pipeline().names());
//
////        new Thread(() -> {
////            try {
////                Thread.sleep(100); // Simulate some processing time
////            } catch (InterruptedException e) {
////                Thread.currentThread().interrupt();
////            }
////            this.connection.channel.writeAndFlush(new CustomPayloadS2CPacket(new BrandCustomPayload("notenoughspectators")));
////            this.connection.channel.writeAndFlush(ReadyS2CPacket.INSTANCE);
////        }).start();
//
//        ConnectedClientData connectedClientData = ConnectedClientData.createDefault((GameProfile)Objects.requireNonNull(this.profile), this.transferred);
//        ServerConfigurationNetworkHandler serverConfigurationNetworkHandler = new ServerConfigurationNetworkHandler(this.connection, connectedClientData);
//        this.connection.transitionInbound(ConfigurationStates.C2S, serverConfigurationNetworkHandler);
//        serverConfigurationNetworkHandler.sendConfigurations();
//        this.state = ServerLoginNetworkHandler.State.ACCEPTED;
//    }
//
//    public void onReady(ReadyC2SPacket packet) {
//        System.out.println(packet);
//    }
//
//    @Override
//    public void addCustomCrashReportInfo(CrashReport report, CrashReportSection section) {
//        section.add("Login phase", (CrashCallable<String>)(() -> this.state.toString()));
//    }
//
//    @Override
//    public void onCookieResponse(CookieResponseC2SPacket packet) {
//        this.disconnect(ServerCommonNetworkHandler.UNEXPECTED_QUERY_RESPONSE_TEXT);
//    }
//
//    static enum State {
//        HELLO,
//        KEY,
//        AUTHENTICATING,
//        NEGOTIATING,
//        VERIFYING,
//        WAITING_FOR_DUPE_DISCONNECT,
//        PROTOCOL_SWITCHING,
//        ACCEPTED;
//    }
//}
