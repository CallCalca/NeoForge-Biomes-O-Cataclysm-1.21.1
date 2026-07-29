
package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;

@EventBusSubscriber
public class DataRecoveryBranchCommands extends ModCommandsCommon {
    protected static final String COMMAND_22 = "setCataclysm";

    private static int setCataclysmToFlood(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);
        ServerPlayer player = arguments.getSource().getPlayer();
        assert player != null;
        if (variables.mode != 1){
            ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.modeMustBeSetToApocalypse")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.4F                                // pitch
            );
            return 0;
        }
        if (variables.state < 2){
            ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.aGameMustBeRunning")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.4F                                // pitch
            );
            return 0;
        } //Check
        if (!variables.deletedBiomes.isEmpty()){
            ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.cannotChangeCataclusm")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.4F                                // pitch
            );
            return 0;

        } //Check

        variables.cataclysm = ModUtils.decodeCataclysmFromEnum(AllCataclysms.FLOODED);
        variables.syncData(world, true, false);
        MutableComponent cataclysm = Component.literal(variables.cataclysm)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.setCataclysmTo", cataclysm)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
        for (ServerPlayer serverPlayer : arguments.getSource().getServer().getPlayerList().getPlayers()) {
            serverPlayer.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.9F                                // pitch
            );
        }

        return 0;
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal("dataRecovery")
                        .requires(source -> false)
                        .executes(arguments -> {
                            ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()), ModUtils.buildUnknowErrorMessage());
                            return 0;
                        })
                )
        );
    }
}
