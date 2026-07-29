package net.calca.biomesofcataclysms.cataclysm.eternaleclipse;

import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.minecraft.server.level.ServerLevel;

import static net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint.clientData;

public enum EternalEclipseStage {
    START_DECAY,                // 0-4 min
    DEATH_OF_VEGETATION,        // 4-6 min
    START_SPREAD,               // 6-8 min
    MYCELIUM_SPREAD,            // 8-10 min
    FUNGUS_SPROUT,              // 10-14 min
    CAVE_GERMINATION;           // 14>
    // FASE 1: La vegetazione comincia a morire a causa del buio (la probabilità di morire cresce linearmente fino alla prossima fase).
    // FASE 2: La vegetazione muore come prima, ma ad un rate del 100%
    // FASE 3: quella che ora è dirt comincia a diventare mycelium (il rate fa 0% -> 100%).
    // FASE 4: stessa cosa della fase 3 ma con un rate del 100%
    // FASE 5: Cominciano a spuntare sul mycelio dei funghi e comincia a cresce. il rate di spawn e crescita aumenta da 0 a 100%.
    // FASE 6: ora comicniano a crescere le liane delle bacche luminose e le liane normali si trasformano in liane luminose.

    public final static Long START_DECAY_TICKS = 4800L;
    public final static Long DEATH_OF_VEGETATION_TICKS = 7200L;
    public final static Long START_SPREAD_TCKS = 9600L;
    public final static Long MYCELIUM_SPREAD_TICKS = 12000L;
    public final static Long FUNGUS_SPROUT_TICKS = 16800L;
    public final static Long CAVE_GERMINATION_TICKS = 99999L;

    private static final long MIDNIGHT_VALUE = 18000L;
    public static final long FIRST_BLOOD_MOON_EVENT = 340L;
    public static final long SECOND_BLOOD_MOON_EVENT = 360L;
    public static final long THIRD_BLOOD_MOON_EVENT = 400L;
    public static final long FOURTH_BLOOD_MOON_EVENT = 440L;
    public static final long AROUND_TIME = 800L; //Indica a quanto tempo di distanza la seconda luna comincia a diventare visibile.

    public static final float HUMID_ENOUGH_TO_SPAWN_FUNGUS = 0.5F;

    public static long getMoonEventStartTime(long moonEvent){
        return MIDNIGHT_VALUE - moonEvent;
    }
    public static long getMoonEventFinishTime(long moonEvent) {
        return MIDNIGHT_VALUE + moonEvent;
    }

    //Serve per controllare se il tempo inserito, quindi il tempo del gioco, sta per coincidere con il tempo di una luna di sangue.
    //Se è così la seconda luna comincia a prendere trasparenza.


    /**
     * Opera eslusivamente sul server
     * @param serverLevel
     * @param biomeID
     * @return
     */
    public static boolean isBloodMoonEventActiveOnBiome(ServerLevel serverLevel, String biomeID){
        long dayTime = Math.floorMod(serverLevel.getDayTime(), 24000L);
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(serverLevel);
        if (globalVars.eternalEclipseBloodMoonEvents.getOrDefault(biomeID, -1) == 0){
            return dayTime <= getMoonEventFinishTime(FIRST_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(FIRST_BLOOD_MOON_EVENT);

        } else if (globalVars.eternalEclipseBloodMoonEvents.getOrDefault(biomeID, -1) == 1) {
            return dayTime <= getMoonEventFinishTime(SECOND_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(SECOND_BLOOD_MOON_EVENT);

        } else if (globalVars.eternalEclipseBloodMoonEvents.getOrDefault(biomeID, -1) == 2) {
            return dayTime <= getMoonEventFinishTime(THIRD_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(THIRD_BLOOD_MOON_EVENT);

        } else if (globalVars.eternalEclipseBloodMoonEvents.getOrDefault(biomeID, -1) == 3) {
            return dayTime <= getMoonEventFinishTime(FOURTH_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(FOURTH_BLOOD_MOON_EVENT);

        } else return globalVars.eternalEclipseBloodMoonEvents.getOrDefault(biomeID, -1) == 4;
    }

    /**
     * Opera esclusivamente sul Client
     * @return
     */
    public static boolean isBloodMoonEventActive(int bloodMoonEvents){
        long dayTime = Math.floorMod(clientData.skyData.serverDayTime, 24000);
        if (bloodMoonEvents == 0){
            return dayTime <= getMoonEventFinishTime(FIRST_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(FIRST_BLOOD_MOON_EVENT);
        } else if (bloodMoonEvents == 1) {
            return dayTime <= getMoonEventFinishTime(SECOND_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(SECOND_BLOOD_MOON_EVENT);
        } else if (bloodMoonEvents == 2) {
            return dayTime <= getMoonEventFinishTime(THIRD_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(THIRD_BLOOD_MOON_EVENT);
        } else if (bloodMoonEvents == 3){
            return dayTime <= getMoonEventFinishTime(FOURTH_BLOOD_MOON_EVENT) && dayTime >= getMoonEventStartTime(FOURTH_BLOOD_MOON_EVENT);
        }else return bloodMoonEvents >= 4;
    }

    /**
     * Opera su client e server
     * @return
     */
    public static long getMoonEventTimeBaseOnBloodMoonEvents(int bloodMoonEvents){
        if (bloodMoonEvents == 0){
            return FIRST_BLOOD_MOON_EVENT;
        } else if (bloodMoonEvents == 1) {
            return SECOND_BLOOD_MOON_EVENT;
        } else if (bloodMoonEvents == 2) {
            return THIRD_BLOOD_MOON_EVENT;
        }else if (bloodMoonEvents == 3){
            return FOURTH_BLOOD_MOON_EVENT;
        }else{
            return FIRST_BLOOD_MOON_EVENT;
        }

    }



    public static EternalEclipseStage getEternalEclipseStage(long elapsedTicks) {
        if (elapsedTicks < 20L * 240L) return EternalEclipseStage.START_DECAY;
        if (elapsedTicks < 20L * 360L) return EternalEclipseStage.DEATH_OF_VEGETATION;
        if (elapsedTicks < 20L * 480L) return EternalEclipseStage.START_SPREAD;
        if (elapsedTicks < 20L * 600L) return EternalEclipseStage.MYCELIUM_SPREAD;
        if (elapsedTicks < 20L * 840L) return EternalEclipseStage.FUNGUS_SPROUT;
        return EternalEclipseStage.CAVE_GERMINATION;
    }
}