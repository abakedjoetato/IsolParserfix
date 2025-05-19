package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.KillRecord;
import com.deadside.bot.utils.GuildIsolationManager;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for KillRecord model with comprehensive data isolation
 */
public class KillRecordRepository {
    private static final Logger logger = LoggerFactory.getLogger(KillRecordRepository.class);
    private static final String COLLECTION_NAME = "kill_records";
    
    private MongoCollection<KillRecord> collection;
    
    public KillRecordRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, KillRecord.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<KillRecord> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, KillRecord.class);
            } catch (Exception e) {
                logger.error("Failed to initialize kill record collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save a kill record with proper isolation checks
     */
    public void save(KillRecord killRecord) {
        try {
            // Ensure kill record has valid isolation fields
            if (killRecord.getGuildId() <= 0 || killRecord.getServerId() == null || killRecord.getServerId().isEmpty()) {
                logger.error("Attempted to save kill record without proper isolation fields");
                return;
            }
            
            getCollection().insertOne(killRecord);
            logger.debug("Saved kill record with proper isolation (Guild={}, Server={})",
                killRecord.getGuildId(), killRecord.getServerId());
        } catch (Exception e) {
            logger.error("Error saving kill record", e);
        }
    }
    
    /**
     * Save multiple kill records with proper isolation checks
     */
    public void saveAll(List<KillRecord> killRecords) {
        try {
            if (killRecords.isEmpty()) {
                return;
            }
            
            // Validate all records have proper isolation
            List<KillRecord> validRecords = new ArrayList<>();
            for (KillRecord record : killRecords) {
                if (record.getGuildId() <= 0 || record.getServerId() == null || record.getServerId().isEmpty()) {
                    logger.error("Skipping kill record without proper isolation fields");
                    continue;
                }
                validRecords.add(record);
            }
            
            if (!validRecords.isEmpty()) {
                getCollection().insertMany(validRecords);
                logger.debug("Saved {} kill records with proper isolation", validRecords.size());
            }
        } catch (Exception e) {
            logger.error("Error saving multiple kill records", e);
        }
    }
    
    /**
     * Find recent kill records for a guild with isolation
     */
    public List<KillRecord> findRecentByGuildId(long guildId, int limit) {
        try {
            if (guildId <= 0) {
                logger.warn("Attempted to find kill records with invalid guild ID: {}", guildId);
                return new ArrayList<>();
            }
            
            Bson filter = Filters.eq("guildId", guildId);
            FindIterable<KillRecord> results = getCollection().find(filter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit);
            
            List<KillRecord> records = new ArrayList<>();
            for (KillRecord record : results) {
                records.add(record);
            }
            
            logger.debug("Found {} recent kill records for guild {} using isolation-aware approach", 
                records.size(), guildId);
            return records;
        } catch (Exception e) {
            logger.error("Error finding recent kill records for guild ID: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find recent kill records for a server with proper isolation
     */
    public List<KillRecord> findRecentByServerIdAndGuildId(String serverId, long guildId, int limit) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to find kill records without proper isolation parameters. Guild ID: {}, Server ID: {}", 
                    guildId, serverId);
                return new ArrayList<>();
            }
            
            Bson filter = Filters.and(
                    Filters.eq("serverId", serverId),
                    Filters.eq("guildId", guildId)
            );
            FindIterable<KillRecord> results = getCollection().find(filter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit);
            
            List<KillRecord> records = new ArrayList<>();
            for (KillRecord record : results) {
                records.add(record);
            }
            
            logger.debug("Found {} recent kill records for guild {} and server {} using isolation-aware approach", 
                records.size(), guildId, serverId);
            return records;
        } catch (Exception e) {
            logger.error("Error finding recent kill records for server ID: {} and guild ID: {}", serverId, guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete all kill records for a specific server in a guild with proper isolation
     * @param serverId The server ID (name)
     * @param guildId The guild ID
     * @return Number of records deleted
     */
    public int deleteByServerIdAndGuildId(String serverId, long guildId) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to delete kill records without proper isolation parameters. Guild ID: {}, Server ID: {}", 
                    guildId, serverId);
                return 0;
            }
            
            Bson filter = Filters.and(
                    Filters.eq("serverId", serverId),
                    Filters.eq("guildId", guildId)
            );
            
            long deletedCount = getCollection().deleteMany(filter).getDeletedCount();
            logger.info("Deleted {} kill records for guild {} and server {} using isolation-aware approach", 
                deletedCount, guildId, serverId);
            return (int) deletedCount;
        } catch (Exception e) {
            logger.error("Error deleting kill records for server ID: {} and guild ID: {}", serverId, guildId, e);
            return 0;
        }
    }
    
    /**
     * Get all kill records using isolation-aware approach
     * This method properly respects isolation boundaries while retrieving all records
     * @return List of all kill records with proper isolation boundaries respected
     */
    public List<KillRecord> getAllKillRecords() {
        List<KillRecord> allRecords = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find kill records for this guild and server with proper isolation
                            List<KillRecord> serverRecords = findRecentByServerIdAndGuildId(server.getServerId(), guildId, Integer.MAX_VALUE);
                            allRecords.addAll(serverRecords);
                            
                            logger.debug("Found {} kill records for guild {} and server {} using isolation-aware approach", 
                                serverRecords.size(), guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Retrieved all kill records using isolation-aware approach: {} total records", allRecords.size());
        } catch (Exception e) {
            logger.error("Error getting all kill records using isolation-aware approach", e);
        }
        
        return allRecords;
    }
    
    /**
     * Get all distinct guild IDs from kill records collection
     * This is useful for isolation-aware operations across multiple guilds
     * @return List of distinct guild IDs
     */
    public List<Long> getDistinctGuildIds() {
        try {
            List<Long> guildIds = new ArrayList<>();
            getCollection().distinct("guildId", Long.class).into(guildIds);
            return guildIds;
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs from kill records collection", e);
            return new ArrayList<>();
        }
    }
}
