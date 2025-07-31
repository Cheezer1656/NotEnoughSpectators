package cheezer.notenoughspectators.mixin;

import net.minecraft.network.play.server.S46PacketSetCompressionLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S46PacketSetCompressionLevel.class)
public interface AccessorS46PacketSetCompressionLevel {
    @Accessor("threshold")
    void setThreshold(int threshold);
}
