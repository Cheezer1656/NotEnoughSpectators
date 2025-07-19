package cheezer.notenoughspectators;

import com.google.common.collect.Queues;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Queue;
import java.util.function.Consumer;

public class PlayerTaskQueue {
    private static final Queue<Consumer<EntityPlayer>> TASK_QUEUE = Queues.newConcurrentLinkedQueue();
    private static final Queue<Consumer<PlayerPosition>> POSITION_TASK_QUEUE = Queues.newConcurrentLinkedQueue();

    public static void addTask(Consumer<EntityPlayer> task) {
        TASK_QUEUE.add(task);
    }

    public static void addPositionTask(Consumer<PlayerPosition> task) {
        POSITION_TASK_QUEUE.add(task);
    }

    public static void processTasks(EntityPlayer player) {
        while (!TASK_QUEUE.isEmpty()) {
            Consumer<EntityPlayer> task = TASK_QUEUE.poll();
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

    public static class PlayerPosition {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        public PlayerPosition(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}