package com.deadside.bot.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages guild isolation context to ensure data operations are always scoped
 * properly to the correct guild and game server
 */
public class GuildIsolationManager {
    private static final Logger logger = LoggerFactory.getLogger(GuildIsolationManager.class);
    
    // Singleton instance
    private static GuildIsolationManager instance;
    
    // Thread local context to store isolation information for the current operation
    private static final ThreadLocal<FilterContext> currentContext = new ThreadLocal<>();
    
    /**
     * Get the singleton instance
     */
    public static synchronized GuildIsolationManager getInstance() {
        if (instance == null) {
            instance = new GuildIsolationManager();
        }
        return instance;
    }
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private GuildIsolationManager() {
        // Private constructor to enforce singleton pattern
    }
    
    /**
     * Set the current context from a slash command event
     */
    public void setContextFromSlashCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild != null) {
            FilterContext context = new FilterContext(guild.getIdLong());
            currentContext.set(context);
            logger.debug("Set isolation context from slash command: Guild ID={}", context.getGuildId());
        } else {
            // DM/private channel - no guild isolation possible
            logger.warn("Attempted to set isolation context from slash command in non-guild environment");
            currentContext.remove();
        }
    }
    
    /**
     * Set the current context from a button interaction event
     */
    public void setContextFromButtonInteraction(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild != null) {
            FilterContext context = new FilterContext(guild.getIdLong());
            currentContext.set(context);
            logger.debug("Set isolation context from button interaction: Guild ID={}", context.getGuildId());
        } else {
            // DM/private channel - no guild isolation possible
            logger.warn("Attempted to set isolation context from button interaction in non-guild environment");
            currentContext.remove();
        }
    }
    
    /**
     * Set the current context from an auto-complete interaction event
     */
    public void setContextFromSlashCommand(CommandAutoCompleteInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild != null) {
            FilterContext context = new FilterContext(guild.getIdLong());
            currentContext.set(context);
            logger.debug("Set isolation context from auto-complete interaction: Guild ID={}", context.getGuildId());
        } else {
            // DM/private channel - no guild isolation possible
            logger.warn("Attempted to set isolation context from auto-complete in non-guild environment");
            currentContext.remove();
        }
    }
    
    /**
     * Set the current context from a select menu interaction event
     */
    public void setContextFromSelectMenuInteraction(StringSelectInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild != null) {
            FilterContext context = new FilterContext(guild.getIdLong());
            currentContext.set(context);
            logger.debug("Set isolation context from select menu interaction: Guild ID={}", context.getGuildId());
        } else {
            // DM/private channel - no guild isolation possible
            logger.warn("Attempted to set isolation context from select menu in non-guild environment");
            currentContext.remove();
        }
    }
    
    /**
     * Set the current context from a message event (for prefix commands)
     */
    public void setContextFromMessage(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        if (guild != null) {
            FilterContext context = new FilterContext(guild.getIdLong());
            currentContext.set(context);
            logger.debug("Set isolation context from message: Guild ID={}", context.getGuildId());
        } else {
            // DM/private channel - no guild isolation possible
            logger.warn("Attempted to set isolation context from message in non-guild environment");
            currentContext.remove();
        }
    }
    
    /**
     * Set the current context with specified guild ID and server ID
     */
    public void setContext(long guildId, String serverId) {
        FilterContext context = new FilterContext(guildId);
        if (serverId != null && !serverId.isEmpty()) {
            context.setServerId(serverId);
        }
        currentContext.set(context);
        logger.debug("Set isolation context manually: Guild ID={}, Server ID={}", context.getGuildId(), context.getServerId());
    }
    
    /**
     * Set the server ID in the current context
     */
    public void setServerIdInContext(String serverId) {
        FilterContext context = currentContext.get();
        if (context != null) {
            context.setServerId(serverId);
            logger.debug("Updated server ID in isolation context: Guild ID={}, Server ID={}", 
                context.getGuildId(), context.getServerId());
        } else {
            // Create a new context with default guild ID if none exists
            logger.warn("Attempted to set server ID without existing context, creating temporary context");
            context = new FilterContext(0); // Invalid guild ID as placeholder
            context.setServerId(serverId);
            currentContext.set(context);
        }
    }
    
    /**
     * Get the current filter context
     */
    public FilterContext getCurrentContext() {
        return currentContext.get();
    }
    
    /**
     * Get the current guild ID from context
     */
    public long getCurrentGuildId() {
        FilterContext context = currentContext.get();
        if (context != null) {
            return context.getGuildId();
        }
        logger.warn("Attempted to access guild ID without context - using default value 0");
        return 0; // Invalid default value
    }
    
    /**
     * Get the current server ID from context
     */
    public String getCurrentServerId() {
        FilterContext context = currentContext.get();
        if (context != null) {
            return context.getServerId();
        }
        logger.warn("Attempted to access server ID without context - returning null");
        return null;
    }
    
    /**
     * Clear the current context when the operation is complete
     * Should be called at the end of each request to prevent context leakage
     */
    public void clearContext() {
        currentContext.remove();
        logger.debug("Cleared isolation context");
    }
    
    /**
     * Create a new filter context with the specified guild and server IDs
     * @param guildId The Discord guild ID
     * @param serverId The game server ID
     * @return The new filter context, or null if invalid parameters
     */
    public FilterContext createFilterContext(long guildId, String serverId) {
        if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
            logger.warn("Attempted to create filter context with invalid parameters: Guild={}, Server={}", 
                guildId, serverId);
            return null;
        }
        
        FilterContext context = new FilterContext(guildId);
        context.setServerId(serverId);
        return context;
    }
    
    /**
     * Verify if a model object belongs to the specified guild and server
     * @param model The model object to verify
     * @param guildId The expected guild ID
     * @param serverId The expected server ID
     * @return True if the model belongs to the specified context
     */
    public boolean verifyModelIsolation(Object model, long guildId, String serverId) {
        if (model == null) {
            return false;
        }
        
        try {
            // Use reflection to check for getGuildId and getServerId methods
            java.lang.reflect.Method getGuildIdMethod = model.getClass().getMethod("getGuildId");
            java.lang.reflect.Method getServerIdMethod = model.getClass().getMethod("getServerId");
            
            Long modelGuildId = (Long) getGuildIdMethod.invoke(model);
            String modelServerId = (String) getServerIdMethod.invoke(model);
            
            return modelGuildId != null && modelGuildId == guildId && 
                   modelServerId != null && modelServerId.equals(serverId);
        } catch (Exception e) {
            logger.error("Failed to verify model isolation for {}: {}", 
                model.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Filter context class that stores the current guild and server ID
     * for data isolation purposes
     */
    public static class FilterContext {
        private long guildId;
        private String serverId;
        
        public FilterContext(long guildId) {
            this.guildId = guildId;
        }
        
        public long getGuildId() {
            return guildId;
        }
        
        public void setGuildId(long guildId) {
            this.guildId = guildId;
        }
        
        public String getServerId() {
            return serverId;
        }
        
        public void setServerId(String serverId) {
            this.serverId = serverId;
        }
        
        /**
         * Check if this context has a valid guild ID
         */
        public boolean hasValidGuildId() {
            return guildId > 0;
        }
        
        /**
         * Check if this context has a valid server ID
         */
        public boolean hasValidServerId() {
            return serverId != null && !serverId.isEmpty();
        }
        
        /**
         * Check if this context has complete isolation information
         */
        public boolean isComplete() {
            return hasValidGuildId() && hasValidServerId();
        }
        
        /**
         * Verify that an entity's guild and server match this context
         * @param entityGuildId The guild ID of the entity
         * @param entityServerId The server ID of the entity
         * @return True if the entity belongs to this context
         */
        public boolean verifyBoundaries(long entityGuildId, String entityServerId) {
            if (!isComplete()) {
                return false;
            }
            
            return entityGuildId == guildId && 
                   entityServerId != null && entityServerId.equals(serverId);
        }
        
        /**
         * Verify that a model object belongs to this context
         * @param model The model object to verify
         * @return True if the model belongs to this context
         */
        public boolean verifyModelBoundaries(Object model) {
            if (model == null || !isComplete()) {
                return false;
            }
            
            try {
                // Use reflection to check for getGuildId and getServerId methods
                java.lang.reflect.Method getGuildIdMethod = model.getClass().getMethod("getGuildId");
                java.lang.reflect.Method getServerIdMethod = model.getClass().getMethod("getServerId");
                
                Long modelGuildId = (Long) getGuildIdMethod.invoke(model);
                String modelServerId = (String) getServerIdMethod.invoke(model);
                
                return verifyBoundaries(modelGuildId, modelServerId);
            } catch (Exception e) {
                Logger logger = LoggerFactory.getLogger(FilterContext.class);
                logger.error("Failed to verify model boundaries for {}: {}", 
                    model.getClass().getSimpleName(), e.getMessage());
                return false;
            }
        }
    }
}