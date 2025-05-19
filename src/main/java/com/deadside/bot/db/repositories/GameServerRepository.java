package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.utils.DataBoundary;
import com.deadside.bot.utils.GuildIsolationManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for GameServer collection with comprehensive isolation
 * Implements methods for proper data isolation between different Discord guilds and servers
 */
public class GameServerRepository {
    private static final Logger logger = LoggerFactory.getLogger(GameServerRepository.class);
    private static final String COLLECTION_NAME = "game_servers";
    
    private MongoCollection<GameServer> collection;
    
    public GameServerRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, GameServer.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<GameServer> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, GameServer.class);
            } catch (Exception e) {
                logger.error("Failed to initialize game server collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save a game server with proper isolation check
     */
    public void save(GameServer server) {
        try {
            // Ensure server has valid isolation fields
            if (server.getGuildId() <= 0) {
                logger.error("Attempted to save game server without proper guild ID: {}", server.getName());
                return;
            }
            
            // Ensure serverId is set as a unique identifier
            if (server.getServerId() == null || server.getServerId().isEmpty()) {
                logger.error("Attempted to save game server without server ID: {}", server.getName());
                return;
            }
            
            ReplaceOptions options = new ReplaceOptions().upsert(true);
            
            if (server.getId() == null) {
                server.setId(new ObjectId());
            }
            
            getCollection().replaceOne(
                Filters.eq("_id", server.getId()), 
                server, 
                options
            );
            
            logger.debug("Saved game server: {} with proper isolation (Guild={})",
                server.getName(), server.getGuildId());
        } catch (Exception e) {
            logger.error("Error saving game server: {}", server.getName(), e);
        }
    }
    
    /**
     * Find a game server by server ID with isolation check
     * @param serverId The server ID to search for
     * @param guildId The guild ID for isolation boundary
     * @return The game server if found and belongs to the specified guild
     */
    public GameServer findByServerIdAndGuildId(String serverId, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("serverId", serverId),
                Filters.eq("guildId", guildId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding game server by ID: {} with guild isolation: {}", serverId, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a game server by ID using isolation-aware approach
     * This method properly respects isolation boundaries when retrieving a server by ID
     * @param serverId The server ID to find
     * @return The game server with proper isolation boundaries respected
     */
    public GameServer findById(String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            logger.error("Cannot find game server with null or empty server ID");
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
                    // Find the server with proper isolation
                    GameServer server = findByServerIdAndGuildId(serverId, guildId);
                    if (server != null) {
                        logger.debug("Found game server {} in guild {} using isolation-aware approach", 
                            serverId, guildId);
                        return server;
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No game server found with ID {} in any guild using isolation-aware approach", serverId);
            return null;
        } catch (Exception e) {
            logger.error("Error finding game server by ID: {} using isolation-aware approach", serverId, e);
            return null;
        }
    }
    
    /**
     * Get all game servers using isolation-aware approach
     * This method properly respects isolation boundaries when retrieving all servers
     * @return List of all game servers with proper isolation boundaries respected
     */
    public List<GameServer> getAllServers() {
        List<GameServer> allServers = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Find all servers for this guild with proper isolation
                    List<GameServer> guildServers = findAllByGuildId(guildId);
                    allServers.addAll(guildServers);
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Retrieved all game servers using isolation-aware approach: {} total servers", 
                allServers.size());
        } catch (Exception e) {
            logger.error("Error getting all servers using isolation-aware approach", e);
        }
        
        return allServers;
    }
    
    /**
     * Get all distinct guild IDs in the database
     * This is useful for proper isolation patterns when you need to work with all guilds
     * @return List of all guild IDs
     */
    public List<Long> getDistinctGuildIds() {
        try {
            List<Long> guildIds = new ArrayList<>();
            getCollection().distinct("guildId", Long.class).into(guildIds);
            return guildIds;
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get all game servers for a specific guild with proper isolation
     * @param guildId The Discord guild ID for isolation
     * @return List of all servers for the given guild
     */
    public List<GameServer> getServersByGuildId(long guildId) {
        try {
            if (guildId <= 0) {
                logger.warn("Attempted to get servers without proper guild ID: {}", guildId);
                return new ArrayList<>();
            }
            
            List<GameServer> servers = new ArrayList<>();
            getCollection().find(Filters.eq("guildId", guildId)).into(servers);
            
            return servers;
        } catch (Exception e) {
            logger.error("Error getting servers by guild ID", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find a game server by MongoDB ObjectID with isolation check
     */
    public GameServer findByObjectIdAndGuildId(ObjectId id, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding game server by ObjectID: {} with guild isolation: {}", id, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a game server by MongoDB ObjectID using isolation-aware approach
     * This method properly respects isolation boundaries when retrieving a server by ObjectID
     * @param id The MongoDB ObjectID to find
     * @return The game server with proper isolation boundaries respected
     */
    public GameServer findByObjectId(ObjectId id) {
        if (id == null) {
            logger.error("Cannot find game server with null ObjectID");
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
                    // Find the server with proper isolation
                    GameServer server = findByObjectIdAndGuildId(id, guildId);
                    if (server != null) {
                        logger.debug("Found game server with ObjectID {} in guild {} using isolation-aware approach", 
                            id, guildId);
                        return server;
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No game server found with ObjectID {} in any guild using isolation-aware approach", id);
            return null;
        } catch (Exception e) {
            logger.error("Error finding game server by ObjectID: {} using isolation-aware approach", id, e);
            return null;
        }
    }
    
    /**
     * Find a game server by name with proper guild isolation
     */
    public GameServer findByNameAndGuildId(String name, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.regex("name", name, "i"), // Case-insensitive name match
                Filters.eq("guildId", guildId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding game server by name: {} with guild isolation: {}", name, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a game server by guild ID and name (exact match)
     * This is an alias for findByNameAndGuildId with clearer naming
     * @param guildId The guild ID for isolation boundary
     * @param name The name of the server to find
     * @return The game server if found
     */
    public GameServer findByGuildIdAndName(long guildId, String name) {
        try {
            Bson filter = Filters.and(
                Filters.eq("name", name), // Exact name match
                Filters.eq("guildId", guildId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding game server by guild: {} and name: {}", guildId, name, e);
            return null;
        }
    }
    
    /**
     * Find a game server by name using isolation-aware approach
     * This method properly respects isolation boundaries when retrieving a server by name
     * @param name The server name to find
     * @return The game server with proper isolation boundaries respected
     */
    public GameServer findByName(String name) {
        if (name == null || name.isEmpty()) {
            logger.error("Cannot find game server with null or empty name");
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
                    // Find the server with proper isolation
                    GameServer server = findByNameAndGuildId(name, guildId);
                    if (server != null) {
                        logger.debug("Found game server with name '{}' in guild {} using isolation-aware approach", 
                            name, guildId);
                        return server;
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No game server found with name '{}' in any guild using isolation-aware approach", name);
            return null;
        } catch (Exception e) {
            logger.error("Error finding game server by name: '{}' using isolation-aware approach", name, e);
            return null;
        }
    }
    
    /**
     * Find all game servers for a specific Discord guild
     * This ensures proper data isolation between different Discord servers
     */
    public List<GameServer> findAllByGuildId(long guildId) {
        try {
            Bson filter = Filters.eq("guildId", guildId);
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding game servers for guild: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find the default game server for a Discord guild
     * Returns the first server found for that guild, or null if none exists
     */
    public GameServer findByGuildId(long guildId) {
        try {
            Bson filter = Filters.eq("guildId", guildId);
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding default game server for guild: {}", guildId, e);
            return null;
        }
    }
    
    /**
     * Delete a game server by ID with proper guild isolation
     * Prevents accidental deletion of servers from another guild
     */
    public boolean deleteByServerIdAndGuildId(String serverId, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("serverId", serverId),
                Filters.eq("guildId", guildId)
            );
            DeleteResult result = getCollection().deleteOne(filter);
            
            if (result.getDeletedCount() > 0) {
                logger.info("Deleted game server with ID: {} from guild: {}", serverId, guildId);
                return true;
            } else {
                logger.warn("Attempted to delete game server with ID: {} from guild: {}, but server was not found or belongs to a different guild", 
                    serverId, guildId);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error deleting game server by ID: {} with guild isolation: {}", serverId, guildId, e);
            return false;
        }
    }
    
    /**
     * Delete a game server by ID using isolation-aware approach
     * This method properly respects isolation boundaries when deleting a server by ID
     * @param serverId The server ID to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteById(String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            logger.error("Cannot delete game server with null or empty server ID");
            return false;
        }
        
        boolean anyDeleted = false;
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Find the server with proper isolation first to ensure it exists
                    GameServer server = findByServerIdAndGuildId(serverId, guildId);
                    if (server != null) {
                        // Delete with proper isolation boundaries
                        boolean deleted = deleteByServerIdAndGuildId(serverId, guildId);
                        if (deleted) {
                            logger.debug("Deleted game server {} in guild {} using isolation-aware approach", 
                                serverId, guildId);
                            anyDeleted = true;
                        }
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            if (!anyDeleted) {
                logger.debug("No game server found with ID {} in any guild to delete using isolation-aware approach", serverId);
            }
            
            return anyDeleted;
        } catch (Exception e) {
            logger.error("Error deleting game server by ID: {} using isolation-aware approach", serverId, e);
            return false;
        }
    }
    
    /**
     * Get all game servers using an isolation-aware approach
     * This method properly respects isolation boundaries
     */
    public List<GameServer> findAll() {
        List<GameServer> allServers = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild's servers with proper isolation
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all servers for this guild (isolation-aware)
                    List<GameServer> guildServers = findAllByGuildId(guildId);
                    allServers.addAll(guildServers);
                } finally {
                    // Always clear context, even if exception occurs
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Retrieved all game servers using isolation-aware approach: {} total records", allServers.size());
        } catch (Exception e) {
            logger.error("Error finding all game servers using isolation-aware approach", e);
        }
        
        return allServers;
    }
    


    /**
     * Delete all game servers for a specific guild
     * Used primarily for data cleanup operations
     */
    public long deleteAllByGuildId(long guildId) {
        try {
            DeleteResult result = getCollection().deleteMany(
                Filters.eq("guildId", guildId)
            );
            logger.info("Deleted {} game servers from guild: {}", result.getDeletedCount(), guildId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting game servers for guild: {}", guildId, e);
            return 0;
        }
    }
    
    /**
     * Delete a game server with proper guild isolation
     * Prevents accidental deletion of servers from another guild
     */
    public boolean deleteWithIsolation(GameServer server, long guildId) {
        try {
            // Check if server belongs to specified guild before deleting
            if (server.getGuildId() == guildId) {
                Bson filter = Filters.eq("_id", server.getId());
                DeleteResult result = getCollection().deleteOne(filter);
                
                if (result.getDeletedCount() > 0) {
                    logger.info("Deleted game server: {} from guild: {}", server.getName(), guildId);
                    return true;
                } else {
                    logger.warn("Failed to delete game server: {} from guild: {}", server.getName(), guildId);
                    return false;
                }
            } else {
                logger.warn("Prevented deletion of game server: {} due to guild isolation mismatch (requested guild: {}, actual guild: {})",
                    server.getName(), guildId, server.getGuildId());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error deleting game server: {} with guild isolation: {}", server.getName(), guildId, e);
            return false;
        }
    }
    
    /**
     * Delete a game server using isolation-aware approach
     * This method properly respects isolation boundaries when deleting a server
     * @param server The server to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean delete(GameServer server) {
        if (server == null || server.getId() == null) {
            logger.error("Cannot delete null game server or server with null ID");
            return false;
        }
        
        try {
            // Check if the server has guild information for proper isolation
            if (server.getGuildId() <= 0) {
                logger.error("Cannot delete game server without proper guild ID: {}", server.getName());
                return false;
            }
            
            // Set isolation context for this guild
            GuildIsolationManager.getInstance().setContext(server.getGuildId(), server.getServerId());
            
            try {
                // Delete with proper isolation boundaries
                Bson filter = Filters.and(
                    Filters.eq("_id", server.getId()),
                    Filters.eq("guildId", server.getGuildId())
                );
                
                boolean deleted = getCollection().deleteOne(filter).getDeletedCount() > 0;
                
                if (deleted) {
                    logger.debug("Deleted game server {} in guild {} using isolation-aware approach", 
                        server.getName(), server.getGuildId());
                    return true;
                } else {
                    logger.debug("No game server found with ID {} in guild {} to delete using isolation-aware approach", 
                        server.getId(), server.getGuildId());
                    return false;
                }
            } finally {
                // Always clear context when done
                GuildIsolationManager.getInstance().clearContext();
            }
        } catch (Exception e) {
            logger.error("Error deleting game server: {} using isolation-aware approach", server.getName(), e);
            return false;
        }
    }
    
    /**
     * Check if a server exists with the given server ID and guild ID
     */
    public boolean existsByServerIdAndGuildId(String serverId, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("serverId", serverId),
                Filters.eq("guildId", guildId)
            );
            return getCollection().countDocuments(filter) > 0;
        } catch (Exception e) {
            logger.error("Error checking if game server exists by ID: {} with guild isolation: {}", serverId, guildId, e);
            return false;
        }
    }
    
    /* 
     * Note: getDistinctGuildIds() is already defined above. This duplicate definition has been removed.
     */
    
    /**
     * Find server by the serverId across all guilds using isolation-aware approach
     * This method properly respects isolation boundaries when retrieving a server by ID
     * @param serverId The server ID to search for
     * @return The first server found with the given ID
     */
    public GameServer findByServerId(String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            logger.error("Cannot find game server with null or empty server ID");
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
                    // Find the server with proper isolation
                    GameServer server = findByServerIdAndGuildId(serverId, guildId);
                    if (server != null) {
                        logger.debug("Found game server with ID {} in guild {} using isolation-aware approach", 
                            serverId, guildId);
                        return server;
                    }
                } finally {
                    // Always clear context when done
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("No game server found with ID {} in any guild using isolation-aware approach", serverId);
            return null;
        } catch (Exception e) {
            logger.error("Error finding server by ID with isolation-aware approach: {}", serverId, e);
            return null;
        }
    }
}