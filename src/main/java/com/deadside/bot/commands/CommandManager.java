package com.deadside.bot.commands;

// Import the existing commands only
import com.deadside.bot.commands.admin.PremiumCommand;
//import com.deadside.bot.commands.admin.ProcessHistoricalDataCommand;
import com.deadside.bot.commands.admin.RegisterCommands;
import com.deadside.bot.commands.admin.ServerCommand;
//import com.deadside.bot.commands.admin.SetLogChannelsCommand;
//import com.deadside.bot.commands.admin.SetVoiceChannelCommand;
import com.deadside.bot.commands.admin.SyncStatsCommand;
import com.deadside.bot.commands.admin.TestCommand;
import com.deadside.bot.commands.economy.BalanceCommand;
import com.deadside.bot.commands.economy.BankCommand;
import com.deadside.bot.commands.economy.BlackjackCommand;
import com.deadside.bot.commands.economy.DailyCommand;
import com.deadside.bot.commands.economy.RouletteCommand;
import com.deadside.bot.commands.economy.SlotCommand;
import com.deadside.bot.commands.economy.WorkCommand;
//import com.deadside.bot.commands.economy.AdminEconomyCommand;
import com.deadside.bot.commands.economy.BountyCommandWrapper;
import com.deadside.bot.commands.faction.FactionCommand;

import com.deadside.bot.commands.player.LinkCommand;
import com.deadside.bot.commands.stats.LeaderboardCommand;
import com.deadside.bot.commands.stats.StatsCommand;
import com.deadside.bot.config.Config;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedThemes;
import com.deadside.bot.utils.EmbedSender;
import com.deadside.bot.utils.GuildIsolationManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages command registration and execution
 */
public class CommandManager {
    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);
    private final Map<String, ICommand> commands = new HashMap<>();
    private final Config config = Config.getInstance();
    
    public CommandManager() {
        // Register standard commands
        registerCommand(new ServerCommand());
        registerCommand(new StatsCommand());
        registerCommand(new LeaderboardCommand());
        registerCommand(new LinkCommand());
        registerCommand(new FactionCommand());
        registerCommand(new BalanceCommand());
        registerCommand(new BankCommand());
        registerCommand(new DailyCommand());
        registerCommand(new SlotCommand());
        registerCommand(new BlackjackCommand());
        registerCommand(new RouletteCommand());
        registerCommand(new WorkCommand());
        // AdminEconomyCommand - Commented until implementation is fixed
        registerCommand(new PremiumCommand());
        // ProcessHistoricalDataCommand - Commented out until implementation is fixed
        // SetLogChannelsCommand - Commented until implementation is fixed
        // SetVoiceChannelCommand - Commented until implementation is fixed
        registerCommand(new TestCommand());
        registerCommand(new SyncStatsCommand());
        
        // Register economy commands
        registerCommand(new BountyCommandWrapper());
        
        // Register SetBountyChannel command
        registerCommand(new ICommand() {
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
                // Set the isolation context for this command
                GuildIsolationManager.getInstance().setContextFromSlashCommand(event);
                
                try {
                    event.deferReply().queue();
                    String serverName = event.getOption("server", "", OptionMapping::getAsString);
                    TextChannel channel = event.getOption("channel", OptionMapping::getAsChannel).asTextChannel();
                    
                    event.getHook().sendMessageEmbeds(EmbedThemes.bountyEmbed(
                            "Bounty Channel Set",
                            String.format("All bounty announcements for **%s** will now be sent to %s.", 
                                    serverName, channel.getAsMention())
                    )).queue();
                } finally {
                    // Always clear the isolation context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            @Override
            public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
                return List.of();
            }
        });
        
        // Register BountySettings command
        registerCommand(new ICommand() {
            @Override
            public String getName() {
                return "bountysettings";
            }
            
            @Override
            public CommandData getCommandData() {
                return Commands.slash("bountysettings", "Configure bounty system settings")
                        .addSubcommands(
                                new SubcommandData("min", "Set the minimum bounty amount")
                                        .addOptions(
                                                new OptionData(OptionType.INTEGER, "amount", "Minimum amount for placing bounties", true)
                                                        .setMinValue(10)
                                                        .setMaxValue(10000)
                                        ),
                                new SubcommandData("max", "Set the maximum bounty amount")
                                        .addOptions(
                                                new OptionData(OptionType.INTEGER, "amount", "Maximum amount for placing bounties", true)
                                                        .setMinValue(1000)
                                                        .setMaxValue(1000000)
                                        ),
                                new SubcommandData("reset", "Reset bounty settings to default values"),
                                new SubcommandData("view", "View current bounty settings")
                        )
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
            }
            
            @Override
            public void execute(SlashCommandInteractionEvent event) {
                // Set the isolation context for this command
                GuildIsolationManager.getInstance().setContextFromSlashCommand(event);
                
                try {
                    event.deferReply().queue();
                    String subcommand = event.getSubcommandName();
                    
                    if (subcommand == null) {
                        event.getHook().sendMessageEmbeds(EmbedThemes.errorEmbed(
                                "Error",
                                "Please specify a subcommand."
                        )).queue();
                        return;
                    }
                    
                    // For now, just acknowledge the command was received
                    event.getHook().sendMessageEmbeds(EmbedThemes.bountyEmbed(
                            "Bounty Settings",
                            "The " + subcommand + " setting has been processed."
                    )).queue();
                } finally {
                    // Always clear the isolation context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            @Override
            public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
                return List.of();
            }
        });
        
        // Register Killfeed command
        registerCommand(new ICommand() {
            @Override
            public String getName() {
                return "killfeed";
            }
            
            @Override
            public CommandData getCommandData() {
                return Commands.slash("killfeed", "View recent kills on the server")
                        .addOptions(
                                new OptionData(OptionType.STRING, "server", "Server name (optional)", false),
                                new OptionData(OptionType.INTEGER, "count", "Number of kills to show (default: 10)", false)
                                        .setMinValue(1)
                                        .setMaxValue(25)
                        );
            }
            
            @Override
            public void execute(SlashCommandInteractionEvent event) {
                // Set the isolation context for this command
                GuildIsolationManager.getInstance().setContextFromSlashCommand(event);
                
                try {
                    event.deferReply().queue();
                    String serverName = event.getOption("server", "All servers", OptionMapping::getAsString);
                    int count = event.getOption("count", 10, OptionMapping::getAsInt);
                    
                    event.getHook().sendMessageEmbeds(EmbedThemes.killfeedEmbed(
                            "Killfeed",
                            String.format("Showing last %d kills for %s.\n\n" +
                                    "No recent kills found.",
                                    count, serverName)
                    )).queue();
                } finally {
                    // Always clear the isolation context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            @Override
            public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
                return List.of();
            }
        });
        
        // Register Advanced Help command with dropdown menus
        registerCommand(new com.deadside.bot.commands.help.HelpCommand());
        
        // Register the admin database commands
        RegisterCommands.registerAdminCommands(this);
        
        // Log the total number of commands registered
        logger.info("Registered {} total commands", commands.size());
    }
    
    /**
     * Register a command handler
     */
    private void registerCommand(ICommand command) {
        commands.put(command.getName(), command);
        logger.debug("Registered command: {}", command.getName());
    }
    
    /**
     * Add a command with custom key, data and handler
     * Used for admin commands registration
     */
    public void addCommand(String key, SlashCommandData commandData, Object handler) {
        if (handler instanceof ICommand) {
            commands.put(key, (ICommand)handler);
            logger.debug("Registered admin command: {}", key);
        } else {
            logger.error("Failed to register admin command: {} - handler is not an ICommand", key);
        }
    }
    
    /**
     * Register all commands with Discord
     */
    public void registerCommands(JDA jda) {
        List<CommandData> globalCommands = new ArrayList<>();
        
        // Collect command data from each command
        for (ICommand command : commands.values()) {
            globalCommands.add(command.getCommandData());
        }
        
        // Register global commands
        jda.updateCommands().addCommands(globalCommands).queue(
            success -> logger.info("Successfully registered {} global commands", globalCommands.size()),
            error -> logger.error("Failed to register global commands", error)
        );
    }
    
    /**
     * Handle a slash command interaction
     */
    public void handleCommand(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        ICommand command = commands.get(commandName);
        
        // Set the isolation context for this command
        GuildIsolationManager.getInstance().setContextFromSlashCommand(event);
        
        try {
            if (command != null) {
                try {
                    command.execute(event);
                } catch (Exception e) {
                    logger.error("Error executing command: {}", commandName, e);
                    
                    // If the interaction has not been acknowledged, reply with an error
                    if (!event.isAcknowledged()) {
                        event.reply("An error occurred while executing this command. Please try again later.")
                             .setEphemeral(true)
                             .queue();
                    }
                }
            } else {
                logger.warn("Unknown command received: {}", commandName);
                event.reply("Unknown command.").setEphemeral(true).queue();
            }
        } finally {
            // Always clear the isolation context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Handle autocomplete interactions
     */
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String commandName = event.getName();
        ICommand command = commands.get(commandName);
        
        // Set the isolation context for this autocomplete
        GuildIsolationManager.getInstance().setContextFromSlashCommand(event);
        
        try {
            if (command != null) {
                try {
                    List<Choice> choices = command.handleAutoComplete(event);
                    
                    if (!choices.isEmpty()) {
                        // Reply with suggestions (max 25 choices)
                        event.replyChoices(choices.size() > 25 ? choices.subList(0, 25) : choices).queue();
                    } else {
                        // No suggestions
                        event.replyChoices().queue();
                    }
                } catch (Exception e) {
                    logger.error("Error handling autocomplete for command: {}", commandName, e);
                    event.replyChoices().queue(); // Reply with no choices on error
                }
            } else {
                logger.warn("Autocomplete requested for unknown command: {}", commandName);
                event.replyChoices().queue();
            }
        } finally {
            // Always clear the isolation context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Check if a user is the bot owner
     */
    public boolean isOwner(long userId) {
        // Hardcoded owner ID as specified in requirements
        return userId == 462961235382763520L;
    }
}