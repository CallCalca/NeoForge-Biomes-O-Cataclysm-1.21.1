package net.calca.biomesofcataclysms.data.client;

import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientDataAccessPoint {

    // ==========================================
    // INSTANZE DELLE SOTTOCLASSI
    // ==========================================
    public MapData mapData = new MapData();
    public DayTimeData dayTimeData = new DayTimeData();
    public SkyData skyData = new SkyData();
    public MoonIconManagerData moonIconData = new MoonIconManagerData();
    public SoundData soundData = new SoundData();

    // Contenitore globale
    public static ClientDataAccessPoint clientData = new ClientDataAccessPoint();


    public static class MapData {
        public int state = 0;
        public String cataclysm = "NULL";
        public Set<String> deletedBiomes = new HashSet<>();
        public Map<String, Integer> eternalEclipseBloodMoonEvents = new HashMap<>();
    }

    public static class DayTimeData {
        public float transitionProgress = 0.0f;
        public long startingTime = -1L;
        public long targetTime = 18000L;
        public float shortestDistance = 0.0f;
        public int errorDelay = 60;
    }

    public static class MoonIconManagerData{
        public String lastBiomeId = "";
        public int bloodMoonEvents = 0;
    }


    public static class SkyData {
        public boolean isDirty = false;

        public boolean isInDeletedBiome = false;
        public long serverDayTime = 0L;
        public int bloodMoonEvents = 0;

        public float TargetScale = 1.0F;
        public float TargetRed = 1.0F;
        public float TargetGreen = 1.0F;
        public float TargetBlue = 1.0F;
        public float TargetAlpha = 1.0F;
        public Integer lunarPhase = 4;

        public float scale = 1.0F;
        public float red = 1.0F;
        public float green = 1.0F;
        public float blue = 1.0F;
        public float alpha = 1.0F;

        // --- LUNA 2 ---
        public boolean isSecondMoonActive = true;
        public float TargetSecondScale = 1.0F;
        public float TargetSecondRed = 1.0F;
        public float TargetSecondGreen = 1.0F;
        public float TargetSecondBlue = 1.0F;
        public float TargetSecondAlpha = 1.0F;
        public Integer secondLunarPhase = 0;

        public float secondScale = 1.0F;
        public float secondRed = 1.0F;
        public float secondGreen = 1.0F;
        public float secondBlue = 1.0F;
        public float secondAlpha = 1.0F;

        public float secondMoonAngleOffset = 180.0F;
        public float secondMoonSpeedMultiplier = 1.0F;
        public float secondMoonOrbitInclination = 0.0F;

        public ResourceLocation secondMoonTexture = ResourceLocation.parse("minecraft:textures/environment/moon_phases.png");
    }

    public static class SoundData{
        public boolean wasInBloodMoonEvent = false;
        public boolean enteringBloodMoonSound = false;
        public boolean exitingBloodMoonSound = false;
        public int delayEnteringBloodMoonSound = 0; // Il suono di quando si entra in un bioma con luna di sangue
        public int delayExitingBloodMoonSound = 0; // Il suono di quando si esce da un bioma con luna di sangue

        //Impostando un numero diverso da 0, si attiverà il suono noteBlock. Il pitch del suono è impostato a 0.x, dove
        //x = valore di questa variabile.
        public float noteBlockSound = 0;
        public float witherSpawnSound = 0;
        public float witherDeathSound = 0;
        public float bellResonateSound = 0;

        public float beaconDeactivateSound = 0;
        public float comparatorClickSound = 0;
    }

    // ==========================================
    // METODI DI RESET
    // ==========================================
    public static void onClientLogOut() {
        clientData = new ClientDataAccessPoint(); // Ora questo RESETTA DAVVERO TUTTO!
    }

    public static void onClientLogIn() {
        clientData = new ClientDataAccessPoint(); //Nuovi dati resettati
        //Recupero istantaneo dei pacchetti
        PacketDistributor.sendToServer(new PersistentData.RequestDedicatedSyncMessage()); //DedicatedData
        PacketDistributor.sendToServer(new PersistentData.RequestBloodMoonEventsSyncMessage()); //Per MoonManager
        PacketDistributor.sendToServer(new PersistentData.RequestRealServerTimeMessage()); //Per MoonManager
        clientData.skyData.isDirty = true; //Per MoonManager
    }
}