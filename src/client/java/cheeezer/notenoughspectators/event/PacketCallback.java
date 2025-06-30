package cheeezer.notenoughspectators.event;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface PacketCallback {
    Event<PacketCallback> EVENT = EventFactory.createArrayBacked(PacketCallback.class, listeners -> (buf) -> {
        for (PacketCallback listener : listeners) {
            listener.onPacketReceived(buf);
        }
    });

    void onPacketReceived(ByteBuf buf);
}
