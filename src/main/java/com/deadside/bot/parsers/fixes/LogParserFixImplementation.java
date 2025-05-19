package com.deadside.bot.parsers.fixes;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.sftp.SftpConnector;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of fixes for the log parser
 * This class provides a fixed implementation of the log parser
 * that addresses all the issues identified in the validation
 */
public class LogParserFixImplementation {
    private static final Logger logger = LoggerFactory.getLogger(LogParserFixImplementation.class);
    
    // Enhanced regex patterns for log parsing
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}:\\d{3})\\]\\[\\s*\\d+\\]");
    private static final Pattern PLAYER_JOIN_PATTERN = Pattern.compile("LogSFPS: \\[Login\\] Player (.+?) connected");
    private static final Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("LogSFPS: \\[Logout\\] Player (.+?) disconnected");
    private static final Pattern PLAYER_KILLED_PATTERN = Pattern.compile("LogSFPS: \\[Kill\\] (.+?) killed (.+?) with (.+?) at distance (\\d+)");
    private static final Pattern PLAYER_DIED_PATTERN = Pattern.compile("LogSFPS: \\[Death\\] (.+?) died from (.+?)");
    private static final Pattern AIRDROP_PATTERN = Pattern.compile("LogSFPS: AirDrop switched to (\\w+)");
    private static final Pattern HELI_CRASH_PATTERN = Pattern.compile("LogSFPS: Helicopter crash spawned at position (.+)");
    private static final Pattern TRADER_EVENT_PATTERN = Pattern.compile("LogSFPS: Trader event started at (.+)");
    private static final Pattern MISSION_PATTERN = Pattern.compile("LogSFPS: Mission (.+?) switched to (\\w+)");
    
    // Log rotation detection patterns
    private static final Pattern LOG_ROTATION_PATTERN = Pattern.compile("LogSFPS: Log file (.+?) opened");
    private static final Pattern SERVER_RESTART_PATTERN = Pattern.compile("LogSFPS: Server restarting|Server initialization started");
    
    // Track file information for rotation detection
    private final Map<String, Long> lastKnownFileSizes = new HashMap<>();
    private final Map<String, Long> lastModifiedTimes = new HashMap<>();
    private final Map<String, Integer> lastLineProcessed = new HashMap<>();
    
    // Duplicate event detection
    private final Map<String, Long> lastEventTimestamps = new HashMap<>();
    private static final long DUPLICATE_THRESHOLD_MS = 3000; // 3 seconds
    
    private final JDA jda;
    private final GameServerRepository gameServerRepository;
    private final SftpConnector sftpConnector;
    
    /**
     * Constructor
     */
    public LogParserFixImplementation(JDA jda, GameServerRepository gameServerRepository,
                                     SftpConnector sftpConnector) {
        this.jda = jda;
        this.gameServerRepository = gameServerRepository;
        this.sftpConnector = sftpConnector;
    }
    
    /**
     * Process logs for all servers
     */
    public void processAllServerLogs() {
        try {
            // Use isolation-aware approach to process servers across all guilds
            List<Long> distinctGuildIds = gameServerRepository.getDistinctGuildIds();
            List<GameServer> servers = new ArrayList<>();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                try {
                    servers.addAll(gameServerRepository.findAllByGuildId(guildId));
                } finally {
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            for (GameServer server : servers) {
                try {
                    // Skip servers without log channel configured
                    if (server.getLogChannelId() == 0) {
                        continue;
                    }
                    
                    parseServerLog(server);
                } catch (Exception e) {
                    logger.error("Error parsing logs for server {}: {}", 
                        server.getName(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error in log parser: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Parse log file for a specific server with enhanced rotation detection
     */
    private void parseServerLog(GameServer server) {
        String logPath = getServerLogPath(server);
        
        try {
            // Get information about the log file
            long fileSize = 0;
            long lastModified = 0;
            
            try {
                fileSize = sftpConnector.getFileSize(server, logPath);
                lastModified = sftpConnector.getLastModified(server, logPath);
            } catch (Exception e) {
                // File might not exist yet
                logger.debug("Could not get log file info: {}", e.getMessage());
            }
            
            // Check if this is our first time seeing this file
            boolean isFirstCheck = !lastKnownFileSizes.containsKey(server.getName());
            
            // Get the last processed line for this server
            int lastLine = lastLineProcessed.getOrDefault(server.getName(), 0);
            
            // Detect log rotation through multiple strategies
            boolean logRotationDetected = false;
            
            // 1. File size decreased (most common scenario)
            if (!isFirstCheck && fileSize > 0 && fileSize < lastKnownFileSizes.get(server.getName())) {
                logger.info("Log rotation detected for server {} - file size decreased from {} to {}", 
                    server.getName(), lastKnownFileSizes.get(server.getName()), fileSize);
                logRotationDetected = true;
            }
            
            // 2. File modification time changed significantly
            if (!isFirstCheck && lastModified > 0 && 
                Math.abs(lastModified - lastModifiedTimes.getOrDefault(server.getName(), 0L)) > 3600000) {
                logger.info("Log rotation detected for server {} - modification time changed significantly", 
                    server.getName());
                logRotationDetected = true;
            }
            
            // Update tracking information
            if (fileSize > 0) {
                lastKnownFileSizes.put(server.getName(), fileSize);
            }
            if (lastModified > 0) {
                lastModifiedTimes.put(server.getName(), lastModified);
            }
            
            // Reset line counter if rotation detected
            if (logRotationDetected) {
                logger.info("Resetting log line counter for server {} due to log rotation", server.getName());
                lastLine = 0;
                server.setLastLogRotation(System.currentTimeMillis());
                gameServerRepository.save(server);
            }
            
            // Read new lines from the log file
            List<String> newLines;
            try {
                newLines = sftpConnector.readLinesAfter(server, logPath, lastLine);
                
                if (newLines.isEmpty()) {
                    return;
                }
                
                // Update the last processed line
                lastLineProcessed.put(server.getName(), lastLine + newLines.size());
                
                // Process the new lines with improved event detection
                processLogLinesWithImprovedEventDetection(server, newLines);
                
                // Check for rotation indicators within the content
                detectRotationIndicatorsInContent(server, newLines);
                
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && 
                    (errorMessage.contains("No such file") || 
                     errorMessage.contains("File not found") ||
                     errorMessage.contains("does not exist"))) {
                    
                    logger.warn("Log file not found for server {}: {}", server.getName(), logPath);
                    
                    // Reset counter on file not found (likely rotation)
                    lastLineProcessed.put(server.getName(), 0);
                    
                    // Attempt to create directory structure
                    try {
                        String testFilePath = server.getLogDirectory() + "/log_parser_test.txt";
                        sftpConnector.writeFile(server, testFilePath, "Log parser test file: " + new Date());
                        logger.info("Successfully created test file in log directory for server {}", server.getName());
                    } catch (Exception ex) {
                        logger.error("Failed to create test file: {}", ex.getMessage());
                    }
                } else if (errorMessage != null && 
                          (errorMessage.contains("reset line counter") || 
                           errorMessage.contains("smaller than expected"))) {
                    
                    // Another indicator of log rotation
                    logger.info("File size inconsistency detected for server {} - resetting line counter", server.getName());
                    lastLineProcessed.put(server.getName(), 0);
                } else {
                    throw e; // Rethrow unexpected errors
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing log file for server {}: {}", server.getName(), e.getMessage(), e);
        }
    }
    
    /**
     * Detect rotation indicators in log content
     */
    private void detectRotationIndicatorsInContent(GameServer server, List<String> lines) {
        for (String line : lines) {
            if (LOG_ROTATION_PATTERN.matcher(line).find() || SERVER_RESTART_PATTERN.matcher(line).find()) {
                logger.info("Log rotation indicator found in content for server {}: {}", server.getName(), line);
                
                // Update rotation timestamp and reset counter
                server.setLastLogRotation(System.currentTimeMillis());
                lastLineProcessed.put(server.getName(), 0);
                gameServerRepository.save(server);
                
                // Send server restart notification
                Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(line);
                String timestamp = timestampMatcher.find() ? timestampMatcher.group(1) : "";
                
                sendServerRestartNotification(server, timestamp);
                break;
            }
        }
    }
    
    /**
     * Process log lines with improved event detection
     */
    private void processLogLinesWithImprovedEventDetection(GameServer server, List<String> lines) {
        Set<String> joinedPlayers = new HashSet<>();
        Set<String> leftPlayers = new HashSet<>();
        int eventCount = 0;
        
        for (String line : lines) {
            // Skip empty lines
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            
            // Extract timestamp for all event embeds
            String timestamp = "";
            Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(line);
            if (timestampMatcher.find()) {
                timestamp = timestampMatcher.group(1);
            }
            
            // Check for explicit server restart indicators
            if (SERVER_RESTART_PATTERN.matcher(line).find()) {
                if (!isDuplicateEvent("server_restart", server.getName())) {
                    sendServerRestartNotification(server, timestamp);
                    eventCount++;
                }
                continue;
            }
            
            // Player join events
            Matcher joinMatcher = PLAYER_JOIN_PATTERN.matcher(line);
            if (joinMatcher.find()) {
                String playerName = joinMatcher.group(1).trim();
                String eventKey = "join_" + playerName + "_" + server.getName();
                
                if (!isDuplicateEvent(eventKey)) {
                    sendPlayerJoinNotification(server, playerName, timestamp);
                    joinedPlayers.add(playerName);
                    eventCount++;
                }
                continue;
            }
            
            // Player leave events
            Matcher leaveMatcher = PLAYER_LEAVE_PATTERN.matcher(line);
            if (leaveMatcher.find()) {
                String playerName = leaveMatcher.group(1).trim();
                String eventKey = "leave_" + playerName + "_" + server.getName();
                
                if (!isDuplicateEvent(eventKey)) {
                    sendPlayerLeaveNotification(server, playerName, timestamp);
                    leftPlayers.add(playerName);
                    eventCount++;
                }
                continue;
            }
            
            // Player killed events
            Matcher killedMatcher = PLAYER_KILLED_PATTERN.matcher(line);
            if (killedMatcher.find()) {
                String killer = killedMatcher.group(1).trim();
                String victim = killedMatcher.group(2).trim();
                String weapon = killedMatcher.group(3).trim();
                String distance = killedMatcher.group(4).trim();
                
                String eventKey = "kill_" + killer + "_" + victim + "_" + server.getName();
                
                if (!isDuplicateEvent(eventKey)) {
                    sendKillNotification(server, killer, victim, weapon, distance, timestamp);
                    eventCount++;
                }
                continue;
            }
            
            // Player death events
            Matcher diedMatcher = PLAYER_DIED_PATTERN.matcher(line);
            if (diedMatcher.find()) {
                String player = diedMatcher.group(1).trim();
                String cause = diedMatcher.group(2).trim();
                
                String eventKey = "death_" + player + "_" + cause + "_" + server.getName();
                
                if (!isDuplicateEvent(eventKey)) {
                    sendDeathNotification(server, player, cause, timestamp);
                    eventCount++;
                }
                continue;
            }
            
            // Airdrop events
            Matcher airdropMatcher = AIRDROP_PATTERN.matcher(line);
            if (airdropMatcher.find()) {
                String status = airdropMatcher.group(1).trim();
                String eventKey = "airdrop_" + status + "_" + server.getName();
                
                if (!isDuplicateEvent(eventKey)) {
                    sendAirdropNotification(server, status, timestamp);
                    eventCount++;
                }
                continue;
            }
            
            // Helicopter crash events
            Matcher heliMatcher = HELI_CRASH_PATTERN.matcher(line);
            if (heliMatcher.find()) {
                String position = heliMatcher.group(1).trim();
                String eventKey = "helicrash_" + server.getName();
                
                if (!isDuplicateEvent(eventKey)) {
                    sendEventNotification(server, "Helicopter Crash", "A helicopter has crashed nearby!", 
                        "Location: " + position, new Color(150, 75, 0), timestamp);
                    eventCount++;
                }
                continue;
            }
            
            // Trader events
            Matcher traderMatcher = TRADER_EVENT_PATTERN.matcher(line);
            if (traderMatcher.find()) {
                String position = traderMatcher.group(1).trim();
                String eventKey = "trader_" + server.getName();
                
                if (!isDuplicateEvent(eventKey)) {
                    sendEventNotification(server, "Trader Event", "A special trader has appeared!", 
                        "Location: " + position, new Color(0, 128, 0), timestamp);
                    eventCount++;
                }
                continue;
            }
            
            // Mission events
            Matcher missionMatcher = MISSION_PATTERN.matcher(line);
            if (missionMatcher.find()) {
                String missionName = missionMatcher.group(1).trim();
                String status = missionMatcher.group(2).trim();
                
                // Only send notifications for relevant mission statuses
                if (status.equalsIgnoreCase("READY") || status.equalsIgnoreCase("ACTIVE") || 
                    status.equalsIgnoreCase("COMPLETED") || status.equalsIgnoreCase("REWARD")) {
                    
                    String eventKey = "mission_" + missionName + "_" + status + "_" + server.getName();
                    
                    if (!isDuplicateEvent(eventKey)) {
                        String title = status.equalsIgnoreCase("READY") || status.equalsIgnoreCase("ACTIVE") 
                            ? "Mission Available" : "Mission Status";
                            
                        sendEventNotification(server, title, 
                            status.equalsIgnoreCase("COMPLETED") ? "Mission completed!" : "A mission is active!", 
                            "Mission: " + missionName + "\nStatus: " + status, 
                            new Color(148, 0, 211), timestamp);
                        eventCount++;
                    }
                }
            }
        }
        
        // Send summary notifications for multiple players if needed
        if (joinedPlayers.size() > 3) {
            sendPlayerSummary(server, joinedPlayers, true);
        }
        
        if (leftPlayers.size() > 3) {
            sendPlayerSummary(server, leftPlayers, false);
        }
        
        if (eventCount > 0) {
            logger.info("Processed {} events from {} log lines for server {}", 
                eventCount, lines.size(), server.getName());
        }
    }
    
    /**
     * Check if an event is a duplicate (to prevent spam)
     */
    private boolean isDuplicateEvent(String eventKey) {
        long now = System.currentTimeMillis();
        Long lastTime = lastEventTimestamps.get(eventKey);
        
        if (lastTime != null && (now - lastTime) < DUPLICATE_THRESHOLD_MS) {
            return true;
        }
        
        lastEventTimestamps.put(eventKey, now);
        return false;
    }
    
    /**
     * Check if an event is a duplicate for a specific server
     */
    private boolean isDuplicateEvent(String eventType, String serverName) {
        return isDuplicateEvent(eventType + "_" + serverName);
    }
    
    /**
     * Get the path to the server log file
     */
    private String getServerLogPath(GameServer server) {
        return server.getLogDirectory() + "/Deadside.log";
    }
    
    /**
     * Send server restart notification
     */
    private void sendServerRestartNotification(GameServer server, String timestamp) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Server Restart")
            .setDescription("The server is restarting or has been restarted.")
            .setColor(Color.ORANGE)
            .setThumbnail("https://i.imgur.com/sF0aSQQ.png") // Server icon
            .setTimestamp(Instant.now());
        
        if (!timestamp.isEmpty()) {
            embed.setFooter("Powered by Discord.gg/EmeraldServers • " + timestamp, null);
        } else {
            embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        }
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send player join notification
     */
    private void sendPlayerJoinNotification(GameServer server, String playerName, String timestamp) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Player Connected")
            .setDescription("**" + playerName + "** has joined the server")
            .setColor(Color.GREEN)
            .setThumbnail("https://i.imgur.com/xbSvHu7.png") // Player icon
            .setTimestamp(Instant.now());
        
        if (!timestamp.isEmpty()) {
            embed.setFooter("Powered by Discord.gg/EmeraldServers • " + timestamp, null);
        } else {
            embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        }
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send player leave notification
     */
    private void sendPlayerLeaveNotification(GameServer server, String playerName, String timestamp) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Player Disconnected")
            .setDescription("**" + playerName + "** has left the server")
            .setColor(Color.RED)
            .setThumbnail("https://i.imgur.com/xbSvHu7.png") // Player icon
            .setTimestamp(Instant.now());
        
        if (!timestamp.isEmpty()) {
            embed.setFooter("Powered by Discord.gg/EmeraldServers • " + timestamp, null);
        } else {
            embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        }
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send kill notification
     */
    private void sendKillNotification(GameServer server, String killer, String victim, 
                                    String weapon, String distance, String timestamp) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Player Kill")
            .setDescription("**" + killer + "** killed **" + victim + "**")
            .setColor(Color.RED)
            .addField("Weapon", weapon, true)
            .addField("Distance", distance + "m", true)
            .setThumbnail("https://i.imgur.com/d4DzRYf.png") // Skull icon
            .setTimestamp(Instant.now());
        
        if (!timestamp.isEmpty()) {
            embed.setFooter("Powered by Discord.gg/EmeraldServers • " + timestamp, null);
        } else {
            embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        }
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send death notification
     */
    private void sendDeathNotification(GameServer server, String player, String cause, String timestamp) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Player Death")
            .setDescription("**" + player + "** died from **" + cause + "**")
            .setColor(new Color(139, 0, 0)) // Dark red
            .setThumbnail("https://i.imgur.com/d4DzRYf.png") // Skull icon
            .setTimestamp(Instant.now());
        
        if (!timestamp.isEmpty()) {
            embed.setFooter("Powered by Discord.gg/EmeraldServers • " + timestamp, null);
        } else {
            embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        }
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send airdrop notification
     */
    private void sendAirdropNotification(GameServer server, String status, String timestamp) {
        String title = "Airdrop Event";
        String description;
        Color color;
        
        if (status.equalsIgnoreCase("Waiting")) {
            description = "An airdrop is inbound!";
            color = Color.BLUE;
        } else if (status.equalsIgnoreCase("Dropped") || status.equalsIgnoreCase("Active")) {
            description = "An airdrop has been deployed!";
            color = new Color(65, 105, 225); // Royal blue
        } else if (status.equalsIgnoreCase("Completed") || status.equalsIgnoreCase("Finished")) {
            description = "The airdrop event has ended.";
            color = Color.GRAY;
        } else {
            description = "Airdrop status: " + status;
            color = Color.BLUE;
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(color)
            .addField("Status", status, false)
            .setThumbnail("https://i.imgur.com/ZsXjvCX.png") // Supply crate icon
            .setTimestamp(Instant.now());
        
        if (!timestamp.isEmpty()) {
            embed.setFooter("Powered by Discord.gg/EmeraldServers • " + timestamp, null);
        } else {
            embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        }
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send generic event notification
     */
    private void sendEventNotification(GameServer server, String title, String description, 
                                     String details, Color color, String timestamp) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(color)
            .addField("Details", details, false)
            .setThumbnail("https://i.imgur.com/main.png")
            .setTimestamp(Instant.now());
        
        if (!timestamp.isEmpty()) {
            embed.setFooter("Powered by Discord.gg/EmeraldServers • " + timestamp, null);
        } else {
            embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        }
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send player summary (for multiple joins/leaves)
     */
    private void sendPlayerSummary(GameServer server, Set<String> players, boolean isJoining) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(isJoining ? "Multiple Players Connected" : "Multiple Players Disconnected")
            .setDescription(String.format("%d players have %s the server", 
                players.size(), isJoining ? "joined" : "left"))
            .setColor(isJoining ? Color.GREEN : Color.RED)
            .setThumbnail("https://i.imgur.com/xbSvHu7.png")
            .setTimestamp(Instant.now());
        
        // Convert set to list and sort
        List<String> sortedPlayers = new ArrayList<>(players);
        Collections.sort(sortedPlayers);
        
        // Add player names (split into multiple fields if needed)
        StringBuilder playerList = new StringBuilder();
        int count = 0;
        int fieldCount = 1;
        
        for (String player : sortedPlayers) {
            playerList.append(player).append("\n");
            count++;
            
            // Split into multiple fields if needed (Discord limit of 25 fields per embed)
            if (count % 10 == 0 && count < players.size()) {
                embed.addField("Players " + fieldCount, playerList.toString(), false);
                playerList = new StringBuilder();
                fieldCount++;
            }
        }
        
        // Add final field
        if (playerList.length() > 0) {
            embed.addField("Players " + fieldCount, playerList.toString(), false);
        }
        
        embed.setFooter("Powered by Discord.gg/EmeraldServers", null);
        
        sendToLogChannel(server, embed.build());
    }
    
    /**
     * Send embed to server's log channel
     */
    private void sendToLogChannel(GameServer server, MessageEmbed embed) {
        Guild guild = jda.getGuildById(server.getGuildId());
        if (guild == null) {
            logger.warn("Guild not found for server {}: {}", server.getName(), server.getGuildId());
            return;
        }
        
        TextChannel logChannel = guild.getTextChannelById(server.getLogChannelId());
        if (logChannel == null) {
            logger.warn("Log channel not found for server {}: {}", server.getName(), server.getLogChannelId());
            return;
        }
        
        logChannel.sendMessageEmbeds(embed).queue(
            success -> logger.debug("Sent log notification to channel {}", logChannel.getId()),
            error -> logger.error("Failed to send log notification: {}", error.getMessage())
        );
    }
}