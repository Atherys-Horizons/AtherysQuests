package com.atherys.quests.command.dialog;

import com.atherys.core.command.ParameterizedCommand;
import com.atherys.core.command.annotation.Aliases;
import com.atherys.core.command.annotation.Description;
import com.atherys.core.command.annotation.Permission;
import com.atherys.quests.AtherysQuests;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;

import javax.annotation.Nonnull;

@Aliases("set")
@Description("Attaches a dialog to an entity.")
@Permission("atherysquests.admin.dialog.attach")
public class AttachDialogCommand implements CommandExecutor, ParameterizedCommand {

    @Nonnull
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) return CommandResult.empty();

        Player player = (Player) src;
        String id = args.<String>getOne("dialogId").get();
        if (AtherysQuests.getInstance().getDialogService().getDialogFromId(id).isPresent()) {
            AtherysQuests.getInstance().getDialogAttachmentService().startAttachment(player, id);
            AtherysQuests.getInstance().getQuestMessagingService().error(player, "Right click an entity to attach the dialog.");
            return CommandResult.success();
        } else {
            AtherysQuests.getInstance().getQuestMessagingService().error(player, "Dialog with ID ", id, " does not exist.");
            return CommandResult.empty();
        }
    }

    @Override
    public CommandElement[] getArguments() {
        return new CommandElement[]{
                GenericArguments.string(Text.of("dialogId"))
        };
    }
}
