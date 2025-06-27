//package cheeezer.notenoughspectators.server;
//
//import net.minecraft.SharedConstants;
//import net.minecraft.network.ClientConnection;
//import net.minecraft.network.DisconnectionInfo;
//import net.minecraft.network.listener.ServerHandshakePacketListener;
//import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
//import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
//import net.minecraft.network.state.LoginStates;
//import net.minecraft.network.state.QueryStates;
//import net.minecraft.server.network.ServerQueryNetworkHandler;
//import net.minecraft.text.Text;
//
//public class ServerHandshakeNetworkHandler implements ServerHandshakePacketListener {
//    private final SpectatorServer server;
//    private final ClientConnection connection;
//
//    public ServerHandshakeNetworkHandler(SpectatorServer server, ClientConnection connection) {
//        this.server = server;
//        this.connection = connection;
//    }
//
//    @Override
//    public void onHandshake(HandshakeC2SPacket packet) {
//        switch (packet.intendedState()) {
//            case LOGIN:
//                this.login(packet, false);
//                break;
//            case STATUS:
//                this.connection.transitionOutbound(QueryStates.S2C);
//                this.connection.transitionInbound(QueryStates.C2S, new ServerQueryNetworkHandler(this.server.getServerMetadata(), this.connection));
//                break;
//            case TRANSFER:
//                this.login(packet, true);
//                break;
//            default:
//                throw new UnsupportedOperationException("Invalid intention " + packet.intendedState());
//        }
//    }
//
//    private void login(HandshakeC2SPacket packet, boolean transfer) {
//        this.connection.transitionOutbound(LoginStates.S2C);
//        if (packet.protocolVersion() != SharedConstants.getGameVersion().protocolVersion()) {
//            Text text;
//            if (packet.protocolVersion() < 754) {
//                text = Text.translatable("multiplayer.disconnect.outdated_client", SharedConstants.getGameVersion().name());
//            } else {
//                text = Text.translatable("multiplayer.disconnect.incompatible", SharedConstants.getGameVersion().name());
//            }
//
//            this.connection.send(new LoginDisconnectS2CPacket(text));
//            this.connection.disconnect(text);
//        } else {
//            this.connection.transitionInbound(LoginStates.C2S, new ServerLoginNetworkHandler(this.server, this.connection, transfer));
//        }
//    }
//
//    @Override
//    public void onDisconnected(DisconnectionInfo info) {
//    }
//
//    @Override
//    public boolean isConnectionOpen() {
//        return this.connection.isOpen();
//    }
//}
//
