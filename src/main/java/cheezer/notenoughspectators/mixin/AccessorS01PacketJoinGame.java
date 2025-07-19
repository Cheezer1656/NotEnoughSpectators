package cheezer.notenoughspectators.mixin;

import net.minecraft.network.play.server.S01PacketJoinGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S01PacketJoinGame.class)
public interface AccessorS01PacketJoinGame {
    @Accessor(value = "entityId")
    void setEntityId_notenoughspectators(int entityId);
}
