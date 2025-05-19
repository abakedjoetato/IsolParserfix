package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.Bounty;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing bounties with proper data isolation
 */
public class BountyRepository {
    private static final Logger logger = LoggerFactory.getLogger(BountyRepository.class);
    private static final String COLLECTION_NAME = "bounties";
    
    private MongoCollection<Bounty> collection;
    
    public BountyRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, Bounty.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<Bounty> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, Bounty.class);
            } catch (Exception e) {
                logger.error("Failed to initialize bounty collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save a bounty with proper isolation checks
     */
    public void save(Bounty bounty) {
        try {
            // Ensure bounty has valid isolation fields
            if (bounty.getGuildId() <= 0 || bounty.getServerId() == null || bounty.getServerId().isEmpty()) {
                logger.error("Attempted to save bounty without proper isolation fields");
                return;
            }
            
            if (bounty.getId() == null) {
                getCollection().insertOne(bounty);
                logger.debug("Inserted new bounty with proper isolation (Guild={}, Server={})",
                    bounty.getGuildId(), bounty.getServerId());
            } else {
                getCollection().replaceOne(
                    Filters.eq("_id", bounty.getId()),
                    bounty
                );
                logger.debug("Updated bounty with isolation (Guild={}, Server={})",
                    bounty.getGuildId(), bounty.getServerId());
            }
        } catch (Exception e) {
            logger.error("Error saving bounty", e);
        }
    }
    
    /**
     * Find active bounties for a target player with proper isolation
     */
    public List<Bounty> findActiveByTargetIdAndGuildIdAndServerId(String targetId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("targetId", targetId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId),
                Filters.eq("active", true)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding active bounties by target ID with isolation: {} (Guild={}, Server={})",
                targetId, guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find active bounties placed by a user with proper isolation
     */
    public List<Bounty> findActiveByPlacerIdAndGuildIdAndServerId(long placerId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("placerId", placerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId),
                Filters.eq("active", true)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding active bounties by placer ID with isolation: {} (Guild={}, Server={})",
                placerId, guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all active bounties for a guild and server
     */
    public List<Bounty> findAllActiveByGuildIdAndServerId(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId),
                Filters.eq("active", true)
            );
            return getCollection().find(filter)
                .sort(Sorts.descending("amount"))
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding all active bounties by guild and server: Guild={}, Server={}",
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find top bounties by amount for a guild and server
     */
    public List<Bounty> findTopBountiesByGuildIdAndServerId(long guildId, String serverId, int limit) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId),
                Filters.eq("active", true)
            );
            return getCollection().find(filter)
                .sort(Sorts.descending("amount"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top bounties by guild and server: Guild={}, Server={}",
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find a bounty by ID with proper isolation
     */
    public Bounty findByIdWithIsolation(ObjectId id, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding bounty by ID with isolation: {} (Guild={}, Server={})",
                id, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Delete a bounty by ID with proper isolation
     */
    public boolean deleteByIdWithIsolation(ObjectId id, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            DeleteResult result = getCollection().deleteOne(filter);
            return result.getDeletedCount() > 0;
        } catch (Exception e) {
            logger.error("Error deleting bounty by ID with isolation: {} (Guild={}, Server={})",
                id, guildId, serverId, e);
            return false;
        }
    }
    
    /**
     * Get all bounties using isolation-aware approach
     * This method properly respects isolation boundaries while retrieving all bounties
     * @return List of all bounties with proper isolation boundaries respected
     */
    public List<Bounty> getAllBounties() {
        List<Bounty> allBounties = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get bounties for this guild with proper isolation boundary
                    List<Bounty> guildBounties = findAllByGuildId(guildId);
                    allBounties.addAll(guildBounties);
                } finally {
                    // Always clear context when done
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Retrieved all bounties using isolation-aware approach: {} total records", allBounties.size());
        } catch (Exception e) {
            logger.error("Error getting all bounties using isolation-aware approach", e);
        }
        
        return allBounties;
    }
    
    /**
     * Find all bounties by guild ID and server ID with proper isolation
     * This method supports the Long parameter type required by isolation framework
     * @param guildId The Discord guild ID for isolation
     * @param serverId The game server ID for isolation
     * @return List of bounties for the specified guild and server
     */
    public List<Bounty> findAllByGuildId(Long guildId) {
        try {
            if (guildId == null || guildId <= 0) {
                logger.warn("Attempted to find bounties with invalid guild ID: {}", guildId);
                return new ArrayList<>();
            }
            
            Bson filter = Filters.eq("guildId", guildId);
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding bounties by guild ID: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    public List<Bounty> findAllByGuildIdAndServerId(Long guildId, String serverId) {
        try {
            if (guildId == null || guildId <= 0) {
                logger.warn("Attempted to find bounties with invalid guild ID: {}", guildId);
                return new ArrayList<>();
            }
            if (serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to find bounties with invalid server ID: {}", serverId);
                return new ArrayList<>();
            }
            
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding bounties by guild ID and server ID: {} / {}", guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get all distinct guild IDs from bounties collection
     * This is useful for isolation-aware operations across multiple guilds
     * @return List of distinct guild IDs
     */
    public List<Long> getDistinctGuildIds() {
        try {
            List<Long> guildIds = new ArrayList<>();
            getCollection().distinct("guildId", Long.class).into(guildIds);
            return guildIds;
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs from bounties collection", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete all bounties by guild and server - used for data cleanup
     */
    public long deleteAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            DeleteResult result = getCollection().deleteMany(filter);
            logger.info("Deleted {} bounties from Guild={}, Server={}", 
                result.getDeletedCount(), guildId, serverId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting bounties by guild and server", e);
            return 0;
        }
    }
    
    /**
     * Mark a bounty as claimed with proper isolation
     */
    public boolean markAsClaimed(ObjectId id, String claimerId, String claimerName, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId),
                Filters.eq("active", true)
            );
            
            Bounty bounty = getCollection().find(filter).first();
            if (bounty == null) {
                return false;
            }
            
            bounty.setActive(false);
            bounty.setClaimerId(claimerId);
            bounty.setClaimerName(claimerName);
            bounty.setClaimedAt(System.currentTimeMillis());
            
            getCollection().replaceOne(filter, bounty);
            return true;
        } catch (Exception e) {
            logger.error("Error marking bounty as claimed with isolation: {} (Guild={}, Server={})",
                id, guildId, serverId, e);
            return false;
        }
    }
}