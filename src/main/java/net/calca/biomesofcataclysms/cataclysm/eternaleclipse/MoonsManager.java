package net.calca.biomesofcataclysms.cataclysm.eternaleclipse;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.network.PacketDistributor;

import static net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint.clientData;

public class MoonsManager {

    // ==========================================
    // LUNA 1 (Vanilla / Blood Moon)
    // ==========================================

    private static float approach(float current, float target, float maxStep) {
        float difference = target - current;
        if (Math.abs(difference) <= maxStep) {
            return target;
        }
        return current + Math.signum(difference) * maxStep;
    }

    /**
     * Aggiorna esclusivamente i dati degli eventi basandosi sulla posizione del giocatore.
     * Mantiene in memoria l'ultimo valore registrato durante la transizione di uscita.
     */
    public static void updateBloodMoonEvents(LevelAccessor level, BlockPos pos) {
        float progress = clientData.dayTimeData.transitionProgress;

        if (progress <= 0.0f) {
            clientData.skyData.bloodMoonEvents = 0;
        } else {
            String biomeID = ModUtils.getBiomeID(level, pos);
            if (clientData.mapData.eternalEclipseBloodMoonEvents != null) {
                int currentBiomeEvents = clientData.mapData.eternalEclipseBloodMoonEvents.getOrDefault(biomeID, 0);

                // Aggiorna la variabile globale solo se stiamo attivamente in un bioma con eventi.
                // In questo modo, uscendo dal bioma, manterrà il vecchio valore permettendo al lerp di sfumare.
                if (currentBiomeEvents > 0) {
                    clientData.skyData.bloodMoonEvents = currentBiomeEvents;
                }
            }
        }
    }

    /**
     * Calcola i colori e i parametri visibili della luna basandosi SOLO sul progresso corrente.
     * Può essere chiamato in autonomia senza richiedere posizioni o accessi al livello.
     */
    public static void updateMoonsColors() {

        // --- GESTIONE LUNA 1 (Graduale) ---
        // 1. Calcoliamo i target finali ad evento massimo
        // Gestione fasi (Cambiano solo quando l'effetto è visibile)
        if (clientData.skyData.lunarPhase == null){
            long totalDays = clientData.skyData.serverDayTime / 24000L;
            clientData.skyData.lunarPhase = (int) (totalDays % 8L); // Rimesso dinamico secondo l'orologio reale
        } else {
            if (clientData.skyData.bloodMoonEvents == 0) {
                clientData.skyData.lunarPhase = 4; // Nuova / Vuota
            } else if (clientData.skyData.bloodMoonEvents == 1) {
                clientData.skyData.lunarPhase = 5;
            } else if (clientData.skyData.bloodMoonEvents == 2) {
                clientData.skyData.lunarPhase = 6;
            } else if (clientData.skyData.bloodMoonEvents == 3) {
                clientData.skyData.lunarPhase = 7;
            } else {
                clientData.skyData.lunarPhase = 0; // Piena
            }
        }

        // --- GESTIONE LUNA 2 (Graduale) ---
        if (clientData.skyData.TargetSecondAlpha == -1.0F) clientData.skyData.TargetSecondAlpha = 0.0F;
    }

    public static void tick(){
        //Se non è ETERNAL ECLIPSE, non si attiva
        if (!(ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE)) return;

        float targetScale = 1.0F + (clientData.skyData.bloodMoonEvents * 0.25F);
        float targetColorFactor = 1.0F;
        if (EternalEclipseStage.isBloodMoonEventActive(clientData.skyData.bloodMoonEvents)){
            targetColorFactor = 1.0F - (clientData.skyData.bloodMoonEvents * 0.25F);
        }

        // 2. Interpoliamo partendo dai valori Vanilla (1.0F) fino ai target basandoci sul progresso
        clientData.skyData.TargetScale = targetScale;
        clientData.skyData.TargetGreen = targetColorFactor;
        clientData.skyData.TargetBlue = targetColorFactor;

        //--------

        float step = 0.005F; // Il tuo step massimo per tick

        clientData.skyData.scale = approach(clientData.skyData.scale, clientData.skyData.TargetScale, step);
        clientData.skyData.red = approach(clientData.skyData.red, clientData.skyData.TargetRed, step);
        clientData.skyData.green = approach(clientData.skyData.green, clientData.skyData.TargetGreen, step);
        clientData.skyData.blue = approach(clientData.skyData.blue, clientData.skyData.TargetBlue, step);
        clientData.skyData.alpha = approach(clientData.skyData.alpha, clientData.skyData.TargetAlpha, step);

        clientData.skyData.secondScale = approach(clientData.skyData.secondScale, clientData.skyData.TargetSecondScale, step);
        clientData.skyData.secondRed = approach(clientData.skyData.secondRed, clientData.skyData.TargetSecondRed, step);
        clientData.skyData.secondGreen = approach(clientData.skyData.secondGreen, clientData.skyData.TargetSecondGreen, step);
        clientData.skyData.secondBlue = approach(clientData.skyData.secondBlue, clientData.skyData.TargetSecondBlue, step);

        if (clientData.skyData.isInDeletedBiome) {
            MoonsManager.updateMoonsColors();
        }

        if (clientData.skyData.TargetSecondAlpha != -1.0F){
            // La seconda luna compare dissolvendosi gradualmente (da alpha 0.0 a 0.85)
            long startTime = EternalEclipseStage.getMoonEventStartTime(EternalEclipseStage.getMoonEventTimeBaseOnBloodMoonEvents(clientData.skyData.bloodMoonEvents));
            long startAppearingTime = startTime - EternalEclipseStage.AROUND_TIME;

            long finishTime = EternalEclipseStage.getMoonEventFinishTime(EternalEclipseStage.getMoonEventTimeBaseOnBloodMoonEvents(clientData.skyData.bloodMoonEvents));
            long finishDisappearingTime = finishTime + EternalEclipseStage.AROUND_TIME;

            // 1. ISOLIAMO L'ORARIO DEL GIORNO CORRENTE (Valore sempre compreso tra 0L e 23999L)
            long timeOfDay = clientData.skyData.serverDayTime % 24000L;

            // --- CALCOLO DI TARGET_SECOND_ALPHA (Versione Ottimizzata) ---
            if (timeOfDay < startAppearingTime || timeOfDay > finishDisappearingTime) {
                // Fuori dai limiti: invisibile
                clientData.skyData.TargetSecondAlpha = 0.0F;
            }
            else if (timeOfDay < startTime) {
                // L'IDE sa già che realServerTime è >= startAppearingTime!
                float numerator = (float)(timeOfDay - startAppearingTime);
                float denominator = (float)(startTime - startAppearingTime);
                clientData.skyData.TargetSecondAlpha = numerator / denominator;
            }
            else if (timeOfDay <= finishTime) {
                // L'IDE sa già che realServerTime è >= startTime!
                clientData.skyData.TargetSecondAlpha = 1.0F;
            }
            else {
                // Qui rimangono solo i tick tra finishTime e finishDisappearingTime.
                // Tutte le altre condizioni sono implicitamente vere!
                float numerator = (float)(finishDisappearingTime - timeOfDay);
                float denominator = (float)(finishDisappearingTime - finishTime);
                clientData.skyData.TargetSecondAlpha = numerator / denominator;
            }

            clientData.skyData.TargetSecondAlpha = Math.clamp(clientData.skyData.TargetSecondAlpha, 0.0F, 1.0F);
        }
        clientData.skyData.secondAlpha = approach(clientData.skyData.secondAlpha, clientData.skyData.TargetSecondAlpha, step);

        Minecraft mc = Minecraft.getInstance();
        //Mentre il gioco è in esecuzione invia un pacchetto server -> client per refresher il realServerTime. Ogni 10 secondi.
        //Ogni secondo, invece, setta isDirti su true, così il sistema è costretto a refresher i propri parametri (il sistema di MoonManager).
        if (mc.player != null && mc.level != null) {
            if (mc.level.getGameTime() % 200 == 0){
                PacketDistributor.sendToServer(new PersistentData.RequestRealServerTimeMessage());
            } else if (mc.level.getGameTime() % 20 == 0) {
                clientData.skyData.isDirty = true;
            }
        }
        clientData.skyData.serverDayTime++;

    }

    public static void reset() {
        clientData.skyData.bloodMoonEvents = 0;

        clientData.skyData.TargetScale = 1.0F;
        clientData.skyData.TargetRed = 1.0F;
        clientData.skyData.TargetGreen = 1.0F;
        clientData.skyData.TargetBlue = 1.0F;
        clientData.skyData.TargetAlpha = 1.0F;
        clientData.skyData.lunarPhase = 4;

        clientData.skyData.TargetSecondScale = 1.0F;
        clientData.skyData.TargetSecondRed = 1.0F;
        clientData.skyData.TargetSecondGreen = 1.0F;
        clientData.skyData.TargetSecondBlue = 1.0F;
        clientData.skyData.TargetSecondAlpha = -1.0F; // Parte da invisibile al reset

    }
}