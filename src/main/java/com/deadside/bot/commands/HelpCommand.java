package com.deadside.bot.commands;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to display help information about other commands
 */
public class HelpCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(HelpCommand.class);

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("help", "Get help with Deadside bot commands")
                .addOptions(
                        new OptionData(OptionType.STRING, "category", "Command category to get help for", false)
                                .addChoice("Economy", "economy")
                                .addChoice("Stats", "stats")
                                .addChoice("Faction", "faction")
                                .addChoice("Server", "server")
                                .addChoice("Admin", "admin")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Defer reply to avoid timeout
        event.deferReply().queue();
        
        try {
            String category = event.getOption("category", OptionMapping::getAsString);
            
            if (category == null) {
                // Show general help
                showGeneralHelp(event);
            } else {
                // Show category-specific help
                switch (category.toLowerCase()) {
                    case "economy":
                        showEconomyHelp(event);
                        break;
                    case "stats":
                        showStatsHelp(event);
                        break;
                    case "faction":
                        showFactionHelp(event);
                        break;
                    case "server":
                        showServerHelp(event);
                        break;
                    case "admin":
                        showAdminHelp(event);
                        break;
                    default:
                        event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                                "Invalid Category",
                                "Please select a valid category from the dropdown menu."
                        )).queue();
                }
            }
        } catch (Exception e) {
            logger.error("Error executing help command", e);
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Error",
                    "An error occurred while processing your request: " + e.getMessage()
            )).queue();
        }
    }

    private void showGeneralHelp(SlashCommandInteractionEvent event) {
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Deadside Bot Help",
                "Welcome to Deadside Discord Bot! Here are the available command categories:\n\n" +
                "• **Economy** - Commands for managing your coins, gambling, and bounties\n" +
                "• **Stats** - Check your player statistics and leaderboards\n" +
                "• **Faction** - Manage your player faction\n" +
                "• **Server** - Server status and configuration\n" +
                "• **Admin** - Commands for server administrators\n\n" +
                "Use `/help [category]` to get more information about specific command categories."
        )).queue();
    }
    
    private void showEconomyHelp(SlashCommandInteractionEvent event) {
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Economy Commands",
                "• `/balance` - Check your coin balance\n" +
                "• `/bank` - Manage your bank account\n" +
                "• `/daily` - Collect your daily coins\n" +
                "• `/work` - Earn coins by working\n" +
                "• `/blackjack` - Play blackjack for coins\n" +
                "• `/roulette` - Play roulette for coins\n" +
                "• `/slot` - Play slot machine for coins\n" +
                "• `/bounty` - Place or view bounties on players"
        )).queue();
    }
    
    private void showStatsHelp(SlashCommandInteractionEvent event) {
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Stats Commands",
                "• `/stats` - View your player statistics\n" +
                "• `/leaderboard` - View server leaderboards\n" +
                "• `/link` - Link your Discord account to your in-game profile"
        )).queue();
    }
    
    private void showFactionHelp(SlashCommandInteractionEvent event) {
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Faction Commands",
                "• `/faction create` - Create a new faction\n" +
                "• `/faction invite` - Invite a player to your faction\n" +
                "• `/faction accept` - Accept a faction invitation\n" +
                "• `/faction leave` - Leave your current faction\n" +
                "• `/faction info` - View information about a faction"
        )).queue();
    }
    
    private void showServerHelp(SlashCommandInteractionEvent event) {
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Server Commands",
                "• `/server info` - View server information\n" +
                "• `/server status` - Check server status\n" +
                "• `/server players` - View current online players\n" +
                "• `/setbountychannel` - Set channel for bounty announcements"
        )).queue();
    }
    
    private void showAdminHelp(SlashCommandInteractionEvent event) {
        event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed(
                "Admin Commands",
                "• `/premium` - Manage premium status\n" +
                "• `/admineconomy` - Manage player economy\n" +
                "• `/processhistoricaldata` - Process historical game data\n" +
                "• `/setlogchannels` - Configure log channels\n" +
                "• `/setvoicechannel` - Set voice channel for player count\n" +
                "• `/test` - Test bot functionality"
        )).queue();
    }

    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getFocusedOption().getName().equals("category")) {
            String input = event.getFocusedOption().getValue().toLowerCase();
            List<Choice> choices = new ArrayList<>();
            
            if ("economy".startsWith(input)) choices.add(new Choice("Economy", "economy"));
            if ("stats".startsWith(input)) choices.add(new Choice("Stats", "stats"));
            if ("faction".startsWith(input)) choices.add(new Choice("Faction", "faction"));
            if ("server".startsWith(input)) choices.add(new Choice("Server", "server"));
            if ("admin".startsWith(input)) choices.add(new Choice("Admin", "admin"));
            
            return choices;
        }
        
        return List.of();
    }
}