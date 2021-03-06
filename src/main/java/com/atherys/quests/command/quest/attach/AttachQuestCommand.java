package com.atherys.quests.command.quest.attach;

import com.atherys.core.command.PlayerCommand;
import com.atherys.core.command.annotation.Aliases;
import com.atherys.core.command.annotation.Children;
import com.atherys.core.command.annotation.Description;
import com.atherys.core.command.annotation.HelpCommand;
import com.atherys.quests.command.quest.CancelQuestAttachmentCommand;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.entity.living.player.Player;

import javax.annotation.Nonnull;

@Aliases("attach")
@Description("Base command for attaching quests.")
@Children({
        AttachQuestToLocationCommand.class,
        AttachQuestToItemCommand.class,
        AttachQuestToBlockCommand.class,
        CancelQuestAttachmentCommand.class
})
@HelpCommand(title = "Quest Attachment Help", prefix = "quest")
public class AttachQuestCommand implements PlayerCommand {

    @Nonnull
    @Override
    public CommandResult execute(@Nonnull Player source, @Nonnull CommandContext args) throws CommandException {
        return CommandResult.success();
    }
}
