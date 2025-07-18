package cheeezer.notenoughspectators;

import com.google.common.collect.Queues;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerPosition;

import java.util.Queue;
import java.util.function.Consumer;

public class PlayerTaskQueue {
    private static final Queue<Consumer<ClientPlayerEntity>> TASK_QUEUE = Queues.newConcurrentLinkedQueue();
    private static final Queue<Consumer<PlayerPosition>> POSITION_TASK_QUEUE = Queues.newConcurrentLinkedQueue();

    public static void addTask(Consumer<ClientPlayerEntity> task) {
        TASK_QUEUE.add(task);
    }

    public static void addPositionTask(Consumer<PlayerPosition> task) {
        POSITION_TASK_QUEUE.add(task);
    }

    public static void processTasks(ClientPlayerEntity player) {
        while (!TASK_QUEUE.isEmpty()) {
            Consumer<ClientPlayerEntity> task = TASK_QUEUE.poll();
            if (task != null) {
                task.accept(player);
            }
        }
    }

    public static void processPositionTasks(PlayerPosition position) {
        while (!POSITION_TASK_QUEUE.isEmpty()) {
            Consumer<PlayerPosition> task = POSITION_TASK_QUEUE.poll();
            if (task != null) {
                task.accept(position);
            }
        }
    }
}
