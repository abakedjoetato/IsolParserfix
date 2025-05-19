package com.deadside.bot.parsers;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.sftp.SftpConnector;
import com.deadside.bot.utils.EmbedThemes;
import com.deadside.bot.utils.ParserStateManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Processes historical data from CSV files with progress tracking
 */
public class HistoricalDataProcessor {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalDataProcessor.class);
    private final JDA jda;
    private final DeadsideCsvParser csvParser;
    
    /**
     * Create a historical data processor
     */
    public HistoricalDataProcessor(JDA jda, DeadsideCsvParser csvParser) {
        this.jda = jda;
        this.csvParser = csvParser;
    }
    
    /**
     * Process historical data for a server with progress updates
     */
    public CompletableFuture<Void> processHistoricalData(SlashCommandInteractionEvent event, GameServer server) {
        logger.info("Starting historical data processing for server: {}", server.getName());
        
        // Set historical data processing flag
        ParserStateManager.setProcessingHistoricalData(true);
        
        // Create a completable future to track processing completion
        CompletableFuture<Void> processingFuture = new CompletableFuture<>();
        
        try {
            // Send initial progress message
            event.getHook().sendMessageEmbeds(
                    EmbedThemes.progressEmbed("Historical Data Processing",
                            "Starting to process historical data for server **" + server.getName() + "**\n" +
                            "This may take several minutes depending on the amount of data.")
            ).queue(message -> {
                // Start processing in separate thread to not block the main thread
                CompletableFuture.runAsync(() -> {
                    try {
                        // Process the data and track stats
                        AtomicInteger killCount = new AtomicInteger(0);
                        AtomicInteger deathCount = new AtomicInteger(0);
                        AtomicInteger errorCount = new AtomicInteger(0);
                        
                        // Process historical data through the CSV parser
                        csvParser.processHistoricalData(server, killCount, deathCount, errorCount);
                        
                        // Update the message with final results
                        message.editMessageEmbeds(
                                EmbedThemes.historicalDataEmbed("Historical Data Processing Complete",
                                        "Successfully processed historical data for server **" + server.getName() + "**\n\n" +
                                        "**Statistics:**\n" +
                                        "• Kills processed: " + killCount.get() + "\n" +
                                        "• Deaths processed: " + deathCount.get() + "\n" +
                                        (errorCount.get() > 0 ? "• Errors encountered: " + errorCount.get() + "\n" : "") +
                                        "\nPlayer statistics have been updated accordingly.")
                        ).queue();
                        
                        // Send a notification to the server log channel if configured
                        notifyServerLogChannel(server, killCount.get(), deathCount.get());
                        
                        // Complete the future
                        processingFuture.complete(null);
                    } catch (Exception e) {
                        logger.error("Error processing historical data for server {}", server.getName(), e);
                        
                        // Update message with error
                        message.editMessageEmbeds(
                                EmbedThemes.errorEmbed("Historical Data Processing Error",
                                        "An error occurred while processing historical data for server **" + 
                                        server.getName() + "**\n\n" +
                                        "Error: " + e.getMessage())
                        ).queue();
                        
                        // Complete the future exceptionally
                        processingFuture.completeExceptionally(e);
                    } finally {
                        // Always reset the processing flag when done
                        ParserStateManager.setProcessingHistoricalData(false);
                    }
                });
            });
        } catch (Exception e) {
            logger.error("Error starting historical data processing for server {}", server.getName(), e);
            
            // Reset processing flag and complete future exceptionally
            ParserStateManager.setProcessingHistoricalData(false);
            processingFuture.completeExceptionally(e);
            
            // Send error message
            event.getHook().sendMessageEmbeds(
                    EmbedThemes.errorEmbed("Historical Data Processing Error",
                            "Failed to start historical data processing: " + e.getMessage())
            ).queue();
        }
        
        return processingFuture;
    }
    
    /**
     * Process historical data for a server and return statistics
     * @param server The server to process data for
     * @return A map containing statistics about the processing
     */
    public Map<String, Integer> processServerHistoricalData(GameServer server) {
        logger.info("Processing historical server data for: {}", server.getName());
        
        // Create counters to track progress
        AtomicInteger killsRecorded = new AtomicInteger(0);
        AtomicInteger deathsRecorded = new AtomicInteger(0);
        AtomicInteger errorsEncountered = new AtomicInteger(0);
        int filesProcessed = 0; // Simple counter for files processed
        
        try {
            // Set historical processing mode
            ParserStateManager.setProcessingHistoricalData(true);
            
            // Get CSV parser to handle the actual parsing
            csvParser.setProcessingHistoricalData(true);
            
            // Process the data through the CSV parser
            csvParser.processHistoricalData(server, killsRecorded, deathsRecorded, errorsEncountered);
            
            // Update server last processed info
            server.setLastProcessedTimestamp(System.currentTimeMillis());
            
            logger.info("Completed historical data processing for server {}. Stats: files={}, kills={}, deaths={}, errors={}", 
                    server.getName(), filesProcessed, killsRecorded.get(), deathsRecorded.get(), errorsEncountered.get());
            
        } catch (Exception e) {
            logger.error("Error processing historical data for server {}", server.getName(), e);
            errorsEncountered.incrementAndGet();
        } finally {
            // Always reset processing flags
            ParserStateManager.setProcessingHistoricalData(false);
            csvParser.setProcessingHistoricalData(false);
        }
        
        // Return the processing statistics
        Map<String, Integer> stats = new HashMap<>();
        stats.put("filesProcessed", filesProcessed);
        stats.put("killsRecorded", killsRecorded.get());
        stats.put("deathsRecorded", deathsRecorded.get());
        stats.put("errorsEncountered", errorsEncountered.get());
        
        return stats;
    }
    
    /**
     * Send a notification to the server's log channel
     */
    private void notifyServerLogChannel(GameServer server, int killCount, int deathCount) {
        try {
            if (server.getLogChannelId() <= 0) {
                return; // No log channel configured
            }
            
            Guild guild = jda.getGuildById(server.getGuildId());
            if (guild == null) {
                logger.warn("Could not find guild {} for server {}", server.getGuildId(), server.getName());
                return;
            }
            
            TextChannel logChannel = guild.getTextChannelById(server.getLogChannelId());
            if (logChannel == null) {
                logger.warn("Could not find log channel {} for server {}", 
                        server.getLogChannelId(), server.getName());
                return;
            }
            
            // Send notification to log channel
            logChannel.sendMessageEmbeds(
                    EmbedThemes.infoEmbed("Historical Data Processing Complete",
                            "Historical data for server **" + server.getName() + "** has been processed.\n\n" +
                            "**Statistics:**\n" +
                            "• Kills processed: " + killCount + "\n" +
                            "• Deaths processed: " + deathCount + "\n\n" +
                            "Player statistics have been updated accordingly.")
            ).queue();
        } catch (Exception e) {
            logger.error("Error sending notification to log channel for server {}", server.getName(), e);
        }
    }
}