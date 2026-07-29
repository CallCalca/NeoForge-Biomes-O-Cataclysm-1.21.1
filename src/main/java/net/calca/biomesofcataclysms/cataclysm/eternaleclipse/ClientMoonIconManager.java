package net.calca.biomesofcataclysms.cataclysm.eternaleclipse;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint.clientData;

public class ClientMoonIconManager {

    public static void tick(ClientTickEvent.Post event){
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        //Se non è ETERNAL ECLIPSE, non si attiva
        if (!(ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE)) return;

        // Se il giocatore o il livello non sono ancora pronti (es. schermata di caricamento), non facciamo nulla
        if (player == null || level == null) {
            return;
        }

        BlockPos pos = player.blockPosition();
        String currentBiomeId = ModUtils.getBiomeID(level, pos);

        // AGGIORNA LA LUNA SOLO SE:
        // 1. Il giocatore ha camminato in un bioma diverso rispetto al tick precedente
        // 2. OPPURE è arrivato un pacchetto dal server che ha messo 'isDirty' a true
        if (!currentBiomeId.equals(clientData.moonIconData.lastBiomeId) || clientData.skyData.isDirty) {
            clientData.skyData.isDirty = false; // Resettiamo il flag

            // Eseguiamo il calcolo una volta sola per questo bioma!
            MoonsManager.reset();
            MoonsManager.updateBloodMoonEvents(level, pos);
        }

        // 1. Verifichiamo se il bioma è cambiato o se i dati sono "dirty"
        boolean biomeChanged = !currentBiomeId.equals(clientData.moonIconData.lastBiomeId);

        if (biomeChanged || clientData.skyData.isDirty) {
            clientData.skyData.isDirty = false; // Resettiamo il flag

            // Ricalcoliamo gli eventi per il bioma corrente
            MoonsManager.reset();
            MoonsManager.updateBloodMoonEvents(level, pos);
        }

        boolean isCurrentBiomeDeleted = clientData.mapData.deletedBiomes.contains(ModUtils.getBiomeID(level, pos));

        // 2. MOSTRA IL TITOLO SE:
        // - Il bioma corrente è tra quelli eliminati
        // - E (è appena cambiato il bioma OPPURE sono cambiati gli eventi BloodMoon)
        if (isCurrentBiomeDeleted && (biomeChanged || clientData.moonIconData.bloodMoonEvents != clientData.skyData.bloodMoonEvents)) {
            startBiomeEvent(level, player.getOnPos());
        }

        // 3. AGGIORNA SEMPRE LO STATO FINALE (Fondamentale!)
        // Aggiorniamo lastBiomeId ad OGNI cambio, altrimenti il tracciamento si rompe
        if (biomeChanged) {
            clientData.moonIconData.lastBiomeId = currentBiomeId;
        }
        clientData.moonIconData.bloodMoonEvents = clientData.skyData.bloodMoonEvents;

    }

    // Palette di colori in esadecimale (RGB): dal Bianco al Rosso Sangue
    private static final int[] MOON_COLORS = {
            0xFFFFFF, // 0/4 - Bianco
            0xFFB3B3, // 1/4 - Rosa/Rosso chiarissimo
            0xFF5555, // 2/4 - Rosso vivo
            0xAA0000, // 3/4 - Rosso scuro (&4)
            0x550000  // 4/4 - Rosso sangue profondo
    };

    private static final String[] MOON_PHASES = {
            "\uD83C\uDF15", // 0/4
            "\uD83C\uDF14", // 1/4
            "\uD83C\uDF13", // 2/4
            "\uD83C\uDF12", // 3/4
            "\uD83C\uDF11"  // 4/4
    };

    public static void startBiomeEvent(ClientLevel level, BlockPos playerPos) {
        int moonPhase = clientData.mapData.eternalEclipseBloodMoonEvents.getOrDefault(ModUtils.getBiomeID(level, playerPos), 0);

        // Timings del Title (fadeIn: 1s, stay: 3s, fadeOut: 1.5s)
        Minecraft.getInstance().gui.setTimes(20, 60, 30);

        updateBiomeTitle(moonPhase);
    }

    private static void updateBiomeTitle(int phaseIndex) {
        // Garantisce che l'indice sia sempre compreso tra 0 e 4
        int index = Math.min(Math.max(phaseIndex, 0), MOON_PHASES.length - 1);

        // Preleva il colore RGB corrispondente dalla palette
        int colorRgb = MOON_COLORS[index];

        // Applica il colore RGB personalizzato allo stile del componente
        MutableComponent moonIcon = Component.literal(MOON_PHASES[index])
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(colorRgb)));

        // Mostra il titolo a schermo
        Minecraft.getInstance().gui.setTitle(moonIcon);
    }
}
