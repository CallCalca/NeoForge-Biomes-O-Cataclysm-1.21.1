package net.calca.biomesofcataclysms.management.client;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint.clientData;

public class ClientSoundManager {
    public static void tick(ClientTickEvent.Post event){
        ClientDataAccessPoint.SoundData soundData = clientData.soundData;

        //Se non è ETERNAL ECLIPSE, non si attiva
        if ((ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE)){
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                //Se il giocatore è attualmente dentro ad un bioma colpito dall'Eclisse, e la bloodMoonEvent è attivo, allora play the sound Entering
                if ((clientData.mapData.deletedBiomes.contains(ModUtils.getBiomeID(mc.level, mc.player.getOnPos()))
                        && EternalEclipseStage.isBloodMoonEventActive(clientData.skyData.bloodMoonEvents)
                        && !soundData.wasInBloodMoonEvent)){
                    soundData.enteringBloodMoonSound = true;
                    soundData.wasInBloodMoonEvent = true;
                    //Altrimenti se è stato suonato in precedenza il suono Entering, e rather Non c'è più l'evento di bloodMoon Attivo, o
                    //il giocatore non è piu in bioma cancellato, suona il suono Exiting
                }else if (soundData.wasInBloodMoonEvent
                        && (!EternalEclipseStage.isBloodMoonEventActive(clientData.skyData.bloodMoonEvents)
                        || !clientData.mapData.deletedBiomes.contains(ModUtils.getBiomeID(mc.level, mc.player.getOnPos())))){
                    soundData.exitingBloodMoonSound = true;
                    soundData.wasInBloodMoonEvent = false;
                }

                if (soundData.enteringBloodMoonSound){
                    if (soundData.delayEnteringBloodMoonSound <= 0){
                        playEnteringBloodMoonSound();
                        soundData.delayEnteringBloodMoonSound = 300;
                        soundData.enteringBloodMoonSound = false;
                    }
                }
                if (soundData.exitingBloodMoonSound){
                    if (soundData.delayExitingBloodMoonSound <= 0){
                        playExitingBloodMoonSound();
                        soundData.delayExitingBloodMoonSound = 80;
                        soundData.exitingBloodMoonSound = false;
                    }
                }
                soundData.delayEnteringBloodMoonSound--;
                soundData.delayExitingBloodMoonSound--;
            }
        }
        
            if (soundData.noteBlockSound > 0){
                playNoteBlockSound(soundData.noteBlockSound);
                soundData.noteBlockSound = 0;
            }
            if (soundData.witherSpawnSound > 0){
                playWitherSpawnSound(soundData.witherSpawnSound);
                soundData.witherSpawnSound = 0;
            }
            if (soundData.witherDeathSound > 0){
                playWitherDeathSound(soundData.witherDeathSound);
                soundData.witherDeathSound = 0;
            }
            if (soundData.bellResonateSound > 0){
                playBellResonateSound(soundData.bellResonateSound);
                soundData.bellResonateSound = 0;
            }
            if (soundData.beaconDeactivateSound > 0){
                playBeaconDeactivateSound(soundData.beaconDeactivateSound);
                soundData.beaconDeactivateSound = 0;
            }
            if (soundData.comparatorClickSound > 0){
                playComparatorClickSound(soundData.comparatorClickSound);
                soundData.comparatorClickSound = 0;
            }


    }

    private static void playEnteringBloodMoonSound() {
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
    private static void playExitingBloodMoonSound() {
        if (FMLEnvironment.dist == Dist.CLIENT) {

            SimpleSoundInstance sound = SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_DIG,          // Suono di uscita
                    0.8F,                               // Pitch
                    1.0F                                // Volume
            );

            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    private static void playNoteBlockSound(float pitch){
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, 1.0F)
        );
    }
    private static void playWitherSpawnSound(float pitch){
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.WITHER_SPAWN, pitch, 0.3F)
        );
    }
    private static void playWitherDeathSound(float pitch){
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.WITHER_DEATH, pitch, 0.3F)
        );
    }
    private static void playBellResonateSound(float pitch){
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.WITHER_SPAWN, pitch, 1.0F)
        );
    }
    private static void playBeaconDeactivateSound(float pitch){
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, pitch, 1.0F)
        );
    }
    private static void playComparatorClickSound(float pitch){
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.COMPARATOR_CLICK, pitch, 0.6F)
        );
    }

}
