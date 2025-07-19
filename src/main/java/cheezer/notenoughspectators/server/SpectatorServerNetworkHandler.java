package cheezer.notenoughspectators.server;

import cheezer.notenoughspectators.PacketEvent;
import cheezer.notenoughspectators.PacketStore;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.network.status.server.S00PacketServerInfo;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;
import java.util.UUID;

import static net.minecraft.network.NetworkManager.attrKeyConnectionState;

public class SpectatorServerNetworkHandler extends SimpleChannelInboundHandler<Packet<?>>  {
    private final SpectatorServer server;
    private Channel channel;

    public SpectatorServerNetworkHandler(SpectatorServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        super.channelActive(context);
        channel = context.channel();
        setConnectionState(EnumConnectionState.HANDSHAKING);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        super.channelInactive(context);
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Packet<?> packet) {
        if (channel.isOpen()) {
            EnumConnectionState phase = getNetworkPhase();
            if (phase == EnumConnectionState.HANDSHAKING) {
                if (packet instanceof C00Handshake) {
                    setConnectionState(((C00Handshake) packet).getRequestedState());
                } else {
                    context.disconnect();
                }
            } else if (phase == EnumConnectionState.STATUS) {
                if (packet instanceof C00PacketServerQuery) {
                    channel.writeAndFlush(new S00PacketServerInfo(server.getServerStatus()));
                } else if (packet instanceof C01PacketPing) {
                    channel.writeAndFlush(new S01PacketPong(((C01PacketPing) packet).getClientTime()));
                }
            } else if (phase == EnumConnectionState.LOGIN) {
                if (packet instanceof C00PacketLoginStart) {
                    channel.writeAndFlush(new S02PacketLoginSuccess(new GameProfile(UUID.fromString("41C82C87-7AfB-4024-BA57-13D2C99CAE77"), "[Minecraft]")));
                    setConnectionState(EnumConnectionState.PLAY);

                    for (Packet<?> packet1 : PacketStore.getPlayPackets()) {
                        channel.writeAndFlush(packet1);
                    }

                    MinecraftForge.EVENT_BUS.register(this);

                    new Thread(() -> {
                        Random rand = new Random();
                        while (channel.isOpen()) {
                            try {
                                Thread.sleep(2000); // 40 ticks
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }

                            channel.writeAndFlush(new S00PacketKeepAlive(rand.nextInt()));
                        }
                    });
                }
            } else if (phase == EnumConnectionState.PLAY) {
            } else {
                System.out.println("Unexpected packet in phase " + phase + ": " + packet.getClass().getSimpleName());
            }
        }
    }

    @SubscribeEvent
    public void onPacketReceived(PacketEvent event) {
        if (channel.isOpen() && getNetworkPhase() == EnumConnectionState.PLAY) {
            channel.writeAndFlush(event.getPacket());
        }
    }

    public void setConnectionState(EnumConnectionState newState) {
        channel.attr(attrKeyConnectionState).set(newState);
        channel.config().setAutoRead(true);
    }

    private EnumConnectionState getNetworkPhase() {
        return channel.attr(attrKeyConnectionState).get();
    }
}
