package net.calca.biomesofcataclysms.mixin;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerLevelRandomTickMixin {

    /**
     * Attraverso questo mixin io faccio:
     * Se il blocco è naturalmente isRandomlyTicking allora return true;
     * Se non è isRandomlyTicking allora:
     *  Se è foglia allora:
     *      se la partita è attiva e siamo in ETERNAL ECLIPSE:
     *          ritorna true;
     * Se non è isRandomlyTicking e non è nemmeno foglia, allora return false (è un blocco che non deve tickare).
     */
    @Redirect(
            method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isRandomlyTicking()Z")
    )
    private boolean onCheckIsRandomlyTicking(BlockState state, LevelChunk chunk, int randomTickSpeed) {
        // 1. Se per Minecraft il blocco deve già subire il tick normalmente, ritorniamo true ed evitiamo calcoli extra
        if (state.isRandomlyTicking()) {
            return true;
        }

        // 2. Se il blocco estratto a caso dal chunk è una foglia sana (che normalmente restituirebbe false)
        if (state.getBlock() instanceof LeavesBlock) {
            ServerLevel level = (ServerLevel) (Object) this;

            // 3. Controlliamo se il cataclisma dell'eclissi è attivo nel mondo
            return ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE);
        }

        // Se non è una foglia coinvolta nel cataclisma e l'originale era false, rimane false.
        return false;
    }
}