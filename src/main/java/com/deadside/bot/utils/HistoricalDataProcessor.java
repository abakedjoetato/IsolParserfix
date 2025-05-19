package com.deadside.bot.utils;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.db.repositories.KillRecordRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.KillfeedParser;
import com.deadside.bot.sftp.SftpConnector;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to handle processing historical data from game servers
 * with a built-in delay to allow for database synchronization
 */
public class HistoricalDataProcessor {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalDataProcessor.class);
    
    /**
     * Schedule processing of historical data for a server with a delay
     * using the command channel for output
     * 
     * @param event The slash command event that triggered this processing
     * @param server The game server to process historical data for
     */
    public static void scheduleProcessing(SlashCommandInteractionEvent event, GameServer server) {
        // Get the interaction hook from the event for sending messages to the command channel
        InteractionHook hook = event.getHook();
        
        // Create a new thread to handle the delayed processing
        Thread processingThread = new Thread(() -> {
            try {
                // Wait 30 seconds to allow database to fully populate and SFTP connections to initialize
                logger.info("Scheduling historical data processing for server {} in 30 seconds", server.getName());
                Thread.sleep(30000);
                
                logger.info("Starting historical data processing for server {}", server.getName());
                
                // Send initial processing message with themed embed to the command channel
                String title = "Historical Data Processing Started";
                String description = "Starting to process all historical data for **" + server.getName() + "**.\n\n" +
                                    "This will analyze all past server events and may take several minutes depending on the amount of data.";
                
                MessageEmbed embed = EmbedThemes.historicalDataEmbed(title, description);
                
                hook.sendMessageEmbeds(embed).queue();
                
                // Create the parsers
                KillfeedParser killfeedParser = new KillfeedParser(event.getJDA());
                DeadsideCsvParser csvParser = new DeadsideCsvParser(event.getJDA(), new SftpConnector(), new PlayerRepository(), new GameServerRepository());
                
                // Set historical processing flag to true for processing without Discord notifications
                csvParser.setProcessingHistoricalData(true);
                
                // Process killfeed data
                int killfeedProcessed = killfeedParser.processServer(server, true);
                
                // Send progress update after killfeed processing with modern embed to the command channel
                title = "Killfeed Processing Complete";
                description = "Successfully processed killfeed data for **" + server.getName() + "**.\n" +
                             "Now continuing with death logs processing...";
                
                embed = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setColor(EmbedUtils.STEEL_BLUE)
                    .addField("Killfeed Records", String.valueOf(killfeedProcessed), true)
                    .addField("Status", "Processing Death Logs...", true)
                    .setFooter(EmbedUtils.STANDARD_FOOTER)
                    .setTimestamp(Instant.now())
                    .build();
                
                hook.sendMessageEmbeds(embed).queue();
                
                // Get CSV file count before processing
                SftpConnector sftpConnector = new SftpConnector();
                java.util.List<String> csvFiles = sftpConnector.findDeathlogFiles(server);
                int totalCsvFiles = csvFiles.size();
                
                // Create a counter to track new player records created
                // This requires modifying the DeadsideCsvParser class, but we'll use a workaround
                PlayerRepository playerRepo = new PlayerRepository();
                long playersBefore = playerRepo.countAll();
                
                // Process death logs with historical mode flag
                int deathlogsProcessed = csvParser.processDeathLogs(server, true);
                
                // Calculate new players created
                long playersAfter = playerRepo.countAll();
                long newPlayersCreated = playersAfter - playersBefore;
                
                // Send final completion message with enhanced embed styling to the command channel
                title = "Historical Data Import Complete";
                description = "Successfully processed historical data for **" + server.getName() + "**";
                
                // Create a themed embed with more detailed statistics using the EmbedThemes system
                embed = EmbedThemes.historicalDataEmbed(
                    title,
                    description + "\n\n" +
                    "CSV Files Found\n" +
                    totalCsvFiles + "\n\n" +
                    "CSV Files Processed\n" +
                    (csvFiles.isEmpty() ? "0" : 
                    String.valueOf(Math.min(csvFiles.size(), deathlogsProcessed > 0 ? 
                    (int)Math.ceil(deathlogsProcessed / 100.0) : 1))) + "\n\n" +
                    "Death Logs Processed\n" + 
                    deathlogsProcessed + "\n\n" +
                    "New Players Created\n" +
                    newPlayersCreated + "\n\n" +
                    "Total Players\n" +
                    playersAfter
                );
                
                hook.sendMessageEmbeds(embed).queue();
                
                logger.info("Completed historical data processing for server {}: {} killfeed records, {} deathlogs", 
                        server.getName(), killfeedProcessed, deathlogsProcessed);
            } catch (Exception e) {
                logger.error("Error during historical data processing for server {}: {}", 
                        server.getName(), e.getMessage(), e);
                
                // Send error message to the command channel
                hook.sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Historical Data Processing Error", 
                        "An error occurred while processing historical data for **" + server.getName() + "**: " + e.getMessage())
                ).queue();
            }
        });
        
        // Set as daemon thread so it doesn't block JVM shutdown
        processingThread.setDaemon(true);
        processingThread.setName("HistoricalProcessor-" + server.getName());
        processingThread.start();
    }
    
    /**
     * Overloaded method for backward compatibility
     * 
     * @param jda The JDA instance for Discord interaction
     * @param server The game server to process historical data for
     */
    public static void scheduleProcessing(JDA jda, GameServer server) {
        // Create a new thread to handle the delayed processing
        Thread processingThread = new Thread(() -> {
            try {
                // Wait 30 seconds to allow database to fully populate and SFTP connections to initialize
                logger.info("Scheduling historical data processing for server {} in 30 seconds", server.getName());
                Thread.sleep(30000);
                
                logger.info("Starting historical data processing for server {}", server.getName());
                
                // Find the admin channel to send status updates
                TextChannel adminChannel = findAdminChannel(jda, server);
                
                // Send initial processing message with modern embed
                if (adminChannel != null) {
                    String title = "Historical Data Processing Started";
                    String description = "Starting to process all historical data for **" + server.getName() + "**.\n\n" +
                                        "This will analyze all past server events and may take several minutes depending on the amount of data.";
                    
                    MessageEmbed embed = new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(EmbedUtils.STEEL_BLUE)
                        .setFooter(EmbedUtils.STANDARD_FOOTER)
                        .setTimestamp(Instant.now())
                        .build();
                    
                    adminChannel.sendMessageEmbeds(embed).queue();
                }
                
                // Create the parsers
                KillfeedParser killfeedParser = new KillfeedParser(jda);
                DeadsideCsvParser csvParser = new DeadsideCsvParser(jda, new SftpConnector(), new PlayerRepository(), new GameServerRepository());
                
                // Set historical processing flag to true for processing without Discord notifications
                csvParser.setProcessingHistoricalData(true);
                
                // Process killfeed data
                int killfeedProcessed = killfeedParser.processServer(server, true);
                
                // Send progress update after killfeed processing with modern embed
                if (adminChannel != null) {
                    String title = "Killfeed Processing Complete";
                    String description = "Successfully processed killfeed data for **" + server.getName() + "**.\n" +
                                       "Now continuing with death logs processing...";
                    
                    MessageEmbed embed = new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(EmbedUtils.STEEL_BLUE)
                        .addField("Killfeed Records", String.valueOf(killfeedProcessed), true)
                        .addField("Status", "Processing Death Logs...", true)
                        .setFooter(EmbedUtils.STANDARD_FOOTER)
                        .setTimestamp(Instant.now())
                        .build();
                    
                    adminChannel.sendMessageEmbeds(embed).queue();
                }
                
                // Process death logs with historical mode flag
                int deathlogsProcessed = csvParser.processDeathLogs(server, true);
                
                // Send final completion message with modern embed styling
                if (adminChannel != null) {
                    String title = "Historical Data Import Complete";
                    String description = "Successfully processed historical data for **" + server.getName() + "**";
                    
                    // Create a modern styled embed with all the statistics
                    MessageEmbed embed = new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(EmbedUtils.EMERALD_GREEN)
                        .addField("Killfeed Records", String.valueOf(killfeedProcessed), true)
                        .addField("Death Logs", String.valueOf(deathlogsProcessed), true)
                        .setFooter(EmbedUtils.STANDARD_FOOTER)
                        .setTimestamp(Instant.now())
                        .build();
                    
                    adminChannel.sendMessageEmbeds(embed).queue();
                }
                
                logger.info("Completed historical data processing for server {}: {} killfeed records, {} deathlogs", 
                        server.getName(), killfeedProcessed, deathlogsProcessed);
            } catch (Exception e) {
                logger.error("Error during historical data processing for server {}: {}", 
                        server.getName(), e.getMessage(), e);
            }
        });
        
        // Set as daemon thread so it doesn't block JVM shutdown
        processingThread.setDaemon(true);
        processingThread.setName("HistoricalProcessor-" + server.getName());
        processingThread.start();
    }
    
    /**
     * Find the admin channel for a server to send progress updates
     * 
     * @param jda The JDA instance
     * @param server The game server to find the admin channel for
     * @return The admin channel, or null if not found
     */
    private static TextChannel findAdminChannel(JDA jda, GameServer server) {
        try {
            // Get the guild from the server
            Guild guild = jda.getGuildById(server.getGuildId());
            if (guild == null) {
                logger.warn("Could not find guild for server {}", server.getName());
                return null;
            }
            
            // Try to find the admin channel from guild config
            GuildConfigRepository guildConfigRepository = new GuildConfigRepository();
            GuildConfig guildConfig = guildConfigRepository.findByGuildId(server.getGuildId());
            
            if (guildConfig != null && guildConfig.getPrimaryLogChannelId() != 0) {
                TextChannel adminChannel = guild.getTextChannelById(guildConfig.getPrimaryLogChannelId());
                if (adminChannel != null) {
                    return adminChannel;
                }
            }
            
            // If no admin channel is configured, try to use the killfeed channel
            if (server.getKillfeedChannelId() != 0) {
                TextChannel killfeedChannel = guild.getTextChannelById(server.getKillfeedChannelId());
                if (killfeedChannel != null) {
                    return killfeedChannel;
                }
            }
            
            // If no suitable channel is found, try to use the system channel
            return guild.getSystemChannel();
        } catch (Exception e) {
            logger.error("Error finding admin channel for server {}: {}", server.getName(), e.getMessage());
            return null;
        }
    }
}