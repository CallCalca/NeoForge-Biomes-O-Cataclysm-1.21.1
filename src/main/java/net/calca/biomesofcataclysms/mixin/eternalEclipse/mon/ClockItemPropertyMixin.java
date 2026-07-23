package net.calca.biomesofcataclysms.mixin.eternalEclipse.mon;

import net.calca.biomesofcataclysms.data.cataclysm.eternaleclipse.EternalEclipseStage;
import net.calca.biomesofcataclysms.management.server.tmep.MoonController;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "net.minecraft.client.renderer.item.ItemProperties$1")
public class ClockItemPropertyMixin {

    // Cambiato method da "call" a "unclampedCall"
    @Inject(method = "unclampedCall", at = @At("HEAD"), cancellable = true)
    private void overrideClockTime(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, CallbackInfoReturnable<Float> cir) {
        if (EternalEclipseStage.isBloodMoonEventActive(MoonController.bloodMoonEvents)) {

            long timeOfDay = MoonController.realServerTime % 24000L;

            // 1. Calcoliamo la frazione grezza
            double dayFraction = timeOfDay / 24000.0D;

            // 2. Applichiamo l'offset di -0.25 usato da Minecraft Vanilla
            double clockAngle = dayFraction - 0.25D;

            // 3. Se il valore va in negativo (es. all'alba), aggiungiamo 1.0 per mantenerlo tra 0.0 e 1.0
            if (clockAngle < 0.0D) {
                clockAngle += 1.0D;
            }

            cir.setReturnValue((float) clockAngle);
        }
    }

}