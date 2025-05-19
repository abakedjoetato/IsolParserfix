package com.deadside.bot.commands.server;

import com.deadside.bot.commands.ICommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

/**
 * Wrapper for SetBountyChannelCommand
 */
public class SetBountyChannelCommandWrapper implements ICommand {
    @Override
    public String getName() {
        return "setbountychannel";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("setbountychannel", "Set the channel for bounty announcements")
                .addOptions(
                        new OptionData(OptionType.STRING, "server", "The server to set the bounty channel for", true),
                        new OptionData(OptionType.CHANNEL, "channel", "The channel to use for bounty announcements", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Hand off execution to the main command implementation
        SetBountyChannelCommand.execute(event);
    }

    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        // No auto-complete needed for this command
        return List.of();
    }
}