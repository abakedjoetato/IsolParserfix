package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.utils.GuildIsolationManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Repository for Player collection with comprehensive isolation between guilds and servers
 */
public class PlayerRepository {
    private static final Logger logger = LoggerFactory.getLogger(PlayerRepository.class);
    private static final String COLLECTION_NAME = "players";
    
    private MongoCollection<Player> collection;
    
    public PlayerRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, Player.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<Player> getCollection() {
        if (collection == null) {
            try {
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, Player.class);
            } catch (Exception e) {
                logger.error("Failed to initialize player collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save a player to the database with proper isolation checks
     * @param player The player to save
     */
    public void save(Player player) {
        try {
            // Ensure player has valid isolation fields
            if (player.getGuildId() <= 0 || player.getServerId() == null || player.getServerId().isEmpty()) {
                logger.error("Attempted to save player without proper isolation fields: {}", player.getName());
                return;
            }
            
            if (player.getId() == null) {
                getCollection().insertOne(player);
                logger.debug("Inserted new player: {} with proper isolation (Guild={}, Server={})",
                    player.getName(), player.getGuildId(), player.getServerId());
            } else {
                getCollection().replaceOne(
                    Filters.eq("_id", player.getId()),
                    player
                );
                logger.debug("Updated player: {} with isolation (Guild={}, Server={})",
                    player.getName(), player.getGuildId(), player.getServerId());
            }
        } catch (Exception e) {
            logger.error("Error saving player: {}", player.getName(), e);
        }
    }
    
    /**
     * Find a player by player ID with guild and server isolation
     */
    public Player findByPlayerIdAndGuildIdAndServerId(String playerId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("playerId", playerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by ID with isolation (ID={}, Guild={}, Server={})",
                playerId, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find a player by player ID using isolation-aware approach
     * This method properly respects isolation boundaries
     * @param playerId The player ID to find
     * @return The player with proper isolation boundaries respected
     */
    public Player findByPlayerId(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            logger.error("Cannot find player with null or empty player ID");
            return null;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find the player with proper isolation
                            Player player = findByPlayerIdAndGuildIdAndServerId(playerId, guildId, server.getServerId());
                            if (player != null) {
                                logger.debug("Found player with ID '{}' in guild {} and server {} using isolation-aware approach", 
                                    playerId, guildId, server.getServerId());
                                return player;
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No player found with ID '{}' in any guild using isolation-aware approach", playerId);
            return null;
        } catch (Exception e) {
            logger.error("Error finding player by ID: '{}' using isolation-aware approach", playerId, e);
            return null;
        }
    }
    
    /**
     * Find a player by Discord ID with guild and server isolation
     */
    public Player findByDiscordIdAndGuildIdAndServerId(String discordId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("discordId", discordId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by Discord ID with isolation", e);
            return null;
        }
    }
    
    /**
     * Find players by guild and server IDs with proper isolation
     */
    public List<Player> findByGuildIdAndServerId(long guildId, String serverId) {
        try {
            return getCollection()
                .find(Filters.and(
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ))
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by guild and server", e);
            return new ArrayList<>();
        }
    }

    /**
     * Count players by guild and server IDs with proper isolation
     */
    public long countPlayersByGuildIdAndServerId(long guildId, String serverId) {
        try {
            return getCollection()
                .countDocuments(Filters.and(
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ));
        } catch (Exception e) {
            logger.error("Error counting players by guild and server", e);
            return 0;
        }
    }
    
    /**
     * Count all players across all guilds and servers using isolation-aware approach
     * This method properly respects isolation boundaries by counting players per guild/server
     * @return Total number of player records
     */
    public long countAll() {
        try {
            long totalCount = 0;
            
            // Get all distinct guild IDs from the player collection
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // For each guild, count players with proper isolation
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Count players for this guild and server with proper isolation
                        long serverCount = countPlayersByGuildIdAndServerId(guildId, server.getServerId());
                        totalCount += serverCount;
                        
                        logger.debug("Counted {} players in guild {} and server {} using isolation-aware approach", 
                            serverCount, guildId, server.getServerId());
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Total count across all guilds and servers using isolation-aware approach: {}", totalCount);
            return totalCount;
        } catch (Exception e) {
            logger.error("Error counting all players using isolation-aware approach", e);
            return 0;
        }
    }
    
    /**
     * Find a player by Deadside ID (deadsideId) across all guilds and servers
     * This method properly respects isolation boundaries by searching with proper context
     * @param deadsideId The Deadside player ID to search for
     * @return The first player found with this ID or null if not found
     */
    public Player findByDeadsideId(String deadsideId) {
        try {
            if (deadsideId == null || deadsideId.isEmpty()) {
                logger.warn("Attempted to find player with null or empty Deadside ID");
                return null;
            }
            
            // Get all distinct guild IDs
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find player with proper isolation
                            Bson filter = Filters.and(
                                Filters.eq("deadsideId", deadsideId),
                                Filters.eq("guildId", guildId),
                                Filters.eq("serverId", server.getServerId())
                            );
                            
                            Player player = getCollection().find(filter).first();
                            if (player != null) {
                                logger.debug("Found player with Deadside ID '{}' in guild {} and server {} using isolation-aware approach", 
                                    deadsideId, guildId, server.getServerId());
                                return player;
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No player found with Deadside ID '{}' in any guild using isolation-aware approach", deadsideId);
            return null;
        } catch (Exception e) {
            logger.error("Error finding player by Deadside ID: '{}' using isolation-aware approach", deadsideId, e);
            return null;
        }
    }
    
    /**
     * Find a player by Deadside ID with proper guild and server isolation
     * @param deadsideId The Deadside game ID of the player
     * @param guildId The Discord guild ID for isolation
     * @param serverId The game server ID for isolation
     * @return The found player or null if not found
     */
    public Player findByDeadsideIdAndGuildIdAndServerId(String deadsideId, long guildId, String serverId) {
        try {
            if (deadsideId == null || deadsideId.isEmpty() || guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to find player by Deadside ID without proper isolation parameters. Deadside ID: {}, Guild ID: {}, Server ID: {}", 
                    deadsideId, guildId, serverId);
                return null;
            }
            
            return getCollection().find(Filters.and(
                Filters.eq("deadsideId", deadsideId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by Deadside ID with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by name with guild and server isolation
     */
    public Player findByNameAndGuildIdAndServerId(String name, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("name", name),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by name with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by name without isolation
     * WARNING: This method does not enforce guild isolation
     * It's used primarily by the KillfeedParser for initial player lookups
     * You should use findByNameAndGuildIdAndServerId instead whenever possible
     * @param name The player name to search for
     * @return The first player found with this name, or null if none
     */
    public Player findByName(String name) {
        try {
            if (name == null || name.isEmpty()) {
                logger.warn("Attempted to find player with null or empty name");
                return null;
            }
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find the player with proper isolation
                            Player player = findByNameAndGuildIdAndServerId(name, guildId, server.getServerId());
                            if (player != null) {
                                logger.debug("Found player with name '{}' in guild {} and server {} using isolation-aware approach", 
                                    name, guildId, server.getServerId());
                                return player;
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No player found with name '{}' in any guild using isolation-aware approach", name);
            return null;
        } catch (Exception e) {
            logger.error("Error finding player by name: '{}' using isolation-aware approach", name, e);
            return null;
        }
    }
    
    /**
     * Get the top players by KD ratio with proper isolation by guild and server
     * @param guildId The Discord guild ID
     * @param serverId The game server ID
     * @param limit Maximum number of players to return
     * @return List of top players with highest KD ratio
     */
    /**
     * Get all players using isolation-aware approach
     * This method properly respects isolation boundaries when retrieving all players
     * @return List of all players with proper isolation boundaries respected
     */
    public List<Player> getAllPlayers() {
        List<Player> allPlayers = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find all players for this guild and server
                            List<Player> serverPlayers = findAllByGuildIdAndServerId(guildId, server.getServerId());
                            allPlayers.addAll(serverPlayers);
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Retrieved all players using isolation-aware approach: {} total records", allPlayers.size());
        } catch (Exception e) {
            logger.error("Error getting all players using isolation-aware approach", e);
        }
        
        return allPlayers;
    }
    
    /**
     * Get all distinct guild IDs from players collection
     * This is useful for isolation-aware operations across multiple guilds
     * @return List of distinct guild IDs
     */
    public List<Long> getDistinctGuildIds() {
        try {
            List<Long> guildIds = new ArrayList<>();
            getCollection().distinct("guildId", Long.class).into(guildIds);
            return guildIds;
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs from players collection", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all players by guild ID and server ID with proper isolation
     * @param guildId The Discord guild ID for isolation
     * @param serverId The game server ID for isolation  
     * @return List of players for the specified guild and server
     */
    public List<Player> findAllByGuildIdAndServerId(Long guildId, String serverId) {
        try {
            if (guildId == null || guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to find players without proper isolation parameters. Guild ID: {}, Server ID: {}", 
                    guildId, serverId);
                GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
                if (defaultContext != null) {
                    guildId = defaultContext.getGuildId();
                    serverId = defaultContext.getServerId();
                    logger.info("Using default filter context for player lookup: Guild={}, Server={}", guildId, serverId);
                } else {
                    return new ArrayList<>();
                }
            }
            
            List<Player> players = new ArrayList<>();
            getCollection().find(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).into(players);
            
            return players;
        } catch (Exception e) {
            logger.error("Error finding players by guild and server IDs", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get all players for a specific guild and server with proper isolation
     * @param guildId The Discord guild ID for isolation
     * @param serverId The game server ID for isolation
     * @return List of all players for the given guild and server
     */
    public List<Player> getAllPlayersWithIsolation(long guildId, String serverId) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to get all players without proper isolation. Guild ID: {}, Server ID: {}", 
                    guildId, serverId);
                return new ArrayList<>();
            }
            
            List<Player> players = new ArrayList<>();
            getCollection().find(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).into(players);
            
            return players;
        } catch (Exception e) {
            logger.error("Error getting all players with isolation", e);
            return new ArrayList<>();
        }
    }
    
    public List<Player> getTopPlayersByKDRatio(long guildId, String serverId, int limit) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                // Change from warning to informational log if this is intentional
                if (guildId <= 0 && serverId == null) {
                    logger.info("Leaderboard query executed with intentionally empty isolation context");
                } else {
                    logger.info("Leaderboard query for top K/D with incomplete isolation context. Guild ID: {}, Server ID: {}", 
                        guildId, serverId);
                }
                return new ArrayList<>();
            }
            
            // Get GameServer to check if it's in read-only or disabled mode
            GameServerRepository serverRepo = new GameServerRepository();
            GameServer server = serverRepo.findByGuildIdAndServerId(guildId, serverId);
            
            if (server != null && (server.isReadOnly() || "disabled".equalsIgnoreCase(server.getIsolationMode()))) {
                logger.info("Server {} is in {} mode - returning empty leaderboard as expected",
                    server.getName(), server.isReadOnly() ? "read-only" : "disabled isolation");
                return new ArrayList<>();
            }
            
            // Create a pipeline to calculate KD ratio and sort by it
            List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId),
                    Filters.gt("kills", 0)
                )),
                Aggregates.project(Projections.fields(
                    Projections.include("_id", "name", "kills", "deaths", "deadsideId", "lastSeen", "guildId", "serverId"),
                    Projections.computed("kdRatio", new Document("$cond", 
                        new Document("if", new Document("$eq", Arrays.asList("$deaths", 0)))
                            .append("then", "$kills")
                            .append("else", new Document("$divide", Arrays.asList("$kills", "$deaths")))
                    ))
                )),
                Aggregates.sort(Sorts.descending("kdRatio")),
                Aggregates.limit(limit)
            );
            
            List<Player> topPlayers = new ArrayList<>();
            getCollection().aggregate(pipeline, Player.class).into(topPlayers);
            
            return topPlayers;
        } catch (Exception e) {
            logger.error("Error getting top players by KD ratio with isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players by name using case-insensitive partial matching with proper guild and server isolation
     * @param name The partial name to search for
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return List of players matching the criteria within isolation boundary
     */
    public List<Player> findByNameLikeAndGuildIdAndServerId(String name, long guildId, String serverId) {
        try {
            // Create a case-insensitive regex pattern for partial name matching
            Bson filter = Filters.and(
                Filters.regex("name", name, "i"),  // "i" for case-insensitive
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by partial name with isolation: {} (Guild={}, Server={})",
                name, guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete a player by ID with isolation check to prevent accidental cross-boundary deletions
     */
    public void deleteById(ObjectId id, long guildId, String serverId) {
        try {
            // First check if the player belongs to the specified guild/server
            Player player = getCollection().find(Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
            
            if (player != null) {
                getCollection().deleteOne(Filters.eq("_id", id));
                logger.debug("Deleted player with ID: {} from Guild={}, Server={}", id, guildId, serverId);
            } else {
                logger.warn("Prevented deletion of player with ID: {} due to isolation boundary mismatch", id);
            }
        } catch (Exception e) {
            logger.error("Error deleting player by ID with isolation check", e);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void deleteById(ObjectId id) {
        if (id == null) {
            logger.error("Cannot delete player with null ID");
            return;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            boolean playerDeleted = false;
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find and delete the player with proper isolation
                            Bson filter = Filters.and(
                                Filters.eq("_id", id),
                                Filters.eq("guildId", guildId),
                                Filters.eq("serverId", server.getServerId())
                            );
                            
                            DeleteResult result = getCollection().deleteOne(filter);
                            if (result.getDeletedCount() > 0) {
                                logger.debug("Deleted player with ID {} in guild {} and server {} using isolation-aware approach", 
                                    id, guildId, server.getServerId());
                                playerDeleted = true;
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            if (!playerDeleted) {
                logger.debug("No player found with ID {} in any guild to delete using isolation-aware approach", id);
            }
        } catch (Exception e) {
            logger.error("Error deleting player by ID: {} using isolation-aware approach", id, e);
        }
    }
    
    /**
     * Delete all players by guildId and serverId - used for data cleanup
     */
    public long deleteAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            DeleteResult result = getCollection().deleteMany(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            ));
            logger.info("Deleted {} players from Guild={}, Server={}", result.getDeletedCount(), guildId, serverId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting players by guild and server", e);
            return 0;
        }
    }
    
    /**
     * Get all players using isolation-aware approach
     * This method properly respects isolation boundaries when retrieving all players
     * @return List of all players with proper isolation boundaries respected
     */
    public List<Player> findAll() {
        List<Player> allPlayers = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find all players for this guild and server
                            List<Player> serverPlayers = findAllByGuildIdAndServerId(guildId, server.getServerId());
                            allPlayers.addAll(serverPlayers);
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Retrieved all players using isolation-aware approach: {} total records", allPlayers.size());
        } catch (Exception e) {
            logger.error("Error getting all players using isolation-aware approach", e);
        }
        
        return allPlayers;
    }
    
    /**
     * Find all players with a specific faction ID with proper isolation
     */
    public List<Player> findByFactionIdAndGuildIdAndServerId(ObjectId factionId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("factionId", factionId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by faction ID with isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Update a player's kills with isolation
     */
    public void incrementKills(String playerId, long guildId, String serverId) {
        try {
            getCollection().updateOne(
                Filters.and(
                    Filters.eq("playerId", playerId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ),
                Updates.inc("kills", 1)
            );
            logger.debug("Incremented kills for player ID: {} with isolation (Guild={}, Server={})",
                playerId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error incrementing kills for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void incrementKills(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            logger.error("Cannot increment kills for player with null or empty ID");
            return;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            boolean playerUpdated = false;
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Update the player kills with proper isolation
                            Bson filter = Filters.and(
                                Filters.eq("playerId", playerId),
                                Filters.eq("guildId", guildId),
                                Filters.eq("serverId", server.getServerId())
                            );
                            
                            // Use the isolated version to increment kills
                            incrementKills(playerId, guildId, server.getServerId());
                            playerUpdated = true;
                            logger.debug("Incremented kills for player ID {} in guild {} and server {} using isolation-aware approach", 
                                playerId, guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            if (!playerUpdated) {
                logger.debug("No player found with ID {} in any guild to increment kills using isolation-aware approach", playerId);
            }
        } catch (Exception e) {
            logger.error("Error incrementing kills for player ID: {} using isolation-aware approach", playerId, e);
        }
    }
    
    /**
     * Update a player's deaths with isolation
     */
    public void incrementDeaths(String playerId, long guildId, String serverId) {
        try {
            getCollection().updateOne(
                Filters.and(
                    Filters.eq("playerId", playerId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ),
                Updates.inc("deaths", 1)
            );
            logger.debug("Incremented deaths for player ID: {} with isolation (Guild={}, Server={})",
                playerId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error incrementing deaths for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Increment deaths for a player using isolation-aware approach
     * This method properly respects isolation boundaries
     * @param playerId The player ID to increment deaths for
     */
    public void incrementDeaths(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            logger.error("Cannot increment deaths for player with null or empty ID");
            return;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            boolean playerUpdated = false;
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Use the isolated version to increment deaths
                            incrementDeaths(playerId, guildId, server.getServerId());
                            playerUpdated = true;
                            logger.debug("Incremented deaths for player ID {} in guild {} and server {} using isolation-aware approach", 
                                playerId, guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            if (!playerUpdated) {
                logger.debug("No player found with ID {} in any guild to increment deaths using isolation-aware approach", playerId);
            }
        } catch (Exception e) {
            logger.error("Error incrementing deaths for player ID: {} using isolation-aware approach", playerId, e);
        }
    }
    
    /**
     * Get top players by kills for a specific guild and server
     */
    public List<Player> getTopPlayersByKills(long guildId, String serverId, int limit) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                // Change from warning to informational log if this is intentional
                if (guildId <= 0 && serverId == null) {
                    logger.info("Leaderboard query executed with intentionally empty isolation context");
                } else {
                    logger.info("Leaderboard query for top kills with incomplete isolation context. Guild ID: {}, Server ID: {}", 
                        guildId, serverId);
                }
                return new ArrayList<>();
            }
            
            // Get GameServer to check if it has restricted isolation
            GameServerRepository serverRepo = new GameServerRepository();
            GameServer server = serverRepo.findByGuildIdAndServerId(guildId, serverId);
            
            if (server != null && server.hasRestrictedIsolation()) {
                // Use consistent isolation mode naming
                String isolationMode = server.isDefaultServer() ? "Default Server" :
                                     server.isReadOnly() ? "read-only" :
                                     server.isIsolationDisabled() ? "disabled isolation" :
                                     server.getIsolationMode();
                
                logger.info("Server {} is in {} mode - returning empty kills leaderboard as expected",
                    server.getName(), isolationMode);
                return new ArrayList<>();
            }
            
            // Filter out players with no names or placeholder names like "**"
            // and include only players from specified guild and server
            Bson validPlayerFilter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.gte("kills", 1),               // Must have at least 1 kill to be on leaderboard
                Filters.eq("guildId", guildId),        // Must be from specified guild
                Filters.eq("serverId", serverId)       // Must be from specified server
            );
            
            return getCollection().find(validPlayerFilter)
                .sort(Sorts.descending("kills"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error getting top players by kills for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by kills
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    /**
     * Get top players by kills using isolation-aware approach
     * This method properly respects isolation boundaries across all guilds and servers
     * @param limit Maximum number of players to return
     * @return List of players with highest kill counts with proper isolation boundaries respected
     */
    public List<Player> getTopPlayersByKills(int limit) {
        try {
            // Use current isolation context if available
            GuildIsolationManager.FilterContext currentContext = GuildIsolationManager.getInstance().getCurrentContext();
            if (currentContext != null && currentContext.isComplete()) {
                logger.debug("Using current isolation context for top kills query: Guild={}, Server={}", 
                    currentContext.getGuildId(), currentContext.getServerId());
                return getTopPlayersByKills(currentContext.getGuildId(), currentContext.getServerId(), limit);
            }
            
            // If we're here, we need to implement proper cross-guild isolation
            List<Player> topPlayersCombined = new ArrayList<>();
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Get top players for this server with proper isolation
                            List<Player> serverTopPlayers = getTopPlayersByKills(guildId, server.getServerId(), limit);
                            topPlayersCombined.addAll(serverTopPlayers);
                            
                            logger.debug("Found {} top players by kills for guild {} and server {} using isolation-aware approach", 
                                serverTopPlayers.size(), guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // Sort the combined list and limit to requested size
            topPlayersCombined.sort((p1, p2) -> Integer.compare(p2.getKills(), p1.getKills()));
            if (topPlayersCombined.size() > limit) {
                topPlayersCombined = topPlayersCombined.subList(0, limit);
            }
            
            logger.debug("Retrieved {} top players by kills across all guilds using isolation-aware approach", 
                topPlayersCombined.size());
            return topPlayersCombined;
        } catch (Exception e) {
            logger.error("Error getting top players by kills using isolation-aware approach", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by K/D ratio with proper guild and server isolation
     * @param guildId Guild ID for isolation boundary
     * @param serverId Server ID for isolation boundary
     * @param limit Maximum number of players to return
     * @param minKills Minimum number of kills required to qualify
     * @return List of players with highest K/D ratio within isolation boundary
     */
    public List<Player> getTopPlayersByKD(long guildId, String serverId, int limit, int minKills) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                // Change from warning to informational log if this is intentional
                if (guildId <= 0 && serverId == null) {
                    logger.info("KD ratio leaderboard query executed with intentionally empty isolation context");
                } else {
                    logger.info("KD ratio leaderboard query with incomplete isolation context. Guild ID: {}, Server ID: {}", 
                        guildId, serverId);
                }
                return new ArrayList<>();
            }
            
            // Get GameServer to check if it has restricted isolation
            GameServerRepository serverRepo = new GameServerRepository();
            GameServer server = serverRepo.findByGuildIdAndServerId(guildId, serverId);
            
            if (server != null && server.hasRestrictedIsolation()) {
                // Use consistent isolation mode naming
                String isolationMode = server.isDefaultServer() ? "Default Server" :
                                     server.isReadOnly() ? "read-only" :
                                     server.isIsolationDisabled() ? "disabled isolation" :
                                     server.getIsolationMode();
                
                logger.info("Server {} is in {} mode - returning empty KD ratio leaderboard as expected",
                    server.getName(), isolationMode);
                return new ArrayList<>();
            }
            
            // Filter out players with no names and those with less than minKills kills
            // This is to avoid new players with 1 kill 0 deaths having infinite KD ratio
            // Also apply proper guild and server isolation
            Bson validPlayerFilter = Filters.and(
                Filters.exists("name"),
                Filters.ne("name", ""),
                Filters.ne("name", "**"),
                Filters.gte("kills", minKills),  // Minimum kills to be ranked (usually 10)
                Filters.eq("guildId", guildId),    // Must be from specified guild
                Filters.eq("serverId", serverId)   // Must be from specified server
            );
            
            // Use aggregation to calculate KD ratio - since divisions by zero need special handling
            return getCollection().find(validPlayerFilter)
                .sort(Sorts.orderBy(
                    // Formula: kills / (deaths == 0 ? 1 : deaths)
                    Sorts.descending("kills"),
                    Sorts.ascending("deaths")
                ))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error getting top players by K/D ratio for guild {} and server {}", guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by K/D ratio - legacy compatibility method
     * @param limit Maximum number of players to return
     * @return List of players with highest K/D ratio within isolation boundary
     */
    /**
     * Get top players by K/D ratio using isolation-aware approach
     * This method properly respects isolation boundaries across all guilds and servers
     * @param limit Maximum number of players to return
     * @return List of players with highest K/D ratios with proper isolation boundaries respected
     */
    public List<Player> getTopPlayersByKD(int limit) {
        try {
            // Use current isolation context if available
            GuildIsolationManager.FilterContext currentContext = GuildIsolationManager.getInstance().getCurrentContext();
            if (currentContext != null && currentContext.isComplete()) {
                logger.debug("Using current isolation context for top K/D query: Guild={}, Server={}", 
                    currentContext.getGuildId(), currentContext.getServerId());
                return getTopPlayersByKDRatio(currentContext.getGuildId(), currentContext.getServerId(), limit);
            }
            
            // If we're here, we need to implement proper cross-guild isolation
            List<Player> topPlayersCombined = new ArrayList<>();
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Get top players for this server with proper isolation
                            List<Player> serverTopPlayers = getTopPlayersByKDRatio(guildId, server.getServerId(), limit);
                            topPlayersCombined.addAll(serverTopPlayers);
                            
                            logger.debug("Found {} top players by K/D for guild {} and server {} using isolation-aware approach", 
                                serverTopPlayers.size(), guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // Sort the combined list and limit to requested size
            topPlayersCombined.sort((p1, p2) -> {
                double kd1 = p1.getKills() > 0 && p1.getDeaths() > 0 ? (double)p1.getKills() / p1.getDeaths() : 0;
                double kd2 = p2.getKills() > 0 && p2.getDeaths() > 0 ? (double)p2.getKills() / p2.getDeaths() : 0;
                return Double.compare(kd2, kd1);
            });
            
            if (topPlayersCombined.size() > limit) {
                topPlayersCombined = topPlayersCombined.subList(0, limit);
            }
            
            logger.debug("Retrieved {} top players by K/D across all guilds using isolation-aware approach", 
                topPlayersCombined.size());
            return topPlayersCombined;
        } catch (Exception e) {
            logger.error("Error getting top players by K/D using isolation-aware approach", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by death count with proper guild and server isolation
     */
    public List<Player> getTopPlayersByDeaths(long guildId, String serverId, int limit) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                // Change from warning to informational log if this is intentional
                if (guildId <= 0 && serverId == null) {
                    logger.info("Leaderboard query executed with intentionally empty isolation context");
                } else {
                    logger.info("Leaderboard query for top deaths with incomplete isolation context. Guild ID: {}, Server ID: {}", 
                        guildId, serverId);
                }
                return new ArrayList<>();
            }
            
            // Get GameServer to check if it has restricted isolation
            GameServerRepository serverRepo = new GameServerRepository();
            GameServer server = serverRepo.findByGuildIdAndServerId(guildId, serverId);
            
            if (server != null && server.hasRestrictedIsolation()) {
                // Use consistent isolation mode naming
                String isolationMode = server.isDefaultServer() ? "Default Server" :
                                     server.isReadOnly() ? "read-only" :
                                     server.isIsolationDisabled() ? "disabled isolation" :
                                     server.getIsolationMode();
                
                logger.info("Server {} is in {} mode - returning empty deaths leaderboard as expected",
                    server.getName(), isolationMode);
                return new ArrayList<>();
            }
            
            // Filter invalid players with proper isolation
            Bson filter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.gte("deaths", 1),              // Must have at least 1 death
                Filters.eq("guildId", guildId),        // Must match the guild ID
                Filters.eq("serverId", serverId)       // Must match the server ID
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("deaths"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top players by deaths for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by deaths
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    /**
     * Get top players by death count using isolation-aware approach
     * This method properly respects isolation boundaries across all guilds and servers
     * @param limit Maximum number of players to return
     * @return List of players with highest death counts with proper isolation boundaries respected
     */
    public List<Player> getTopPlayersByDeaths(int limit) {
        try {
            // Use current isolation context if available
            GuildIsolationManager.FilterContext currentContext = GuildIsolationManager.getInstance().getCurrentContext();
            if (currentContext != null && currentContext.isComplete()) {
                logger.debug("Using current isolation context for top deaths query: Guild={}, Server={}", 
                    currentContext.getGuildId(), currentContext.getServerId());
                return getTopPlayersByDeaths(currentContext.getGuildId(), currentContext.getServerId(), limit);
            }
            
            // If we're here, we need to implement proper cross-guild isolation
            List<Player> topPlayersCombined = new ArrayList<>();
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Get top players for this server with proper isolation
                            List<Player> serverTopPlayers = getTopPlayersByDeaths(guildId, server.getServerId(), limit);
                            topPlayersCombined.addAll(serverTopPlayers);
                            
                            logger.debug("Found {} top players by deaths for guild {} and server {} using isolation-aware approach", 
                                serverTopPlayers.size(), guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // Sort the combined list and limit to requested size
            topPlayersCombined.sort((p1, p2) -> Integer.compare(p2.getDeaths(), p1.getDeaths()));
            if (topPlayersCombined.size() > limit) {
                topPlayersCombined = topPlayersCombined.subList(0, limit);
            }
            
            logger.debug("Retrieved {} top players by deaths across all guilds using isolation-aware approach", 
                topPlayersCombined.size());
            return topPlayersCombined;
        } catch (Exception e) {
            logger.error("Error getting top players by deaths using isolation-aware approach", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by distance with proper guild and server isolation
     */
    public List<Player> getTopPlayersByDistance(long guildId, String serverId, int limit) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                // Change from warning to informational log if this is intentional
                if (guildId <= 0 && serverId == null) {
                    logger.info("Distance leaderboard query executed with intentionally empty isolation context");
                } else {
                    logger.info("Distance leaderboard query with incomplete isolation context. Guild ID: {}, Server ID: {}", 
                        guildId, serverId);
                }
                return new ArrayList<>();
            }
            
            // Get GameServer to check if it has restricted isolation
            GameServerRepository serverRepo = new GameServerRepository();
            GameServer server = serverRepo.findByGuildIdAndServerId(guildId, serverId);
            
            if (server != null && server.hasRestrictedIsolation()) {
                // Use consistent isolation mode naming
                String isolationMode = server.isDefaultServer() ? "Default Server" :
                                     server.isReadOnly() ? "read-only" :
                                     server.isIsolationDisabled() ? "disabled isolation" :
                                     server.getIsolationMode();
                
                logger.info("Server {} is in {} mode - returning empty distance leaderboard as expected",
                    server.getName(), isolationMode);
                return new ArrayList<>();
            }
            
            // Filter invalid players with proper isolation
            Bson filter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.exists("distanceTraveled"),    // Must have distance data
                Filters.gt("distanceTraveled", 0),     // Must have traveled some distance
                Filters.eq("guildId", guildId),        // Must match the guild ID
                Filters.eq("serverId", serverId)       // Must match the server ID
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("distanceTraveled"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top players by distance for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by distance
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    /**
     * Get top players by kill distance using isolation-aware approach
     * This method properly respects isolation boundaries across all guilds and servers
     * @param limit Maximum number of players to return
     * @return List of players with longest kill distances with proper isolation boundaries respected
     */
    public List<Player> getTopPlayersByDistance(int limit) {
        try {
            // Use current isolation context if available
            GuildIsolationManager.FilterContext currentContext = GuildIsolationManager.getInstance().getCurrentContext();
            if (currentContext != null && currentContext.isComplete()) {
                logger.debug("Using current isolation context for top distance query: Guild={}, Server={}", 
                    currentContext.getGuildId(), currentContext.getServerId());
                return getTopPlayersByDistance(currentContext.getGuildId(), currentContext.getServerId(), limit);
            }
            
            // If we're here, we need to implement proper cross-guild isolation
            List<Player> topPlayersCombined = new ArrayList<>();
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Get top players for this server with proper isolation
                            List<Player> serverTopPlayers = getTopPlayersByDistance(guildId, server.getServerId(), limit);
                            topPlayersCombined.addAll(serverTopPlayers);
                            
                            logger.debug("Found {} top players by distance for guild {} and server {} using isolation-aware approach", 
                                serverTopPlayers.size(), guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // Sort the combined list and limit to requested size
            topPlayersCombined.sort((p1, p2) -> Double.compare(p2.getLongestKillDistance(), p1.getLongestKillDistance()));
            if (topPlayersCombined.size() > limit) {
                topPlayersCombined = topPlayersCombined.subList(0, limit);
            }
            
            logger.debug("Retrieved {} top players by distance across all guilds using isolation-aware approach", 
                topPlayersCombined.size());
            return topPlayersCombined;
        } catch (Exception e) {
            logger.error("Error getting top players by distance using isolation-aware approach", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by kill streak with proper guild and server isolation
     */
    public List<Player> getTopPlayersByKillStreak(long guildId, String serverId, int limit) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                // Change from warning to informational log if this is intentional
                if (guildId <= 0 && serverId == null) {
                    logger.info("Kill streak leaderboard query executed with intentionally empty isolation context");
                } else {
                    logger.info("Kill streak leaderboard query with incomplete isolation context. Guild ID: {}, Server ID: {}", 
                        guildId, serverId);
                }
                return new ArrayList<>();
            }
            
            // Get GameServer to check if it has restricted isolation
            GameServerRepository serverRepo = new GameServerRepository();
            GameServer server = serverRepo.findByGuildIdAndServerId(guildId, serverId);
            
            if (server != null && server.hasRestrictedIsolation()) {
                // Use consistent isolation mode naming
                String isolationMode = server.isDefaultServer() ? "Default Server" :
                                     server.isReadOnly() ? "read-only" :
                                     server.isIsolationDisabled() ? "disabled isolation" :
                                     server.getIsolationMode();
                
                logger.info("Server {} is in {} mode - returning empty kill streak leaderboard as expected",
                    server.getName(), isolationMode);
                return new ArrayList<>();
            }
            
            // Filter invalid players with proper isolation
            Bson filter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.exists("longestKillStreak"),   // Must have streak data
                Filters.gt("longestKillStreak", 0),    // Must have a streak
                Filters.eq("guildId", guildId),        // Must match the guild ID
                Filters.eq("serverId", serverId)       // Must match the server ID
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("longestKillStreak"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top players by kill streak for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by kill streak
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    /**
     * Get top players by kill streak using isolation-aware approach
     * This method properly respects isolation boundaries across all guilds and servers
     * @param limit Maximum number of players to return
     * @return List of players with highest kill streaks with proper isolation boundaries respected
     */
    public List<Player> getTopPlayersByKillStreak(int limit) {
        try {
            // Use current isolation context if available
            GuildIsolationManager.FilterContext currentContext = GuildIsolationManager.getInstance().getCurrentContext();
            if (currentContext != null && currentContext.isComplete()) {
                logger.debug("Using current isolation context for top kill streak query: Guild={}, Server={}", 
                    currentContext.getGuildId(), currentContext.getServerId());
                return getTopPlayersByKillStreak(currentContext.getGuildId(), currentContext.getServerId(), limit);
            }
            
            // If we're here, we need to implement proper cross-guild isolation
            List<Player> topPlayersCombined = new ArrayList<>();
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Get top players for this server with proper isolation
                            List<Player> serverTopPlayers = getTopPlayersByKillStreak(guildId, server.getServerId(), limit);
                            topPlayersCombined.addAll(serverTopPlayers);
                            
                            logger.debug("Found {} top players by kill streak for guild {} and server {} using isolation-aware approach", 
                                serverTopPlayers.size(), guildId, server.getServerId());
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // Sort the combined list and limit to requested size
            topPlayersCombined.sort((p1, p2) -> Integer.compare(p2.getLongestKillStreak(), p1.getLongestKillStreak()));
            if (topPlayersCombined.size() > limit) {
                topPlayersCombined = topPlayersCombined.subList(0, limit);
            }
            
            logger.debug("Retrieved {} top players by kill streak across all guilds using isolation-aware approach", 
                topPlayersCombined.size());
            return topPlayersCombined;
        } catch (Exception e) {
            logger.error("Error getting top players by kill streak using isolation-aware approach", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Update player's kill streak statistics with isolation
     * Increments current streak and updates longest if needed
     */
    public void incrementKillStreak(String playerId, long guildId, String serverId) {
        try {
            Player player = findByPlayerIdAndGuildIdAndServerId(playerId, guildId, serverId);
            if (player != null) {
                player.incrementKillStreak();
                save(player);
                logger.debug("Updated kill streak for player: {} in Guild={}, Server={}",
                    player.getName(), guildId, serverId);
            }
        } catch (Exception e) {
            logger.error("Error updating kill streak for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Increment kill streak for a player using isolation-aware approach
     * This method properly respects isolation boundaries
     * @param playerId The player ID to increment kill streak for
     */
    public void incrementKillStreak(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            logger.error("Cannot increment kill streak for player with null or empty ID");
            return;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            boolean playerUpdated = false;
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find player with proper isolation
                            Player player = findByPlayerIdAndGuildIdAndServerId(playerId, guildId, server.getServerId());
                            if (player != null) {
                                // Use existing method to increment kill streak with proper isolation
                                incrementKillStreak(playerId, guildId, server.getServerId());
                                playerUpdated = true;
                                logger.debug("Incremented kill streak for player ID {} in guild {} and server {} using isolation-aware approach", 
                                    playerId, guildId, server.getServerId());
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            if (!playerUpdated) {
                logger.debug("No player found with ID {} in any guild to increment kill streak using isolation-aware approach", playerId);
            }
        } catch (Exception e) {
            logger.error("Error incrementing kill streak for player ID: {} using isolation-aware approach", playerId, e);
        }
    }
    
    /**
     * Reset a player's current kill streak (when they die) with isolation
     */
    public void resetKillStreak(String playerId, long guildId, String serverId) {
        try {
            Player player = findByPlayerIdAndGuildIdAndServerId(playerId, guildId, serverId);
            if (player != null) {
                player.resetKillStreak();
                save(player);
                logger.debug("Reset kill streak for player: {} in Guild={}, Server={}",
                    player.getName(), guildId, serverId);
            }
        } catch (Exception e) {
            logger.error("Error resetting kill streak for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Reset kill streak for a player using isolation-aware approach
     * This method properly respects isolation boundaries
     * @param playerId The player ID to reset kill streak for
     */
    public void resetKillStreak(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            logger.error("Cannot reset kill streak for player with null or empty ID");
            return;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            boolean playerUpdated = false;
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find player with proper isolation
                            Player player = findByPlayerIdAndGuildIdAndServerId(playerId, guildId, server.getServerId());
                            if (player != null) {
                                // Use existing method to reset kill streak with proper isolation
                                resetKillStreak(playerId, guildId, server.getServerId());
                                playerUpdated = true;
                                logger.debug("Reset kill streak for player ID {} in guild {} and server {} using isolation-aware approach", 
                                    playerId, guildId, server.getServerId());
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            if (!playerUpdated) {
                logger.debug("No player found with ID {} in any guild to reset kill streak using isolation-aware approach", playerId);
            }
        } catch (Exception e) {
            logger.error("Error resetting kill streak for player ID: {} using isolation-aware approach", playerId, e);
        }
    }
    
    /**
     * Find a player by Discord ID with isolation
     * @param discordId The Discord user ID
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return The player or null if not found
     */
    public Player findByDiscordId(String discordId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("discordId", discordId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by Discord ID with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by Discord ID
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param discordId The Discord user ID
     * @return The player or null if not found
     */
    public Player findByDiscordId(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            logger.error("Cannot find player with null or empty Discord ID");
            return null;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find the player with proper isolation
                            Player player = findByDiscordIdAndGuildIdAndServerId(discordId, guildId, server.getServerId());
                            if (player != null) {
                                logger.debug("Found player with Discord ID '{}' in guild {} and server {} using isolation-aware approach", 
                                    discordId, guildId, server.getServerId());
                                return player;
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No player found with Discord ID '{}' in any guild using isolation-aware approach", discordId);
            return null;
        } catch (Exception e) {
            logger.error("Error finding player by Discord ID: '{}' using isolation-aware approach", discordId, e);
            return null;
        }
    }
    
    /**
     * Find a player by exact name match with isolation
     * @param name The exact player name to find
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return The player or null if not found
     */
    public Player findByNameExact(String name, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("name", name),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by exact name with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by exact name match
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param name The exact player name to find
     * @return The player or null if not found
     */
    public Player findByNameExact(String name) {
        if (name == null || name.isEmpty()) {
            logger.error("Cannot find player with null or empty name");
            return null;
        }
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find the player with proper isolation
                            Bson filter = Filters.and(
                                Filters.eq("name", name),
                                Filters.eq("guildId", guildId),
                                Filters.eq("serverId", server.getServerId())
                            );
                            Player player = getCollection().find(filter).first();
                            if (player != null) {
                                logger.debug("Found player with exact name '{}' in guild {} and server {} using isolation-aware approach", 
                                    name, guildId, server.getServerId());
                                return player;
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No player found with exact name '{}' in any guild using isolation-aware approach", name);
            return null;
        } catch (Exception e) {
            logger.error("Error finding player by exact name: '{}' using isolation-aware approach", name, e);
            return null;
        }
    }
    
    /**
     * Find players with names similar to the provided name with isolation
     * @param namePattern The name pattern to search for
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation 
     * @return List of players with matching names within the isolation boundary
     */
    public List<Player> findByNameLike(String namePattern, long guildId, String serverId) {
        try {
            // Case-insensitive regex search with isolation
            String regexPattern = namePattern.toLowerCase().replace("*", ".*");
            Bson filter = Filters.and(
                Filters.regex("name", "(?i)" + regexPattern),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter)
                .limit(10) // Limit results to prevent large result sets
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by name pattern with isolation: {}", namePattern, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players with names similar to the provided name
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param namePattern The name pattern to search for
     * @return List of players with matching names
     */
    public List<Player> findByNameLike(String namePattern) {
        if (namePattern == null || namePattern.isEmpty()) {
            logger.error("Cannot search for players with null or empty name pattern");
            return new ArrayList<>();
        }
        
        try {
            List<Player> allMatchingPlayers = new ArrayList<>();
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find players with matching names with proper isolation
                            List<Player> matchingPlayers = findByNameLike(namePattern, guildId, server.getServerId());
                            if (matchingPlayers != null && !matchingPlayers.isEmpty()) {
                                allMatchingPlayers.addAll(matchingPlayers);
                                logger.debug("Found {} players matching name pattern '{}' in guild {} and server {} using isolation-aware approach", 
                                    matchingPlayers.size(), namePattern, guildId, server.getServerId());
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Found {} total players matching name pattern '{}' across all guilds using isolation-aware approach", 
                allMatchingPlayers.size(), namePattern);
            return allMatchingPlayers;
        } catch (Exception e) {
            logger.error("Error finding players by name pattern: '{}' using isolation-aware approach", namePattern, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players by faction ID with isolation
     * @param factionId The faction ID to filter by
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return List of players in the faction within isolation boundary
     */
    public List<Player> findByFactionId(ObjectId factionId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("factionId", factionId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by faction ID with isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players by faction ID
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param factionId The faction ID to filter by
     * @return List of players in the faction
     */
    public List<Player> findByFactionId(ObjectId factionId) {
        if (factionId == null) {
            logger.error("Cannot find players with null faction ID");
            return new ArrayList<>();
        }
        
        try {
            List<Player> allFactionPlayers = new ArrayList<>();
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Find players in faction with proper isolation
                            List<Player> serverFactionPlayers = findByFactionId(factionId, guildId, server.getServerId());
                            if (serverFactionPlayers != null && !serverFactionPlayers.isEmpty()) {
                                allFactionPlayers.addAll(serverFactionPlayers);
                                logger.debug("Found {} players in faction {} for guild {} and server {} using isolation-aware approach", 
                                    serverFactionPlayers.size(), factionId, guildId, server.getServerId());
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Found {} total players in faction {} across all guilds using isolation-aware approach", 
                allFactionPlayers.size(), factionId);
            return allFactionPlayers;
        } catch (Exception e) {
            logger.error("Error finding players by faction ID: {} using isolation-aware approach", factionId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Count players in a faction with isolation
     * @param factionId The faction ID to count members for
     * @param guildId The guild ID for isolation 
     * @param serverId The server ID for isolation
     * @return The number of players in the faction within isolation boundary
     */
    public long countByFactionId(ObjectId factionId, long guildId, String serverId) {
        try {
            return getCollection().countDocuments(Filters.and(
                Filters.eq("factionId", factionId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            ));
        } catch (Exception e) {
            logger.error("Error counting players by faction ID with isolation", e);
            return 0;
        }
    }
    
    /**
     * Count players in a faction using isolation-aware approach
     * This method properly respects isolation boundaries
     * @param factionId The faction ID to count members for
     * @return Count of players in the faction with proper isolation boundaries respected
     */
    public long countByFactionId(ObjectId factionId) {
        if (factionId == null) {
            logger.error("Cannot count players with null faction ID");
            return 0;
        }
        
        try {
            long totalCount = 0;
            
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild to maintain proper isolation
                    GameServerRepository gameServerRepo = new GameServerRepository();
                    List<com.deadside.bot.db.models.GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
                    
                    // Process each server with proper isolation
                    for (com.deadside.bot.db.models.GameServer server : servers) {
                        if (server == null || server.getServerId() == null) continue;
                        
                        // Set server context for detailed isolation
                        GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                        
                        try {
                            // Count players in faction with proper isolation
                            long serverCount = countByFactionId(factionId, guildId, server.getServerId());
                            totalCount += serverCount;
                            
                            if (serverCount > 0) {
                                logger.debug("Counted {} players in faction {} for guild {} and server {} using isolation-aware approach", 
                                    serverCount, factionId, guildId, server.getServerId());
                            }
                        } finally {
                            // Reset to guild-level context
                            GuildIsolationManager.getInstance().setContext(guildId, null);
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Counted {} total players in faction {} across all guilds using isolation-aware approach", 
                totalCount, factionId);
            return totalCount;
        } catch (Exception e) {
            logger.error("Error counting players by faction ID: {} using isolation-aware approach", factionId, e);
            return 0;
        }
    }
    
    /**
     * Get a default filter context for legacy method compatibility
     * This is used only as a fallback for legacy methods to prevent complete data leakage
     */
    /**
     * Get a default isolation context for database operations
     * This method has been updated to prioritize the current context if available
     * 
     * @return A filter context with guild and server IDs, or null if none available
     */
    private GuildIsolationManager.FilterContext getDefaultFilterContext() {
        try {
            // First check if we already have an active isolation context
            GuildIsolationManager.FilterContext currentContext = GuildIsolationManager.getInstance().getCurrentContext();
            if (currentContext != null && currentContext.isComplete()) {
                logger.debug("Using current isolation context as default: Guild={}, Server={}", 
                    currentContext.getGuildId(), currentContext.getServerId());
                return currentContext;
            }
            
            // If no current context, try to use one from the current guild settings if available
            com.deadside.bot.db.repositories.GuildConfigRepository guildRepo = new com.deadside.bot.db.repositories.GuildConfigRepository();
            List<Long> guildIds = guildRepo.getDistinctGuildIds();
            
            if (!guildIds.isEmpty()) {
                Long primaryGuildId = guildIds.get(0);
                
                // Find a valid server for this guild
                GameServerRepository serverRepo = new GameServerRepository();
                List<com.deadside.bot.db.models.GameServer> servers = serverRepo.findAllByGuildId(primaryGuildId);
                
                if (!servers.isEmpty()) {
                    com.deadside.bot.db.models.GameServer primaryServer = servers.get(0);
                    logger.debug("Using isolation context from primary guild: Guild={}, Server={}", 
                        primaryGuildId, primaryServer.getServerId());
                    return GuildIsolationManager.getInstance().createFilterContext(primaryGuildId, primaryServer.getServerId());
                }
            }
            
            // As a last resort, try to find any server config
            MongoCollection<Document> serversCollection = MongoDBConnection.getInstance()
                .getDatabase().getCollection("game_servers");
            
            Document server = serversCollection.find().first();
            if (server != null) {
                long guildId = server.getLong("guildId");
                String serverId = server.getString("serverId");
                
                if (guildId > 0 && serverId != null && !serverId.isEmpty()) {
                    logger.debug("Using isolation context from database scan: Guild={}, Server={}", guildId, serverId);
                    return GuildIsolationManager.getInstance().createFilterContext(guildId, serverId);
                }
            }
            
            logger.warn("Could not determine a valid isolation context for this operation");
            return null;
        } catch (Exception e) {
            logger.error("Error getting default filter context", e);
            return null;
        }
    }
}