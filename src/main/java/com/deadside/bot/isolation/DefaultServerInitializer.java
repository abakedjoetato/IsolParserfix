package com.deadside.bot.isolation;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializes default server for guilds that don't have any servers configured
 * This ensures all repositories have valid isolation configuration
 */
public class DefaultServerInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DefaultServerInitializer.class);
    
    // Default guild ID and server ID for bootstrapping
    // This will be used for any operations that don't have context
    public static final long DEFAULT_GUILD_ID = 1L;
    public static final String DEFAULT_SERVER_NAME = "Default Server";
    public static final String DEFAULT_SERVER_ID = "default";
    
    /**
     * Initialize the default server if it doesn't exist
     * This ensures all repositories have a valid default guild and server
     */
    public static void initializeDefaultServer() {
        GameServerRepository gameServerRepository = new GameServerRepository();
        
        // Try to find any server first
        if (hasAnyServer(gameServerRepository)) {
            logger.info("Found existing servers, skipping default server initialization");
            return;
        }
        
        // Create and save the default server
        try {
            logger.info("Initializing default server for guild ID: {}", DEFAULT_GUILD_ID);
            
            GameServer defaultServer = new GameServer();
            defaultServer.setServerId(DEFAULT_SERVER_ID);
            defaultServer.setName(DEFAULT_SERVER_NAME);
            defaultServer.setGuildId(DEFAULT_GUILD_ID);
            defaultServer.setHost("localhost");
            defaultServer.setPort(28082);
            defaultServer.setSftpPort(22);
            defaultServer.setSftpUsername("default");
            defaultServer.setSftpPassword("default");
            defaultServer.setLogPath("/Deadside/Saved/Logs/Deadside.log");
            defaultServer.setLogFormat("csv");
            defaultServer.setLastLogRotation(System.currentTimeMillis());
            defaultServer.setIpAddress("127.0.0.1");
            defaultServer.setKillfeedChannelId(0);
            defaultServer.setLogChannelId(0);
            defaultServer.setStatus("Default");
            
            // Set isolation mode to read-only for Default Server
            defaultServer.setReadOnly(true);
            defaultServer.setIsolationMode("read-only");
            
            // Set isolation context before saving
            com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(DEFAULT_GUILD_ID, DEFAULT_SERVER_ID);
            
            // Save with proper isolation context
            gameServerRepository.save(defaultServer);
            
            // Clear context after saving
            com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
            
            logger.info("Default server initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize default server", e);
        }
    }
    
    /**
     * Check if there are any servers in the database
     * Uses isolation-aware methods to check for servers
     */
    private static boolean hasAnyServer(GameServerRepository repository) {
        try {
            // Instead of using getAllServers() which doesn't respect isolation,
            // we'll get a list of distinct guild IDs, then check each guild
            
            List<Long> distinctGuildIds = repository.getDistinctGuildIds();
            
            // If we have any guild IDs, check if any guild has servers
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                
                // Check if this guild has any servers (using isolation-aware method)
                List<GameServer> guildServers = repository.findAllByGuildId(guildId);
                
                // Clear context after checking
                com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                
                if (!guildServers.isEmpty()) {
                    return true;
                }
            }
            
            // If we get here, no guild has any servers
            return false;
        } catch (Exception e) {
            logger.error("Error checking for existing servers using isolation-aware methods", e);
            return false;
        }
    }
    
    /**
     * Add default server to a guild if it doesn't exist yet
     * This ensures every guild has at least one server configuration
     * @param guildId The ID of the guild to add the default server to
     */
    public static void addDefaultServerToGuild(Long guildId) {
        if (guildId == null || guildId <= 0) {
            logger.warn("Invalid guild ID for default server: {}", guildId);
            return;
        }
        
        logger.info("Adding default server to guild: {}", guildId);
        GameServerRepository repository = new GameServerRepository();
        
        try {
            // Set context for this guild
            com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
            
            // Check if this guild already has a default server
            List<GameServer> guildServers = repository.findAllByGuildId(guildId);
            boolean hasDefaultServer = false;
            
            // Check if this guild already has a default server
            for (GameServer server : guildServers) {
                if (DEFAULT_SERVER_ID.equals(server.getServerId())) {
                    hasDefaultServer = true;
                    logger.info("Default server already exists for guild: {}", guildId);
                    break;
                }
            }
            
            if (!hasDefaultServer) {
                logger.info("Creating default server for guild: {}", guildId);
                GameServer guildDefaultServer = new GameServer();
                guildDefaultServer.setServerId(DEFAULT_SERVER_ID);
                guildDefaultServer.setName(DEFAULT_SERVER_NAME);
                guildDefaultServer.setHost("localhost");
                guildDefaultServer.setPort(28082);
                guildDefaultServer.setSftpPort(22);
                guildDefaultServer.setSftpUsername("default");
                guildDefaultServer.setSftpPassword("default");
                guildDefaultServer.setLogPath("/Deadside/Saved/Logs/Deadside.log");
                guildDefaultServer.setLogFormat("csv");
                guildDefaultServer.setLastLogRotation(System.currentTimeMillis());
                guildDefaultServer.setIpAddress("127.0.0.1");
                guildDefaultServer.setKillfeedChannelId(0);
                guildDefaultServer.setLogChannelId(0);
                guildDefaultServer.setStatus("Default");
                guildDefaultServer.setGuildId(guildId);
                
                // Set isolation mode to read-only for Default Server
                guildDefaultServer.setReadOnly(true);
                guildDefaultServer.setIsolationMode("read-only");
                
                // Save the guild's default server
                repository.save(guildDefaultServer);
                logger.info("Successfully created default server for guild: {}", guildId);
            }
            
            // Clear context
            com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
        } catch (Exception e) {
            logger.error("Error adding default server to guild: {}", guildId, e);
            // Clear context even in case of error
            com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
        }
    }
}