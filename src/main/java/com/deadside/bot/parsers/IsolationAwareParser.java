package com.deadside.bot.parsers;

import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.utils.GuildIsolationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class to make parsers isolation-aware
 * This ensures that parsers only operate on servers within their isolation context
 */
public class IsolationAwareParser {
    private static final Logger logger = LoggerFactory.getLogger(IsolationAwareParser.class);

    /**
     * Get all servers for the current isolation context
     * @param repository The game server repository
     * @return List of servers within the current isolation context
     */
    public static List<GameServer> getContextualServers(GameServerRepository repository) {
        // Get current isolation context
        GuildIsolationManager isolationManager = GuildIsolationManager.getInstance();
        long currentGuildId = isolationManager.getCurrentGuildId();
        
        if (currentGuildId <= 0) {
            logger.warn("Attempted to get contextual servers without valid guild ID");
            return List.of(); // Empty list - no valid context
        }
        
        // Find all servers for this guild
        return repository.findAllByGuildId(currentGuildId);
    }
    
    /**
     * Filter a list of servers to only include those within the current context
     * @param servers List of servers to filter
     * @return Filtered list of servers
     */
    public static List<GameServer> filterServersByContext(List<GameServer> servers) {
        if (servers == null || servers.isEmpty()) {
            return List.of();
        }
        
        // Get current isolation context
        GuildIsolationManager isolationManager = GuildIsolationManager.getInstance();
        long currentGuildId = isolationManager.getCurrentGuildId();
        
        if (currentGuildId <= 0) {
            logger.warn("Attempted to filter servers without valid guild ID");
            return List.of();
        }
        
        // Filter servers by guild ID
        return servers.stream()
            .filter(server -> server.getGuildId() == currentGuildId)
            .collect(Collectors.toList());
    }
    
    /**
     * Set the isolation context for a parser operation
     * @param guildId The Discord guild ID
     * @param serverId The game server ID
     * @return True if context was set successfully
     */
    public static boolean setParserContext(long guildId, String serverId) {
        if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
            logger.warn("Attempted to set parser context with invalid parameters: Guild={}, Server={}", 
                guildId, serverId);
            return false;
        }
        
        try {
            GuildIsolationManager.getInstance().setContext(guildId, serverId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to set parser context", e);
            return false;
        }
    }
    
    /**
     * Clear the isolation context after a parser operation
     */
    public static void clearParserContext() {
        try {
            GuildIsolationManager.getInstance().clearContext();
        } catch (Exception e) {
            logger.error("Failed to clear parser context", e);
        }
    }
    
    /**
     * Verify that a server belongs to the given guild
     * @param server The server to verify
     * @param guildId The Discord guild ID
     * @return True if the server belongs to the guild
     */
    public static boolean verifyServerIsolation(GameServer server, long guildId) {
        if (server == null || guildId <= 0) {
            return false;
        }
        
        return server.getGuildId() == guildId;
    }
}