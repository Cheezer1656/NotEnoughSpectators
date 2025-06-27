package cheeezer.notenoughspectators.mixin.client;

import cheeezer.notenoughspectators.PacketSniffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.handler.NetworkStateTransitionHandler;
import net.minecraft.network.handler.PacketException;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.state.NetworkState;
import net.minecraft.util.profiling.jfr.FlightProfiler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.List;

@Mixin(DecoderHandler.class)
public class DecoderHandlerMixin<T extends PacketListener> {
//    @Shadow @Final public NetworkState<?> state;
//
//    @Shadow @Final private static Logger LOGGER;
//
//    @Inject(method = "<init>", at = @At("TAIL"))
//    public void init(NetworkState<?> state, CallbackInfo ci) {
//        System.out.println("DecoderHandlerMixin initialized with state: " + state);
//    }
//
//    @Inject(method = "decode", at = @At("HEAD"), cancellable = true)
//    protected void decode(ChannelHandlerContext context, ByteBuf buf, List<Object> objects, CallbackInfo ci) throws Exception {
//        int i = buf.readableBytes();
//        if (i != 0) {
//            Packet<? super T> packet;
//            try {
//                packet = (Packet<? super T>) this.state.codec().decode(buf);
//            } catch (Exception var7) {
//                if (var7 instanceof PacketException) {
//                    buf.skipBytes(buf.readableBytes());
//                }
//
//                throw var7;
//            }
//
//            PacketType<? extends Packet<? super T>> packetType = packet.getPacketType();
//            FlightProfiler.INSTANCE.onPacketReceived(this.state.id(), packetType, context.channel().remoteAddress(), i);
//            if (buf.readableBytes() > 0) {
//                throw new IOException(
//                        "Packet "
//                                + this.state.id().getId()
//                                + "/"
//                                + packetType
//                                + " ("
//                                + packet.getClass().getSimpleName()
//                                + ") was larger than I expected, found "
//                                + buf.readableBytes()
//                                + " bytes extra whilst reading packet "
//                                + packetType
//                );
//            } else {
//                objects.add(packet);
//                System.out.println("Done with packet: " + packet.getClass().getSimpleName());
//                if (state.id() == NetworkPhase.PLAY && packetType.side() == NetworkSide.CLIENTBOUND) {
//                    PacketSniffer.decoderCalls++;
//                };
//                if (LOGGER.isDebugEnabled()) {
//                    LOGGER.debug(ClientConnection.PACKET_RECEIVED_MARKER, " IN: [{}:{}] {} -> {} bytes", this.state.id().getId(), packetType, packet.getClass().getName(), i);
//                }
//
//                NetworkStateTransitionHandler.onDecoded(context, packet);
//            }
//        }
//        ci.cancel();
//    }
}
