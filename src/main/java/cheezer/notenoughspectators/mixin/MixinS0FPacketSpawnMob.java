package cheezer.notenoughspectators.mixin;

import net.minecraft.entity.DataWatcher;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(S0FPacketSpawnMob.class)
public class MixinS0FPacketSpawnMob {
    @Shadow private List<DataWatcher.WatchableObject> watcher;

    @Redirect(method = "writePacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/DataWatcher;writeTo(Lnet/minecraft/network/PacketBuffer;)V"))
    private void writePacketData(DataWatcher watcher, PacketBuffer buf) throws Exception {
        if (watcher == null) {
            System.out.println("Wachte is  null;");
            for (DataWatcher.WatchableObject watchableObject : this.watcher) {
                java.lang.reflect.Method method = DataWatcher.class.getDeclaredMethod("writeWatchableObjectToPacketBuffer", PacketBuffer.class, DataWatcher.WatchableObject.class);
                method.setAccessible(true);
                method.invoke(null, buf, watchableObject);
            }
            buf.writeByte(127);
        } else {
            watcher.writeTo(buf);
        }
    }
}
