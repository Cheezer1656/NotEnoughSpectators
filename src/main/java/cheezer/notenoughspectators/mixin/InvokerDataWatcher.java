package cheezer.notenoughspectators.mixin;

import net.minecraft.entity.DataWatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DataWatcher.class)
public interface InvokerDataWatcher {
    @Invoker(value = "writeWatchableObjectToPacketBuffer")
    static void invokeWriteWatchableObjectToPacketBuffer_notenoughspectators(net.minecraft.network.PacketBuffer buf, DataWatcher.WatchableObject watchableObject) {
        throw new UnsupportedOperationException("This method should be invoked via Mixin");
    }
}
