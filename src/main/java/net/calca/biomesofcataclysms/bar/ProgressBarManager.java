package net.calca.biomesofcataclysms.bar;

import net.calca.biomesofcataclysms.data.ModVariables;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;

public class ProgressBarManager {
    public static void initializeOnServerStart(ModVariables.MapVariables variables, ServerLevel serverLevel){
            TimerProgressBar.initialize(variables, serverLevel);
    }

    public static void addPlayerOnLogIn(Player player){
        if (player instanceof ServerPlayer serverPlayer){
            TimerProgressBar.TIMER_PROGRESS_BAR.startFor(serverPlayer);
        }

    }
    public static class TimerProgressBar {
        public static final ProgressBar TIMER_PROGRESS_BAR =
                new ProgressBar(
                10,
                BossEvent.BossBarColor.BLUE,
                Component.translatable("progressBar.title.timerProgressBar").getString()
        );

        public static void initialize(ServerLevel serverLevel){
            ModVariables.MapVariables variables = ModVariables.MapVariables.get(serverLevel);
            initialize(variables, serverLevel);
        }

        public static void initialize(ModVariables.MapVariables variables, ServerLevel serverLevel){
            int maxValue = (variables.totalBiomes) * variables.tickDelayBetweenCataclysm;
            TIMER_PROGRESS_BAR.setMaxValue(maxValue);
            System.out.println("MAX:" + maxValue/20/60);
            int totalSeconds = variables.timer / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String formattedTime = String.format("%02d:%02d", minutes, seconds);
            TIMER_PROGRESS_BAR.setTitle(Component.translatable("progressBar.title.timerProgressBar", formattedTime));
            setOverlay(minutes);
            TIMER_PROGRESS_BAR.startForAllPlayers(serverLevel);
            setColor(variables);
            TIMER_PROGRESS_BAR.getBossBar().setVisible(variables.timer != -1);
        }

        public static void setOverlay(int minutes){
            if (minutes <= 63) TIMER_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
            else if (minutes < 120) TIMER_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
            else if (minutes < 240) TIMER_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_12);
            else TIMER_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);

        }

        public static void setColor(ModVariables.MapVariables variables){
            if (variables.biomesAffected == variables.totalBiomes || (variables.timer <= 0 && variables.timer != -1)){
                TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.RED);
                TIMER_PROGRESS_BAR.setProgress(0.0f);
            }else if (variables.gracePeriod > 0 && variables.timer != -1){
                TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
            }else{
                TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.BLUE);
            }
        }

        public static void setTitle(ModVariables.MapVariables variables){
            int totalSeconds = variables.timer / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String formattedTime;
            if (variables.difficulty <= 1){
                formattedTime = String.format("%02d:%02d", minutes, seconds);
            } else if (variables.difficulty > 3) {
                String obfuscated = "§k01";
                formattedTime = String.valueOf(obfuscated + "§r:" + obfuscated);
            }else{
                String obfuscated = "§k01";
                formattedTime = String.valueOf(String.format("%02d", minutes) + ":" + obfuscated);
            }
            TIMER_PROGRESS_BAR.setTitle(Component.translatable("progressBar.title.timerProgressBar", formattedTime));

        }

        public static void tick(ServerLevel serverLevel){
            ModVariables.MapVariables variables = ModVariables.MapVariables.get(serverLevel);
            tick(variables);
        }
        public static void tick(ModVariables.MapVariables variables){
            setTitle(variables);
            TIMER_PROGRESS_BAR.setRemainingTicks(variables.timer);
        }

    }
    public static class SunStormProgressBar {
        public static final ProgressBar SUN_STORM_PROGRESS_BAR =
                new ProgressBar(
                10,
                BossEvent.BossBarColor.YELLOW,
                Component.translatable("progressBar.title.sunStormProgressBar").getString()
        );

        public static void initialize(ServerLevel serverLevel){
            ModVariables.MapVariables variables = ModVariables.MapVariables.get(serverLevel);
            initialize(variables, serverLevel);
        }

        public static void initialize(ModVariables.MapVariables variables, ServerLevel serverLevel){
            int maxValue = (variables.totalBiomes) * variables.tickDelayBetweenCataclysm;
            SUN_STORM_PROGRESS_BAR.setMaxValue(maxValue);
            System.out.println("MAX:" + maxValue/20/60);
            int totalSeconds = variables.timer / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String formattedTime = String.format("%02d:%02d", minutes, seconds);
            SUN_STORM_PROGRESS_BAR.setTitle(Component.translatable("progressBar.title.timerProgressBar", formattedTime));
            setOverlay(minutes);
            SUN_STORM_PROGRESS_BAR.startForAllPlayers(serverLevel);
            setColor(variables);
            SUN_STORM_PROGRESS_BAR.getBossBar().setVisible(variables.timer != -1);
        }

        public static void setOverlay(int minutes){
            if (minutes <= 63) SUN_STORM_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
            else if (minutes < 120) SUN_STORM_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
            else if (minutes < 240) SUN_STORM_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_12);
            else SUN_STORM_PROGRESS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);

        }

        public static void setColor(ModVariables.MapVariables variables){
            if (variables.biomesAffected == variables.totalBiomes || (variables.timer <= 0 && variables.timer != -1)){
                SUN_STORM_PROGRESS_BAR.setColor(BossEvent.BossBarColor.RED);
                SUN_STORM_PROGRESS_BAR.setProgress(0.0f);
            }else if (variables.gracePeriod > 0 && variables.timer != -1){
                SUN_STORM_PROGRESS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
            }else{
                SUN_STORM_PROGRESS_BAR.setColor(BossEvent.BossBarColor.BLUE);
            }
        }

        public static void setTitle(ModVariables.MapVariables variables){
            int totalSeconds = variables.timer / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String formattedTime;
            if (variables.difficulty <= 1){
                formattedTime = String.format("%02d:%02d", minutes, seconds);
            } else if (variables.difficulty > 3) {
                String obfuscated = "§k01";
                formattedTime = String.valueOf(obfuscated + "§r:" + obfuscated);
            }else{
                String obfuscated = "§k01";
                formattedTime = String.valueOf(String.format("%02d", minutes) + ":" + obfuscated);
            }
            SUN_STORM_PROGRESS_BAR.setTitle(Component.translatable("progressBar.title.timerProgressBar", formattedTime));

        }

        public static void tick(ServerLevel serverLevel){
            ModVariables.MapVariables variables = ModVariables.MapVariables.get(serverLevel);
            tick(variables);
        }
        public static void tick(ModVariables.MapVariables variables){
            setTitle(variables);
            SUN_STORM_PROGRESS_BAR.setRemainingTicks(variables.timer);
        }

    }

}
