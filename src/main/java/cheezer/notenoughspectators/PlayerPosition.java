package cheezer.notenoughspectators;

public class PlayerPosition {
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