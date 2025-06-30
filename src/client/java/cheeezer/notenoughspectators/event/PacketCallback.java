package cheeezer.notenoughspectators.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.packet.Packet;

public interface PacketCallback {
    Event<PacketCallback> EVENT = EventFactory.createArrayBacked(PacketCallback.class, listeners -> (packet) -> {
        for (PacketCallback listener : listeners) {
            listener.onPacketReceived(packet);
        }
    });

    void onPacketReceived(Packet<?> packet);
}

