package net.calca.biomesofcataclysms.mixin.enable;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class) // In 1.21.1 il metodo si trova nella classe base del BlockState
public class TorchRandomTickEnablerMixin {

    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
    private void forceTorchTicking(CallbackInfoReturnable<Boolean> cir) {
        // Se il blocco è una torcia, diciamo al gioco: "Sì, questo blocco deve tickare a caso"
        if (((BlockBehaviour.BlockStateBase) (Object) this).getBlock() instanceof TorchBlock) {
            cir.setReturnValue(true);
        }
    }
}