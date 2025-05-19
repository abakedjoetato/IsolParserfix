package com.deadside.bot.utils;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.KillRecordRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.db.repositories.FactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for cleaning up server-related data when a server is removed
 */
public class ServerDataCleanupUtil {
    private static final Logger logger = LoggerFactory.getLogger(ServerDataCleanupUtil.class);
    
    // Initialize repositories
    private static final KillRecordRepository killRecordRepository = new KillRecordRepository();
    private static final PlayerRepository playerRepository = new PlayerRepository();
    private static final FactionRepository factionRepository = new FactionRepository();
    
    /**
     * Clean up all data associated with a game server
     */
    public static CleanupSummary cleanupServerData(GameServer server) {
        CleanupSummary summary = new CleanupSummary();
        
        try {
            // 1. Delete all kill records for this server and count them
            int killRecordsDeleted = killRecordRepository.deleteByServerIdAndGuildId(
                    server.getName(), server.getGuildId());
            summary.setKillRecordsDeleted(killRecordsDeleted);
            
            // 2. Handle player stats - delete players for this specific server
            long playerRecordsDeleted = playerRepository.deleteAllByGuildIdAndServerId(
                    server.getGuildId(), server.getName());
            summary.setPlayerRecordsDeleted((int)playerRecordsDeleted); // Safe cast - unlikely to exceed Integer.MAX_VALUE
            
            // 3. Handle factions - Delete factions associated with this server
            // Currently factions are guild-specific, so we only delete if this is the primary server
            int factionsDeleted = 0;
            if (server.isPrimaryServer()) {
                // Get all factions for this guild
                var factions = factionRepository.findAllByGuildId(server.getGuildId());
                // Delete each faction
                for (var faction : factions) {
                    if (factionRepository.delete(faction)) {
                        factionsDeleted++;
                    }
                }
            }
            summary.setFactionsDeleted(factionsDeleted);
            
            // 4. No specific bounty implementation yet, but infrastructure is ready
            summary.setBountiesDeleted(0);
            
            summary.setSuccess(true);
            
            logger.info("Cleaned up data for server '{}' in guild {}: {} kill records, {} player records, {} factions", 
                    server.getName(), server.getGuildId(), killRecordsDeleted, playerRecordsDeleted, factionsDeleted);
        } catch (Exception e) {
            logger.error("Error cleaning up data for server '{}' in guild {}", 
                    server.getName(), server.getGuildId(), e);
            summary.setSuccess(false);
            summary.setErrorMessage(e.getMessage());
        }
        
        return summary;
    }
    
    /**
     * Summary of cleanup results
     */
    public static class CleanupSummary {
        private boolean success;
        private String errorMessage;
        private int killRecordsDeleted;
        private int playerRecordsDeleted;
        private int bountiesDeleted;
        private int factionsDeleted;
        
        public CleanupSummary() {
            this.success = false;
            this.errorMessage = "";
            this.killRecordsDeleted = 0;
            this.playerRecordsDeleted = 0;
            this.bountiesDeleted = 0;
            this.factionsDeleted = 0;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public int getKillRecordsDeleted() {
            return killRecordsDeleted;
        }
        
        public void setKillRecordsDeleted(int killRecordsDeleted) {
            this.killRecordsDeleted = killRecordsDeleted;
        }
        
        public int getPlayerRecordsDeleted() {
            return playerRecordsDeleted;
        }
        
        public void setPlayerRecordsDeleted(int playerRecordsDeleted) {
            this.playerRecordsDeleted = playerRecordsDeleted;
        }
        
        public int getBountiesDeleted() {
            return bountiesDeleted;
        }
        
        public void setBountiesDeleted(int bountiesDeleted) {
            this.bountiesDeleted = bountiesDeleted;
        }
        
        public int getFactionsDeleted() {
            return factionsDeleted;
        }
        
        public void setFactionsDeleted(int factionsDeleted) {
            this.factionsDeleted = factionsDeleted;
        }
        
        @Override
        public String toString() {
            return "CleanupSummary{" +
                    "success=" + success +
                    ", killRecordsDeleted=" + killRecordsDeleted +
                    ", playerRecordsDeleted=" + playerRecordsDeleted +
                    ", bountiesDeleted=" + bountiesDeleted +
                    ", factionsDeleted=" + factionsDeleted +
                    '}';
        }
    }
}