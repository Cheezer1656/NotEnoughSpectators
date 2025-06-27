package cheeezer.notenoughspectators;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.OpaqueByteBufHolder;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.handler.PacketException;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;

public class PacketSniffer extends ChannelInboundHandlerAdapter {
    private static final ArrayList<ByteBuf> CONFIG_PACKETS = new ArrayList<>();
    private static final ArrayList<ByteBuf> PLAY_PACKETS = new ArrayList<>();
    public static final ArrayList<Packet<?>> configPackets = new ArrayList<>();
    public static final ArrayList<Packet<?>> playPackets = new ArrayList<>();
    private static ByteBuf currentBuffer = Unpooled.buffer();
    public static int packetCount = 0;
    public static int decoderCalls = 0;

    public PacketSniffer() {
        // Clear all packets
        CONFIG_PACKETS.clear();
        PLAY_PACKETS.clear();
        configPackets.clear();
        playPackets.clear();
        currentBuffer = Unpooled.buffer();
        packetCount = 0;
    }

    public static ArrayList<ByteBuf> getConfigPackets() {
        return (ArrayList<ByteBuf>) CONFIG_PACKETS.clone();
    }

    public static ArrayList<ByteBuf> getPlayPackets() {
        return (ArrayList<ByteBuf>) PLAY_PACKETS.clone();
    }

    public static void appendBuffer(Packet packet) {
//        System.out.println("Appending buffer with size: " + currentBuffer.readableBytes());
//        System.out.println(currentBuffer.getByte(0));
//        if (currentBuffer.getByte(0) == 21) {
//            for (int i = 0; i < currentBuffer.readableBytes(); i++) {
//                System.out.printf("\n\n%02X ", currentBuffer.getByte(i));
//            }
//        }
//        PLAY_PACKETS.add(currentBuffer);
//        currentBuffer = Unpooled.buffer();
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object value) {
        value = OpaqueByteBufHolder.unpack(value);
        if (value instanceof ByteBuf byteBuf && byteBuf.readableBytes() != 0) {
//            Packet<?> packet;
//            try {
//                packet = (Packet<?>) context.pipeline().get(DecoderHandler.class).state.codec().decode(byteBuf);
//            } catch (Exception e) {
//                e.printStackTrace();
//                if (e instanceof PacketException) {
//                    byteBuf.skipBytes(byteBuf.readableBytes());
//                }
//
//                throw e;
//            }
            NetworkPhase phase = getNetworkPhase(context);
            if (phase == NetworkPhase.PLAY) {
                if (packetCount <= 5) {
//                    System.out.print(getNetworkPhase(context) + ": ");
                    //            System.out.println(byteBuf.readableBytes());
                    for (int i = 0; i < byteBuf.readableBytes(); i++) {
                        System.out.printf("%02X ", byteBuf.getByte(i));
                    }
                    packetCount++;
                }
                PLAY_PACKETS.add(byteBuf.copy());
//                currentBuffer.writeBytes(byteBuf.copy());
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
