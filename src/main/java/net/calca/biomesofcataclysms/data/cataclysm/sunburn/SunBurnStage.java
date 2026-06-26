package net.calca.biomesofcataclysms.data.cataclysm.sunburn;

public enum SunBurnStage {
    FIRE,   // 0-1 min
    BURNING,     // 1-2 min
    HOT,     // 2:00-2:30
    MELTING,    // 2:30-2:45
    INSTANT_TRANSFORM; //2:45+

    public static SunBurnStage getSunBurnStage(long elapsedTicks) {
        if (elapsedTicks < 20L * 60L) return SunBurnStage.FIRE; //Dura 60 secondi
        if (elapsedTicks < 20L * 105L) return SunBurnStage.BURNING; //Dura altri 45
        if (elapsedTicks < 20L * 180L) return SunBurnStage.HOT; //Dura 75
        if (elapsedTicks < 20L * 270L) return SunBurnStage.MELTING; // Final dura 90 secondi
        return SunBurnStage.INSTANT_TRANSFORM; // Dopo 165 secondi totali
    }
}