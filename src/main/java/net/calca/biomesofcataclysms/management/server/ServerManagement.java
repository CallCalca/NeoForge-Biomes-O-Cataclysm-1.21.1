package net.calca.biomesofcataclysms.management.server;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.RuntimeData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.cataclysm.sunburn.SunBurnStage;
import net.calca.biomesofcataclysms.management.chunk.ChunkProcessor;
import net.calca.biomesofcataclysms.management.chunk.ChunkQueueManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

import static net.calca.biomesofcataclysms.data.cataclysm.sunburn.SunBurnStage.getSunBurnStage;
import static net.calca.biomesofcataclysms.management.chunk.sunburn.SunBurnProcessorHelper.resetSunBurntWaves;

// Done with the help of https://github.com/CoFH/CoFHCore/blob/1.19.x/src/main/java/cofh/core/event/AreaEffectEvents.java
// Don't be a jerk License
@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ServerManagement {

    private static void countDown(PersistentData.MapVariables variables, MinecraftServer server, int ticks, String nextBiome) {
        if (ticks >= 0 && ticks % 20 == 0) {
            int seconds = ticks / 20;
            Component cataclysm = Component.literal(variables.cataclysm).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
            Component secondsText = Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
            String translationKey = Util.makeDescriptionId("biome", ResourceLocation.parse(nextBiome));
            Component nextBiomeText = Component.translatable(translationKey).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);

            if (ticks <= 200){
                Component msg;
                if (ticks == 200) {
                    if (variables.difficulty >= 3){
                        nextBiomeText = Component.literal("?7/?fhs?")
                                .withStyle(ChatFormatting.RED)
                                .withStyle(ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.OBFUSCATED);
                    }
                    msg = Component.translatable("event.biomesofcataclysms.countDown", nextBiomeText, cataclysm, secondsText)
                            .withStyle(ChatFormatting.GREEN);
                } else if (ticks == 0) {
                    for (ServerLevel level : server.getAllLevels()){
                        ModUtils.sendChatMessage(level, Component.translatable("event.biomesofcataclysms.biomeAffected", nextBiomeText, cataclysm)
                                .withStyle(ChatFormatting.GREEN));
                    }

                    msg = secondsText.copy().append("...");
                } else {
                    msg = secondsText.copy().append("...");
                }
                if (ticks > 0) {
                    if (variables.difficulty < 4){
                        for (ServerLevel level : server.getAllLevels()) {
                            ModUtils.sendChatMessage(level, msg);
                        }
                    }
                    if (!(seconds % 2 == 0)) {
                        if (variables.difficulty < 4){
                            float pitch = (float) (((10 - seconds) - 1) * 0.06);
                            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                player.playNotifySound(SoundEvents.COMPARATOR_CLICK, SoundSource.MASTER, 0.6F, 0.6F + pitch);
                            }
                        }
                    } else {
                        if (variables.difficulty < 4){
                            float pitch = (float) (((10 - seconds) - 2) * 0.08);
                            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                player.playNotifySound(SoundEvents.COMPARATOR_CLICK, SoundSource.MASTER, 0.6F, 0.4F + pitch);
                            }
                        }
                    }
                } else {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ModUtils.playLocalBiomeAffectedSound(player);
                    }
                }

            }else{
                if (variables.difficulty == 0){
                    if (variables.tickToNextCataclysm == 30*20){
                        for (ServerLevel level : server.getAllLevels()){
                            ModUtils.sendChatMessage(level, Component.translatable("event.biomesofcataclysms.countDown", nextBiomeText, cataclysm, secondsText)
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }else if (variables.difficulty == 1){
                    if (variables.tickToNextCataclysm == 20*20) {
                        for (ServerLevel level : server.getAllLevels()) {
                            ModUtils.sendChatMessage(level, Component.translatable("event.biomesofcataclysms.countDown", nextBiomeText, cataclysm, secondsText)
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
            }



        }

    }
    private static void gracePeriodCheck(PersistentData.MapVariables globalVars, MinecraftServer server) {
        if (globalVars.graceCheckHappen) return;
        int ticks = globalVars.gracePeriod;
        if (ticks == 0) {
            globalVars.graceCheckHappen = true;
            for (ServerLevel level : server.getAllLevels()) {
                ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.endOfGracePeriod")
                        .withStyle(ChatFormatting.YELLOW));
                for (ServerPlayer player : level.players()) {
                    player.playNotifySound(
                            SoundEvents.NOTE_BLOCK_BASS.value(), // suono
                            SoundSource.MASTER,                 // categoria audio
                            1F,                               // volume
                            0.8F                                // pitch
                    );
                }
            }
            if (globalVars.deletedBiomes.size() != globalVars.totalBiomes){
                ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.BLUE);
            }
        }
    }

    private static void spawnMonstersAroundEternalDarkness(Player player, Level level) {
        // Eseguiamo il codice solo sul Server e ogni 100 tick (5 secondi)
        if (!level.isClientSide() && level.getGameTime() % 100 == 0) {
            ServerLevel serverLevel = (ServerLevel) level;

            // Definisci un raggio d'azione (es. da 0 a 32 blocchi)
            int radius = 16;
            int xOffset = player.getRandom().nextIntBetweenInclusive(-radius, radius);
            int zOffset = player.getRandom().nextIntBetweenInclusive(-radius, radius);
            int yOffset = player.getRandom().nextIntBetweenInclusive(-4, 4); // Cerca anche un po' sopra/sotto

            BlockPos spawnPos = player.blockPosition().offset(xOffset, yOffset, zOffset);

            // Scegliamo un mostro a caso (es. uno Zombie)
            EntityType<? extends Mob> entityType = EntityType.ZOMBIE;

            // Verifichiamo se il blocco è adatto allo spawn (aria per l'entità, solido sotto)
            if (serverLevel.getBlockState(spawnPos).isAir() && serverLevel.getBlockState(spawnPos.below()).isSolid()) {

                // Spawna l'entità ignorando le restrizioni di luce e distanza di vanilla
                Mob mob = entityType.create(serverLevel);
                if (mob != null) {
                    mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getRandom().nextFloat() * 360F, 0.0F);

                    // Finalizza lo spawn (imposta equipaggiamento, effetti, ecc.)
                    mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.NATURAL, null);

                    serverLevel.addFreshEntity(mob);
                }
            }
        }

    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(server.overworld());


        if (globalVars.state != 2) return;
        AllCataclysms type = ModUtils.decodeCataclysmFromString(globalVars.cataclysm);


        if (globalVars.tickFloodHeights(server.overworld())) {
            // Se l'altezza è cambiata, chiediamo al manager di rimettere in coda i chunk PARTIAL
            ChunkQueueManager.wakeUpPartialChunks(server.overworld());
        }

        int initialDestructionSpeed = 4;
        for (ServerLevel level : server.getAllLevels()) {

            int dynamicDestructionSpeed = 4;

            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;
                if (globalVars.difficulty <= 2) {
                    player.forceAddEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, true), player);
                }
                ChunkPos pPos = new ChunkPos(player.blockPosition());
                Holder<Biome> currentBiome = level.getBiome(player.blockPosition());
                Optional<ResourceKey<Biome>> biomeKey = currentBiome.unwrapKey();
                if (biomeKey.isPresent()) {
                String biomeId = biomeKey.get().location().toString();
                SunBurnStage stage = SunBurnStage.FIRE; // Default
                if (type == AllCataclysms.SUN_BURNT) {
                    long elapsed = globalVars.getSunBurnElapsedTicks(biomeId, level);
                    stage = getSunBurnStage(elapsed);
                }

                if ((type != AllCataclysms.SUN_BURNT || stage == SunBurnStage.INSTANT_TRANSFORM) && type != AllCataclysms.FLOODED){
                    if (globalVars.pcPower == 0) { // Potato
                        dynamicDestructionSpeed = 1;
                        initialDestructionSpeed = 1;
                    } else if (globalVars.pcPower == 1) { //Low
                        if (ModUtils.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 2;
                        } else {
                            dynamicDestructionSpeed = 1;
                        }
                        initialDestructionSpeed = 2;
                    } else if (globalVars.pcPower == 2) { //Medium
                        if (ModUtils.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 3;
                        } else {
                            dynamicDestructionSpeed = 2;
                        }
                        initialDestructionSpeed = 3;
                    } else if (globalVars.pcPower == 3) { //High
                        if (ModUtils.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 4;
                        } else {
                            dynamicDestructionSpeed = 2;
                        }
                        initialDestructionSpeed = 4;
                    } else if (globalVars.pcPower == 4) { // Estreme
                        if (ModUtils.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 4;
                        } else if (ModUtils.hasChunksInRadius(level, pPos, 16)) {
                            dynamicDestructionSpeed = 3;
                        } else {
                            dynamicDestructionSpeed = 1;
                        }
                        initialDestructionSpeed = 4;
                    } else if (globalVars.pcPower == 5) { // Max
                        if (ModUtils.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 4;
                        } else if (ModUtils.hasChunksInRadius(level, pPos, 16)) {
                            dynamicDestructionSpeed = 3;
                        } else if (ModUtils.hasChunksInRadius(level, pPos, 24)) {
                            dynamicDestructionSpeed = 3;
                        } else {
                            dynamicDestructionSpeed = 2;
                        }
                        initialDestructionSpeed = 4;
                    }
                } else if (type == AllCataclysms.FLOODED) {
                    dynamicDestructionSpeed = 2;
                }

                    if (globalVars.deletedBiomes.contains(biomeId)) {
                        if (server.getTickCount() % 2000 == 0) {
                            ChunkQueueManager.rescueMissedChunks(level, 8);
                        }
                        break;
                    }
                }
            }

            if (server.getTickCount() % 10 == 0) {
                globalVars.scanContinuous(level);
            }

            if (server.getTickCount() % 20 == 0) {
                ChunkQueueManager.cleanDynamicQueue(level);
                ChunkQueueManager.refreshDynamicQueue(level);
            }

            if (globalVars.difficulty == 5 && dynamicDestructionSpeed > 3) {
                dynamicDestructionSpeed = 3;
            }

            if (type != AllCataclysms.SUN_BURNT && type != AllCataclysms.FLOODED){
                ChunkProcessor.processInitialQueue(level, initialDestructionSpeed);
            }else{
                boolean queueEmpty = ChunkQueueManager.isQueueEmpty(level);
                boolean timerElapsed = (level.getGameTime() % 200 == 0); // 200 tick = 10 secondi
                if (queueEmpty || timerElapsed) {
                    resetSunBurntWaves(level);
                }
            }
            if (globalVars.gracePeriod <= 0) {
                ChunkProcessor.processDynamicQueue(level, dynamicDestructionSpeed);
            }
        }

        gracePeriodCheck(globalVars, server);

        if (globalVars.gracePeriod == 0) {
            globalVars.tickToNextCataclysm--;
            if (globalVars.timer != -1){
                if (globalVars.timer <= 0){
                    for (ServerLevel level : server.getAllLevels()) {
                        for (ServerPlayer player : level.players()) {
                            if (player.isSpectator()) continue;
                            if (!player.hasEffect(MobEffects.WATER_BREATHING) && !player.isUnderWater()){
                                player.setAirSupply(player.getAirSupply() - 5);
                            } else if (player.hasEffect(MobEffects.WATER_BREATHING)) {
                                if (player.getAirSupply() > 290){
                                    player.setAirSupply(290);
                                }

                            }
                        }
                    }
                }else{
                    globalVars.timer--;
                    ProgressBarManager.TimerProgressBar.tick(globalVars);
                }
            }
            if (!globalVars.graceCheckHappen) {
                for (ServerLevel level : server.getAllLevels()) {
                    ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.endOfGracePeriod")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.playNotifySound(
                                SoundEvents.NOTE_BLOCK_BASS.value(), // suono
                                SoundSource.MASTER,                 // categoria audio
                                1F,                                 // volume
                                0.8F                                // pitch
                        );
                    }
                }
            }
            globalVars.graceCheckHappen = true;
        } else {
            globalVars.gracePeriod--;
        }

        countDown(globalVars, server, globalVars.tickToNextCataclysm, globalVars.nextBiomeToAffect);

        if (globalVars.tickToNextCataclysm <= 0) {
            globalVars.tickToNextCataclysm = globalVars.tickDelayBetweenCataclysm;

            if (type == AllCataclysms.SUN_BURNT) {
                for (ServerLevel level : server.getAllLevels()) {
                    String biomeId = globalVars.nextBiomeToAffect;
                    globalVars.startSunBurn(biomeId, level);
                }
            }

            if (type == AllCataclysms.FLOODED) {
                for (ServerLevel level : server.getAllLevels()) {
                    globalVars.startFloodForBiome(globalVars.nextBiomeToAffect, level);
                }
            }

            globalVars.selectNextBiomeGlobal(server);

            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    GameManager.biomesCheck(player, level, globalVars);
                }
            }

            if (type != AllCataclysms.SUN_BURNT && type != AllCataclysms.FLOODED){
                if (globalVars.difficulty == 0 || globalVars.difficulty == 1) {
                    int totalChunks = RuntimeData.INITIAL_ORDER.values().stream()
                            .mapToInt(ArrayDeque::size)
                            .sum();
                    globalVars.gracePeriod = (totalChunks / initialDestructionSpeed) * 4;
                    if (globalVars.gracePeriod > 0) {
                        ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
                        globalVars.graceCheckHappen = false;
                        MutableComponent component1 = Component.translatable("command.biomesofcataclysms.gracePeriod").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        int seconds = globalVars.gracePeriod / 20;
                        MutableComponent component2 = Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        for (ServerLevel level : server.getAllLevels()) {
                            ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.setGracePeriod", component2, component1)
                                    .withStyle(ChatFormatting.YELLOW)); //Starting
                        }
                    }
                } else if (globalVars.difficulty == 2) {
                    int totalChunks = RuntimeData.INITIAL_ORDER.values().stream()
                            .mapToInt(ArrayDeque::size)
                            .sum();
                    globalVars.gracePeriod = (totalChunks / 4) * 4;
                    if (globalVars.gracePeriod > 15 * 20) globalVars.gracePeriod = 15 * 20;
                    if (globalVars.gracePeriod > 0) {
                        ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
                        globalVars.graceCheckHappen = false;
                        MutableComponent component1 = Component.translatable("command.biomesofcataclysms.gracePeriod").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        int seconds = globalVars.gracePeriod / 20;
                        MutableComponent component2 = Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        ModUtils.sendChatMessage(server.overworld(), Component.translatable("command.biomesofcataclysms.setGracePeriod", component2, component1)
                                .withStyle(ChatFormatting.YELLOW)); //Starting
                    }
                }

            }
        }

        //globalVars.syncData(server.overworld());
        for (ServerLevel level : server.getAllLevels()) {
            globalVars.syncData(level, true, false);
        }

    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
        if (ModUtils.decodeCataclysmFromString(globalVars.cataclysm) != AllCataclysms.ETERNAL_ECLIPSE) return;

        BlockPos pos = event.getPos();
        Holder<Biome> biome = level.getBiome(pos);
        Optional<ResourceKey<Biome>> biomeKey = biome.unwrapKey();
        if (biomeKey.isEmpty()) return;
        String biomeId = biomeKey.get().location().toString();
        if (!globalVars.deletedBiomes.contains(biomeId)) return;

        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
    }

    @SubscribeEvent
    public static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
        if (ModUtils.decodeCataclysmFromString(globalVars.cataclysm) != AllCataclysms.ETERNAL_ECLIPSE) return;

        BlockPos pos = event.getEntity().getOnPos();
        Holder<Biome> biome = level.getBiome(pos);
        Optional<ResourceKey<Biome>> biomeKey = biome.unwrapKey();
        if (biomeKey.isEmpty()) return;
        String biomeId = biomeKey.get().location().toString();
        if (!globalVars.deletedBiomes.contains(biomeId)) return;

        event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
    }

    //Used only in debug mode
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PersistentData.MapVariables vars = PersistentData.MapVariables.get(level);
            if (!vars.debugMode) return;
            if (event.getState().is(Blocks.STONE)) {
                    String target = Biomes.FOREST.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);
            }else if (event.getState().is(Blocks.GRASS_BLOCK)){
                String target = Biomes.PLAINS.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.DIRT)) {
                String target = Biomes.RIVER.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.NETHERRACK)) {
                String target = Biomes.NETHER_WASTES.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.SOUL_SAND)) {
                String target = Biomes.SOUL_SAND_VALLEY.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.END_STONE)) {
                String target = Biomes.THE_END.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.JUNGLE_LOG)) {
                String target = Biomes.JUNGLE.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.ACACIA_LOG)) {
                String target = Biomes.SAVANNA.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.SPRUCE_LOG)) {
                String target = Biomes.OLD_GROWTH_PINE_TAIGA.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.PRISMARINE)) {
                String target = Biomes.OCEAN.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.ICE)) {
                String target = Biomes.COLD_OCEAN.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.PACKED_ICE)) {
                String target = Biomes.LUKEWARM_OCEAN.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.SCULK)) {
                String target = Biomes.DEEP_DARK.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            }
        }
    }

}
