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
     * This method now also checks for server isolation mode and logs accordingly
     */
    public void setContext(long guildId, String serverId) {
        FilterContext context = new FilterContext(guildId);
        if (serverId != null && !serverId.isEmpty()) {
            context.setServerId(serverId);
            
            // Check if this server has a special isolation mode
            try {
                com.deadside.bot.db.repositories.GameServerRepository serverRepo = 
                    new com.deadside.bot.db.repositories.GameServerRepository();
                com.deadside.bot.db.models.GameServer server = 
                    serverRepo.findByServerIdAndGuildId(serverId, guildId);
                
                if (server != null) {
                    boolean isRestrictedMode = "Default Server".equals(server.getName()) || 
                                           server.isReadOnly() || 
                                           "disabled".equalsIgnoreCase(server.getIsolationMode()) ||
                                           "read-only".equalsIgnoreCase(server.getIsolationMode());
                                          
                    if (isRestrictedMode) {
                        String serverMode = "Default Server".equals(server.getName()) ? "Default Server" :
                                          server.isReadOnly() ? "read-only" :
                                          server.getIsolationMode() + " isolation";
                        
                        // Store isolation mode in context for easier access elsewhere
                        context.setIsolationMode(serverMode);
                        
                        logger.debug("Set isolation context for restricted server: Guild ID={}, Server ID={}, Mode={}",
                            context.getGuildId(), context.getServerId(), serverMode);
                    }
                }
            } catch (Exception e) {
                // Don't let this check interfere with setting the context
                logger.debug("Error checking server isolation mode: {}", e.getMessage());
            }
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
     * This method now also checks and includes isolation mode information
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
        
        // Check if this server has a special isolation mode
        try {
            com.deadside.bot.db.repositories.GameServerRepository serverRepo = 
                new com.deadside.bot.db.repositories.GameServerRepository();
            com.deadside.bot.db.models.GameServer server = 
                serverRepo.findByServerIdAndGuildId(serverId, guildId);
            
            if (server != null) {
                // Determine isolation mode from server properties
                if ("Default Server".equals(server.getName())) {
                    context.setIsolationMode("Default Server");
                } else if (server.isReadOnly()) {
                    context.setIsolationMode("read-only");
                } else if (server.getIsolationMode() != null && !server.getIsolationMode().isEmpty()) {
                    context.setIsolationMode(server.getIsolationMode());
                }
                
                logger.debug("Created filter context with isolation mode {}: Guild={}, Server={}", 
                    context.getIsolationMode(), guildId, serverId);
            }
        } catch (Exception e) {
            // Don't let this check interfere with creating the context
            logger.debug("Error checking server isolation mode during context creation: {}", e.getMessage());
        }
        
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
        private String isolationMode = "standard"; // Default isolation mode
        
        public FilterContext(long guildId) {
            this.guildId = guildId;
        }
        
        public FilterContext(long guildId, String serverId) {
            this.guildId = guildId;
            this.serverId = serverId;
        }
        
        public FilterContext(long guildId, String serverId, String isolationMode) {
            this.guildId = guildId;
            this.serverId = serverId;
            this.isolationMode = isolationMode;
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
        
        public String getIsolationMode() {
            return isolationMode;
        }
        
        public void setIsolationMode(String isolationMode) {
            this.isolationMode = isolationMode;
        }
        
        /**
         * Check if this server is in read-only mode
         * @return true if the server is in read-only mode
         */
        public boolean isReadOnly() {
            return "read-only".equalsIgnoreCase(isolationMode) || 
                   isolationMode.contains("read-only");
        }
        
        /**
         * Check if this server has disabled isolation
         * @return true if isolation is disabled for this server
         */
        public boolean isIsolationDisabled() {
            return "disabled".equalsIgnoreCase(isolationMode) ||
                   isolationMode.contains("disabled");
        }
        
        /**
         * Check if this is the Default Server
         * @return true if this is the Default Server
         */
        public boolean isDefaultServer() {
            return "Default Server".equals(isolationMode) ||
                   (serverId != null && "default".equalsIgnoreCase(serverId));
        }
        
        /**
         * Check if this server is in restricted mode (read-only, disabled, or Default Server)
         * @return true if the server has any type of restricted isolation mode
         */
        public boolean isRestrictedMode() {
            return isReadOnly() || isIsolationDisabled() || isDefaultServer();
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