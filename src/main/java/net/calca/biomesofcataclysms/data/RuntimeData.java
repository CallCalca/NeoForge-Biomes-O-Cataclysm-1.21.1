package net.calca.biomesofcataclysms.data;

import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.management.chunk.ChunkProcessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

//Questi dati sono i dati Runtime. Vengono letti, scritti e sovrascritti durante il corso della partita. Sono molto
//più leggeri da processare dei dati Persistent (quindi da leggere e da scrivere), ma vengono automaticamente resettate ogni volta
//che la sessione di gioco scade. Prima di lasciare il mondo, allora, questi dati vengono copiati in dei dati NBT in modo da
//divenire persistenti. I dati Persistent vengono letti all'avvio del mondo per sincronizzare i dati Runtime con il loro valore,
//mentre vengono scritti alla chiusura del mondo (o quando si mette la partita in pausa) in modo da aggiornare il proprio valore.
public class RuntimeData {
        public static final Map<ResourceKey<Level>, ArrayDeque<Long>> INITIAL_ORDER = new HashMap<>();
        public static final Map<ResourceKey<Level>, ArrayDeque<Long>> DYNAMIC_ORDER = new HashMap<>();
        public static final Map<ResourceKey<Level>, ChunkProcessor.DimensionState> INITIAL_STATES = new HashMap<>();
        public static final Map<ResourceKey<Level>, ChunkProcessor.DimensionState> DYNAMIC_STATES = new HashMap<>();
        public static final Map<ResourceKey<Level>, Map<Long, ChunkInstance>> CHUNKS = new HashMap<>();

        public static boolean isEternalEclipseApocalypseActive = false;
}
