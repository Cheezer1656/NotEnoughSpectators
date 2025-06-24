package cheeezer.notenoughspectators.server;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.listener.ServerConfigurationPacketListener;
import net.minecraft.network.listener.TickablePacketListener;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.state.PlayStateFactories;
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
        System.out.println("MC SERVER: "+MinecraftClient.getInstance().getServer()); // TODO: Remove this line
        this.connection.transitionOutbound(PlayStateFactories.S2C.bind(RegistryByteBuf.makeFactory(MinecraftClient.getInstance().getServer().getRegistryManager())));

        this.disconnect(Text.of("Testing disconnect")); // TODO: Remove this line
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