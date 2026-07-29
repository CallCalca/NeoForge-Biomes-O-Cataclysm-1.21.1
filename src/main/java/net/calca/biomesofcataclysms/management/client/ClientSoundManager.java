package net.calca.biomesofcataclysms.management.client;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint.clientData;

public class ClientSoundManager {
    public static void tick(ClientTickEvent.Post event){

        //Se non è ETERNAL ECLIPSE, non si attiva
        if (!(ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE)) return;

        boolean shouldPlayEnteringBloodMoon = false;
        boolean shouldPlayExitingBloodMoon = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            //Se il giocatore è attualmente dentro ad un bioma colpito dall'Eclisse, e la bloodMoonEvent è attivo, allora play the sound Entering
                if ((clientData.mapData.deletedBiomes.contains(ModUtils.getBiomeID(mc.level, mc.player.getOnPos()))
                        && EternalEclipseStage.isBloodMoonEventActive(clientData.skyData.bloodMoonEvents)
                        && !clientData.soundData.wasInBloodMoonEvent)){
                    shouldPlayEnteringBloodMoon = true;
                    clientData.soundData.wasInBloodMoonEvent = true;
            //Altrimenti se è stato suonato in precedenza il suono Entering, e rather Non c'è più l'evento di bloodMoon Attivo, o
                    //il giocatore non è piu in bioma cancellato, suona il suono Exiting
                }else if (clientData.soundData.wasInBloodMoonEvent
                        && (!EternalEclipseStage.isBloodMoonEventActive(clientData.skyData.bloodMoonEvents)
                        || !clientData.mapData.deletedBiomes.contains(ModUtils.getBiomeID(mc.level, mc.player.getOnPos())))){
                    shouldPlayExitingBloodMoon = true;
                    clientData.soundData.wasInBloodMoonEvent = false;
            }

            if (shouldPlayEnteringBloodMoon){
                if (clientData.soundData.delaySound1 <= 0){
                    playEnteringBloodMoonSound();
                    clientData.soundData.delaySound1 = 300;
                    shouldPlayEnteringBloodMoon = false;
                }
            }
            if (shouldPlayExitingBloodMoon){
                if (clientData.soundData.delaySound2 <= 0){
                    playExitingBloodMoonSound();
                    clientData.soundData.delaySound2 = 80;
                    shouldPlayExitingBloodMoon = false;
                }
            }

            clientData.soundData.delaySound1--;
            clientData.soundData.delaySound2--;

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
