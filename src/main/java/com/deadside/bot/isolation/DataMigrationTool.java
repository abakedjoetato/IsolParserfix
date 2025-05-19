package com.deadside.bot.isolation;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.GameServer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for migrating existing data to proper isolation boundaries
 * This ensures all data has proper guildId and serverId fields
 */
public class DataMigrationTool {
    private static final Logger logger = LoggerFactory.getLogger(DataMigrationTool.class);
    
    // Collection names in the MongoDB database
    private static final String PLAYERS_COLLECTION = "players";
    private static final String FACTIONS_COLLECTION = "factions";
    private static final String LINKED_PLAYERS_COLLECTION = "linked_players";
    private static final String GAME_SERVERS_COLLECTION = "game_servers";
    
    // Default values for missing fields
    private static final String DEFAULT_SERVER_ID = "default";
    
    /**
     * Run the migration process to add isolation boundaries to all existing data
     * @return Summary of the migration results
     */
    public String migrateAllData() {
        StringBuilder summary = new StringBuilder();
        
        try {
            // Get all game servers for reference
            List<GameServer> servers = getAllGameServers();
            
            // Create server mapping by guild
            Map<Long, List<GameServer>> serversByGuild = createServerMapping(servers);
            
            // Migrate each collection
            int playerCount = migrateCollection(PLAYERS_COLLECTION, serversByGuild);
            int factionCount = migrateCollection(FACTIONS_COLLECTION, serversByGuild);
            int linkedPlayerCount = migrateCollection(LINKED_PLAYERS_COLLECTION, serversByGuild);
            
            // Build the summary
            summary.append("=== Data Isolation Migration Complete ===\n");
            summary.append("Players migrated: ").append(playerCount).append("\n");
            summary.append("Factions migrated: ").append(factionCount).append("\n");
            summary.append("Linked players migrated: ").append(linkedPlayerCount).append("\n");
            summary.append("Total records updated: ").append(playerCount + factionCount + linkedPlayerCount).append("\n");
            
            return summary.toString();
        } catch (Exception e) {
            logger.error("Failed to run data migration", e);
            return "Migration failed: " + e.getMessage();
        }
    }
    
    /**
     * Get all game servers from the database
     */
    private List<GameServer> getAllGameServers() {
        try {
            MongoCollection<GameServer> collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(GAME_SERVERS_COLLECTION, GameServer.class);
            return collection.find().into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Failed to get game servers", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Create a mapping of guild IDs to their servers
     */
    private Map<Long, List<GameServer>> createServerMapping(List<GameServer> servers) {
        Map<Long, List<GameServer>> mapping = new HashMap<>();
        
        for (GameServer server : servers) {
            long guildId = server.getGuildId();
            if (!mapping.containsKey(guildId)) {
                mapping.put(guildId, new ArrayList<>());
            }
            mapping.get(guildId).add(server);
        }
        
        return mapping;
    }
    
    /**
     * Migrate a collection to add isolation fields where missing
     * @param collectionName The name of the collection to migrate
     * @param serversByGuild Mapping of guild IDs to their servers
     * @return Count of records updated
     */
    private int migrateCollection(String collectionName, Map<Long, List<GameServer>> serversByGuild) {
        try {
            MongoCollection<Document> collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(collectionName);
            
            // Find records missing isolation fields
            int count = 0;
            
            // First update records with guildId but missing serverId
            count += updateRecordsWithGuildIdButNoServerId(collection, serversByGuild);
            
            // Then update records missing both guildId and serverId
            count += updateRecordsMissingBothFields(collection);
            
            logger.info("Updated {} records in collection {}", count, collectionName);
            return count;
        } catch (Exception e) {
            logger.error("Failed to migrate collection: {}", collectionName, e);
            return 0;
        }
    }
    
    /**
     * Update records that have a guildId but no serverId
     */
    private int updateRecordsWithGuildIdButNoServerId(MongoCollection<Document> collection, 
                                                     Map<Long, List<GameServer>> serversByGuild) {
        int count = 0;
        try {
            // Find records with guildId but missing serverId
            List<Document> records = collection.find(
                Filters.and(
                    Filters.exists("guildId", true),
                    Filters.or(
                        Filters.exists("serverId", false),
                        Filters.eq("serverId", null),
                        Filters.eq("serverId", "")
                    )
                )
            ).into(new ArrayList<>());
            
            // Update each record
            for (Document record : records) {
                long guildId = record.getLong("guildId");
                List<GameServer> guildServers = serversByGuild.get(guildId);
                
                // Use the first server for this guild, or default
                String serverId = DEFAULT_SERVER_ID;
                if (guildServers != null && !guildServers.isEmpty()) {
                    serverId = guildServers.get(0).getServerId();
                }
                
                // Update the record
                collection.updateOne(
                    Filters.eq("_id", record.get("_id")),
                    Updates.set("serverId", serverId)
                );
                count++;
            }
        } catch (Exception e) {
            logger.error("Error updating records with guildId but no serverId", e);
        }
        return count;
    }
    
    /**
     * Update records missing both guildId and serverId
     */
    private int updateRecordsMissingBothFields(MongoCollection<Document> collection) {
        int count = 0;
        try {
            // Find records missing both guildId and serverId
            List<Document> records = collection.find(
                Filters.or(
                    Filters.exists("guildId", false),
                    Filters.eq("guildId", null),
                    Filters.eq("guildId", 0)
                )
            ).into(new ArrayList<>());
            
            // Update each record with default values
            for (Document record : records) {
                collection.updateOne(
                    Filters.eq("_id", record.get("_id")),
                    Updates.combine(
                        Updates.set("guildId", getDefaultGuildId()),
                        Updates.set("serverId", DEFAULT_SERVER_ID)
                    )
                );
                count++;
            }
        } catch (Exception e) {
            logger.error("Error updating records missing both fields", e);
        }
        return count;
    }
    
    /**
     * Get a default guild ID for records missing it
     * Tries to find the most active guild in the system
     */
    private long getDefaultGuildId() {
        try {
            // Get player counts by guild
            MongoCollection<Document> playersCollection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(PLAYERS_COLLECTION);
            
            List<Document> results = playersCollection.aggregate(List.of(
                new Document("$match", new Document("guildId", new Document("$exists", true))),
                new Document("$group", new Document("_id", "$guildId")
                    .append("count", new Document("$sum", 1))),
                new Document("$sort", new Document("count", -1)),
                new Document("$limit", 1)
            )).into(new ArrayList<>());
            
            if (!results.isEmpty()) {
                Document result = results.get(0);
                return result.getLong("_id");
            }
        } catch (Exception e) {
            logger.error("Error finding default guild ID", e);
        }
        
        // Default fallback value
        return 123456789L; // This should be replaced with an actual guild ID in production
    }
}