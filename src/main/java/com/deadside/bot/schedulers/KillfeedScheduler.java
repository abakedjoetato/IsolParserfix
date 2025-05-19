package com.deadside.bot.schedulers;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.parsers.KillfeedParser;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Scheduler for processing killfeed data
 */
public class KillfeedScheduler {
    private static final Logger logger = LoggerFactory.getLogger(KillfeedScheduler.class);
    private final GameServerRepository serverRepository;
    private final GuildConfigRepository guildConfigRepository;
    private KillfeedParser killfeedParser;
    
    public KillfeedScheduler() {
        this.serverRepository = new GameServerRepository();
        this.guildConfigRepository = new GuildConfigRepository();
    }
    
    /**
     * Initialize the scheduler with JDA instance
     * @param jda The JDA instance
     */
    public void initialize(JDA jda) {
        this.killfeedParser = new KillfeedParser(jda);
    }
    
    /**
     * Process killfeed data for all servers
     * @param processHistorical If true, will process all historical files; otherwise just new entries
     */
    public void processAllServers(boolean processHistorical) {
        if (killfeedParser == null) {
            logger.error("KillfeedScheduler not initialized with JDA instance");
            return;
        }
        
        try {
            logger.info("Starting scheduled killfeed processing" + (processHistorical ? " (including historical data)" : ""));
            
            // Use isolation-aware method to get servers by guild
            List<GameServer> servers = getServersWithProperIsolation();
            int totalProcessed = 0;
            
            for (GameServer server : servers) {
                // Check if this guild has premium (if not, killfeed is still allowed)
                GuildConfig guildConfig = guildConfigRepository.findByGuildId(server.getGuildId());
                
                // Process killfeed for this server
                int processed = killfeedParser.processServer(server);
                totalProcessed += processed;
                
                // Save server state with updated progress
                if (processed > 0) {
                    serverRepository.save(server);
                }
            }
            
            logger.info("Completed scheduled killfeed processing, total kills processed: {}", totalProcessed);
        } catch (Exception e) {
            logger.error("Error in scheduled killfeed processing", e);
        }
    }
    
    /**
     * Process only new killfeed data (default behavior for scheduled runs)
     */
    public void processAllServers() {
        processAllServers(false);
    }
    
    /**
     * Get all servers with proper isolation by fetching per guild
     * @return List of servers with proper isolation context
     */
    private List<GameServer> getServersWithProperIsolation() {
        try {
            // Use GameServerRepository to get distinct guild IDs with isolation-aware methods
            List<Long> distinctGuildIds = serverRepository.getDistinctGuildIds();
            List<GameServer> allServers = new java.util.ArrayList<>();
            
            // For each guild, get its servers with proper isolation
            for (Long guildId : distinctGuildIds) {
                if (guildId != null && guildId > 0) {
                    // Set isolation context for this guild
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                    
                    // Get servers for this guild
                    List<GameServer> guildServers = serverRepository.findAllByGuildId(guildId);
                    allServers.addAll(guildServers);
                    
                    // Clear context after use
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // If no servers found through guild configs, try an isolation-aware approach as fallback
            if (allServers.isEmpty()) {
                logger.warn("No servers found through guild configs, using isolation-aware guild iteration as fallback");
                List<Long> dbGuildIds = serverRepository.getDistinctGuildIds();
                
                for (Long guildId : dbGuildIds) {
                    if (guildId > 0) {
                        // Set isolation context for this guild
                        com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                        
                        try {
                            // Get servers for this guild with proper isolation
                            List<GameServer> guildServers = serverRepository.findAllByGuildId(guildId);
                            allServers.addAll(guildServers);
                        } finally {
                            // Always clear context
                            com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                        }
                    }
                }
            }
            
            return allServers;
        } catch (Exception e) {
            logger.error("Error getting servers with proper isolation: {}", e.getMessage(), e);
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * Process all historical killfeed data
     * Should only be called when a new server is added or via admin command
     */
    public void processAllHistoricalData() {
        processAllServers(true);
    }
}
