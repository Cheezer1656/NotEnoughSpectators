package cheeezer.notenoughspectators.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface MovementCallback {
    Event<MovementCallback> EVENT = EventFactory.createArrayBacked(MovementCallback.class, listeners -> (movementType) -> {
        for (MovementCallback listener : listeners) {
            listener.onMovementPacket(movementType);
        }
    });

    void onMovementPacket(MovementType movementType);

    enum MovementType {
        POSITION,
        ROTATION,
        POSITION_AND_ROTATION,
        UNKNOWN
    }
}
