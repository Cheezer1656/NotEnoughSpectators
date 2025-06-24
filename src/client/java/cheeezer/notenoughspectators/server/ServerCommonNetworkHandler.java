package cheeezer.notenoughspectators.server;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.ServerCommonPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.*;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.crash.CrashCallable;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.profiler.Profilers;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public abstract class ServerCommonNetworkHandler implements ServerCommonPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int KEEP_ALIVE_INTERVAL = 15000;
    private static final int TRANSITION_TIMEOUT = 15000;
    private static final Text TIMEOUT_TEXT = Text.translatable("disconnect.timeout");
    public static final Text UNEXPECTED_QUERY_RESPONSE_TEXT = Text.translatable("multiplayer.disconnect.unexpected_query_response");
    protected final ClientConnection connection;
    private final boolean transferred;
    private long lastKeepAliveTime;
    private boolean waitingForKeepAlive;
    private long keepAliveId;
    private long transitionStartTime;
    private boolean transitioning = false;
    private int latency;
    private volatile boolean flushDisabled = false;

    public ServerCommonNetworkHandler(ClientConnection connection, ConnectedClientData clientData) {
        this.connection = connection;
        this.lastKeepAliveTime = Util.getMeasuringTimeMs();
        this.latency = clientData.latency();
        this.transferred = clientData.transferred();
    }

    private void markTransitionTime() {
        if (!this.transitioning) {
            this.transitionStartTime = Util.getMeasuringTimeMs();
            this.transitioning = true;
        }
    }

    @Override
    public void onDisconnected(DisconnectionInfo info) {}

    @Override
    public void onPacketException(Packet packet, Exception exception) throws CrashException {
        ServerCommonPacketListener.super.onPacketException(packet, exception);
    }

    @Override
    public void onKeepAlive(KeepAliveC2SPacket packet) {
        if (this.waitingForKeepAlive && packet.getId() == this.keepAliveId) {
            int i = (int) (Util.getMeasuringTimeMs() - this.lastKeepAliveTime);
            this.latency = (this.latency * 3 + i) / 4;
            this.waitingForKeepAlive = false;
        } else if (!this.isHost()) {
            this.disconnect(TIMEOUT_TEXT);
        }
    }

    @Override
    public void onPong(CommonPongC2SPacket packet) {
    }

    @Override
    public void onCustomPayload(CustomPayloadC2SPacket packet) {
    }

    @Override
    public void onCustomClickAction(CustomClickActionC2SPacket packet) {}

    @Override
    public void onResourcePackStatus(ResourcePackStatusC2SPacket packet) {}

    @Override
    public void onCookieResponse(CookieResponseC2SPacket packet) {
        this.disconnect(UNEXPECTED_QUERY_RESPONSE_TEXT);
    }

    protected void baseTick() {
        Profilers.get().push("keepAlive");
        long l = Util.getMeasuringTimeMs();
        if (!this.isHost() && l - this.lastKeepAliveTime >= 15000L) {
            if (this.waitingForKeepAlive) {
                this.disconnect(TIMEOUT_TEXT);
            } else if (this.checkTransitionTimeout(l)) {
                this.waitingForKeepAlive = true;
                this.lastKeepAliveTime = l;
                this.keepAliveId = l;
                this.sendPacket(new KeepAliveS2CPacket(this.keepAliveId));
            }
        }

        Profilers.get().pop();
    }

    private boolean checkTransitionTimeout(long time) {
        if (this.transitioning) {
            if (time - this.transitionStartTime >= 15000L) {
                this.disconnect(TIMEOUT_TEXT);
            }

            return false;
        } else {
            return true;
        }
    }

    public void disableFlush() {
        this.flushDisabled = true;
    }

    public void enableFlush() {
        this.flushDisabled = false;
        this.connection.flush();
    }

    public void sendPacket(Packet<?> packet) {
        this.send(packet, null);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener) {
        if (packet.transitionsNetworkState()) {
            this.markTransitionTime();
        }

        boolean bl = !this.flushDisabled;

        try {
            this.connection.send(packet, channelFutureListener, bl);
        } catch (Throwable var7) {
            CrashReport crashReport = CrashReport.create(var7, "Sending packet");
            CrashReportSection crashReportSection = crashReport.addElement("Packet being sent");
            crashReportSection.add("Packet class", (CrashCallable<String>) (() -> packet.getClass().getCanonicalName()));
            throw new CrashException(crashReport);
        }
    }

    public void disconnect(Text reason) {
        this.disconnect(new DisconnectionInfo(reason));
    }

    public void disconnect(DisconnectionInfo disconnectionInfo) {
        this.connection.send(new DisconnectS2CPacket(disconnectionInfo.reason()), PacketCallbacks.always(() -> this.connection.disconnect(disconnectionInfo)));
        this.connection.tryDisableAutoRead();
    }

    protected boolean isHost() {
        return false;
    }

    protected abstract GameProfile getProfile();

    @Debug
    public GameProfile getDebugProfile() {
        return this.getProfile();
    }

    public int getLatency() {
        return this.latency;
    }

    protected ConnectedClientData createClientData(SyncedClientOptions syncedOptions) {
        return new ConnectedClientData(this.getProfile(), this.latency, syncedOptions, this.transferred);
    }
}
