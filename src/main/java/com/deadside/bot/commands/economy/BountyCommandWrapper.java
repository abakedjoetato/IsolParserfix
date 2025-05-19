package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

/**
 * Wrapper for BountyCommand to implement ICommand interface
 */
public class BountyCommandWrapper implements ICommand {
    @Override
    public String getName() {
        return "bounty";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("bounty", "Place or view bounties on players")
                .addSubcommands(
                        new SubcommandData("place", "Place a bounty on a player")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "playername", "The name of the player to place a bounty on", true),
                                        new OptionData(OptionType.INTEGER, "amount", "Amount of currency to offer as bounty", true)
                                ),
                        new SubcommandData("list", "List all active bounties")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "server", "Server name (optional)", false)
                                ),
                        new SubcommandData("check", "Check bounties on a specific player")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "playername", "Player name to check", true)
                                )
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Defer reply since all commands involve database operations
        event.deferReply().queue();
        
        try {
            String subcommand = event.getSubcommandName();
            if (subcommand == null) {
                event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                        "Error",
                        "Please specify a subcommand."
                )).queue();
                return;
            }
            
            // Send a placeholder response for now
            event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                    "Bounty System",
                    "The bounty command '" + subcommand + "' is being processed. Full functionality will be available soon."
            )).queue();
            
        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Error",
                    "An error occurred executing the bounty command."
            )).queue();
        }
    }

    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        // No autocomplete for now
        return List.of();
    }
}