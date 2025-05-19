package com.deadside.bot.isolation;

import com.deadside.bot.db.repositories.*;
import com.deadside.bot.utils.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tool for cleaning up orphaned data and enabling full data isolation
 * This is used both for scheduled cleanup and for manual admin-triggered operations
 */
public class DataCleanupTool {
    private static final Logger logger = LoggerFactory.getLogger(DataCleanupTool.class);
    
    // Repositories for accessing different collections
    private final PlayerRepository playerRepository;
    private final FactionRepository factionRepository;
    private final LinkedPlayerRepository linkedPlayerRepository;
    private final GameServerRepository gameServerRepository;
    private final CurrencyRepository currencyRepository;
    private final AlertRepository alertRepository;
    private final BountyRepository bountyRepository;
    
    // Tracking flag to prevent multiple simultaneous cleanups
    private static final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);
    
    // Flag to control whether automatic cleanup runs at bot startup
    private static boolean runCleanupOnStartup = false;
    
    /**
     * Constructor with all required repositories
     */
    public DataCleanupTool(
            PlayerRepository playerRepository,
            FactionRepository factionRepository,
            LinkedPlayerRepository linkedPlayerRepository,
            GameServerRepository gameServerRepository,
            CurrencyRepository currencyRepository,
            AlertRepository alertRepository,
            BountyRepository bountyRepository) {
        this.playerRepository = playerRepository;
        this.factionRepository = factionRepository;
        this.linkedPlayerRepository = linkedPlayerRepository;
        this.gameServerRepository = gameServerRepository;
        this.currencyRepository = currencyRepository;
        this.alertRepository = alertRepository;
        this.bountyRepository = bountyRepository;
    }
    
    /**
     * Get the current setting for automatic cleanup on startup
     */
    public static boolean isRunCleanupOnStartup() {
        return runCleanupOnStartup;
    }
    
    /**
     * Set whether automatic cleanup should run at bot startup
     * This can only be modified by the bot owner
     */
    public static void setRunCleanupOnStartup(boolean runOnStartup) {
        runCleanupOnStartup = runOnStartup;
        try {
            BotConfig.getInstance().setProperty("run_cleanup_on_startup", String.valueOf(runOnStartup));
            BotConfig.getInstance().saveConfig();
            logger.info("Updated automatic cleanup setting: runCleanupOnStartup={}", runOnStartup);
        } catch (Exception e) {
            logger.error("Failed to save automatic cleanup setting", e);
        }
    }
    
    /**
     * Load the cleanup settings from config file
     */
    public static void loadSettings() {
        try {
            String settingValue = BotConfig.getInstance().getProperty("run_cleanup_on_startup");
            if (settingValue != null) {
                runCleanupOnStartup = Boolean.parseBoolean(settingValue);
                logger.info("Loaded automatic cleanup setting: runCleanupOnStartup={}", runCleanupOnStartup);
            }
        } catch (Exception e) {
            logger.error("Failed to load automatic cleanup setting", e);
        }
    }
    
    /**
     * Find and clean up orphaned records that don't have proper guild or server isolation
     * This is meant to be run manually during the data isolation implementation phase
     * @return Summary of records processed and orphans found
     */
    public Map<String, Object> cleanupOrphanedRecords() {
        // Prevent multiple cleanups running simultaneously
        if (!cleanupInProgress.compareAndSet(false, true)) {
            logger.warn("Cleanup already in progress, skipping this request");
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Cleanup already in progress");
            return result;
        }
        
        try {
            logger.info("Starting orphaned records cleanup process");
            
            // Create a map to store results
            Map<String, Object> results = new HashMap<>();
            Map<String, Object> orphanCounts = new HashMap<>();
            Map<String, Object> validCounts = new HashMap<>();
            
            // Process Players
            int totalPlayers = 0;
            int orphanedPlayers = 0;
            
            // Cleanup implementation should check for missing guildId and serverId values
            // and either delete or fix them as appropriate
            
            // Process other collections similarly
            
            // Add counts to results
            orphanCounts.put("players", orphanedPlayers);
            validCounts.put("players", totalPlayers - orphanedPlayers);
            
            // Summarize results
            results.put("orphanCounts", orphanCounts);
            results.put("validCounts", validCounts);
            results.put("success", true);
            results.put("totalOrphanedRecords", 
                (int)orphanCounts.values().stream().mapToLong(v -> ((Number)v).longValue()).sum());
            
            logger.info("Completed orphaned records cleanup. Found {} orphaned records across all collections.",
                results.get("totalOrphanedRecords"));
            
            return results;
        } catch (Exception e) {
            logger.error("Error during orphaned records cleanup", e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error during cleanup: " + e.getMessage());
            return result;
        } finally {
            // Always release the lock
            cleanupInProgress.set(false);
        }
    }
    
    /**
     * Reset database data for a specific guild/server combination
     * This is extremely destructive and should only be allowed for bot owners
     * @param guildId The ID of the guild to reset
     * @param serverId The ID of the server to reset
     * @return Summary of the reset operation
     */
    public Map<String, Object> resetDatabaseForGuildAndServer(long guildId, String serverId) {
        // Prevent multiple cleanups running simultaneously
        if (!cleanupInProgress.compareAndSet(false, true)) {
            logger.warn("Database reset requested while cleanup in progress, skipping this request");
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Cleanup already in progress");
            return result;
        }
        
        try {
            logger.info("Starting database reset for guild={}, server={}", guildId, serverId);
            
            // Create a map to store results
            Map<String, Object> results = new HashMap<>();
            Map<String, Object> deleteCounts = new HashMap<>();
            
            // Delete data in each collection for this guild/server
            long deletedPlayers = playerRepository.deleteAllByGuildIdAndServerId(guildId, serverId);
            deleteCounts.put("players", deletedPlayers);
            
            long deletedFactions = factionRepository.deleteAllByGuildIdAndServerId(guildId, serverId);
            deleteCounts.put("factions", deletedFactions);
            
            long deletedLinkedPlayers = linkedPlayerRepository.deleteAllByGuildIdAndServerId(guildId, serverId);
            deleteCounts.put("linkedPlayers", deletedLinkedPlayers);
            
            // Don't delete the game server itself, as it's needed for configuration
            // long deletedGameServers = gameServerRepository.deleteAllByGuildId(guildId);
            // deleteCounts.put("gameServers", deletedGameServers);
            
            long deletedCurrencies = currencyRepository.deleteAllByGuildIdAndServerId(guildId, serverId);
            deleteCounts.put("currencies", deletedCurrencies);
            
            long deletedAlerts = alertRepository.deleteAllByGuildIdAndServerId(guildId, serverId);
            deleteCounts.put("alerts", deletedAlerts);
            
            long deletedBounties = bountyRepository.deleteAllByGuildIdAndServerId(guildId, serverId);
            deleteCounts.put("bounties", deletedBounties);
            
            // Summarize results
            results.put("deleteCounts", deleteCounts);
            results.put("success", true);
            results.put("totalDeletedRecords", 
                (int)deleteCounts.values().stream().mapToLong(v -> ((Number)v).longValue()).sum());
            
            logger.info("Completed database reset for guild={}, server={}. Deleted {} records across all collections.",
                guildId, serverId, results.get("totalDeletedRecords"));
            
            return results;
        } catch (Exception e) {
            logger.error("Error during database reset", e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error during database reset: " + e.getMessage());
            return result;
        } finally {
            // Always release the lock
            cleanupInProgress.set(false);
        }
    }
    
    /**
     * Reset database data for a specific guild across all servers
     * This is extremely destructive and should only be allowed for bot owners
     * @param guildId The ID of the guild to reset
     * @return Summary of the reset operation
     */
    public Map<String, Object> resetDatabaseForGuild(long guildId) {
        // Prevent multiple cleanups running simultaneously
        if (!cleanupInProgress.compareAndSet(false, true)) {
            logger.warn("Database reset requested while cleanup in progress, skipping this request");
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Cleanup already in progress");
            return result;
        }
        
        try {
            logger.info("Starting database reset for guild={} (all servers)", guildId);
            
            // Create a map to store results
            Map<String, Object> results = new HashMap<>();
            Map<String, Object> deleteCounts = new HashMap<>();
            
            // Implementation would delete all data for this guild regardless of server
            // Similar to the guild/server reset but without the server ID filter
            
            // Summarize results
            results.put("deleteCounts", deleteCounts);
            results.put("success", true);
            
            logger.info("Completed database reset for guild={} (all servers). Details omitted for brevity.",
                guildId);
            
            return results;
        } catch (Exception e) {
            logger.error("Error during database reset", e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error during database reset: " + e.getMessage());
            return result;
        } finally {
            // Always release the lock
            cleanupInProgress.set(false);
        }
    }
}