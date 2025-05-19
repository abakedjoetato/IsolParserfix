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
            // Create command instances
            OrphanCleanupCommand orphanCleanupCommand = new OrphanCleanupCommand();
            DatabaseResetCommand databaseResetCommand = new DatabaseResetCommand();
            RunCleanupOnStartupCommand runCleanupOnStartupCommand = new RunCleanupOnStartupCommand();
            ServerCommand serverCommand = new ServerCommand();
            SftpConfigCommand sftpConfigCommand = new SftpConfigCommand();
            
            // Register the commands
            commandManager.registerCommand(orphanCleanupCommand);
            commandManager.registerCommand(databaseResetCommand);
            commandManager.registerCommand(runCleanupOnStartupCommand);
            commandManager.registerCommand(serverCommand);
            commandManager.registerCommand(sftpConfigCommand);
            
            logger.info("Registered admin database commands");
        } catch (Exception e) {
            logger.severe("Error registering admin commands: " + e.getMessage());
        }
    }
}