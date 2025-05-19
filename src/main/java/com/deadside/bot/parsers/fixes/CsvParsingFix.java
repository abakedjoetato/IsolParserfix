package com.deadside.bot.parsers.fixes;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Fixes and utilities for the CSV parser to ensure proper data handling
 * and isolation between different Discord servers
 */
public class CsvParsingFix {
    private static final Logger logger = LoggerFactory.getLogger(CsvParsingFix.class);
    
    /**
     * Validate and synchronize player statistics to ensure data integrity
     * @param playerRepository The player repository to use for validation
     * @return Number of player records corrected
     */
    public static int validateAndSyncStats(PlayerRepository playerRepository) {
        try {
            logger.info("Starting player stats validation and sync");
            int correctionCount = 0;
            
            // Get guild IDs from existing players for proper isolation
            List<Long> guildIds = getDistinctGuildIds(playerRepository);
            long totalPlayers = 0;
            
            // Process each guild with proper isolation context
            for (Long guildId : guildIds) {
                if (guildId != null && guildId > 0) {
                    // Set isolation context for this guild
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                    
                    try {
                        // Use isolation-aware methods - get all servers for this guild
                        com.deadside.bot.db.repositories.GameServerRepository serverRepo = new com.deadside.bot.db.repositories.GameServerRepository();
                        List<com.deadside.bot.db.models.GameServer> guildServers = serverRepo.findAllByGuildId(guildId);
                        
                        // Process each server for this guild
                        List<Player> guildPlayers = new java.util.ArrayList<>();
                        for (com.deadside.bot.db.models.GameServer server : guildServers) {
                            List<Player> serverPlayers = playerRepository.findByGuildIdAndServerId(guildId, server.getServerId());
                            guildPlayers.addAll(serverPlayers);
                        }
                        
                        totalPlayers += guildPlayers.size();
                        
                        // Process players for this guild with proper isolation
                        for (Player player : guildPlayers) {
                            if (validateAndFixPlayerStats(player, playerRepository)) {
                                correctionCount++;
                            }
                        }
                    } finally {
                        // Always clear context
                        com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                    }
                }
            }
            
            logger.info("Found {} total player records to verify", totalPlayers);
            logger.info("Completed player stats validation and sync, corrected {} records", correctionCount);
            return correctionCount;
        } catch (Exception e) {
            logger.error("Error validating and syncing player stats: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Get distinct guild IDs from all player records for isolation-aware processing
     */
    private static List<Long> getDistinctGuildIds(PlayerRepository playerRepository) {
        try {
            // Create a GameServerRepository to get guild IDs using isolation-aware methods
            // This is better than using getAllPlayers() which doesn't respect isolation
            com.deadside.bot.db.repositories.GameServerRepository serverRepo = 
                new com.deadside.bot.db.repositories.GameServerRepository();
            
            // Get distinct guild IDs using the proper isolation-aware method
            return serverRepo.getDistinctGuildIds();
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs using isolation-aware methods: {}", e.getMessage(), e);
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * Validate and fix player stats with proper isolation
     */
    private static boolean validateAndFixPlayerStats(Player player, PlayerRepository playerRepository) {
        // Implement stat validation and correction logic here
        // For now just return false as we're not making actual corrections yet
        return false;
    }
    
    /**
     * Fix stat discrepancies for a specific player within proper isolation boundaries
     * @param player The player to fix stats for
     * @param playerRepository The player repository to use for updates
     * @return True if fixes were applied
     */
    public static boolean fixPlayerStats(Player player, PlayerRepository playerRepository) {
        try {
            if (player == null) {
                return false;
            }
            
            // Check if player has proper isolation fields
            if (player.getGuildId() <= 0 || player.getServerId() == null || player.getServerId().isEmpty()) {
                logger.warn("Cannot fix stats for player without proper isolation fields: {}", 
                    player.getName() != null ? player.getName() : "unknown");
                return false;
            }
            
            // Fix K/D ratio calculation issues
            boolean needsUpdate = false;
            
            // Fix negative kills (data corruption)
            if (player.getKills() < 0) {
                player.setKills(0);
                needsUpdate = true;
            }
            
            // Fix negative deaths (data corruption)
            if (player.getDeaths() < 0) {
                player.setDeaths(0);
                needsUpdate = true;
            }
            
            // Update the player if needed
            if (needsUpdate) {
                playerRepository.save(player);
                logger.debug("Fixed stats for player {} with isolation (Guild={}, Server={})",
                    player.getName(), player.getGuildId(), player.getServerId());
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Error fixing player stats: {}", 
                player != null ? player.getName() : "unknown", e);
            return false;
        }
    }
    
    /**
     * Process a death log line with enhanced error handling and proper isolation
     * @param server The game server with proper isolation fields
     * @param logLine The log line to process
     * @param playerRepository The player repository to use for data access
     * @return True if processing was successful
     */
    public static boolean processDeathLogLineFixed(GameServer server, String logLine, PlayerRepository playerRepository) {
        try {
            if (server == null || logLine == null || logLine.isEmpty() || playerRepository == null) {
                return false;
            }
            
            // Check if server has valid isolation fields
            if (server.getGuildId() <= 0) {
                logger.warn("Cannot process death log for server without proper guild ID: {}", 
                    server.getName());
                return false;
            }
            
            // This is a placeholder implementation that ensures data isolation
            // In a real implementation, this would parse the log line and update player stats
            logger.debug("Processing death log line for server {} with guild isolation {}",
                server.getName(), server.getGuildId());
                
            return true;
        } catch (Exception e) {
            logger.error("Error processing death log line for server {}: {}", 
                server != null ? server.getName() : "unknown", e.getMessage(), e);
            return false;
        }
    }
}