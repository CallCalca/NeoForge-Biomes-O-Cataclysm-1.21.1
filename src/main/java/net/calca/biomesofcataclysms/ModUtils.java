package net.calca.biomesofcataclysms;

import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.RuntimeData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.data.chunk.ChunkState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.Map;
import java.util.Objects;

import static net.calca.biomesofcataclysms.management.chunk.ChunkQueueManager.getOrCreateDimChunkMap;

public class ModUtils {
    public static boolean isPlayerInGame(ServerPlayer player) {
        return player != null && !player.isSpectator();
    }

    public static void sendChatMessage(LevelAccessor level, Component textComponent) {
        for (Player player : level.players()) {
            player.sendSystemMessage(textComponent);
        }
    }

    public static void executeCommandAsEntity(ServerPlayer player, String command){
        if (player == null) return;
        Objects.requireNonNull(player.getServer()).getCommands().performPrefixedCommand(
                new CommandSourceStack(CommandSource.NULL,
                        player.position(),
                        player.getRotationVector(),
                        player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null, 4,
                        player.getName().getString(),
                        player.getDisplayName(),
                        Objects.requireNonNull(player.level().getServer()),
                        player),
                command
        );
    }

    public static void sendLocalChatMessageTo(Player player, Component textComponent){
        if (player == null) return;
        if (!player.level().isClientSide())
            player.displayClientMessage(textComponent, false);
    }

    public static void sendLocalActionBarMessageTo(Player player, Component textComponent){
        if (player == null) return;
        if (!player.level().isClientSide())
            player.displayClientMessage(textComponent, true);
    }

    public static boolean isAGameAlreadyStarted(PersistentData.MapVariables variables){
        if (variables.tickToNextCataclysm == variables.tickDelayBetweenCataclysm && variables.biomesAffected == 0){
            return false;
        }else{
            return true;
        }
    }

    public static MutableComponent buildErrorMessage(boolean fatalError, int errorNumber, Component errorPoint, MutableComponent errorDescription){
        String key;
        ChatFormatting chatFormatting;
        if (fatalError){
            key = "error.biomesofcataclysms.fatalError";
            chatFormatting = ChatFormatting.RED;
        }else{
            key = "error.biomesofcataclysms.somethingWentWrong";
            chatFormatting = ChatFormatting.GOLD;
        }
        return Component.empty()
                .append(Component.translatable("error.biomesofcataclysms.errorInfo", errorNumber, errorPoint)
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal(" ")
                .append(Component.translatable(key)
                        .withStyle(chatFormatting))
                        .withStyle(style -> style.withBold(false)))
                .append(Component.literal("\n"))
                .append(errorDescription.copy()
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)));
    }
    public static MutableComponent buildUnknowErrorMessage(){
        return
                Component.translatable("error.biomesofcataclysms.somethingWentWrong")
                        .withStyle(ChatFormatting.DARK_RED)
                        .append(Component.literal(" "))
                        .append(Component.translatable("error.biomesofcataclysms.errorInfo", "NULL", "UNKOWN")
                                .withStyle(ChatFormatting.RED))
                        .append(Component.literal(" "))
                        .append(Component.translatable("error.biomesofcataclysms.unknow")
                                .withStyle(ChatFormatting.YELLOW));

    }

    public static MutableComponent buildWarningMessage(boolean criticalWarning, Component targetPoint, MutableComponent errorDescription){
        String key;
        ChatFormatting chatFormatting;
        if (criticalWarning){
            key = "error.biomesofcataclysms.criticalWarning";
            chatFormatting = ChatFormatting.GOLD;
        }else{
            key = "error.biomesofcataclysms.warning";
            chatFormatting = ChatFormatting.YELLOW;
        }
        return Component.empty()
                .append(Component.translatable(key, targetPoint)
                        .withStyle(chatFormatting, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(errorDescription.copy()
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)));
    }
    public static MutableComponent buildSuccessMessage(Component targetPoint, MutableComponent successDescription,
                                                       String procedureUsed, int timeElapsed){
        ChatFormatting chatFormatting;
        chatFormatting = ChatFormatting.GREEN;
        MutableComponent infos = Component.translatable("success.biomesofcataclysms.successInfos", procedureUsed, timeElapsed);

        return Component.empty()
                .append(Component.translatable("success.biomesofcataclysms.operationSuccess", targetPoint)
                        .withStyle(chatFormatting, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(successDescription.copy()
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)))
                .append(Component.literal("\n"))
                .append(infos.copy()
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)));
    }

    public static void playLocalErrorSound(Player player){
        if (player == null) return;
        player.playNotifySound(
                SoundEvents.BEACON_DEACTIVATE, // suono
                SoundSource.MASTER,                 // categoria audio
                1F,                               // volume
                0.4F                                // pitch
        );

    }
    public static void playLocalBiomeAffectedSound(Player player){
        if (player == null) return;
        player.playNotifySound(
                SoundEvents.WITHER_DEATH,
                SoundSource.MASTER,
                0.4F,
                0.6F
        );

    }

    public static String decodeCataclysmFromEnum(AllCataclysms allCataclysms){
        if (allCataclysms == AllCataclysms.DESTROYED){
            return Component.translatable("possibleCataclysm.biomesofcataclysms.destroyed").getString();
        } else if (allCataclysms == AllCataclysms.FLOODED) {
            return Component.translatable("possibleCataclysm.biomesofcataclysms.flooded").getString();
        } else if (allCataclysms == AllCataclysms.SUN_BURNT) {
            return Component.translatable("possibleCataclysm.biomesofcataclysms.sun_burnt").getString();
        }else if (allCataclysms == AllCataclysms.ETERNAL_ECLIPSE) {
            return Component.translatable("possibleCataclysm.biomesofcataclysms.eternal_eclipse").getString();
        }else{
            return ModUtils.buildErrorMessage(false, 10, Component.literal("decodeCataclysmFromEnum"),
                    Component.translatable("error.biomesofcataclysms.error10")).getString();
        }
    }
    public static AllCataclysms decodeCataclysmFromString(String cataclysm){
        if (Objects.equals(cataclysm, Component.translatable("possibleCataclysm.biomesofcataclysms.destroyed").getString())){
            return AllCataclysms.DESTROYED;
        } else if (Objects.equals(cataclysm, Component.translatable("possibleCataclysm.biomesofcataclysms.flooded").getString())) {
            return AllCataclysms.FLOODED;
        } else if (Objects.equals(cataclysm, Component.translatable("possibleCataclysm.biomesofcataclysms.sun_burnt").getString())) {
            return AllCataclysms.SUN_BURNT;
        } else if (Objects.equals(cataclysm, Component.translatable("possibleCataclysm.biomesofcataclysms.eternal_eclipse").getString())) {
            return AllCataclysms.ETERNAL_ECLIPSE;
        }else{
            return null;
        }
    }


    public static boolean hasChunksInRadius(ServerLevel level, ChunkPos center, int radius) {
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(level.dimension());
        if (registry == null || registry.isEmpty()) return false;

        int radiusSq = radius * radius;

        for (ChunkInstance mod : registry.values()) {
            if (mod.state == ChunkState.DONE) continue;

            int dx = mod.pos.x - center.x;
            int dz = mod.pos.z - center.z;

            if (dx * dx + dz * dz <= radiusSq) {
                return true;
            }
        }

        return false;
    }
    public static ChunkInstance getOrCreateChunkMod(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> dimMap = getOrCreateDimChunkMap(dim);

        long packed = pos.toLong();
        ChunkInstance mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkInstance(dim, pos, level);
            dimMap.put(packed, mod);
        }

        return mod;
    }
}
