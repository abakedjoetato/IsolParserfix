package com.deadside.bot.utils;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating consistent data boundary filters
 * This ensures that all data operations respect proper isolation boundaries
 */
public class DataBoundary {
    private static final Logger logger = LoggerFactory.getLogger(DataBoundary.class);
    
    /**
     * Create a filter that restricts data to a specific guild
     */
    public static Bson createGuildFilter(long guildId) {
        if (guildId <= 0) {
            logger.warn("Attempted to create guild filter with invalid guild ID: {}", guildId);
        }
        return Filters.eq("guildId", guildId);
    }
    
    /**
     * Create a filter that restricts data to a specific server
     */
    public static Bson createServerFilter(String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            logger.warn("Attempted to create server filter with invalid server ID");
            serverId = "unknown"; // Fallback to prevent null reference
        }
        return Filters.eq("serverId", serverId);
    }
    
    /**
     * Create a filter that combines guild and server restrictions
     * This is the primary method for enforcing proper data isolation
     */
    public static Bson createIsolationFilter(long guildId, String serverId) {
        if (guildId <= 0) {
            logger.warn("Attempted to create isolation filter with invalid guild ID: {}", guildId);
        }
        
        if (serverId == null || serverId.isEmpty()) {
            logger.warn("Attempted to create isolation filter with invalid server ID");
            serverId = "unknown"; // Fallback to prevent null reference
        }
        
        return Filters.and(
            Filters.eq("guildId", guildId),
            Filters.eq("serverId", serverId)
        );
    }
    
    /**
     * Create a filter using the current context from GuildIsolationManager
     */
    public static Bson createContextFilter() {
        GuildIsolationManager isolationManager = GuildIsolationManager.getInstance();
        GuildIsolationManager.FilterContext context = isolationManager.getCurrentContext();
        
        if (context == null) {
            logger.error("Attempted to create context filter without active context - using default values");
            return createIsolationFilter(0, "unknown");
        }
        
        long guildId = context.getGuildId();
        String serverId = context.getServerId();
        
        if (!context.hasValidGuildId()) {
            logger.warn("Context filter using invalid guild ID: {}", guildId);
        }
        
        if (!context.hasValidServerId()) {
            logger.warn("Context filter using invalid server ID: {}", serverId);
            serverId = "unknown"; // Fallback to prevent null reference
        }
        
        return createIsolationFilter(guildId, serverId);
    }
    
    /**
     * Create a filter for guild only (partial isolation)
     * This should be used sparingly, as it doesn't provide full isolation
     */
    public static Bson createGuildOnlyContextFilter() {
        GuildIsolationManager isolationManager = GuildIsolationManager.getInstance();
        GuildIsolationManager.FilterContext context = isolationManager.getCurrentContext();
        
        if (context == null) {
            logger.error("Attempted to create guild-only context filter without active context - using default values");
            return createGuildFilter(0);
        }
        
        long guildId = context.getGuildId();
        
        if (!context.hasValidGuildId()) {
            logger.warn("Guild-only context filter using invalid guild ID: {}", guildId);
        }
        
        return createGuildFilter(guildId);
    }
    
    /**
     * Log a data boundary violation
     * This is used for security auditing and troubleshooting isolation issues
     * 
     * @param operation The operation being performed (e.g., "find", "save", "delete")
     * @param entityType The type of entity (e.g., "Player", "Bounty")
     * @param entityId The ID of the entity (if known)
     * @param contextGuildId The guild ID from the context
     * @param contextServerId The server ID from the context
     * @param entityGuildId The guild ID of the entity
     * @param entityServerId The server ID of the entity
     */
    public static void logBoundaryViolation(
            String operation, 
            String entityType, 
            String entityId,
            long contextGuildId, 
            String contextServerId,
            long entityGuildId,
            String entityServerId) {
        
        logger.warn("DATA BOUNDARY VIOLATION: Operation={}, Type={}, ID={}, " +
                    "Context[Guild={}, Server={}], Entity[Guild={}, Server={}]",
                operation, entityType, entityId,
                contextGuildId, contextServerId,
                entityGuildId, entityServerId);
    }
}