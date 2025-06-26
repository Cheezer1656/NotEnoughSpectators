package cheeezer.notenoughspectators;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.OpaqueByteBufHolder;
import net.minecraft.network.handler.DecoderHandler;

import java.util.ArrayList;

public class PacketSniffer extends ChannelInboundHandlerAdapter {
    private static final ArrayList<ByteBuf> PACKETS = new ArrayList<>();
    public static int packetCount = 0;

    public static ArrayList<ByteBuf> getPackets() {
        return (ArrayList<ByteBuf>) PACKETS.clone();
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object value) {
        value = OpaqueByteBufHolder.unpack(value);
        if (value instanceof ByteBuf byteBuf) {
            NetworkPhase phase = getNetworkPhase(context);
            if (phase == NetworkPhase.PLAY) {
                if (packetCount <= 2) {
                    System.out.print(getNetworkPhase(context) + ": ");
                    //            System.out.println(byteBuf.readableBytes());
                    for (int i = 0; i < byteBuf.readableBytes(); i++) {
                        System.out.printf("%02X ", byteBuf.getByte(i));
                    }
                    packetCount++;
                }
                PACKETS.add(byteBuf.copy());
            }
        }

        context.fireChannelRead(value);
    }

    private NetworkPhase getNetworkPhase(ChannelHandlerContext context) {
        DecoderHandler decoderHandler = context.channel().pipeline().get(DecoderHandler.class);

        if (decoderHandler == null) {
            return NetworkPhase.LOGIN;
        }
        return decoderHandler.state.id();
    }
}
