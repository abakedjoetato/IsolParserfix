package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.utils.GuildIsolationManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.client.DistinctIterable;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for GuildConfig objects in MongoDB with isolation awareness
 */
public class GuildConfigRepository {
    private static final Logger logger = LoggerFactory.getLogger(GuildConfigRepository.class);
    private MongoCollection<Document> collection;
    
    /**
     * Creates a new GuildConfigRepository
     */
    public GuildConfigRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase().getCollection("guild_configs");
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
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection("guild_configs");
            } catch (Exception e) {
                logger.error("Failed to initialize guild config collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Find a guild configuration by guild ID
     * Guild configs are naturally isolated by guild ID
     * 
     * @param guildId The Discord guild ID
     * @return The guild configuration, or null if not found
     */
    public GuildConfig findByGuildId(long guildId) {
        try {
            if (guildId <= 0) {
                logger.warn("Attempted to find guild config with invalid guild ID: {}", guildId);
                return null;
            }
            
            Document doc = getCollection().find(Filters.eq("guildId", guildId)).first();
            if (doc == null) {
                return null;
            }
            
            return new GuildConfig(doc);
        } catch (Exception e) {
            logger.error("Error finding guild config by guild ID: {}", guildId, e);
            return null;
        }
    }
    
    /**
     * Save a guild configuration with isolation validation
     * 
     * @param config The guild configuration to save
     * @return The saved guild configuration
     */
    public GuildConfig save(GuildConfig config) {
        try {
            // Ensure guild config has valid isolation fields
            if (config.getGuildId() <= 0) {
                logger.error("Attempted to save guild config without proper guild ID: {}", config.getGuildId());
                return config;
            }
            
            Document doc = config.toDocument();
            
            if (config.getId() == null) {
                getCollection().insertOne(doc);
                config.setId(doc.getObjectId("_id"));
                logger.debug("Inserted new guild config with proper isolation (Guild={})",
                    config.getGuildId());
            } else {
                getCollection().replaceOne(
                        Filters.eq("_id", config.getId()),
                        doc,
                        new ReplaceOptions().upsert(true)
                );
                logger.debug("Updated guild config with isolation (Guild={})",
                    config.getGuildId());
            }
            
            return config;
        } catch (Exception e) {
            logger.error("Error saving guild configuration for guild ID: {}", config.getGuildId(), e);
            return config;
        }
    }
    
    /**
     * Delete a guild configuration with isolation validation
     * 
     * @param config The guild configuration to delete
     */
    public void delete(GuildConfig config) {
        try {
            if (config == null || config.getId() == null) {
                logger.warn("Attempted to delete null guild config");
                return;
            }
            
            if (config.getGuildId() <= 0) {
                logger.warn("Attempted to delete guild config without proper guild ID: {}", config.getGuildId());
                return;
            }
            
            getCollection().deleteOne(Filters.and(
                Filters.eq("_id", config.getId()),
                Filters.eq("guildId", config.getGuildId())
            ));
            logger.debug("Deleted guild config with isolation (Guild={})", config.getGuildId());
        } catch (Exception e) {
            logger.error("Error deleting guild config: {}", config.getId(), e);
        }
    }
    
    /**
     * Delete a guild configuration by ID with isolation validation
     * This method enforces guild ID check for proper isolation
     * 
     * @param id The MongoDB ObjectId
     * @param guildId The guild ID for isolation
     */
    public void delete(ObjectId id, long guildId) {
        try {
            if (id == null) {
                logger.warn("Attempted to delete guild config with null ID");
                return;
            }
            
            if (guildId <= 0) {
                logger.warn("Attempted to delete guild config without proper guild ID: {}", guildId);
                return;
            }
            
            getCollection().deleteOne(Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId)
            ));
            logger.debug("Deleted guild config with isolation (Guild={})", guildId);
        } catch (Exception e) {
            logger.error("Error deleting guild config by ID: {} with guild ID: {}", id, guildId, e);
        }
    }
    
    /**
     * Delete a guild configuration by ID
     * This method is deprecated and should be replaced with the isolation-aware version
     * 
     * @param id The MongoDB ObjectId
     */
    @Deprecated
    public void delete(ObjectId id) {
        logger.warn("Called unsafe non-isolated guild config delete by ID: {}. Consider using delete(ObjectId, long) instead.", id);
        try {
            getCollection().deleteOne(Filters.eq("_id", id));
        } catch (Exception e) {
            logger.error("Error deleting guild config by ID: {}", id, e);
        }
    }
    
    /**
     * Delete a guild configuration by guild ID
     * Guild configs are naturally isolated by guild ID
     * 
     * @param guildId The Discord guild ID
     */
    public void deleteByGuildId(long guildId) {
        try {
            if (guildId <= 0) {
                logger.warn("Attempted to delete guild config with invalid guild ID: {}", guildId);
                return;
            }
            
            getCollection().deleteOne(Filters.eq("guildId", guildId));
            logger.debug("Deleted guild config by guild ID with isolation (Guild={})", guildId);
        } catch (Exception e) {
            logger.error("Error deleting guild config by guild ID: {}", guildId, e);
        }
    }
    
    /**
     * Find all guild configurations that have premium status using isolation-aware approach
     * 
     * @return List of premium guild configurations with proper isolation boundaries respected
     */
    public List<GuildConfig> findAllPremium() {
        List<GuildConfig> result = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Find premium config with proper isolation
                    Document doc = getCollection().find(Filters.and(
                        Filters.eq("guildId", guildId),
                        Filters.eq("premium", true)
                    )).first();
                    
                    if (doc != null) {
                        result.add(new GuildConfig(doc));
                        logger.debug("Found premium guild config for guild {} using isolation-aware approach", guildId);
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Found {} premium guild configs using isolation-aware approach", result.size());
        } catch (Exception e) {
            logger.error("Error finding premium guild configs using isolation-aware approach", e);
        }
        
        return result;
    }
    
    /**
     * Find all guild configurations using isolation-aware approach
     * This method properly respects isolation boundaries
     * @return List of all guild configurations with proper isolation boundaries respected
     */
    public List<GuildConfig> findAll() {
        List<GuildConfig> result = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Find config with proper isolation
                    Document doc = getCollection().find(Filters.eq("guildId", guildId)).first();
                    
                    if (doc != null) {
                        result.add(new GuildConfig(doc));
                        logger.debug("Found guild config for guild {} using isolation-aware approach", guildId);
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Found {} guild configs using isolation-aware approach", result.size());
        } catch (Exception e) {
            logger.error("Error finding all guild configs using isolation-aware approach", e);
        }
        
        return result;
    }
    
    /**
     * Get all distinct guild IDs from guild configurations
     * This is useful for isolation-aware operations
     * @return List of all guild IDs
     */
    public List<Long> getDistinctGuildIds() {
        List<Long> guildIds = new ArrayList<>();
        try {
            DistinctIterable<Long> distinctIds = getCollection().distinct("guildId", Long.class);
            distinctIds.into(guildIds);
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs from guild configs", e);
        }
        return guildIds;
    }
}