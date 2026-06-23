package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.ModVariables;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static net.calca.biomesofcataclysms.ModUtils.decodeCataclysmFromEnum;
import static net.calca.biomesofcataclysms.event.ModServerEvents.flushRuntimeToSaved;

@EventBusSubscriber
public class SingleCommands extends ModCommandsCommon {
    protected static final String COMMAND_0 = "startOrResume";
    protected static final String COMMAND_1 = "pause";
    protected static final String COMMAND_2 = "reset";

    private static int startOrResume(CommandContext<CommandSourceStack> arguments){
        ServerPlayer serverPlayer = arguments.getSource().getPlayer();
        assert serverPlayer != null;
        MinecraftServer server = arguments.getSource().getServer();
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        if (variables.tickToNextCataclysm == variables.tickDelayBetweenCataclysm && variables.biomesAffected == 0){
            if (variables.state == 0){
                variables.state = 1;
                gameAboutToStartSettingsMessage(world, serverPlayer, variables);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.playNotifySound(
                            SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                            SoundSource.MASTER,                 // categoria audio
                            1F,                               // volume
                            0.8F                                // pitch
                    );
                }
            } else if (variables.state == 1) {
                variables.state = 2;
                ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.start")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                if (variables.totalBiomes == 0){
                    variables.generateFullBiomeList(world);
                }

                if (variables.mode == 0){ //Classic
                    variables.cataclysm = decodeCataclysmFromEnum(AllCataclysms.DESTROYED);

                } else if (variables.mode == 1) { //Apocalypse
                    settingUpApocalypseMode(variables, world);
                }
                settingUpPcPowerComponents(variables, world, server);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.playNotifySound(
                            SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                            SoundSource.MASTER,                 // categoria audio
                            1F,                               // volume
                            1.2F                                // pitch
                    );
                }
            }
        }else if (variables.state == 1){
            variables.state = 2;
            ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.resume")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Resuming
            settingUpPcPowerComponents(variables, world, server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.playNotifySound(
                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                        SoundSource.MASTER,                 // categoria audio
                        1F,                               // volume
                        1.2F                                // pitch
                );
            }
        } else if (variables.state == 2) {
            ModUtils.sendLocalChatMessageTo(serverPlayer, Component.translatable("command.biomesofcataclysms.gameAlreadyStarted")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            arguments.getSource().getPlayer().playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.4F                                // pitch
            );
        }else{
            ModUtils.sendLocalChatMessageTo(serverPlayer,
                    ModUtils.buildErrorMessage(
                            true,
                            7,
                            Component.literal(COMMAND_0 + "[C]"),
                            Component.translatable("error.biomesofcataclysms.error7")
                    ));
            ModUtils.playLocalErrorSound(serverPlayer);
        }

        variables.syncData(world, true, false);

        return 0;
    }
    private static int pause(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        if (variables.state == 2){
            ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.pause")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

            MinecraftServer server = arguments.getSource().getServer();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.playNotifySound(
                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                        SoundSource.MASTER,                 // categoria audio
                        1F,                               // volume
                        0.6F                                // pitch
                );
            }

            for (ServerLevel serverLevel : server.getAllLevels()) {
                flushRuntimeToSaved(serverLevel);

                if (!ModUtils.isAGameAlreadyStarted(variables) && variables.state == 1){
                    variables.state = 0;
                }
            }

            variables.state = 1;
            variables.syncData(world, true, false);
        }else{
            ServerPlayer player = arguments.getSource().getPlayer();
            assert player != null;
            ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.alreadyPaused")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.4F                                // pitch
            );
        }
        return 0;
    }
    private static int reset(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ServerPlayer player = arguments.getSource().getPlayer();
        assert player != null;
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.reset")
                .withStyle(ChatFormatting.RED)); //Starting
        if (variables.state != 0 && variables.state != 1) {
            ModUtils.executeCommandAsEntity(player, "biomesOfCataclysms " + COMMAND_1);
        }else{
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.6F                                // pitch
            );
        }

        return 0;
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal(COMMAND_0) //startOrResume
                        .executes(SingleCommands::startOrResume))
                .then(Commands.literal(COMMAND_1) //pause
                        .executes(SingleCommands::pause))
                .then(Commands.literal(COMMAND_2) //reset
                        .executes(SingleCommands::reset)));
    }
}
