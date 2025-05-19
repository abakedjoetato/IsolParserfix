package com.deadside.bot.isolation;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.utils.GuildIsolationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for data isolation between guilds and game servers
 * This ensures that each guild's data is properly isolated from other guilds
 */
public class IsolationManager {
    private static final Logger logger = LoggerFactory.getLogger(IsolationManager.class);
    
    private static IsolationManager instance;
    
    // Cache of isolation contexts for performance
    private final Map<String, IsolationContext> contextCache = new ConcurrentHashMap<>();
    
    /**
     * Context for data isolation operations
     */
    public static class IsolationContext {
        private final long guildId;
        private final String serverId;
        private final GuildIsolationManager.FilterContext filterContext;
        
        private IsolationContext(long guildId, String serverId, GuildIsolationManager.FilterContext filterContext) {
            this.guildId = guildId;
            this.serverId = serverId;
            this.filterContext = filterContext;
        }
        
        /**
         * Get the Discord guild ID
         */
        public long getGuildId() {
            return guildId;
        }
        
        /**
         * Get the game server ID
         */
        public String getServerId() {
            return serverId;
        }
        
        /**
         * Get the filter context for database operations
         */
        public GuildIsolationManager.FilterContext getFilterContext() {
            return filterContext;
        }
        
        /**
         * Verify data boundaries for an entity
         * @param entityGuildId The guild ID of the entity
         * @param entityServerId The server ID of the entity
         * @return True if the entity belongs to this context
         */
        public boolean verifyBoundaries(long entityGuildId, String entityServerId) {
            return filterContext.verifyBoundaries(entityGuildId, entityServerId);
        }
        
        /**
         * Verify that a model object belongs to this context
         * @param model The model object to verify
         * @return True if the model belongs to this context
         */
        public boolean verifyModelBoundaries(Object model) {
            return filterContext.verifyModelBoundaries(model);
        }
    }
    
    private IsolationManager() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized IsolationManager getInstance() {
        if (instance == null) {
            instance = new IsolationManager();
        }
        return instance;
    }
    
    /**
     * Create an isolation context for a guild and server
     * @param guildId The Discord guild ID
     * @param serverId The game server ID
     * @return An isolation context for enforcing boundaries
     */
    public IsolationContext createContext(long guildId, String serverId) {
        if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
            logger.warn("Attempted to create invalid isolation context: guild={}, server={}", guildId, serverId);
            return null;
        }
        
        String cacheKey = guildId + ":" + serverId;
        return contextCache.computeIfAbsent(cacheKey, k -> {
            GuildIsolationManager.FilterContext filterContext = 
                GuildIsolationManager.getInstance().createFilterContext(guildId, serverId);
            
            if (filterContext == null) {
                return null;
            }
            
            logger.debug("Created new isolation context for guild {} and server {}", guildId, serverId);
            return new IsolationContext(guildId, serverId, filterContext);
        });
    }
    
    /**
     * Verify data boundaries for a model object
     * @param model The model to verify
     * @param guildId The expected guild ID
     * @param serverId The expected server ID
     * @return True if the model belongs to the specified context
     */
    public boolean verifyDataBoundary(Object model, long guildId, String serverId) {
        if (model == null) {
            return false;
        }
        
        return GuildIsolationManager.getInstance().verifyModelIsolation(model, guildId, serverId);
    }
    
    /**
     * Clear the isolation context cache
     */
    public void clearCache() {
        contextCache.clear();
    }
    
    /**
     * Reset the singleton instance (mainly for testing)
     */
    public static void reset() {
        instance = null;
    }
}