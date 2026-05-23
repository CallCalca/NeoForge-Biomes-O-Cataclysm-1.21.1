package net.calca.biomesofcataclysms.bar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.UUID;

public class ProgressBar {
    private final ServerBossEvent bossBar;
    private int maxValue;

    public ProgressBar(int maxTicks, BossEvent.BossBarColor color, String title) {
        this.maxValue = Math.max(1, maxTicks);
        this.bossBar = new ServerBossEvent(
                Component.literal(title),
                color,
                BossEvent.BossBarOverlay.PROGRESS
        );
        this.bossBar.setVisible(true);
        setRemainingTicks(maxTicks);
    }

    public UUID getId(){
        return bossBar.getId();
    }

    public void setOverlay(BossEvent.BossBarOverlay overlay){
        bossBar.setOverlay(overlay);
    }

    public void startFor(ServerPlayer player) {
        if (!bossBar.getPlayers().contains(player)){
            bossBar.addPlayer(player);
        }
        setRemainingTicks(maxValue);
    }

    public void startForAllPlayers(ServerLevel serverLevel) {
        for (ServerPlayer serverPlayer : serverLevel.players()){
            bossBar.addPlayer(serverPlayer);
        }
        setRemainingTicks(maxValue);
    }

    public void setTitle(Component title) {
        bossBar.setName(title);
    }

    public void setColor(BossEvent.BossBarColor color) {
        bossBar.setColor(color);
    }

    public void setMaxValue (int maxValue){
        this.maxValue = maxValue;
    }

    public void setRemainingTicks(int remainingTicks) {
        float progress = Math.max(0.0f, Math.min(1.0f, remainingTicks / (float) maxValue));
        bossBar.setProgress(progress);
    }

    //Return value is form 0.0 to 1.0;
    public float calculateProgress(int maxValue, int currentValue){
        return Math.max(0.0f, Math.min(1.0f, currentValue / (float) maxValue));
    }

    public void setProgress(float progress){
        bossBar.setProgress(progress);
    }

    public void removeFroPlayer(ServerPlayer player) {
        bossBar.removePlayer(player);
    }

    public void removeForAllPlayers() {
        bossBar.removeAllPlayers();
    }

    public ServerBossEvent getBossBar() {
        return bossBar;
    }
}
