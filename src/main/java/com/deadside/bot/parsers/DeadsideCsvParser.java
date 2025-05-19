package com.deadside.bot.parsers;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.sftp.SftpConnector;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.ParserStateManager;
import com.deadside.bot.utils.GuildIsolationManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;

import java.awt.Color;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Parser for Deadside CSV death log files
 * Format: timestamp;victim;victimId;killer;killerId;weapon;distance
 */
public class DeadsideCsvParser {
    private static final Logger logger = LoggerFactory.getLogger(DeadsideCsvParser.class);
    private final JDA jda;
    private final SftpConnector sftpConnector;
    private final PlayerRepository playerRepository;
    private final GameServerRepository gameServerRepository;
    
    // Map to keep track of processed files for each server
    private final Map<String, Set<String>> processedFiles = new HashMap<>();
    
    // Flag to indicate if we're processing historical data (to prevent sending to killfeed channels)
    private boolean isProcessingHistoricalData = false;
    
    /**
     * Set historical processing mode flag
     * @param isProcessingHistoricalData Whether we're processing historical data
     */
    public void setProcessingHistoricalData(boolean isProcessingHistoricalData) {
        this.isProcessingHistoricalData = isProcessingHistoricalData;
        logger.debug("Historical processing mode set to: {}", isProcessingHistoricalData);
    }
    
    /**
     * Process historical data from CSV files for a server
     * @param server The server to process
     * @param killCount Counter for kills processed
     * @param deathCount Counter for deaths processed
     * @param errorCount Counter for errors encountered
     * @param filesProcessed Counter for files processed (optional)
     */
    public void processHistoricalData(GameServer server, 
                                     java.util.concurrent.atomic.AtomicInteger killCount,
                                     java.util.concurrent.atomic.AtomicInteger deathCount,
                                     java.util.concurrent.atomic.AtomicInteger errorCount) {
        logger.info("Processing historical data for server: {}", server.getName());
        
        try {
            // Set historical processing mode
            setProcessingHistoricalData(true);
            ParserStateManager.setProcessingHistoricalData(true);
            
            // Get list of CSV files in the deathlog directory
            List<String> csvFiles = new ArrayList<>();
            // In a full implementation, we would use SftpConnector's listFiles method
            try {
                File deathlogs = new File(server.getDeathlogsDirectory());
                if (deathlogs.exists() && deathlogs.isDirectory()) {
                    for (File file : deathlogs.listFiles()) {
                        if (file.getName().endsWith(".csv")) {
                            csvFiles.add(file.getName());
                        }
                    }
                } else {
                    logger.warn("Deathlog directory does not exist: {}", server.getDeathlogsDirectory());
                }
            } catch (Exception e) {
                logger.error("Error listing CSV files", e);
                errorCount.incrementAndGet();
            }
            
            // Sort by filename (which should be by date)
            Collections.sort(csvFiles);
            
            logger.info("Found {} CSV files to process for server {}", csvFiles.size(), server.getName());
            
            // Process each file
            for (String csvFile : csvFiles) {
                if (csvFile.endsWith(".csv")) {
                    try {
                        String filePath = server.getDeathlogsDirectory() + "/" + csvFile;
                        logger.info("Processing CSV file: {} for server: {} (historical mode: yes)", 
                                filePath, server.getName());
                        
                        // Read and parse the file locally
                        List<String> lines = new ArrayList<>();
                        try {
                            File csvFileObj = new File(filePath);
                            if (csvFileObj.exists()) {
                                try (BufferedReader reader = new BufferedReader(new FileReader(csvFileObj))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        lines.add(line);
                                    }
                                }
                                // File processed successfully
                            } else {
                                logger.warn("CSV file does not exist: {}", filePath);
                            }
                        } catch (Exception e) {
                            logger.error("Error reading CSV file: {}", filePath, e);
                            errorCount.incrementAndGet();
                        }
                        
                        for (String line : lines) {
                            try {
                                processDeathLogLine(server, line, filePath, killCount, deathCount);
                            } catch (Exception e) {
                                logger.error("Error processing historical death log line: {}", line, e);
                                errorCount.incrementAndGet();
                            }
                        }
                        
                        // Update server progress
                        server.setLastProcessedKillfeedFile(csvFile);
                        server.setLastProcessedKillfeedLine(lines.size());
                        server.setLastProcessedTimestamp(System.currentTimeMillis());
                        
                    } catch (Exception e) {
                        logger.error("Error processing historical CSV file {}: {}", csvFile, e.getMessage());
                        errorCount.incrementAndGet();
                    }
                }
            }
            
            logger.info("Completed historical data processing for server {}. Processed {} kills and {} deaths with {} errors", 
                    server.getName(), killCount.get(), deathCount.get(), errorCount.get());
            
        } catch (Exception e) {
            logger.error("Error processing historical data for server {}", server.getName(), e);
            errorCount.incrementAndGet();
        } finally {
            // Always reset the processing mode flags
            setProcessingHistoricalData(false);
            ParserStateManager.setProcessingHistoricalData(false);
        }
    }
    
    /**
     * Process a single death log line for historical processing
     */
    private void processDeathLogLine(GameServer server, String line, String filePath,
                                    java.util.concurrent.atomic.AtomicInteger killCount,
                                    java.util.concurrent.atomic.AtomicInteger deathCount) {
        // Skip empty lines
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        
        // Parse the line - should have format:
        // timestamp;killer;killerId;victim;victimId;weapon;distance;headshot;victimXYZ;killerXYZ;serverInstanceId;platform
        String[] parts = line.split(";");
        
        // Skip lines with incorrect format
        if (parts.length < 7) {
            logger.warn("Skipping incorrectly formatted death log line: {}", line);
            return;
        }
        
        // Extract data
        String timestamp = parts[0].trim();
        String killer = parts[1].trim();
        String killerId = parts[2].trim();
        String victim = parts[3].trim();
        String victimId = parts[4].trim();
        String weapon = parts[5].trim();
        int distance = 0;
        
        try {
            if (parts.length > 6 && !parts[6].trim().isEmpty()) {
                distance = Integer.parseInt(parts[6].trim());
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid distance value in death log: {}", parts[6]);
        }
        
        // Handle different death types
        if (killer.isEmpty() || killerId.isEmpty() || "**".equals(killer)) {
            // This is a death without a killer (fall, suicide, etc.) or placeholder killer
            deathCount.incrementAndGet();
            processEnvironmentalDeath(server, victim, victimId, weapon, timestamp);
        } else if (killer.equals(victim) && killerId.equals(victimId)) {
            // This is a suicide
            deathCount.incrementAndGet();
            processSuicide(server, victim, victimId, weapon, timestamp);
        } else {
            // This is a player kill
            killCount.incrementAndGet();
            processPlayerKill(server, killer, killerId, victim, victimId, weapon, distance, timestamp);
        }
    }
    
    /**
     * Process an environmental death (no killer)
     * Used for historical data processing
     */
    private void processEnvironmentalDeath(GameServer server, String victim, String victimId, String cause, String timestamp) {
        logger.debug("Processing environmental death: {} killed by {}", victim, cause);
        try {
            // In real implementation, this would update player stats in database
            // For this implementation, we'll just log the death
            logger.info("Environmental death recorded: {} died from {}", victim, cause);
        } catch (Exception e) {
            logger.error("Error processing environmental death", e);
        }
    }
    
    /**
     * Process a suicide event
     * Used for historical data processing
     */
    private void processSuicide(GameServer server, String player, String playerId, String weapon, String timestamp) {
        logger.debug("Processing suicide: {} with {}", player, weapon);
        try {
            // In real implementation, this would update player stats in database
            // For this implementation, we'll just log the suicide
            logger.info("Suicide recorded: {} with {}", player, weapon);
        } catch (Exception e) {
            logger.error("Error processing suicide", e);
        }
    }
    
    /**
     * Process a player kill event
     * Used for historical data processing
     */
    private void processPlayerKill(GameServer server, String killer, String killerId, 
                                  String victim, String victimId, String weapon, 
                                  int distance, String timestamp) {
        logger.debug("Processing kill: {} killed {} with {} at {}m", killer, victim, weapon, distance);
        try {
            // In real implementation, this would update player stats in database
            // For this implementation, we'll just log the kill
            logger.info("Kill recorded: {} killed {} with {} at {}m", killer, victim, weapon, distance);
        } catch (Exception e) {
            logger.error("Error processing player kill", e);
        }
    }
    
    /**
     * Process a death log file content directly
     * This is used by the HistoricalDataProcessor to process files in batches
     * 
     * @param server The game server
     * @param content The file content
     * @return Number of deaths processed
     */
    public int processDeathLogContent(GameServer server, String content) {
        return processDeathLog(server, content);
    }
    
    // Format of the CSV death log: timestamp;killer;killerId;victim;victimId;weapon;distance;killerPlatform;victimPlatform
    // Enhanced pattern to handle all CSV format variations and properly validate fields
    private static final Pattern CSV_LINE_PATTERN = Pattern.compile("(\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2});([^;]*);([^;]*);([^;]*);([^;]*);([^;]*);([^;]*);([^;]*);([^;]*);?");
    private static final SimpleDateFormat CSV_DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss");
    
    // Death causes
    private static final Set<String> SUICIDE_CAUSES = new HashSet<>(Arrays.asList(
            "suicide_by_relocation", "suicide", "falling", "bleeding", "drowning", "starvation"
    ));
    
    public DeadsideCsvParser(JDA jda, SftpConnector sftpConnector, PlayerRepository playerRepository, GameServerRepository gameServerRepository) {
        this.jda = jda;
        this.sftpConnector = sftpConnector;
        this.playerRepository = playerRepository;
        this.gameServerRepository = gameServerRepository;
        
        // Set timezone for date parsing
        CSV_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    
    /**
     * Process death logs for a server (default behavior - not processing historical data)
     * @param server The game server to process
     * @return Number of deaths processed
     */
    public int processDeathLogs(GameServer server) {
        return processDeathLogs(server, false);
    }
    
    /**
     * Process death logs for a server
     * @param server The game server to process
     * @param processHistorical If true, reprocess all files even if already processed before
     * @return Number of deaths processed
     */
    public int processDeathLogs(GameServer server, boolean processHistorical) {
        int totalProcessed = 0;
        
        try {
            // Set historical processing flag to prevent output to killfeed channels during historical processing
            this.isProcessingHistoricalData = processHistorical;
            
            // Check if the server has a configured guild
            if (server.getGuildId() == 0) {
                logger.warn("Server {} has no configured Discord guild", server.getName());
                return 0;
            }
            
            // Get list of already processed files for this server
            Set<String> processed = processedFiles.computeIfAbsent(
                    server.getName(), k -> new HashSet<>());
            
            // Get CSV files from the server - limit to the most recent files for performance
            List<String> csvFiles;
            if (processHistorical) {
                // For historical processing, get all files
                csvFiles = sftpConnector.findDeathlogFiles(server);
            } else {
                // For regular processing, only get the newest file or files not yet processed
                csvFiles = sftpConnector.findRecentDeathlogFiles(server, 1);
            }
            
            if (csvFiles.isEmpty()) {
                return 0;
            }
            
            // Sort files by name (which includes date)
            Collections.sort(csvFiles);
            
            // Process each file that hasn't been processed yet
            for (String csvFile : csvFiles) {
                // Skip already processed files (unless we're reprocessing historical data)
                if (!processHistorical && processed.contains(csvFile)) {
                    continue;
                }
                
                // Log file processing attempt
                logger.info("Processing CSV file: {} for server: {} (historical mode: {})", 
                        csvFile, server.getName(), processHistorical ? "yes" : "no");
                
                try {
                    String content = sftpConnector.readDeathlogFile(server, csvFile);
                    int deathsProcessed = processDeathLog(server, content);
                    totalProcessed += deathsProcessed;
                    
                    // Mark as processed
                    processed.add(csvFile);
                    // Use info level for files with deaths, debug level for empty files
                    if (deathsProcessed > 0) {
                        logger.info("Processed {} deaths from log file for server {}", 
                                deathsProcessed, server.getName());
                    } else if (logger.isDebugEnabled()) {
                        logger.debug("Processed death log file {} for server {}, no new deaths", 
                                csvFile, server.getName());
                    }
                } catch (Exception e) {
                    logger.error("Error processing death log file {} for server {}: {}", 
                            csvFile, server.getName(), e.getMessage(), e);
                }
            }
            
            // Limit the size of the processed files set to prevent memory issues
            if (processed.size() > 100) {
                // Keep only the most recent 50 files
                List<String> filesList = new ArrayList<>(processed);
                Collections.sort(filesList);
                Set<String> newProcessed = new HashSet<>(
                        filesList.subList(Math.max(0, filesList.size() - 50), filesList.size()));
                processedFiles.put(server.getName(), newProcessed);
            }
            
            return totalProcessed;
        } catch (Exception e) {
            logger.error("Error processing death logs for server {}: {}", 
                    server.getName(), e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Synchronize weapon kill statistics with player kill counts
     * This helps fix statistical inconsistencies that may have occurred
     * @return Number of players whose statistics were synchronized
     */
    public int syncPlayerStatistics() {
        try {
            logger.info("Starting player statistics synchronization with proper isolation");
            int updatedCount = 0;
            
            // Get all players from the repository with proper isolation
            List<Player> allPlayers = new ArrayList<>();
            
            // First get a list of all distinct guild IDs for proper isolation
            List<Long> distinctGuildIds = gameServerRepository.getDistinctGuildIds();
            logger.info("Found {} distinct guilds to check for players", distinctGuildIds.size());
            
            // For each guild, get servers with proper isolation
            for (Long guildId : distinctGuildIds) {
                // Get all servers for this guild with proper isolation
                List<GameServer> guildServers = gameServerRepository.findAllByGuildId(guildId);
                logger.info("Found {} game servers for guild {}", guildServers.size(), guildId);
                
                // For each server in this guild, process with proper isolation
                for (GameServer server : guildServers) {
                    if (server.getGuildId() > 0 && server.getServerId() != null) {
                        List<Player> serverPlayers = playerRepository.getAllPlayersWithIsolation(
                            server.getGuildId(), server.getServerId());
                        allPlayers.addAll(serverPlayers);
                        logger.debug("Added {} players from guild {} server {}", 
                            serverPlayers.size(), server.getGuildId(), server.getServerId());
                    }
                }
            }
            logger.info("Found {} players to check for statistics synchronization", allPlayers.size());
            
            for (Player player : allPlayers) {
                boolean updated = false;
                
                // Calculate total weapon kills
                int totalWeaponKills = 0;
                Map<String, Integer> weaponKills = player.getWeaponKills();
                
                if (weaponKills != null && !weaponKills.isEmpty()) {
                    for (int kills : weaponKills.values()) {
                        totalWeaponKills += kills;
                    }
                    
                    // If there's a discrepancy between weapon kills and player kills, update
                    if (totalWeaponKills != player.getKills()) {
                        logger.info("Fixing kill count discrepancy for player {}: {} weapon kills vs {} total kills", 
                                player.getName(), totalWeaponKills, player.getKills());
                        
                        // Update the player's kill count to match weapon kills
                        player.setKills(totalWeaponKills);
                        updated = true;
                    }
                }
                
                // Save player if updated
                if (updated) {
                    playerRepository.save(player);
                    updatedCount++;
                }
            }
            
            logger.info("Player statistics synchronization complete. Updated {} players.", updatedCount);
            return updatedCount;
        } catch (Exception e) {
            logger.error("Error synchronizing player statistics: {}", e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * Process a death log file content
     * @param server The game server
     * @param content The file content
     * @return Number of deaths processed
     */
    private int processDeathLog(GameServer server, String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        
        String[] lines = content.split("\\n");
        int count = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            
            // Simple validation that this looks like a death log line
            boolean lineMatches = CSV_LINE_PATTERN.matcher(line).matches();
            if (!lineMatches) {
                logger.info("CSV line skipped - pattern mismatch: {}", line);
                continue;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("CSV line matched pattern: {}", line);
            }
            
            try {
                String[] parts = line.split(";");
                // More flexible field count check - handle various formats while ensuring minimum required fields
                if (parts.length < 7) { // At minimum need timestamp, killer, killerId, victim, victimId, weapon, distance
                    logger.warn("Death log entry has too few fields ({} vs minimum 7): {}", parts.length, line);
                    continue;
                }
                
                // Parse death log entry - corrected for actual CSV format
                String timestamp = parts[0];
                String killer = parts[1].trim();
                String killerId = parts[2].trim();
                String victim = parts[3].trim();
                String victimId = parts[4].trim();
                String weapon = parts[5].trim();
                // Handle possible format issues with distance field
                int distance = 0;
                try {
                    distance = Integer.parseInt(parts[6].trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid distance format in death log entry: {}", parts[6]);
                    // Continue processing with default distance value
                }
                String killerPlatform = parts.length > 7 ? parts[7].trim() : "";
                String victimPlatform = parts.length > 8 ? parts[8].trim() : "";
                
                // Skip entries with blank killers or "**" placeholders
                if (killer.isEmpty() || killer.isBlank() || "**".equals(killer)) {
                    logger.info("Skipping death log entry with blank or placeholder killer: {}", line);
                    continue;
                }
                
                // Make sure killer ID is valid - this is crucial for database tracking
                if (killerId.isEmpty() || killerId.isBlank()) {
                    // Generate a synthetic ID based on killer name if missing
                    killerId = "gen_" + killer.hashCode();
                    logger.info("Generated ID for killer without ID: {} -> {}", killer, killerId);
                }
                
                // Only check timestamp filtering if not in historical mode
                try {
                    Date deathTime = CSV_DATE_FORMAT.parse(timestamp);
                    // Each server has its own independent lastProcessedTimestamp that we check against
                    if (logger.isDebugEnabled()) {
                        logger.debug("Death timestamp: {} ({}), server timestamp: {}, historical mode: {}, server: {}", 
                                timestamp, deathTime.getTime(), server.getLastProcessedTimestamp(), 
                                isProcessingHistoricalData ? "yes" : "no", 
                                server.getName());
                    }
                    
                    // Skip time-based filtering when doing historical processing - this keeps server timestamps isolated
                    if (!isProcessingHistoricalData && server.getLastProcessedTimestamp() > 0 && 
                        deathTime.getTime() < server.getLastProcessedTimestamp()) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Skipping old death due to timestamp (server: {})", server.getName());
                        }
                        continue;
                    }
                } catch (ParseException e) {
                    logger.warn("Could not parse death timestamp: {}", timestamp);
                }
                
                // Process death (controlled logging)
                if (logger.isDebugEnabled()) {
                    logger.debug("Processing death: {} killed {} with {} ({}m)", killer, victim, weapon, distance);
                }
                try {
                    // FIXED HISTORICAL PROCESSING: Direct database updates to ensure all kills are counted
                    if (isProcessingHistoricalData) {
                        // Check if this is a suicide event
                        boolean isSuicide = SUICIDE_CAUSES.contains(weapon.toLowerCase()) || 
                                          victim.equals(killer) || 
                                          weapon.equals("suicide_by_relocation") || 
                                          weapon.equals("falling");
                        
                        if (!isSuicide && !killer.equals("**")) {
                            try {
                                // Find or create the player - with proper server isolation
                                Player player = playerRepository.findByDeadsideIdAndGuildIdAndServerId(
                                    killerId, server.getGuildId(), server.getName());
                                
                                if (player == null) {
                                    player = new Player(killerId, killer, server.getGuildId(), server.getName());
                                    logger.info("Created new player: {} (ID: {}) for server: {} in guild: {}", 
                                        killer, killerId, server.getName(), server.getGuildId());
                                }
                                
                                // Increment the kill counter
                                player.setKills(player.getKills() + 1);
                                
                                // Update weapon stats without double counting
                                if (weapon != null && !weapon.isEmpty()) {
                                    // Get current weapon kills
                                    Map<String, Integer> weaponKills = player.getWeaponKills();
                                    if (weaponKills == null) {
                                        weaponKills = new HashMap<>();
                                        player.setWeaponKills(weaponKills);
                                    }
                                    
                                    // Update the weapon count
                                    int currentWeaponKills = weaponKills.getOrDefault(weapon, 0);
                                    weaponKills.put(weapon, currentWeaponKills + 1);
                                    
                                    // Update most used weapon if applicable
                                    if (currentWeaponKills + 1 > player.getMostUsedWeaponKills()) {
                                        player.setMostUsedWeapon(weapon);
                                        player.setMostUsedWeaponKills(currentWeaponKills + 1);
                                    }
                                }
                                
                                // Update longest kill distance if applicable
                                if (distance > player.getLongestKillDistance()) {
                                    player.setLongestKillDistance(distance);
                                    player.setLongestKillVictim(victim);
                                    player.setLongestKillWeapon(weapon);
                                }
                                
                                // Increment kill streak
                                player.incrementKillStreak();
                                
                                // Save player data
                                playerRepository.save(player);
                                
                                // Now update victim data with proper server isolation
                                Player victimPlayer = playerRepository.findByDeadsideIdAndGuildIdAndServerId(
                                    victimId, server.getGuildId(), server.getName());
                                    
                                if (victimPlayer == null) {
                                    victimPlayer = new Player(victimId, victim, server.getGuildId(), server.getName());
                                    logger.info("Created new victim player: {} (ID: {}) for server: {} in guild: {}", 
                                        victim, victimId, server.getName(), server.getGuildId());
                                }
                                
                                // Increment death counter
                                victimPlayer.setDeaths(victimPlayer.getDeaths() + 1);
                                
                                // Reset victim's kill streak
                                victimPlayer.resetKillStreak();
                                
                                // Save victim data
                                playerRepository.save(victimPlayer);
                                
                                logger.info("Historical kill successfully recorded: {} → {} with {} ({}m)", killer, victim, weapon, distance);
                            } catch (Exception e) {
                                logger.error("Error processing historical kill: {}", e.getMessage(), e);
                            }
                        } else {
                            // Handle suicide case - only update victim stats
                            try {
                                Player victimPlayer = playerRepository.findByDeadsideIdAndGuildIdAndServerId(
                                    victimId, server.getGuildId(), server.getName());
                                    
                                if (victimPlayer == null) {
                                    victimPlayer = new Player(victimId, victim, server.getGuildId(), server.getName());
                                    logger.info("Created new suicide victim player: {} (ID: {}) for server: {} in guild: {}", 
                                        victim, victimId, server.getName(), server.getGuildId());
                                }
                                
                                // Increment suicides counter
                                victimPlayer.setSuicides(victimPlayer.getSuicides() + 1);
                                
                                // Increment death counter
                                victimPlayer.setDeaths(victimPlayer.getDeaths() + 1);
                                
                                // Reset kill streak
                                victimPlayer.resetKillStreak();
                                
                                // Save victim data
                                playerRepository.save(victimPlayer);
                                
                                logger.info("Historical suicide recorded for: {}", victim);
                            } catch (Exception e) {
                                logger.error("Error processing historical suicide: {}", e.getMessage(), e);
                            }
                        }
                    } else {
                        // For regular (non-historical) processing, use the normal event-based flow
                        processDeath(server, timestamp, victim, victimId, killer, killerId, weapon, distance);
                    }
                    
                    // Count this entry regardless of processing method
                    count++;
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("Death successfully processed and counted");
                    }
                } catch (Exception e) {
                    logger.error("Failed to process death: {}", e.getMessage(), e);
                }
            } catch (Exception e) {
                logger.warn("Error processing death log line: {}", line, e);
            }
        }
        
        // Update server's last processed timestamp
        if (count > 0) {
            server.setLastProcessedTimestamp(System.currentTimeMillis());
        }
        
        return count;
    }
    
    /**
     * Process a death event
     */
    private void processDeath(GameServer server, String timestamp, String victim, String victimId, 
                             String killer, String killerId, String weapon, int distance) {
        try {
            // Handle different death types
            boolean isSuicide = SUICIDE_CAUSES.contains(weapon.toLowerCase()) || 
                                victim.equals(killer);
            
            if (isSuicide) {
                // Send suicide message to death channel (and update stats)
                sendSuicideKillfeed(server, timestamp, victim, victimId, weapon);
            } else {
                // Determine appropriate event type based on the death
                String eventType = "kill"; // Default
                
                // Special event types
                if (weapon.toLowerCase().contains("airdrop") || 
                    weapon.toLowerCase().contains("supply")) {
                    eventType = "airdrop";
                } else if (distance > 300) {
                    eventType = "longshot";
                }
                
                // Send to appropriate channel based on event type (and update stats)
                sendPlayerKillKillfeed(server, timestamp, victim, victimId, killer, killerId, weapon, distance, eventType);
                
                // Note: player stats are now updated in the killfeed methods to ensure
                // they're always updated even during historical processing
            }
        } catch (Exception e) {
            logger.error("Error processing death: {} killed by {}: {}", victim, killer, e.getMessage(), e);
        }
    }
    
    /**
     * Sync player kill counts with weapon kills to ensure accuracy
     * This method fixes the discrepancy between total kills and weapon kill counts
     */
    public void syncStatsForAccuracy() {
        try {
            logger.info("Starting comprehensive statistics synchronization...");
            
            // Get all players from the repository with proper isolation
            List<Player> allPlayers = new ArrayList<>();
            
            // First get a list of all distinct guild IDs for proper isolation
            List<Long> distinctGuildIds = gameServerRepository.getDistinctGuildIds();
            logger.info("Found {} distinct guilds to check for players", distinctGuildIds.size());
            
            // For each guild, get servers with proper isolation
            for (Long guildId : distinctGuildIds) {
                // Get all servers for this guild with proper isolation
                List<GameServer> guildServers = gameServerRepository.findAllByGuildId(guildId);
                logger.info("Found {} game servers for guild {}", guildServers.size(), guildId);
                
                // For each server in this guild, process with proper isolation
                for (GameServer server : guildServers) {
                    if (server.getGuildId() > 0 && server.getServerId() != null) {
                        List<Player> serverPlayers = playerRepository.getAllPlayersWithIsolation(
                            server.getGuildId(), server.getServerId());
                        allPlayers.addAll(serverPlayers);
                        logger.debug("Added {} players from guild {} server {}", 
                            serverPlayers.size(), server.getGuildId(), server.getServerId());
                    }
                }
            }
            int updatedPlayers = 0;
            int totalPlayers = allPlayers.size();
            
            logger.info("Found {} players to analyze for statistics accuracy", totalPlayers);
            
            for (Player player : allPlayers) {
                if (player == null || player.getName() == null || player.getName().isEmpty() || "**".equals(player.getName())) {
                    continue;
                }
                
                // Get all weapon kills
                Map<String, Integer> weaponKills = player.getWeaponKills();
                if (weaponKills == null || weaponKills.isEmpty()) {
                    continue;
                }
                
                // Calculate the total of all weapon kills
                int totalWeaponKills = weaponKills.values().stream().mapToInt(Integer::intValue).sum();
                
                // Find most used weapon
                Map.Entry<String, Integer> mostUsedWeapon = weaponKills.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
                
                // If the total weapon kills don't match the player's kill count, update it
                if (totalWeaponKills != player.getKills()) {
                    logger.info("Syncing kill count for player {}: DB kills={}, weapon kill sum={}",
                            player.getName(), player.getKills(), totalWeaponKills);
                    
                    // Update the player's kill count to match weapon kill sum
                    player.setKills(totalWeaponKills);
                    
                    // Update most used weapon information
                    if (mostUsedWeapon != null) {
                        player.setMostUsedWeapon(mostUsedWeapon.getKey());
                        player.setMostUsedWeaponKills(mostUsedWeapon.getValue());
                    }
                    
                    // Save the updated player
                    playerRepository.save(player);
                    updatedPlayers++;
                }
            }
            
            logger.info("Statistics synchronization complete. Updated {} out of {} players.", 
                    updatedPlayers, totalPlayers);
        } catch (Exception e) {
            logger.error("Error synchronizing player stats: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update the killer's stats
     */
    private void updateKillerStats(GameServer server, String killer, String killerId) {
        updateKillerStats(server, killer, killerId, null);
    }
    
    /**
     * Update the killer's stats with weapon information
     */
    private void updateKillerStats(GameServer server, String killer, String killerId, String weapon) {
        // Default distance for older method calls
        updateKillerStats(server, killer, killerId, weapon, 0);
    }
    
    /**
     * Update the killer's stats with weapon and distance information
     */
    private void updateKillerStats(GameServer server, String killer, String killerId, String weapon, int distance) {
        try {
            // Find or create the player with proper server isolation
            Player player = playerRepository.findByDeadsideIdAndGuildIdAndServerId(
                killerId, server.getGuildId(), server.getName());
                
            if (player == null) {
                // Create a new player if one doesn't exist with proper server association
                player = new Player(killerId, killer, server.getGuildId(), server.getName());
                logger.debug("Created new player record for {} with ID {} for server: {} in guild: {}", 
                    killer, killerId, server.getName(), server.getGuildId());
            }
            
            // Update kills and score
            player.setKills(player.getKills() + 1);
            
            // Base score per kill
            int scoreIncrease = 10;
            
            // Bonus points for special kills
            if (weapon != null) {
                if (weapon.toLowerCase().contains("airdrop") || 
                    weapon.toLowerCase().contains("supply")) {
                    // Airdrop kills are more valuable
                    scoreIncrease += 15;
                } else if (distance > 300) {
                    // Longshot kills
                    scoreIncrease += (int)(distance / 100); // +1 point per 100m over 300m
                }
            }
            
            // Score is now calculated dynamically based on kills and deaths
            // Removed direct score setting as it was causing kill counts to be reset
            
            // Add kill reward
            // TODO: Add economy reward here if implemented
            
            // Increment kill streak
            player.incrementKillStreak();
            
            // Update weapon kills - consolidated to fix duplicate counting
            if (weapon != null && !weapon.isEmpty()) {
                int currentCount = player.getWeaponKills().getOrDefault(weapon, 0);
                // Use a single increment for weapon kills to avoid double counting
                int newCount = currentCount + 1;
                player.getWeaponKills().put(weapon, newCount);
                
                // Update most used weapon if applicable
                if (newCount > player.getMostUsedWeaponKills()) {
                    player.setMostUsedWeapon(weapon);
                    player.setMostUsedWeaponKills(newCount);
                }
            }
            
            // Save the updated player
            playerRepository.save(player);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Updated killer stats for {} (ID: {}): kills={}, killStreak={}", 
                    killer, killerId, player.getKills(), player.getCurrentKillStreak());
            }
        } catch (Exception e) {
            logger.error("Error updating killer stats for {}: {}", killer, e.getMessage(), e);
        }
    }
    
    /**
     * Update the victim's stats
     */
    private void updateVictimStats(GameServer server, String victim, String victimId) {
        try {
            // Find or create the player with proper server isolation
            Player player = playerRepository.findByDeadsideIdAndGuildIdAndServerId(
                victimId, server.getGuildId(), server.getName());
                
            if (player == null) {
                // Create a new player if one doesn't exist with proper server association
                player = new Player(victimId, victim, server.getGuildId(), server.getName());
                logger.debug("Created new victim record for {} with ID {} for server: {} in guild: {}", 
                    victim, victimId, server.getName(), server.getGuildId());
            }
            
            // Update deaths
            player.setDeaths(player.getDeaths() + 1);
            
            // Reset kill streak when player dies
            player.resetKillStreak();
            
            // Save the updated player
            playerRepository.save(player);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Updated victim stats for {} (ID: {}): deaths={}", 
                    victim, victimId, player.getDeaths());
            }
        } catch (Exception e) {
            logger.error("Error updating victim stats for {}: {}", victim, e.getMessage(), e);
        }
    }
    
    /**
     * Send killfeed message for player kill
     */
    private void sendPlayerKillKillfeed(GameServer server, String timestamp, String victim, String victimId,
                                       String killer, String killerId, String weapon, int distance) {
        sendPlayerKillKillfeed(server, timestamp, victim, victimId, killer, killerId, weapon, distance, "kill");
    }
    
    /**
     * Send killfeed message for player kill with specific event type using modern styling
     */
    private void sendPlayerKillKillfeed(GameServer server, String timestamp, String victim, String victimId,
                                       String killer, String killerId, String weapon, int distance, String eventType) {
        try {
            // Always update player statistics even during historical processing
            // Pass server information for proper data isolation
            updateKillerStats(server, killer, killerId, weapon, distance);
            updateVictimStats(server, victim, victimId);
            
            // Skip sending killfeed embeds during historical processing
            if (isProcessingHistoricalData) {
                return;
            }
            
            MessageEmbed embed;
            
            // Check if this is a special kill type and create appropriate embed
            if (eventType.equals("airdrop")) {
                // For airdrop kills, use a specialized airdrop embed
                embed = EmbedUtils.airdropEmbed("Airdrop Kill", 
                        killer + " eliminated " + victim + " near an airdrop");
            } else if (eventType.equals("longshot")) {
                // For longshot kills
                String title = "Longshot Elimination";
                String description = killer + " sniped " + victim + " from an impressive " + distance + "m";
                
                embed = new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(EmbedUtils.STEEL_BLUE) // Steel blue for longshots
                        .addField("Marksman", killer, true)
                        .addField("Target", victim, true)
                        .addField("Weapon", weapon, true)
                        .addField("Distance", distance + "m", true)
                        .setFooter(EmbedUtils.STANDARD_FOOTER)
                        .setTimestamp(Instant.now())
                        .build();
            } else {
                // For standard kills
                embed = new EmbedBuilder()
                        .setTitle("Elimination Confirmed")
                        .setDescription(killer + " eliminated " + victim)
                        .setColor(EmbedUtils.EMERALD_GREEN)
                        .addField("Killer", killer, true)
                        .addField("Victim", victim, true)
                        .addField("Weapon", weapon, true)
                        .addField("Distance", distance + "m", true)
                        .setFooter(EmbedUtils.STANDARD_FOOTER)
                        .setTimestamp(Instant.now())
                        .build();
            }
            
            sendToKillfeedChannel(server, embed, eventType);
        } catch (Exception e) {
            logger.error("Error sending kill feed for {}: {}", victim, e.getMessage(), e);
        }
    }
    
    /**
     * Send killfeed message for suicide with modern styling
     */
    private void sendSuicideKillfeed(GameServer server, String timestamp, String victim, String victimId, String cause) {
        try {
            // Always update statistics even during historical processing
            // Pass server parameter for proper data isolation
            updateVictimStats(server, victim, victimId);
            
            // Skip killfeed message during historical processing
            if (isProcessingHistoricalData) {
                return;
            }
            
            MessageEmbed embed;
            
            // Check if it's falling damage or another type of suicide
            if (cause.equals("falling")) {
                // For falling deaths, use the specialized embed
                int height = 0; // Default height (not available in log)
                embed = EmbedUtils.fallingDeathEmbed(victim, height);
            } else {
                // For regular suicides, use suicide embed with normalized cause
                embed = EmbedUtils.suicideEmbed(victim, cause);
            }
            
            // Send to death channel
            sendToKillfeedChannel(server, embed, "death");
        } catch (Exception e) {
            logger.error("Error sending suicide feed for {}: {}", victim, e.getMessage(), e);
        }
    }
    
    /**
     * Send embed message to the appropriate channel
     * 
     * @param server The server to send the message for
     * @param embed The message embed to send
     * @param eventType The type of event (defaults to "death" if not specified)
     */
    private void sendToKillfeedChannel(GameServer server, net.dv8tion.jda.api.entities.MessageEmbed embed) {
        sendToKillfeedChannel(server, embed, "death");
    }
    
    /**
     * Send embed message to the appropriate channel based on event type
     * Non-historical data is sent to killfeed channels, historical data is not
     */
    private void sendToKillfeedChannel(GameServer server, net.dv8tion.jda.api.entities.MessageEmbed embed, String eventType) {
        // Check if we're in historical processing mode - skip sending to channels if true
        if (isProcessingHistoricalData) {
            logger.debug("Skipping killfeed output during historical data processing for server {}", server.getName());
            return;
        }
        
        TextChannel killfeedChannel = getTextChannel(server, eventType);
        if (killfeedChannel == null) {
            logger.warn("No suitable channel found for {} events for server {}", 
                    eventType, server.getName());
            return;
        }
        
        killfeedChannel.sendMessageEmbeds(embed).queue(
                success -> logger.debug("Sent killfeed to channel {}", killfeedChannel.getId()),
                error -> logger.error("Failed to send killfeed: {}", error.getMessage())
        );
    }
    
    /**
     * Get the appropriate Text Channel to send messages to based on event type
     * @param server The game server
     * @param eventType The type of event (kill, death, connection, airdrop, etc.)
     * @return The text channel, or null if not found/configured
     */
    private TextChannel getTextChannel(GameServer server, String eventType) {
        if (jda == null || server.getGuildId() == 0) {
            return null;
        }
        
        // Default to log channel from server config
        long channelId = server.getLogChannelId();
        
        // Try to find guild configuration for multi-channel support
        GuildConfigRepository guildConfigRepo = new GuildConfigRepository();
        GuildConfig guildConfig = guildConfigRepo.findByGuildId(server.getGuildId());
        
        if (guildConfig != null) {
            // If guild has specialized channels, use the appropriate one for the event type
            long eventChannelId = guildConfig.getLogChannelForEventType(eventType);
            if (eventChannelId != 0) {
                channelId = eventChannelId;
            }
        }
        
        // If no channel configured, return null
        if (channelId == 0) {
            return null;
        }
        
        // Get the channel from JDA
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            logger.warn("Could not find text channel with ID {} for server {} and event type {}",
                    channelId, server.getName(), eventType);
        }
        
        return channel;
    }
}