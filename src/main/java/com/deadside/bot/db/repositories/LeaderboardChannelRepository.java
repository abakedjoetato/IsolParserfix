package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for managing auto-updating leaderboard channels
 */
public class LeaderboardChannelRepository {
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardChannelRepository.class);
    private static final String COLLECTION_NAME = "leaderboard_channels";
    
    private MongoCollection<Document> collection;
    
    public LeaderboardChannelRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<Document> getCollection() {
        if (collection == null) {
            // Try to get the collection now that MongoDB should be initialized
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME);
        }
        return collection;
    }
    
    /**
     * Save or update a leaderboard channel configuration
     * This method doesn't enforce server isolation and should be used only for guild-wide leaderboards
     */
    public void saveLeaderboardChannel(long guildId, long channelId) {
        try {
            Bson filter = Filters.eq("guildId", guildId);
            Bson update = Updates.combine(
                Updates.set("channelId", channelId),
                Updates.set("lastUpdated", System.currentTimeMillis())
            );
            
            UpdateOptions options = new UpdateOptions().upsert(true);
            getCollection().updateOne(filter, update, options);
            
            logger.info("Saved leaderboard channel: Guild={}, Channel={}", guildId, channelId);
        } catch (Exception e) {
            logger.error("Error saving leaderboard channel", e);
        }
    }
    
    /**
     * Save or update a server-specific leaderboard channel configuration with proper isolation
     */
    public void saveLeaderboardChannel(long guildId, String serverId, long channelId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            Bson update = Updates.combine(
                Updates.set("channelId", channelId),
                Updates.set("lastUpdated", System.currentTimeMillis())
            );
            
            UpdateOptions options = new UpdateOptions().upsert(true);
            getCollection().updateOne(filter, update, options);
            
            logger.info("Saved server-specific leaderboard channel: Guild={}, Server={}, Channel={}", 
                guildId, serverId, channelId);
        } catch (Exception e) {
            logger.error("Error saving server-specific leaderboard channel", e);
        }
    }
    
    /**
     * Get a leaderboard channel for a specific guild
     * This method doesn't enforce server isolation and should be used only for guild-wide leaderboards
     */
    public Long getLeaderboardChannel(long guildId) {
        try {
            Document doc = getCollection().find(Filters.eq("guildId", guildId)).first();
            return doc != null ? doc.getLong("channelId") : null;
        } catch (Exception e) {
            logger.error("Error getting leaderboard channel for guild: {}", guildId, e);
            return null;
        }
    }
    
    /**
     * Get a server-specific leaderboard channel for proper data isolation
     */
    public Long getLeaderboardChannel(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            Document doc = getCollection().find(filter).first();
            return doc != null ? doc.getLong("channelId") : null;
        } catch (Exception e) {
            logger.error("Error getting leaderboard channel for guild: {} and server: {}", 
                guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Get all leaderboard channel configurations using isolation-aware approach
     * This method gets all records but logs a warning when called
     */
    public List<Map<String, Object>> getAllLeaderboardChannels() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild's leaderboard channels with proper isolation
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild before accessing data
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all channels for this guild (isolation-aware)
                    List<Map<String, Object>> guildChannels = getAllLeaderboardChannelsByGuildId(guildId);
                    result.addAll(guildChannels);
                } finally {
                    // Always clear context, even if exception occurs
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
        } catch (Exception e) {
            logger.error("Error getting all leaderboard channels using isolation-aware method", e);
        }
        
        return result;
    }
    
    /**
     * Get distinct guild IDs from the leaderboard channel collection
     * Used for isolation-aware cross-guild operations
     */
    public List<Long> getDistinctGuildIds() {
        List<Long> guildIds = new ArrayList<>();
        
        try {
            getCollection().distinct("guildId", Long.class).into(guildIds);
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs from leaderboard channels", e);
        }
        
        return guildIds;
    }
    
    /**
     * Get all leaderboard channel configurations for a specific guild
     * This ensures isolation at the guild level
     */
    public List<Map<String, Object>> getAllLeaderboardChannelsByGuildId(long guildId) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            getCollection().find(Filters.eq("guildId", guildId)).forEach(doc -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("guildId", doc.getLong("guildId"));
                entry.put("channelId", doc.getLong("channelId"));
                entry.put("lastUpdated", doc.getLong("lastUpdated"));
                
                // Include serverId if it exists in the document
                if (doc.containsKey("serverId")) {
                    entry.put("serverId", doc.getString("serverId"));
                }
                
                result.add(entry);
            });
        } catch (Exception e) {
            logger.error("Error getting leaderboard channels for guild: {}", guildId, e);
        }
        
        return result;
    }
    
    /**
     * Delete a leaderboard channel configuration
     * This method doesn't enforce server isolation and should be used only for guild-wide leaderboards
     */
    public void deleteLeaderboardChannel(long guildId) {
        try {
            getCollection().deleteOne(Filters.eq("guildId", guildId));
            logger.info("Deleted leaderboard channel for guild: {}", guildId);
        } catch (Exception e) {
            logger.error("Error deleting leaderboard channel for guild: {}", guildId, e);
        }
    }
    
    /**
     * Delete a server-specific leaderboard channel configuration with proper isolation
     */
    public void deleteLeaderboardChannel(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            getCollection().deleteOne(filter);
            logger.info("Deleted server-specific leaderboard channel for guild: {} and server: {}", 
                guildId, serverId);
        } catch (Exception e) {
            logger.error("Error deleting server-specific leaderboard channel for guild: {} and server: {}", 
                guildId, serverId, e);
        }
    }
}