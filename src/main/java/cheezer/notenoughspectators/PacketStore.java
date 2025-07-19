package cheezer.notenoughspectators;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Packet;

import java.util.ArrayList;

public class PacketStore extends ChannelInboundHandlerAdapter {
    private static final ArrayList<Packet<?>> PLAY_PACKETS = new ArrayList<>();
    private static long seed = 0;

    public static void clearPackets() {
        PLAY_PACKETS.clear();
    }

    public static ArrayList<Packet<?>> getPlayPackets() {
        return (ArrayList<Packet<?>>) PLAY_PACKETS.clone();
    }

    public static void addPlayPacket(Packet<?> packet) {
        PLAY_PACKETS.add(packet);
    }

    public static long getSeed() {
        return seed;
    }

    public static void setSeed(long newSeed) {
        seed = newSeed;
    }
}
