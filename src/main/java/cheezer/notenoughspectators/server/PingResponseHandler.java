package cheezer.notenoughspectators.server;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PingResponseHandler
        extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getLogger();
    private final SpectatorServer spectatorServer;

    public PingResponseHandler(SpectatorServer networkSystem) {
        this.spectatorServer = networkSystem;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void channelRead(ChannelHandlerContext channelHandlerContext, Object object) throws Exception {
        ByteBuf byteBuf = (ByteBuf)object;
        byteBuf.markReaderIndex();
        boolean bl = true;
        try {
            if (byteBuf.readUnsignedByte() != 254) {
                return;
            }
            InetSocketAddress inetSocketAddress = (InetSocketAddress)channelHandlerContext.channel().remoteAddress();
            int i = byteBuf.readableBytes();
            switch (i) {
                case 0: {
                    logger.debug("Ping: (<1.3.x) from {}:{}", inetSocketAddress.getAddress(), inetSocketAddress.getPort());
                    String string = String.format("%s\u00a7%d\u00a7%d", spectatorServer.getMOTD(), spectatorServer.getCurrentPlayerCount(), spectatorServer.getMaxPlayers());
                    this.writeAndFlush(channelHandlerContext, this.getStringBuffer(string));
                    break;
                }
                case 1: {
                    if (byteBuf.readUnsignedByte() != 1) {
                        return;
                    }
                    logger.debug("Ping: (1.4-1.5.x) from {}:{}", inetSocketAddress.getAddress(), inetSocketAddress.getPort());
                    String string = String.format("\u00a71\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d", 127, spectatorServer.getMinecraftVersion(), spectatorServer.getMOTD(), spectatorServer.getCurrentPlayerCount(), spectatorServer.getMaxPlayers());
                    this.writeAndFlush(channelHandlerContext, this.getStringBuffer(string));
                    break;
                }
                default: {
                    boolean bl2 = byteBuf.readUnsignedByte() == 1;
                    bl2 &= byteBuf.readUnsignedByte() == 250;
                    bl2 &= "MC|PingHost".equals(new String(byteBuf.readBytes(byteBuf.readShort() * 2).array(), Charsets.UTF_16BE));
                    int j = byteBuf.readUnsignedShort();
                    bl2 &= byteBuf.readUnsignedByte() >= 73;
                    bl2 &= 3 + byteBuf.readBytes(byteBuf.readShort() * 2).array().length + 4 == j;
                    bl2 &= byteBuf.readInt() <= 65535;
                    if (!(bl2 &= byteBuf.readableBytes() == 0)) {
                        return;
                    }
                    logger.debug("Ping: (1.6) from {}:{}", inetSocketAddress.getAddress(), inetSocketAddress.getPort());
                    String string2 = String.format("\u00a71\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d", 127, spectatorServer.getMinecraftVersion(), spectatorServer.getMOTD(), spectatorServer.getCurrentPlayerCount(), spectatorServer.getMaxPlayers());
                    ByteBuf byteBuf2 = this.getStringBuffer(string2);
                    try {
                        this.writeAndFlush(channelHandlerContext, byteBuf2);
                        break;
                    }
                    finally {
                        byteBuf2.release();
                    }
                }
            }
            byteBuf.release();
            bl = false;
        }
        catch (RuntimeException runtimeException) {
        }
        finally {
            if (bl) {
                byteBuf.resetReaderIndex();
                channelHandlerContext.channel().pipeline().remove("legacy_query");
                channelHandlerContext.fireChannelRead(object);
            }
        }
    }

    private void writeAndFlush(ChannelHandlerContext ctx, ByteBuf data) {
        ctx.pipeline().firstContext().writeAndFlush(data).addListener(ChannelFutureListener.CLOSE);
    }

    private ByteBuf getStringBuffer(String string) {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeByte(255);
        char[] cs = string.toCharArray();
        byteBuf.writeShort(cs.length);
        for (char c : cs) {
            byteBuf.writeChar(c);
        }
        return byteBuf;
    }
}

