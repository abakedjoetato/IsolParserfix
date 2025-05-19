package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.sftp.SftpConnector;
import com.deadside.bot.utils.EmbedUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to synchronize weapon kill statistics with player kill counts
 * This helps fix statistical inconsistencies that may have occurred
 */
public class SyncStatsCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(SyncStatsCommand.class);
    private final JDA jda;
    private final SftpConnector sftpConnector;
    private final PlayerRepository playerRepository;
    private final GameServerRepository gameServerRepository;
    
    public SyncStatsCommand() {
        // Get instances from other parts of the application
        this.jda = null; // This will be populated when the command is executed
        this.sftpConnector = new SftpConnector();
        this.playerRepository = new PlayerRepository();
        this.gameServerRepository = new GameServerRepository();
    }
    
    @Override
    public String getName() {
        return "syncstats";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash("syncstats", "Synchronize weapon kill statistics with player kill counts")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Defer reply to allow for potentially long operation
        event.deferReply().queue();
        
        try {
            // Create CSV parser with the appropriate dependencies
            DeadsideCsvParser csvParser = new DeadsideCsvParser(
                    event.getJDA(), 
                    sftpConnector,
                    playerRepository,
                    gameServerRepository
            );
            
            // Run synchronization in a separate thread to avoid blocking
            Thread syncThread = new Thread(() -> {
                try {
                    // Run the synchronization
                    int updatedCount = csvParser.syncPlayerStatistics();
                    
                    if (updatedCount > 0) {
                        // Success - players were updated
                        event.getHook().sendMessageEmbeds(
                                EmbedUtils.successEmbed(
                                        "Statistics Synchronized",
                                        "Successfully synchronized statistics for " + updatedCount + " players.\n\n" +
                                        "Weapon kill counts and total kill counts are now consistent across all player records."
                                )
                        ).queue();
                    } else if (updatedCount == 0) {
                        // No players needed updating
                        event.getHook().sendMessageEmbeds(
                                EmbedUtils.infoEmbed(
                                        "Statistics Already Synchronized",
                                        "All player statistics are already synchronized. No updates were needed."
                                )
                        ).queue();
                    } else {
                        // Error occurred
                        event.getHook().sendMessageEmbeds(
                                EmbedUtils.errorEmbed(
                                        "Synchronization Error",
                                        "An error occurred while trying to synchronize player statistics. " +
                                        "Check the server logs for more details."
                                )
                        ).queue();
                    }
                } catch (Exception e) {
                    logger.error("Error during stats synchronization", e);
                    event.getHook().sendMessageEmbeds(
                            EmbedUtils.errorEmbed(
                                    "Synchronization Error",
                                    "An error occurred: " + e.getMessage()
                            )
                    ).queue();
                }
            });
            
            // Start the thread
            syncThread.setName("StatsSyncThread");
            syncThread.start();
            
        } catch (Exception e) {
            logger.error("Error executing syncstats command", e);
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed(
                            "Command Error",
                            "An error occurred while executing the command: " + e.getMessage()
                    )
            ).queue();
        }
    }
}