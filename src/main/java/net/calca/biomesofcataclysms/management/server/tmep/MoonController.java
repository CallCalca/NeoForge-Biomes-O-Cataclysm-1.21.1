package net.calca.biomesofcataclysms.management.server.tmep;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.eternaleclipse.EternalEclipseStage;
import net.calca.biomesofcataclysms.management.player.ClientTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.network.PacketDistributor;

public class MoonController {
    public static boolean isInDeletedBiome = false;
    public static long realServerTime = 0L;
    public static int bloodMoonEvents = 0;
    public static boolean isDirty = false; //Viene utilizzato quando il pacchetto di eternalEclipseBloodMoonEvents viene inviato al client, segnalando
                                            //che è necessario aggiornare i valori dei dati.
    // ==========================================
    // LUNA 1 (Vanilla / Blood Moon)
    // ==========================================
    private static float TargetScale = 1.0F;
    private static float TargetRed = 1.0F;
    private static float TargetGreen = 1.0F;
    private static float TargetBlue = 1.0F;
    private static float TargetAlpha = 1.0F;
    public static Integer lunarPhase = 4; //Se messo a null, resta vanilla, Se -1, imposta i valori in base a quante lune di sangue

    public static float scale = 1.0F;
    public static float red = 1.0F;
    public static float green = 1.0F;
    public static float blue = 1.0F;
    public static float alpha = 1.0F;

    // ==========================================
    // LUNA 2 (Seconda Luna Indipendente)
    // ==========================================
    public static boolean isSecondMoonActive = true;
    private static float TargetSecondScale = 1.0F;
    private static float TargetSecondRed = 1.0F;
    private static float TargetSecondGreen = 1.0F;
    private static float TargetSecondBlue = 1.0F;
    private static float TargetSecondAlpha = 1.0F;
    public static Integer secondLunarPhase = 0;

    public static float secondScale = 1.0F;
    public static float secondRed = 1.0F;
    public static float secondGreen = 1.0F;
    public static float secondBlue = 1.0F;
    public static float secondAlpha = 1.0F;

    // --- CONTROLLI ORBITALI DELLA SECONDA LUNA ---
    public static float secondMoonAngleOffset = 180.0F;
    public static float secondMoonSpeedMultiplier = 1.0F;
    public static float secondMoonOrbitInclination = 0.0F;

    public static ResourceLocation secondMoonTexture = ResourceLocation.parse("minecraft:textures/environment/moon_phases.png");


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
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.clientSide;
        if (globalVars == null) return;

        float progress = ClientTimeManager.transitionProgress;

        if (progress <= 0.0f) {
            bloodMoonEvents = 0;
        } else {
            String biomeID = ModUtils.getBiomeID(level, pos);
            if (globalVars.eternalEclipseBloodMoonEvents != null) {
                int currentBiomeEvents = globalVars.eternalEclipseBloodMoonEvents.getOrDefault(biomeID, 0);

                // Aggiorna la variabile globale solo se stiamo attivamente in un bioma con eventi.
                // In questo modo, uscendo dal bioma, manterrà il vecchio valore permettendo al lerp di sfumare.
                if (currentBiomeEvents > 0) {
                    bloodMoonEvents = currentBiomeEvents;
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
        if (lunarPhase == null){
            long totalDays = realServerTime / 24000L;
            lunarPhase = (int) (totalDays % 8L); // Rimesso dinamico secondo l'orologio reale
        } else {
            if (bloodMoonEvents == 0) {
                lunarPhase = 4; // Nuova / Vuota
            } else if (bloodMoonEvents == 1) {
                lunarPhase = 5;
            } else if (bloodMoonEvents == 2) {
                lunarPhase = 6;
            } else if (bloodMoonEvents == 3) {
                lunarPhase = 7;
            } else {
                lunarPhase = 0; // Piena
            }
        }

        // --- GESTIONE LUNA 2 (Graduale) ---
        if (TargetSecondAlpha == -1.0F) TargetSecondAlpha = 0.0F;
    }

    public static void tick(){

        float targetScale = 1.0F + (bloodMoonEvents * 0.25F);
        float targetColorFactor = 1.0F;
        if (EternalEclipseStage.isBloodMoonEventActive(bloodMoonEvents)){
            targetColorFactor = 1.0F - (bloodMoonEvents * 0.25F);
        }

        // 2. Interpoliamo partendo dai valori Vanilla (1.0F) fino ai target basandoci sul progresso
        TargetScale = targetScale;
        TargetGreen = targetColorFactor;
        TargetBlue = targetColorFactor;

        //--------

        float step = 0.005F; // Il tuo step massimo per tick

        scale = approach(scale, TargetScale, step);
        red = approach(red, TargetRed, step);
        green = approach(green, TargetGreen, step);
        blue = approach(blue, TargetBlue, step);
        alpha = approach(alpha, TargetAlpha, step);

        secondScale = approach(secondScale, TargetSecondScale, step);
        secondRed = approach(secondRed, TargetSecondRed, step);
        secondGreen = approach(secondGreen, TargetSecondGreen, step);
        secondBlue = approach(secondBlue, TargetSecondBlue, step);

        if (isInDeletedBiome) {
            MoonController.updateMoonsColors();
        }

        if (TargetSecondAlpha != -1.0F){
            // La seconda luna compare dissolvendosi gradualmente (da alpha 0.0 a 0.85)
            long startTime = EternalEclipseStage.getMoonEventStartTime(EternalEclipseStage.getMoonEventTimeBaseOnBloodMoonEvents(bloodMoonEvents));
            long startAppearingTime = startTime - EternalEclipseStage.AROUND_TIME;

            long finishTime = EternalEclipseStage.getMoonEventFinishTime(EternalEclipseStage.getMoonEventTimeBaseOnBloodMoonEvents(bloodMoonEvents));
            long finishDisappearingTime = finishTime + EternalEclipseStage.AROUND_TIME;

            // 1. ISOLIAMO L'ORARIO DEL GIORNO CORRENTE (Valore sempre compreso tra 0L e 23999L)
            long timeOfDay = realServerTime % 24000L;

            // --- CALCOLO DI TARGET_SECOND_ALPHA (Versione Ottimizzata) ---
            if (timeOfDay < startAppearingTime || timeOfDay > finishDisappearingTime) {
                // Fuori dai limiti: invisibile
                TargetSecondAlpha = 0.0F;
            }
            else if (timeOfDay < startTime) {
                // L'IDE sa già che realServerTime è >= startAppearingTime!
                float numerator = (float)(timeOfDay - startAppearingTime);
                float denominator = (float)(startTime - startAppearingTime);
                TargetSecondAlpha = numerator / denominator;
            }
            else if (timeOfDay <= finishTime) {
                // L'IDE sa già che realServerTime è >= startTime!
                TargetSecondAlpha = 1.0F;
            }
            else {
                // Qui rimangono solo i tick tra finishTime e finishDisappearingTime.
                // Tutte le altre condizioni sono implicitamente vere!
                float numerator = (float)(finishDisappearingTime - timeOfDay);
                float denominator = (float)(finishDisappearingTime - finishTime);
                TargetSecondAlpha = numerator / denominator;
            }

            TargetSecondAlpha = Math.clamp(TargetSecondAlpha, 0.0F, 1.0F);
        }
        secondAlpha = approach(secondAlpha, TargetSecondAlpha, step);
    }

    public static void reset() {
        bloodMoonEvents = 0;

        TargetScale = 1.0F;
        TargetRed = 1.0F;
        TargetGreen = 1.0F;
        TargetBlue = 1.0F;
        TargetAlpha = 1.0F;
        lunarPhase = 4;

        TargetSecondScale = 1.0F;
        TargetSecondRed = 1.0F;
        TargetSecondGreen = 1.0F;
        TargetSecondBlue = 1.0F;
        TargetSecondAlpha = -1.0F; // Parte da invisibile al reset

    }
    public static void logOut() {
        isInDeletedBiome = false;
        bloodMoonEvents = 0;
        realServerTime = 0L;

        TargetScale = 1.0F;
        TargetRed = 1.0F;
        TargetGreen = 1.0F;
        TargetBlue = 1.0F;
        TargetAlpha = 1.0F;
        lunarPhase = 4;

        scale = 1.0F;
        red = 1.0F;
        green = 1.0F;
        blue = 1.0F;
        alpha = 1.0F;


        TargetSecondScale = 1.0F;
        TargetSecondRed = 1.0F;
        TargetSecondGreen = 1.0F;
        TargetSecondBlue = 1.0F;
        TargetSecondAlpha = -1.0F; // Parte da invisibile al reset
        secondLunarPhase = 0;

        secondScale = 1.0F;
        secondRed = 1.0F;
        secondGreen = 1.0F;
        secondBlue = 1.0F;
        secondAlpha = 1.0F;

        secondMoonAngleOffset = 180.0F;
        secondMoonSpeedMultiplier = 1.0F;
        secondMoonOrbitInclination = 0.0F;
    }
    public static void logIn(){
        PacketDistributor.sendToServer(new PersistentData.RequestBloodMoonEventsSyncMessage());
        PacketDistributor.sendToServer(new PersistentData.RequestRealServerTimeMessage());
        isDirty = true;
    }
}