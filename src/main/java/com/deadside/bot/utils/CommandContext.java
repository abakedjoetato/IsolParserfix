package com.deadside.bot.utils;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command context class that enforces data isolation and maintains proper boundaries
 * This should be used in all command handlers to ensure operations respect guild and server isolation
 */
public class CommandContext {
    private static final Logger logger = LoggerFactory.getLogger(CommandContext.class);
    
    private final SlashCommandInteractionEvent event;
    private final Guild guild;
    private final long guildId;
    private final GameServer gameServer;
    private final String serverId;
    private final GuildIsolationManager.FilterContext filterContext;
    private final boolean validContext;
    
    /**
     * Create a command context from a slash command interaction
     * @param event The slash command event
     * @param selectedServer The currently selected game server (may be null)
     */
    public CommandContext(SlashCommandInteractionEvent event, GameServer selectedServer) {
        this.event = event;
        this.guild = event.getGuild();
        this.guildId = guild != null ? guild.getIdLong() : 0;
        
        // Set the game server, either from selection or by looking it up
        if (selectedServer != null) {
            this.gameServer = selectedServer;
        } else {
            // Try to find a default server for this guild
            GameServerRepository serverRepo = new GameServerRepository();
            this.gameServer = serverRepo.findByGuildId(guildId);
        }
        
        // Set the server ID for isolation
        this.serverId = (gameServer != null) ? gameServer.getServerId() : "default";
        
        // Create a filter context for data operations
        if (guildId > 0 && serverId != null && !serverId.isEmpty()) {
            this.filterContext = GuildIsolationManager.getInstance().createFilterContext(guildId, serverId);
            this.validContext = (this.filterContext != null);
        } else {
            this.filterContext = null;
            this.validContext = false;
        }
        
        // Log context creation
        if (validContext) {
            logger.debug("Created valid command context for guild {} and server {}", guildId, serverId);
        } else {
            logger.warn("Created invalid command context for guild {} and server {}", guildId, serverId);
        }
    }
    
    /**
     * Check if this is a valid context for data operations
     * @return True if this context has proper isolation boundaries
     */
    public boolean isValid() {
        return validContext;
    }
    
    /**
     * Get the slash command event
     * @return The original event
     */
    public SlashCommandInteractionEvent getEvent() {
        return event;
    }
    
    /**
     * Get the guild
     * @return The Discord guild
     */
    public Guild getGuild() {
        return guild;
    }
    
    /**
     * Get the guild ID
     * @return The Discord guild ID
     */
    public long getGuildId() {
        return guildId;
    }
    
    /**
     * Get the game server
     * @return The selected game server
     */
    public GameServer getGameServer() {
        return gameServer;
    }
    
    /**
     * Get the server ID
     * @return The game server ID
     */
    public String getServerId() {
        return serverId;
    }
    
    /**
     * Get the filter context for data operations
     * @return The filter context
     */
    public GuildIsolationManager.FilterContext getFilterContext() {
        return filterContext;
    }
    
    /**
     * Ensure all data operations use proper isolation
     * @param entityGuildId The guild ID of the entity being accessed
     * @param entityServerId The server ID of the entity being accessed
     * @return True if the entity belongs to this context
     */
    public boolean verifyBoundaries(long entityGuildId, String entityServerId) {
        boolean valid = (entityGuildId == guildId) && 
                         (entityServerId != null && entityServerId.equals(serverId));
        
        if (!valid) {
            DataBoundary.logBoundaryViolation(
                "verify", "Entity", "unknown", 
                guildId, serverId, entityGuildId, entityServerId
            );
        }
        
        return valid;
    }
}