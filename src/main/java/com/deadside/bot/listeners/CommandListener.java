package com.deadside.bot.listeners;

import com.deadside.bot.commands.CommandManager;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for Discord command events
 */
public class CommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
    private final CommandManager commandManager;
    
    public CommandListener(CommandManager commandManager) {
        this.commandManager = commandManager;
    }
    
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Bot is ready! Logged in as: {}", event.getJDA().getSelfUser().getName());
        logger.info("Connected to {} guilds", event.getJDA().getGuilds().size());
    }
    
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        // Log command execution
        String guildName = event.getGuild() != null ? event.getGuild().getName() : "DM";
        String userName = event.getUser().getName();
        String commandName = event.getName();
        String subcommandName = event.getSubcommandName() != null ? " " + event.getSubcommandName() : "";
        
        logger.info("Command executed: /{}{} by {} in {}", 
                commandName, subcommandName, userName, guildName);
        
        // Handle the command
        commandManager.handleCommand(event);
    }
    
    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        // Handle autocomplete by passing to command manager
        commandManager.handleAutoComplete(event);
    }
}
