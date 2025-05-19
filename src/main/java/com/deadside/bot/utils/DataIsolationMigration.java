package com.deadside.bot.utils;

import com.deadside.bot.db.MongoDBConnection;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to migrate existing data to ensure proper isolation
 * between different Discord servers and game servers
 */
public class DataIsolationMigration {
    private static final Logger logger = LoggerFactory.getLogger(DataIsolationMigration.class);
    
    /**
     * Migrate all collections to ensure proper isolation
     * @return A summary of the migration results
     */
    public String migrateAllData() {
        StringBuilder summary = new StringBuilder("=== Data Isolation Migration Complete ===\n");
        int playerCount = migrateCollection("players");
        int factionCount = migrateCollection("factions");
        int linkedPlayerCount = migrateCollection("linked_players");
        
        summary.append("Players migrated: ").append(playerCount).append("\n");
        summary.append("Factions migrated: ").append(factionCount).append("\n");
        summary.append("Linked players migrated: ").append(linkedPlayerCount).append("\n");
        summary.append("Total records updated: ").append(playerCount + factionCount + linkedPlayerCount);
        
        logger.info("Data isolation migration completed");
        return summary.toString();
    }
    
    /**
     * Migrate a specific collection to ensure proper isolation
     * @param collectionName The name of the collection to migrate
     * @return The number of records migrated
     */
    private int migrateCollection(String collectionName) {
        try {
            MongoCollection<Document> collection = MongoDBConnection.getInstance()
                .getDatabase().getCollection(collectionName);
            
            // Find records with missing or invalid isolation fields
            FindIterable<Document> documentsToMigrate = collection.find(
                Filters.or(
                    Filters.exists("guildId", false),
                    Filters.eq("guildId", null),
                    Filters.eq("guildId", 0),
                    Filters.exists("serverId", false),
                    Filters.eq("serverId", null),
                    Filters.eq("serverId", "")
                )
            );
            
            int count = 0;
            
            // Process each document found
            for (Document doc : documentsToMigrate) {
                // Check if we can find a valid guild/server association
                Document gameServer = findAssociatedGameServer(doc);
                if (gameServer != null) {
                    // Update the document with the correct isolation fields
                    long guildId = gameServer.getLong("guildId");
                    String serverId = gameServer.getString("serverId");
                    
                    collection.updateOne(
                        Filters.eq("_id", doc.getObjectId("_id")),
                        Updates.combine(
                            Updates.set("guildId", guildId),
                            Updates.set("serverId", serverId)
                        )
                    );
                    
                    count++;
                }
            }
            
            logger.info("Updated {} records in collection {}", count, collectionName);
            return count;
        } catch (Exception e) {
            logger.error("Error migrating collection {}", collectionName, e);
            return 0;
        }
    }
    
    /**
     * Find a game server association for a document
     * This tries to associate orphaned records with a valid server
     * @param doc The document to find an association for
     * @return The associated game server document, or null if none found
     */
    private Document findAssociatedGameServer(Document doc) {
        try {
            MongoCollection<Document> serversCollection = MongoDBConnection.getInstance()
                .getDatabase().getCollection("game_servers");
            
            // First, check if there's a serverId field we can use
            if (doc.containsKey("serverId") && doc.get("serverId") != null && !doc.getString("serverId").isEmpty()) {
                String serverId = doc.getString("serverId");
                Document server = serversCollection.find(Filters.eq("serverId", serverId)).first();
                if (server != null) {
                    return server;
                }
            }
            
            // If no server ID, try to find by player name or other identifiers
            if (doc.containsKey("name")) {
                String name = doc.getString("name");
                
                // Try to find in linked_players collection
                MongoCollection<Document> linkedPlayersCollection = MongoDBConnection.getInstance()
                    .getDatabase().getCollection("linked_players");
                
                Document linkedPlayer = linkedPlayersCollection.find(
                    Filters.and(
                        Filters.eq("inGameName", name),
                        Filters.exists("guildId", true),
                        Filters.ne("guildId", 0),
                        Filters.exists("serverId", true),
                        Filters.ne("serverId", "")
                    )
                ).first();
                
                if (linkedPlayer != null) {
                    // Find the associated game server
                    String serverId = linkedPlayer.getString("serverId");
                    Document server = serversCollection.find(Filters.eq("serverId", serverId)).first();
                    if (server != null) {
                        return server;
                    }
                }
            }
            
            // If only one server exists, use that as default
            long serverCount = serversCollection.countDocuments();
            if (serverCount == 1) {
                return serversCollection.find().first();
            }
            
            // Otherwise, couldn't find an association
            return null;
        } catch (Exception e) {
            logger.error("Error finding associated game server", e);
            return null;
        }
    }
}