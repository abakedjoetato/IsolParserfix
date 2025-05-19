package com.deadside.bot.isolation;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command context that enforces data isolation boundaries
 * This ensures that commands respect guild and server isolation
 */
public class IsolationCommandContext {
    private static final Logger logger = LoggerFactory.getLogger(IsolationCommandContext.class);
    
    private final SlashCommandInteractionEvent event;
    private final Guild guild;
    private final long guildId;
    private final GameServer gameServer;
    private final String serverId;
    private final IsolationManager.IsolationContext isolationContext;
    private final boolean isValid;
    
    /**
     * Create a command context from a slash command event
     * @param event The slash command interaction event
     */
    public IsolationCommandContext(SlashCommandInteractionEvent event) {
        this.event = event;
        this.guild = event.getGuild();
        this.guildId = guild != null ? guild.getIdLong() : 0;
        
        // Try to find a default server for this guild
        GameServerRepository serverRepo = new GameServerRepository();
        this.gameServer = serverRepo.findByGuildId(guildId);
        
        // Set the server ID and create isolation context
        this.serverId = (gameServer != null) ? gameServer.getServerId() : "default";
        this.isolationContext = IsolationManager.getInstance().createContext(guildId, serverId);
        this.isValid = (this.isolationContext != null);
        
        // Log context creation
        if (isValid) {
            logger.debug("Created valid isolation command context for guild {} and server {}", 
                guildId, serverId);
        } else {
            logger.warn("Created invalid isolation command context for guild {} and server {}", 
                guildId, serverId);
        }
    }
    
    /**
     * Create a command context from a slash command event and specific server
     * @param event The slash command interaction event
     * @param selectedServer The selected game server
     */
    public IsolationCommandContext(SlashCommandInteractionEvent event, GameServer selectedServer) {
        this.event = event;
        this.guild = event.getGuild();
        this.guildId = guild != null ? guild.getIdLong() : 0;
        this.gameServer = selectedServer;
        
        // Set the server ID and create isolation context
        this.serverId = (gameServer != null) ? gameServer.getServerId() : "default";
        this.isolationContext = IsolationManager.getInstance().createContext(guildId, serverId);
        this.isValid = (this.isolationContext != null);
        
        // Verify that the selected server belongs to this guild for security
        if (gameServer != null && gameServer.getGuildId() != guildId) {
            logger.error("Security violation: Attempted to create context with server from another guild");
            throw new DataIsolationAspect.DataIsolationException(
                "Cannot use a server from another guild");
        }
        
        // Log context creation
        if (isValid) {
            logger.debug("Created valid isolation command context for guild {} and server {}", 
                guildId, serverId);
        } else {
            logger.warn("Created invalid isolation command context for guild {} and server {}", 
                guildId, serverId);
        }
    }
    
    /**
     * Verify that an object belongs to the correct isolation boundary
     * @param object The object to check
     * @return True if the object belongs to this context's isolation boundary
     */
    public boolean verifyBoundary(Object object) {
        return IsolationManager.getInstance().verifyDataBoundary(object, guildId, serverId);
    }
    
    /**
     * Check if this is a valid context with proper isolation boundaries
     * @return True if this context has valid isolation boundaries
     */
    public boolean isValid() {
        return isValid;
    }
    
    /**
     * Get the slash command event
     * @return The original slash command event
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
     * @return The game server for this context
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
     * Get the isolation context
     * @return The isolation context for database operations
     */
    public IsolationManager.IsolationContext getIsolationContext() {
        return isolationContext;
    }
}