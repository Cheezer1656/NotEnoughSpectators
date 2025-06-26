package cheeezer.notenoughspectators.server;

import cheeezer.notenoughspectators.PacketSniffer;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.listener.ServerConfigurationPacketListener;
import net.minecraft.network.listener.TickablePacketListener;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.state.PlayStateFactories;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.*;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ServerConfigurationNetworkHandler
        extends ServerCommonNetworkHandler
        implements ServerConfigurationPacketListener,
        TickablePacketListener,
        FabricServerConfigurationNetworkHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Text INVALID_PLAYER_DATA_TEXT = Text.translatable("multiplayer.disconnect.invalid_player_data");
    private final GameProfile profile;
    private final Queue<ServerPlayerConfigurationTask> tasks = new ConcurrentLinkedQueue();
    @Nullable
    private ServerPlayerConfigurationTask currentTask;
    private SyncedClientOptions syncedOptions;

    private net.minecraft.network.state.NetworkState state;

    public ServerConfigurationNetworkHandler(ClientConnection clientConnection, ConnectedClientData connectedClientData) {
        super(clientConnection, connectedClientData);
        this.profile = connectedClientData.gameProfile();
        this.syncedOptions = connectedClientData.syncedOptions();
    }

    @Override
    protected GameProfile getProfile() {
        return this.profile;
    }

    @Override
    public void onDisconnected(DisconnectionInfo info) {
        LOGGER.info("{} lost connection: {}", this.profile, info.reason().getString());
        super.onDisconnected(info);
    }

    @Override
    public boolean isConnectionOpen() {
        return this.connection.isOpen();
    }

    public void sendConfigurations() {
        this.sendPacket(new CustomPayloadS2CPacket(new BrandCustomPayload("notenoughspectators")));

        this.tasks.add(new JoinWorldTask());
        this.pollTask();
    }

    public void endConfiguration() {
        this.tasks.add(new JoinWorldTask());
        this.pollTask();
    }

    @Override
    public void onClientOptions(ClientOptionsC2SPacket packet) {
        this.syncedOptions = packet.options();
    }

    @Override
    public void onResourcePackStatus(ResourcePackStatusC2SPacket packet) {
        super.onResourcePackStatus(packet);
        if (packet.status().hasFinished()) {
            this.onTaskFinished(SendResourcePackTask.KEY);
        }
    }

    @Override
    public void onSelectKnownPacks(SelectKnownPacksC2SPacket packet) {}

    @Override
    public void onReady(ReadyC2SPacket packet) {
        this.onTaskFinished(JoinWorldTask.KEY);
        System.out.println("MC SERVER: "+MinecraftClient.getInstance().getServer());
        DynamicRegistryManager.Immutable registryManager = MinecraftClient.getInstance().getServer().getRegistryManager();
        this.connection.transitionOutbound(PlayStateFactories.S2C.bind(RegistryByteBuf.makeFactory(registryManager)));

        state = this.connection.channel.pipeline().get(EncoderHandler.class).state;
        this.connection.channel.pipeline().remove("encoder");

//        DisconnectS2CPacket disconnectS2CPacket = new DisconnectS2CPacket(Text.of("Ready to play!"));
//        ByteBuf buf = Unpooled.buffer();
//        state.codec().encode(buf, disconnectS2CPacket);
//        this.connection.channel.writeAndFlush(buf);

        new Thread(() -> {
            System.out.println("Thread started to send packets");
            try {
                Thread.sleep(1000); // Wait for the server to be ready
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Sending packets to client");
            for (ByteBuf buf : PacketSniffer.getPackets()) {
                System.out.println("\n");
                for (int i = 0; i < buf.readableBytes(); i++) {
                    System.out.printf("%02X ", buf.getByte(i));
                }
                this.connection.channel.writeAndFlush(buf);
            }
//            DisconnectS2CPacket disconnectS2CPacket = new DisconnectS2CPacket(Text.of("Ready to play!"));
//            ByteBuf buf = Unpooled.buffer();
//            state.codec().encode(buf, disconnectS2CPacket);
//            this.connection.channel.writeAndFlush(buf);
        }).start();
    }

    @Override
    public void tick() {
        this.baseTick();
    }

    private void pollTask() {
        if (this.currentTask != null) {
            throw new IllegalStateException("Task " + this.currentTask.getKey().id() + " has not finished yet");
        } else if (this.isConnectionOpen()) {
            ServerPlayerConfigurationTask serverPlayerConfigurationTask = (ServerPlayerConfigurationTask)this.tasks.poll();
            if (serverPlayerConfigurationTask != null) {
                this.currentTask = serverPlayerConfigurationTask;
                serverPlayerConfigurationTask.sendPacket(this::sendPacket);
            }
        }
    }

    private void onTaskFinished(ServerPlayerConfigurationTask.Key key) {
        ServerPlayerConfigurationTask.Key key2 = this.currentTask != null ? this.currentTask.getKey() : null;
        if (!key.equals(key2)) {
            throw new IllegalStateException("Unexpected request for task finish, current task: " + key2 + ", requested: " + key);
        } else {
            this.currentTask = null;
            this.pollTask();
        }
    }
}