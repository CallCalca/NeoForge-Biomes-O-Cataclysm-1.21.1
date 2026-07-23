package net.calca.biomesofcataclysms.mixin.eternalEclipse;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MushroomStemRandomTickStateMixin {
    @Shadow
    public abstract Block getBlock();

    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
    private void boc$forceMushroomStemTick(CallbackInfoReturnable<Boolean> cir) {
        // Se il blocco in questione è il gambo del fungo, costringiamo il gioco a farlo ticcare
        if (this.getBlock() == Blocks.MUSHROOM_STEM) {
            cir.setReturnValue(true);
        }
    }
}