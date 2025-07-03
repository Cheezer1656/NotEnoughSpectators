package cheeezer.notenoughspectators;

import cheeezer.notenoughspectators.event.RawPacketCallback;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.OpaqueByteBufHolder;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.handler.PacketException;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.BlockStateParticleEffect;

import java.util.ArrayList;

public class PacketSniffer extends ChannelInboundHandlerAdapter {
    private static final ArrayList<Packet> CONFIG_PACKETS = new ArrayList<>();
    private static final ArrayList<ByteBuf> PLAY_PACKETS = new ArrayList<>();

    public PacketSniffer() {
        // Clear all packets when joining a new server
        CONFIG_PACKETS.clear();
        PLAY_PACKETS.clear();
    }

    public static ArrayList<Packet<?>> getConfigPackets() {
        return (ArrayList<Packet<?>>) CONFIG_PACKETS.clone();
    }

    public static ArrayList<ByteBuf> getPlayPackets() {
        return (ArrayList<ByteBuf>) PLAY_PACKETS.clone();
    }

    public static void addConfigPacket(Packet<?> packet) {
        CONFIG_PACKETS.add(packet);
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object value) {
        value = OpaqueByteBufHolder.unpack(value);
        if (value instanceof ByteBuf byteBuf && byteBuf.readableBytes() != 0) {
            Packet<?> packet;
            try {
                packet = (Packet<?>) context.pipeline().get(DecoderHandler.class).state.codec().decode(byteBuf.copy());
            } catch (Exception e) {
                NotEnoughSpectators.LOGGER.debug("Error decoding packet: " + e.getMessage());
                e.printStackTrace();
                if (e instanceof PacketException) {
                    byteBuf.skipBytes(byteBuf.readableBytes());
                }

                return;
            }
            NetworkPhase phase = getNetworkPhase(context);
            if (phase == NetworkPhase.PLAY && packet.getPacketType().side() == NetworkSide.CLIENTBOUND) {
                if (!(packet instanceof ParticleS2CPacket packet1 && packet1.getParameters() instanceof BlockStateParticleEffect)) { // Certain particles causes a decode error for spectator clients
                    switch (packet) {
                        case OpenScreenS2CPacket ignored:
                            break;
                        case PlayerPositionLookS2CPacket ignored:
                            break;
                        case InventoryS2CPacket ignored:
                            break;
                        case ScreenHandlerSlotUpdateS2CPacket ignored:
                            break;
                        case ScreenHandlerPropertyUpdateS2CPacket ignored:
                            break;
                        case PlayerRespawnS2CPacket ignored:
                            break;
                        case PlayerAbilitiesS2CPacket ignored:
                            break;
                        case GameStateChangeS2CPacket gameStateChangePacket:
                            if (gameStateChangePacket.getReason() == GameStateChangeS2CPacket.GAME_MODE_CHANGED) break;
                        default:
                            PLAY_PACKETS.add(byteBuf.copy());
                            RawPacketCallback.EVENT.invoker().onPacketReceived(byteBuf.copy());
                    }
                }
            }
        }

        context.fireChannelRead(value);
    }

    public static NetworkPhase getNetworkPhase(ChannelHandlerContext context) {
        DecoderHandler decoderHandler = context.channel().pipeline().get(DecoderHandler.class);

        if (decoderHandler == null) {
            return NetworkPhase.LOGIN;
        }
        return decoderHandler.state.id();
    }
}
