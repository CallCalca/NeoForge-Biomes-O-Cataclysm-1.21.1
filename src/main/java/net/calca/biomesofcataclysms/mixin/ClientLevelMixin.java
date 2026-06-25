package net.calca.biomesofcataclysms.mixin;

import net.calca.biomesofcataclysms.management.player.ClientTimeManager;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Miriamo alla classe interna statica
@Mixin(ClientLevel.ClientLevelData.class)
public class ClientLevelMixin {

    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void onGetDayTime(CallbackInfoReturnable<Long> cir) {
        long originalTime = cir.getReturnValue();
        cir.setReturnValue(ClientTimeManager.getRenderTime(originalTime));
    }
}