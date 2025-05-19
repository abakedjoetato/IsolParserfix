package com.deadside.bot.schedulers;

import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.db.repositories.GameServerRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler to update voice channels with player count information
 */
public class PlayerCountVoiceChannelUpdater {
    private static final Logger logger = LoggerFactory.getLogger(PlayerCountVoiceChannelUpdater.class);
    private final JDA jda;
    private final GuildConfigRepository guildConfigRepository;
    private final GameServerRepository gameServerRepository;
    private final ScheduledExecutorService scheduler;
    
    /**
     * Create a new player count voice channel updater
     * 
     * @param jda The JDA instance
     * @param scheduler The scheduler service to use for scheduling updates
     */
    public PlayerCountVoiceChannelUpdater(JDA jda, ScheduledExecutorService scheduler) {
        this.jda = jda;
        this.guildConfigRepository = new GuildConfigRepository();
        this.gameServerRepository = new GameServerRepository();
        this.scheduler = scheduler;
        
        // Schedule the updater to run every 5 minutes
        scheduleUpdates();
    }
    
    /**
     * Schedule the voice channel updates
     */
    private void scheduleUpdates() {
        int updateInterval = 5; // minutes
        
        scheduler.scheduleAtFixedRate(
            this::updateAllVoiceChannels,
            1,
            updateInterval,
            TimeUnit.MINUTES
        );
        
        logger.info("Scheduled player count voice channel updates every {} minutes", updateInterval);
    }
    
    /**
     * Update all voice channels
     */
    private void updateAllVoiceChannels() {
        try {
            logger.debug("Starting scheduled player count voice channel updates");
            
            // Get all guild configurations using isolation-aware approach
            List<GuildConfig> configs = new ArrayList<>();
            
            // Use JDA to get distinct guild IDs for proper isolation
            List<Long> distinctGuildIds = jda.getGuilds().stream()
                .map(guild -> guild.getIdLong())
                .toList();
            logger.debug("Found {} distinct guilds for voice channel updates", distinctGuildIds.size());
            
            // For each guild, get configs with proper isolation
            for (Long guildId : distinctGuildIds) {
                if (guildId > 0) {
                    // Set isolation context for this guild
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                    
                    try {
                        // Get config for this guild using isolation-aware method
                        GuildConfig guildConfig = guildConfigRepository.findByGuildId(guildId);
                        if (guildConfig != null) {
                            configs.add(guildConfig);
                        }
                    } finally {
                        // Always clear context
                        com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                    }
                }
            }
            
            for (GuildConfig config : configs) {
                // Only process guilds with a voice channel configured
                if (config.getPlayerCountVoiceChannelId() <= 0 || config.getPlayerCountServerName() == null) {
                    continue;
                }
                
                // Get the guild
                Guild guild = jda.getGuildById(config.getGuildId());
                if (guild == null) {
                    logger.warn("Guild not found for ID: {}", config.getGuildId());
                    continue;
                }
                
                // Get the voice channel
                VoiceChannel voiceChannel = guild.getVoiceChannelById(config.getPlayerCountVoiceChannelId());
                if (voiceChannel == null) {
                    logger.warn("Voice channel not found for ID: {} in guild: {}", 
                            config.getPlayerCountVoiceChannelId(), guild.getName());
                    continue;
                }
                
                // Find the server by name
                GameServer server = null;
                List<GameServer> servers = gameServerRepository.findAllByGuildId(config.getGuildId());
                for (GameServer s : servers) {
                    if (s.getName().equalsIgnoreCase(config.getPlayerCountServerName())) {
                        server = s;
                        break;
                    }
                }
                
                if (server == null) {
                    voiceChannel.getManager().setName("Players: Server Offline").queue();
                    logger.warn("Server not found: {} for guild: {}", 
                            config.getPlayerCountServerName(), guild.getName());
                    continue;
                }
                
                // Update the voice channel with player count
                String newName = String.format("Players: %d/%d", 
                        server.getPlayerCount(), server.getMaxPlayers());
                
                voiceChannel.getManager().setName(newName).queue(
                    success -> logger.debug("Updated voice channel: {} to {}", voiceChannel.getId(), newName),
                    error -> logger.error("Failed to update voice channel: {}", voiceChannel.getId(), error)
                );
                
                logger.info("Updated player count voice channel for guild {} to {}", 
                        guild.getName(), newName);
            }
            
            logger.debug("Completed scheduled player count voice channel updates");
            
        } catch (Exception e) {
            logger.error("Error updating player count voice channels", e);
        }
    }
}