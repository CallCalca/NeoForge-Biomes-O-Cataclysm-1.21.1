package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.PersistentData;
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
public class ModeBranchCommands extends ModCommandsCommon {
    protected static final String COMMAND_3 = "classic";
    protected static final String COMMAND_4 = "apocalypse";

    private static int setModeClassic(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);
        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
            return 0;
        }

        if (variables.mode == 1){
            variables.mode = 0;
            variables.syncData(world, true, false);
            MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ModUtils.sendChatMessage(world,
                    Component.translatable("command.biomesofcataclysms.setModeTo", modeToString)
                            .withStyle(ChatFormatting.GREEN));
            for (ServerPlayer players : world.players()){
                players.playNotifySound(
                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                        SoundSource.MASTER,                 // categoria audio
                        1F,                               // volume
                        1.4F                                // pitch
                );
            }
        }else{
            MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ModUtils.sendChatMessage(world,
                    Component.translatable("command.biomesofcataclysms.modeAlreadySet", modeToString)
                            .withStyle(ChatFormatting.YELLOW));
            Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.4F                                // pitch
            );
        }

        return 0;
    }
    private static int setModeApocalypse(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
            return 0;
        }

        if (variables.mode == 0){
            variables.mode = 1;
            variables.syncData(world, true, false);
            MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ModUtils.sendChatMessage(world,
                    Component.translatable("command.biomesofcataclysms.setModeTo", modeToString)
                            .withStyle(ChatFormatting.GREEN));
            for (ServerPlayer players : world.players()){
                players.playNotifySound(
                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                        SoundSource.MASTER,                 // categoria audio
                        1F,                               // volume
                        1.4F                                // pitch
                );
            }
        }else{
            MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ModUtils.sendChatMessage(world,
                    Component.translatable("command.biomesofcataclysms.modeAlreadySet", modeToString)
                            .withStyle(ChatFormatting.YELLOW));
            Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.4F                                // pitch
            );
        }

        return 0;
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal("setMode")
                        .then(Commands.literal(COMMAND_3) //classic
                                .executes(ModeBranchCommands::setModeClassic)
                        )
                        .then(Commands.literal(COMMAND_4) //apocalypse
                                .executes(ModeBranchCommands::setModeApocalypse)
                        )
                )
        );
    }
}
