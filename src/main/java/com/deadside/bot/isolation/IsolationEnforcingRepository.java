package com.deadside.bot.isolation;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for repositories that enforce data isolation
 * This provides a template for all repository implementations to inherit from
 * @param <T> The type of entity stored in the repository
 */
public abstract class IsolationEnforcingRepository<T> {
    private static final Logger logger = LoggerFactory.getLogger(IsolationEnforcingRepository.class);
    
    /**
     * Find all entities that belong to a specific guild and server
     * This ensures proper data isolation between Discord guilds and game servers
     * @param guildId The Discord guild ID
     * @param serverId The game server ID
     * @return List of entities that match the isolation criteria
     */
    public List<T> findAllIsolated(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding all entities isolated by guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Count all entities that belong to a specific guild and server
     * @param guildId The Discord guild ID
     * @param serverId The game server ID
     * @return Count of entities that match the isolation criteria
     */
    public long countIsolated(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().countDocuments(filter);
        } catch (Exception e) {
            logger.error("Error counting entities isolated by guild: {} and server: {}", 
                guildId, serverId, e);
            return 0;
        }
    }
    
    /**
     * Check if an entity belongs to the specified guild and server
     * @param entity The entity to check
     * @param guildId The expected guild ID
     * @param serverId The expected server ID
     * @return True if the entity belongs to the specified isolation boundary
     */
    public boolean verifyIsolationBoundary(T entity, long guildId, String serverId) {
        try {
            return IsolationManager.getInstance().verifyDataBoundary(entity, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error verifying isolation boundary for entity type: {}", 
                entity.getClass().getName(), e);
            return false;
        }
    }
    
    /**
     * Enforce isolation before saving an entity
     * @param entity The entity to save
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @throws DataIsolationAspect.DataIsolationException If the entity violates isolation boundaries
     */
    public void enforceSaveIsolation(T entity, long guildId, String serverId) {
        if (!verifyIsolationBoundary(entity, guildId, serverId)) {
            throw new DataIsolationAspect.DataIsolationException(
                "Cannot save entity to incorrect isolation boundary");
        }
    }
    
    /**
     * Get the MongoDB collection for this repository
     * @return The collection used for data storage
     */
    protected abstract MongoCollection<T> getCollection();
}