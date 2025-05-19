package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.CommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Helper class to register admin commands
 */
public class RegisterCommands {
    private static final Logger logger = Logger.getLogger(RegisterCommands.class.getName());
    
    /**
     * Register all admin commands with JDA
     * @param commandManager The command manager to register commands with
     */
    public static void registerAdminCommands(CommandManager commandManager) {
        try {
            // Add the database admin commands
            SlashCommandData orphanCleanupCommand = OrphanCleanupCommand.getCommandData();
            SlashCommandData databaseResetCommand = DatabaseResetCommand.getCommandData();
            SlashCommandData runCleanupOnStartupCommand = RunCleanupOnStartupCommand.getCommandData();
            
            // Register the commands
            commandManager.addCommand("cleanup-orphans", orphanCleanupCommand, new OrphanCleanupCommand());
            commandManager.addCommand("db-reset", databaseResetCommand, new DatabaseResetCommand());
            commandManager.addCommand("set-startup-cleanup", runCleanupOnStartupCommand, new RunCleanupOnStartupCommand());
            
            logger.info("Registered admin database commands");
        } catch (Exception e) {
            logger.severe("Error registering admin commands: " + e.getMessage());
        }
    }
}