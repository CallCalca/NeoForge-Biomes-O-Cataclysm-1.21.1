package net.calca.biomesofcataclysms.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    /*

    // Questo "invoca" il metodo protetto getChunks() di Minecraft
    @Invoker("getChunks")
    Iterable<ChunkHolder> invokeGetChunks();
     */

}