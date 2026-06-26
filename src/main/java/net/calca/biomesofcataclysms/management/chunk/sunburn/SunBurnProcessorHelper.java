package net.calca.biomesofcataclysms.management.chunk.sunburn;

import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.RuntimeData;
import net.calca.biomesofcataclysms.data.cataclysm.sunburn.SunBurnStage;
import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.data.chunk.ChunkState;
import net.calca.biomesofcataclysms.management.chunk.ChunkProcessor;
import net.calca.biomesofcataclysms.management.chunk.ChunkQueueManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Set;

public class SunBurnProcessorHelper {

    public static void applySunBurntChunk(ServerLevel level,
                                          ChunkPos pos,
                                          Set<String> targetBiomes,
                                          PersistentData.MapVariables vars) {

        String activeBiomeId = targetBiomes.iterator().next();
        long elapsed = vars.getSunBurnElapsedTicks(activeBiomeId, level);
        SunBurnStage stage = SunBurnStage.getSunBurnStage(elapsed);

        // --- 1. STADIO INSTANT_TRANSFORM: OTTIMIZZATO PER LAG E LAVA ---
        if (stage == SunBurnStage.INSTANT_TRANSFORM) {
            // FASE 1: VAPORIZZAZIONE (Piazza Pulita)
            // Rimuoviamo TUTTI i blocchi speciali dal chunk prima di toccare il terreno.
            // FASE 1: VAPORIZZAZIONE (Piazza Pulita)
            // --- Dentro applySunBurntChunk -> stage == SunBurnStage.INSTANT_TRANSFORM ---
            // FASE 1: VAPORIZZAZIONE
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos scanPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos.getBlockAt(x, 0, z));
                    int limit = 0;
                    while (limit < 64) {
                        BlockState state = level.getBlockState(scanPos);

                        if (state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.LAVA)) {
                            scanPos = scanPos.below();
                            limit++;
                            continue;
                        }

                        // --- AGGIUNTA PROTEZIONE QUI ---
                        // Se troviamo questi blocchi, fermiamo la scansione della colonna: sono "suolo"
                        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)) {
                            break;
                        }

                        if (isExpiringBlock(state) || isWoodLog(state) || state.is(Blocks.COAL_BLOCK) || isGlass(state)) {
                            // ... (codice vaporizzazione esistente)
                            level.setBlock(scanPos, Blocks.AIR.defaultBlockState(), 2);
                            scanPos = scanPos.below();
                            limit++;
                        } else {
                            break;
                        }
                    }
                }
            }

            // FASE 2: LIQUEFAZIONE (Piazzamento Lava)
            // Ora che gli alberi e il vetro sono spariti, la Heightmap è stabile.
            // FASE 2: LIQUEFAZIONE
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos.getBlockAt(x, 0, z));

                    for (int d = 0; d < 10; d++) {
                        BlockPos current = groundPos.below(d);
                        if (current.getY() < level.getMinBuildHeight()) break;

                        BlockState currentState = level.getBlockState(current);

                        // --- PROTEZIONE LAVA ---
                        // Se il blocco attuale è uno di questi, non trasformarlo in lava e passa al prossimo (o ferma la colonna)
                        if (currentState.is(Blocks.OBSIDIAN) || currentState.is(Blocks.END_PORTAL) || currentState.is(Blocks.END_PORTAL_FRAME)) {
                            continue; // Salta questo blocco e non mettere lava
                        }

                        placeSmartLava(level, current);
                    }
                }
            }
            return;
        }

        // --- 2. LOGICA STANDARD (EARLY, MID, HOT, FINAL) ---
        int attempts = getSunBurnAttempts(stage);
        int safety = attempts * 16;
        RandomSource random = level.getRandom();

        while (attempts > 0 && safety-- > 0) {
            BlockPos topPos = findRandomSurfacePos(level, pos, random);
            if (topPos == null) break;

            int depthOffset = random.nextInt(11);
            BlockPos surfacePos = topPos.below(depthOffset);
            if (surfacePos.getY() < level.getMinBuildHeight()) continue;

            String biomeIdAtPos = level.getBiome(surfacePos).unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            if (!targetBiomes.contains(biomeIdAtPos)) continue;

            BlockState state = level.getBlockState(surfacePos);
            if (state.isAir() || state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.END_PORTAL)  || state.is(Blocks.END_PORTAL_FRAME)) continue;

            boolean specialActionDone = false;

            // Gestione PRIORITARIA blocchi speciali
            if (isGrassLike(state)) {
                level.setBlock(surfacePos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                specialActionDone = true;
            } else if (stage != SunBurnStage.FIRE) {
                if (isGlass(state)) {
                    explodeSingleBlock(level, surfacePos, state);
                    specialActionDone = true;
                } else if (isExpiringBlock(state)) {
                    level.setBlock(surfacePos, Blocks.AIR.defaultBlockState(), 3);
                    specialActionDone = true;
                } else if (isWoodLog(state) || (state.is(Blocks.COAL_BLOCK) && isWoodLog(level.getBlockState(surfacePos.below())))) {
                    level.setBlock(surfacePos, Blocks.AIR.defaultBlockState(), 3);
                    handleWoodSpecialEffects(level, surfacePos, random);
                    specialActionDone = true;
                }
            }

            if (specialActionDone) {
                attempts--;
                continue;
            }

            // --- TRASFORMAZIONE DIRETTA DEL TERRENO ---
            if (!hasAdjacentFire(level, surfacePos)) {
                tryPlaceAdjacentFire(level, surfacePos);
            }

            if (stage == SunBurnStage.FIRE) {
                attempts--;
            } else if (stage == SunBurnStage.BURNING || stage == SunBurnStage.HOT) {
                if (state.is(Blocks.NETHERRACK)) {
                    level.setBlock(surfacePos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                } else if (!state.is(Blocks.COAL_BLOCK) &&  !isGlass(state) && !isExpiringBlock(state) && !isWoodLog(state) && !isGrassLike(state)){
                    level.setBlock(surfacePos, Blocks.NETHERRACK.defaultBlockState(), 3);
                }
                attempts--;
            } else { // FINAL STAGE
                if (state.is(Blocks.MAGMA_BLOCK)) {
                    placeSmartLava(level, surfacePos);
                } else if (!state.is(Blocks.COAL_BLOCK) && !isGlass(state) && !isExpiringBlock(state) && !isWoodLog(state) && !isGrassLike(state)){
                    level.setBlock(surfacePos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                }
                attempts--;
            }
        }
    }

    //Questo metodo dovrebbe essere in grado di evitare la generazione di colate di lava fluttuanti. Dovrebbe piazzare la lava solo su blocchi di terreno
    private static void placeSmartLava(ServerLevel level, BlockPos pos) {
        // Controllo critico: se sotto c'è aria o un fluido non solido, DEVE essere fluida
        if (level.isEmptyBlock(pos.below()) || !level.getBlockState(pos.below()).isSolid()) {
            // Piazziamo lava fluida (Level 1).
            // USIAMO FLAG 3 per forzare l'aggiornamento dei fluidi circostanti
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);

            // Reazione a catena verso l'alto per correggere eventuali sorgenti rimaste "appese"
            BlockPos above = pos.above();
            BlockState stateAbove = level.getBlockState(above);
            int chainLimit = 0;
            while (stateAbove.is(Blocks.LAVA) && chainLimit < 20) {
                // Se sopra è una sorgente (0), la facciamo diventare fluida (1)
                if (stateAbove.getValue(BlockStateProperties.LEVEL) == 0) {
                    level.setBlock(above, Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1), 3);
                }
                above = above.above();
                stateAbove = level.getBlockState(above);
                chainLimit++;
            }
        } else {
            // Appoggiata su solido: Sorgente pura
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
        }
    }

    //Il legno non si rompe e basta, ma ha una piccola percentuale di probabilità di divenire blocco di carbone. Invece,
    //se la probabilità è a sfavore, il blocco sparisce, ma ha una piccola probabilità di generare un item di Charcoal.
    private static void handleWoodSpecialEffects(ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.70f) {
            if (random.nextFloat() < 0.40f) {
                Block.popResource(level, pos, new ItemStack(Items.CHARCOAL));
            }
        } else {
            // Controlliamo che non ci sia già un FallingBlock in quel punto per evitare sovrapposizioni visive
            AABB searchBox = new AABB(pos).inflate(0.1);
            if (level.getEntitiesOfClass(FallingBlockEntity.class, searchBox).isEmpty()) {

                // IL FIX È QUI: FallingBlockEntity.fall() aggiunge già l'entità al mondo!
                FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, pos, Blocks.COAL_BLOCK.defaultBlockState());
                fallingBlock.dropItem = false;
                // NON chiamare level.addFreshEntity(fallingBlock);
            }
        }
    }

    //Questo metodo cerca di capire quale è, in un chunk specifico, il bioma subito sulla superficie di un chunk.
    public static boolean hasExposedBiome(ServerLevel level, ChunkPos pos, Set<String> targetBiomes) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Prendiamo il blocco più in alto (esposto al cielo)
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getMinBlockX() + x, pos.getMinBlockZ() + z);
                mutablePos.set(pos.getMinBlockX() + x, y - 1, pos.getMinBlockZ() + z);

                String biomeId = level.getBiome(mutablePos).unwrapKey()
                        .map(k -> k.location().toString()).orElse("unknown");

                if (targetBiomes.contains(biomeId)) {
                    return true; // Trovato almeno un punto di contatto con il sole!
                }
            }
        }
        return false; // Il bioma è interamente coperto da altri biomi o roccia
    }

    public static void resetSunBurntWaves(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
        if (registry == null) return;

        for (ChunkInstance mod : registry.values()) {
            if (mod.state == ChunkState.PARTIAL) {
                mod.state = ChunkState.QUEUED; // Torna disponibile
                // Opzionale: lo riaggiungiamo alla coda se non c'è già
                ChunkQueueManager.registerDynamicChunk(level, mod.pos);
            }
        }
    }

    //Un tentativo è un blocco che verrà elaborato in uno specifico chunk. In base alla fase del Sun Burn, i tentativi ad ogni wave cambiano.
    private static int getSunBurnAttempts(SunBurnStage stage) {
        return switch (stage) {
            case FIRE -> 15;
            case BURNING -> 30;
            case HOT -> 100;
            case MELTING -> 150;
            case INSTANT_TRANSFORM -> 0;
        };
    }

    //I seguenti metodi cercano di capire se è possibile piazzare un po di fuoco sul blocco che è stato scelto dal tentativo.
    private static void tryPlaceAdjacentFire(ServerLevel level, BlockPos pos) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE;

        for (Direction dir : Direction.values()) {
            BlockPos firePos = pos.relative(dir);
            BlockState current = level.getBlockState(firePos);

            if (!current.isAir()) continue;

            BlockState fire = Blocks.FIRE.defaultBlockState();
            if (fire.canSurvive(level, firePos)) {
                level.setBlock(firePos, fire, flags);
                return;
            }
        }
    }
    private static boolean hasAdjacentFire(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(Blocks.FIRE)) {
                return true;
            }
        }
        return false;
    }

    //Metodi di Util che ritornano true se il blocco dato in input coincide con uno del registro.
    private static boolean isGlass(BlockState state) {
        return state.is(Blocks.GLASS)
                || state.is(Blocks.TINTED_GLASS)
                || state.is(Blocks.WHITE_STAINED_GLASS)
                || state.is(Blocks.ORANGE_STAINED_GLASS)
                || state.is(Blocks.MAGENTA_STAINED_GLASS)
                || state.is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                || state.is(Blocks.YELLOW_STAINED_GLASS)
                || state.is(Blocks.LIME_STAINED_GLASS)
                || state.is(Blocks.PINK_STAINED_GLASS)
                || state.is(Blocks.GRAY_STAINED_GLASS)
                || state.is(Blocks.LIGHT_GRAY_STAINED_GLASS)
                || state.is(Blocks.CYAN_STAINED_GLASS)
                || state.is(Blocks.PURPLE_STAINED_GLASS)
                || state.is(Blocks.BLUE_STAINED_GLASS)
                || state.is(Blocks.BROWN_STAINED_GLASS)
                || state.is(Blocks.GREEN_STAINED_GLASS)
                || state.is(Blocks.RED_STAINED_GLASS)
                || state.is(Blocks.BLACK_STAINED_GLASS)
                || state.is(Blocks.AMETHYST_BLOCK)
                || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.SMALL_AMETHYST_BUD)
                || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                || state.is(Blocks.LARGE_AMETHYST_BUD)
                || state.is(Blocks.AMETHYST_CLUSTER)
                || state.is(Blocks.GLOWSTONE);
    }
    private static boolean isExpiringBlock(BlockState state) {
        return state.is(Blocks.ACACIA_LEAVES)
                || state.is(Blocks.BIRCH_LEAVES)
                || state.is(Blocks.AZALEA_LEAVES)
                || state.is(Blocks.CHERRY_LEAVES)
                || state.is(Blocks.JUNGLE_LEAVES)
                || state.is(Blocks.OAK_LEAVES)
                || state.is(Blocks.DARK_OAK_LEAVES)
                || state.is(Blocks.MANGROVE_LEAVES)
                || state.is(Blocks.SPRUCE_LEAVES)
                || state.is(Blocks.FLOWERING_AZALEA_LEAVES)
                || state.is(Blocks.NETHER_WART_BLOCK)
                || state.is(Blocks.WARPED_WART_BLOCK)
                || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY)
                || state.is(Blocks.TORCHFLOWER)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.PINK_PETALS)
                || state.is(Blocks.SPORE_BLOSSOM)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CRIMSON_ROOTS)
                || state.is(Blocks.WARPED_ROOTS)
                || state.is(Blocks.NETHER_SPROUTS)
                || state.is(Blocks.WEEPING_VINES)
                || state.is(Blocks.TWISTING_VINES)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY)
                || state.is(Blocks.PITCHER_PLANT)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.CRIMSON_FUNGUS)
                || state.is(Blocks.WARPED_FUNGUS)
                || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.SMALL_DRIPLEAF)
                || state.is(Blocks.CHORUS_FLOWER)
                || state.is(Blocks.CHORUS_PLANT)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.NETHER_WART)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.SEA_PICKLE)
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.PUMPKIN_STEM)
                || state.is(Blocks.MELON_STEM)
                || state.is(Blocks.FLOWERING_AZALEA)
                || state.is(Blocks.AZALEA)
                || state.is(Blocks.ACACIA_SAPLING)
                || state.is(Blocks.MANGROVE_PROPAGULE)
                || state.is(Blocks.SPRUCE_SAPLING)
                || state.is(Blocks.BIRCH_SAPLING)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.DARK_OAK_SAPLING)
                || state.is(Blocks.CHERRY_SAPLING)
                || state.is(Blocks.JUNGLE_SAPLING)
                || state.is(Blocks.OAK_SAPLING)
                || state.is(Blocks.MELON)
                || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.BEE_NEST)
                || state.is(Blocks.SCULK_SENSOR)
                || state.is(Blocks.SCULK_SHRIEKER)
                || state.is(Blocks.SCULK_CATALYST)
                || state.is(Blocks.SCULK_VEIN)
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.DEAD_TUBE_CORAL_BLOCK)
                || state.is(Blocks.DEAD_BRAIN_CORAL_BLOCK)
                || state.is(Blocks.DEAD_BUBBLE_CORAL_BLOCK)
                || state.is(Blocks.DEAD_FIRE_CORAL_BLOCK)
                || state.is(Blocks.DEAD_HORN_CORAL_BLOCK)
                || state.is(Blocks.DEAD_TUBE_CORAL)
                || state.is(Blocks.DEAD_BRAIN_CORAL)
                || state.is(Blocks.DEAD_BUBBLE_CORAL)
                || state.is(Blocks.DEAD_FIRE_CORAL)
                || state.is(Blocks.DEAD_HORN_CORAL)
                || state.is(Blocks.DEAD_TUBE_CORAL_FAN)
                || state.is(Blocks.DEAD_BRAIN_CORAL_FAN)
                || state.is(Blocks.DEAD_BUBBLE_CORAL_FAN)
                || state.is(Blocks.DEAD_FIRE_CORAL_FAN)
                || state.is(Blocks.DEAD_HORN_CORAL_FAN)
                || state.is(Blocks.SPONGE)
                || state.is(Blocks.WET_SPONGE)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.WATER)
                || state.is(Blocks.BUBBLE_COLUMN)
                || state.is(Blocks.BLUE_ICE);
    }
    private static boolean isWoodLog(BlockState state) {
        return state.is(Blocks.ACACIA_LOG)
                || state.is(Blocks.BIRCH_LOG)
                || state.is(Blocks.CHERRY_LOG)
                || state.is(Blocks.JUNGLE_LOG)
                || state.is(Blocks.OAK_LOG)
                || state.is(Blocks.DARK_OAK_LOG)
                || state.is(Blocks.MANGROVE_LOG)
                || state.is(Blocks.SPRUCE_LOG)
                || state.is(Blocks.STRIPPED_ACACIA_LOG)
                || state.is(Blocks.STRIPPED_BIRCH_LOG)
                || state.is(Blocks.STRIPPED_CHERRY_LOG)
                || state.is(Blocks.STRIPPED_JUNGLE_LOG)
                || state.is(Blocks.STRIPPED_OAK_LOG)
                || state.is(Blocks.STRIPPED_DARK_OAK_LOG)
                || state.is(Blocks.STRIPPED_MANGROVE_LOG)
                || state.is(Blocks.STRIPPED_SPRUCE_LOG)
                || state.is(Blocks.MANGROVE_ROOTS);
    }
    private static boolean isGrassLike(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    //Usato per generare l'effetto della rottura del vetro
    private static void explodeSingleBlock(ServerLevel level, BlockPos pos, BlockState state) {
        level.levelEvent(2001, pos, Block.getId(state));
        level.destroyBlock(pos, false);
    }

    private static BlockPos findRandomSurfacePos(ServerLevel level, ChunkPos chunk, RandomSource random) {
        int x = chunk.getMinBlockX() + random.nextInt(16);
        int z = chunk.getMinBlockZ() + random.nextInt(16);

        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }
}
