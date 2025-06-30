package cheeezer.notenoughspectators.event;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface RawPacketCallback {
    Event<RawPacketCallback> EVENT = EventFactory.createArrayBacked(RawPacketCallback.class, listeners -> (buf) -> {
        for (RawPacketCallback listener : listeners) {
            listener.onPacketReceived(buf);
        }
    });

    void onPacketReceived(ByteBuf buf);
}
