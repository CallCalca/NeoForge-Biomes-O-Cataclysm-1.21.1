package net.calca.biomesofcataclysms.management.server;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biomes;

public class GameManager {
    public static void biomesCheck(Player player, ServerLevel level, PersistentData.MapVariables vars){
        allNetherBiomesDeleted(player, level, vars);
        allEndBiomesDeleted(player, level, vars);
        allOverworldBiomesDeleted(player, level, vars);
        allBiomesDeleted(player, level, vars);
    }

    private static void allNetherBiomesDeleted(Player player, ServerLevel level, PersistentData.MapVariables vars){
        if (!vars.allNetherBiomesHitShouldNotify) return;
        if (vars.deletedBiomes.contains(Biomes.NETHER_WASTES.location().toString())
                && vars.deletedBiomes.contains(Biomes.SOUL_SAND_VALLEY.location().toString())
                && vars.deletedBiomes.contains(Biomes.CRIMSON_FOREST.location().toString())
                && vars.deletedBiomes.contains(Biomes.WARPED_FOREST.location().toString())
                && vars.deletedBiomes.contains(Biomes.BASALT_DELTAS.location().toString())){
            BiomesOfCataclysms.queueServerWork(40, () -> {

            MutableComponent dimension = Component.translatable("gameManager.biomesofcataclysms.nether")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

            MutableComponent cataclysm = Component.literal(vars.cataclysm)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

            ModUtils.sendChatMessage(level, Component.translatable("gameManager.biomesofcataclysms.allBiomesHit", dimension, cataclysm)
                    .withStyle(ChatFormatting.YELLOW));
            playSound(player);

                vars.allNetherBiomesHitShouldNotify = false;
                vars.syncData(level, true, false);
            });
        }

    }
    private static void allEndBiomesDeleted(Player player, ServerLevel level, PersistentData.MapVariables vars){
        if (!vars.allEndBiomesHitShouldNotify) return;
        if (vars.deletedBiomes.contains(Biomes.END_HIGHLANDS.location().toString())
                && vars.deletedBiomes.contains(Biomes.END_BARRENS.location().toString())
                && vars.deletedBiomes.contains(Biomes.END_MIDLANDS.location().toString())
                && vars.deletedBiomes.contains(Biomes.SMALL_END_ISLANDS.location().toString())
                && vars.deletedBiomes.contains(Biomes.THE_END.location().toString())){
            BiomesOfCataclysms.queueServerWork(40, () -> {

            MutableComponent dimension = Component.translatable("gameManager.biomesofcataclysms.end")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

            MutableComponent cataclysm = Component.literal(vars.cataclysm)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

            ModUtils.sendChatMessage(level, Component.translatable("gameManager.biomesofcataclysms.allBiomesHit", dimension, cataclysm)
                    .withStyle(ChatFormatting.YELLOW));
            playSound(player);

                vars.allEndBiomesHitShouldNotify = false;
                vars.syncData(level, true, false);
            });
        }


    }
    private static void allOverworldBiomesDeleted(Player player, ServerLevel level, PersistentData.MapVariables vars){
        if (!vars.allOverworldBiomesHitShouldNotify) return;
        if (vars.deletedBiomes.containsAll(vars.overworldBiomeList)){
            BiomesOfCataclysms.queueServerWork(40, () -> {
                MutableComponent dimension = Component.translatable("gameManager.biomesofcataclysms.overworld")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                MutableComponent cataclysm = Component.literal(vars.cataclysm)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                ModUtils.sendChatMessage(level, Component.translatable("gameManager.biomesofcataclysms.allBiomesHit", dimension, cataclysm)
                        .withStyle(ChatFormatting.YELLOW));
                playSound(player);

                vars.allOverworldBiomesHitShouldNotify = false;
                vars.syncData(level, true, false);
            });
        }


    }
    private static void allBiomesDeleted(Player player, ServerLevel level, PersistentData.MapVariables vars){
        if (vars.deletedBiomes.size() == vars.totalBiomes){
            BiomesOfCataclysms.queueServerWork(80, () -> {

                ModUtils.sendChatMessage(level, Component.translatable("gameManager.biomesofcataclysms.allDimensionsHit")
                        .withStyle(ChatFormatting.YELLOW));
                playSound(player);

            });
            vars.tickToNextCataclysm = Integer.MAX_VALUE;
            vars.syncData(level, true, false);
            ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.RED);
        }


    }

    private static void playSound(Player player){
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ModUtils.playLocalBellResonateSound(serverPlayer, 1F);
        ModUtils.playLocalBellResonateSound(serverPlayer, 0.9F);
        ModUtils.playLocalBellResonateSound(serverPlayer, 0.8F);
        ModUtils.playLocalBellResonateSound(serverPlayer, 0.7F);

        MinecraftServer server = serverPlayer.getServer();
        assert server != null;

        BiomesOfCataclysms.queueServerWork(10, () -> {
            server.execute(() -> {
                ModUtils.playLocalBellResonateSound(serverPlayer, 0.6F);
                ModUtils.playLocalBellResonateSound(serverPlayer, 0.5F);
                ModUtils.playLocalBellResonateSound(serverPlayer, 0.4F);
                ModUtils.playLocalBellResonateSound(serverPlayer, 0.3F);
            });
        });
    }

}
