package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Command for managing bounties
 */
public class BountyCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(BountyCommand.class);
    
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
            
            switch (subcommand) {
                case "place":
                    handlePlaceBounty(event);
                    break;
                case "list":
                    handleListBounties(event);
                    break;
                case "check":
                    handleCheckBounties(event);
                    break;
                default:
                    event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                            "Unknown Subcommand",
                            "That subcommand is not recognized."
                    )).queue();
            }
        } catch (Exception e) {
            logger.error("Error executing bounty command", e);
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Error",
                    "An error occurred: " + e.getMessage()
            )).queue();
        }
    }
    
    private void handlePlaceBounty(SlashCommandInteractionEvent event) {
        String targetName = event.getOption("playername", OptionMapping::getAsString);
        int amount = event.getOption("amount", 0, OptionMapping::getAsInt);
        
        if (targetName == null || amount <= 0) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Invalid Parameters",
                    "Please provide a valid player name and a positive amount."
            )).queue();
            return;
        }
        
        // Send placeholder response (will be connected to database in future)
        event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed(
                "Bounty Placed",
                String.format("You have placed a **%d coin** bounty on **%s**.\n\n" +
                        "This bounty will be announced in the bounty channel.",
                        amount, targetName)
        )).queue();
    }
    
    private void handleListBounties(SlashCommandInteractionEvent event) {
        String serverName = event.getOption("server", OptionMapping::getAsString);
        
        // Send placeholder response
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Active Bounties",
                "Bounty listing functionality is coming soon!" + 
                (serverName != null ? " Server filter: " + serverName : "")
        )).queue();
    }
    
    private void handleCheckBounties(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("playername", OptionMapping::getAsString);
        
        if (playerName == null || playerName.isEmpty()) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Missing Player Name",
                    "Please provide a player name to check."
            )).queue();
            return;
        }
        
        // Send placeholder response
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Bounty Check",
                String.format("Checking bounties for player: **%s**\n\n" +
                        "Bounty check functionality is coming soon!", playerName)
        )).queue();
    }

    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        // No autocomplete for now
        return List.of();
    }
}