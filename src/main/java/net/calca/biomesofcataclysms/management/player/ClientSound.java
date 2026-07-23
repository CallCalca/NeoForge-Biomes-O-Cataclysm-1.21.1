package net.calca.biomesofcataclysms.management.player;

import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.eternaleclipse.EternalEclipseStage;
import net.calca.biomesofcataclysms.management.server.tmep.MoonController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public class ClientSound {

    public static void logOut(){
        bloodMoonEvents = 0;
        delaySound1 = 0;
        delaySound2 = 0;
    }

    private static int bloodMoonEvents = 0;
    private static boolean wasInBloodMoonEvent = false;
    private static int delaySound1 = 0; // Il suono di quando si entra in un bioma con luna di sangue
    private static int delaySound2 = 0; // Il suyono di quando si esce da un bioma con luna di sangue
    public static void tick(ClientTickEvent.Post event){
        boolean shouldPlayEnteringBloodMoon = false;
        boolean shouldPlayExitingBloodMoon = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
                if ((bloodMoonEvents < MoonController.bloodMoonEvents && EternalEclipseStage.isBloodMoonEventActive(MoonController.bloodMoonEvents))){
                    shouldPlayEnteringBloodMoon = true;
                    bloodMoonEvents = MoonController.bloodMoonEvents;
                    wasInBloodMoonEvent = true;
                }else if ((bloodMoonEvents > MoonController.bloodMoonEvents && EternalEclipseStage.isBloodMoonEventActive(MoonController.bloodMoonEvents)) || (wasInBloodMoonEvent && !EternalEclipseStage.isBloodMoonEventActive(MoonController.bloodMoonEvents))){
                    shouldPlayExitingBloodMoon = true;
                    bloodMoonEvents = MoonController.bloodMoonEvents;
                    wasInBloodMoonEvent = false;
            }

            if (shouldPlayEnteringBloodMoon){
                if (delaySound1 <= 0){
                    playEnteringBloodMoonSound();
                    delaySound1 = 300;
                }
            }
            if (shouldPlayExitingBloodMoon){
                if (delaySound2 <= 0){
                    playExitingBloodMoonSound();
                    delaySound2 = 80;
                }
            }

            delaySound1--;
            delaySound2--;

        }

    }

    public static void playEnteringBloodMoonSound() {
        // Verifica di sicurezza per eseguire il codice solo lato Client
        if (FMLEnvironment.dist == Dist.CLIENT) {

            // forUI crea un suono 2D non spaziale (senza coordinate X, Y, Z).
            // Verrà riprodotto direttamente nelle cuffie/casse del giocatore.
            SimpleSoundInstance sound = SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_EMERGE, // Suono cupo
                    0.3F,                               // Pitch (tonalità grave)
                    1.5F                                // Volume
            );

            // Invia il suono al SoundManager di Minecraft
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    public static void playExitingBloodMoonSound() {
        if (FMLEnvironment.dist == Dist.CLIENT) {

            SimpleSoundInstance sound = SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_DIG,          // Suono di uscita
                    0.8F,                               // Pitch
                    1.0F                                // Volume
            );

            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

}
