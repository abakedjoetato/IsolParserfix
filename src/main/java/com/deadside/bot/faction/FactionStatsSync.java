package com.deadside.bot.faction;

import com.deadside.bot.db.models.Faction;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.FactionRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service to synchronize faction statistics based on player data
 */
public class FactionStatsSync {
    private static final Logger logger = LoggerFactory.getLogger(FactionStatsSync.class);
    
    private final FactionRepository factionRepository;
    private final PlayerRepository playerRepository;
    private final ExecutorService executor;
    
    // XP rewards for various actions
    private static final int XP_PER_KILL = 10;
    private static final int XP_BONUS_LONG_DISTANCE = 5; // Bonus for kills over 100m
    private static final int XP_PENALTY_DEATH = -5;
    private static final int XP_PENALTY_SUICIDE = -10;
    
    /**
     * Constructor initializes repositories and thread pool
     */
    public FactionStatsSync() {
        this.factionRepository = new FactionRepository();
        this.playerRepository = new PlayerRepository();
        this.executor = Executors.newSingleThreadExecutor();
        
        logger.debug("FactionStatsSync service initialized");
    }
    
    /**
     * Update statistics for all factions
     */
    public void updateAllFactions() {
        try {
            logger.info("Starting faction statistics update for all factions");
            
            // Get all factions using isolation-aware approach
            List<Long> distinctGuildIds = factionRepository.getDistinctGuildIds();
            
            if (distinctGuildIds.isEmpty()) {
                logger.info("No guild IDs found to update factions for");
                return;
            }
            
            int totalFactions = 0;
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                try {
                    List<Faction> guildFactions = factionRepository.findAllByGuildId(guildId);
                    totalFactions += guildFactions.size();
                    
                    // Update each faction in this guild context
                    for (Faction faction : guildFactions) {
                        updateFaction(faction.getId());
                    }
                } finally {
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            if (totalFactions == 0) {
                logger.info("No factions found to update across all guilds");
                return;
            }
            
            logger.info("Completed faction statistics update for {} factions", totalFactions);
        } catch (Exception e) {
            logger.error("Error updating all faction statistics: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update statistics for a specific faction
     */
    public void updateFaction(ObjectId factionId) {
        if (factionId == null) {
            logger.warn("Cannot update faction with null ID");
            return;
        }
        
        try {
            Faction faction = factionRepository.findById(factionId);
            if (faction == null) {
                logger.warn("Faction not found with ID: {}", factionId);
                return;
            }
            
            // Get all members of the faction
            List<Player> members = playerRepository.findByFactionId(factionId);
            int memberCount = members.size();
            
            // Count totals from all members
            int totalKills = 0;
            int totalDeaths = 0;
            
            for (Player member : members) {
                totalKills += member.getKills();
                totalDeaths += member.getDeaths();
            }
            
            // Update faction stats
            faction.setMemberCount(memberCount);
            faction.setTotalKills(totalKills);
            faction.setTotalDeaths(totalDeaths);
            
            // Save updated faction
            factionRepository.save(faction);
            
            logger.debug("Updated faction {} stats: {} members, {} kills, {} deaths", 
                    faction.getName(), memberCount, totalKills, totalDeaths);
        } catch (Exception e) {
            logger.error("Error updating faction {}: {}", factionId, e.getMessage(), e);
        }
    }
    
    /**
     * Process faction experience when a member gets a kill
     */
    public void processMemberKill(String playerId, int killDistance) {
        executor.submit(() -> {
            try {
                Player player = playerRepository.findByDeadsideId(playerId);
                if (player == null || player.getFactionId() == null) {
                    return;
                }
                
                // Base XP for kill
                int xpToAdd = XP_PER_KILL;
                
                // Bonus XP for long-distance kills
                if (killDistance > 100) {
                    xpToAdd += XP_BONUS_LONG_DISTANCE;
                }
                
                // Add XP to faction
                boolean leveledUp = factionRepository.addExperience(player.getFactionId(), xpToAdd);
                
                if (leveledUp) {
                    logger.info("Faction {} leveled up due to kill by player {}", 
                            player.getFactionId(), player.getName());
                }
                
                logger.debug("Added {} XP to faction {} for kill by player {}", 
                        xpToAdd, player.getFactionId(), player.getName());
            } catch (Exception e) {
                logger.error("Error processing faction kill XP for player {}: {}", 
                        playerId, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Process faction experience when a member dies
     */
    public void processMemberDeath(String playerId) {
        executor.submit(() -> {
            try {
                Player player = playerRepository.findByDeadsideId(playerId);
                if (player == null || player.getFactionId() == null) {
                    return;
                }
                
                // Add XP penalty to faction (might be negative)
                factionRepository.addExperience(player.getFactionId(), XP_PENALTY_DEATH);
                
                logger.debug("Added {} XP to faction {} for death of player {}", 
                        XP_PENALTY_DEATH, player.getFactionId(), player.getName());
            } catch (Exception e) {
                logger.error("Error processing faction death XP for player {}: {}", 
                        playerId, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Process faction experience when a member commits suicide
     */
    public void processMemberSuicide(String playerId) {
        executor.submit(() -> {
            try {
                Player player = playerRepository.findByDeadsideId(playerId);
                if (player == null || player.getFactionId() == null) {
                    return;
                }
                
                // Add XP penalty to faction for suicide (more severe than regular death)
                factionRepository.addExperience(player.getFactionId(), XP_PENALTY_SUICIDE);
                
                logger.debug("Added {} XP to faction {} for suicide of player {}", 
                        XP_PENALTY_SUICIDE, player.getFactionId(), player.getName());
            } catch (Exception e) {
                logger.error("Error processing faction suicide XP for player {}: {}", 
                        playerId, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Shutdown the faction stats service cleanly
     */
    public void shutdown() {
        logger.info("Shutting down FactionStatsSync service");
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}