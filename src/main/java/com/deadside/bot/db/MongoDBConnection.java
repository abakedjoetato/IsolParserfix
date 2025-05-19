package com.deadside.bot.db;

import com.deadside.bot.config.Config;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.client.model.ReplaceOptions;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Singleton connection to MongoDB
 */
public class MongoDBConnection {
    private static final Logger logger = LoggerFactory.getLogger(MongoDBConnection.class);
    private static MongoDBConnection instance;
    private MongoDatabase database;
    private MongoClient mongoClient;
    
    // Common MongoDB options
    public static final ReplaceOptions UPSERT_OPTION = new ReplaceOptions().upsert(true);

    private MongoDBConnection() {
        // Private constructor for singleton
    }

    /**
     * Initialize the MongoDB connection
     */
    public static synchronized void initialize(String mongoUri) {
        if (instance == null) {
            instance = new MongoDBConnection();
            instance.connect(mongoUri);
        }
    }

    /**
     * Connect to MongoDB
     */
    private void connect(String mongoUri) {
        try {
            if (mongoUri == null || mongoUri.isEmpty()) {
                throw new IllegalStateException("MongoDB URI is not configured. Please set MONGODB_URI environment variable.");
            }

            // Configure codec registry for POJOs
            CodecRegistry pojoCodecRegistry = fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    fromProviders(PojoCodecProvider.builder().automatic(true).build())
            );

            // Configure and create client
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(mongoUri))
                    .codecRegistry(pojoCodecRegistry)
                    .build();

            // Get database name from config
            String databaseName = Config.getInstance().getMongoDatabase();
            
            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(databaseName);
            
            logger.info("Connected to MongoDB database: {}", databaseName);
        } catch (Exception e) {
            logger.error("Failed to initialize MongoDB connection", e);
            throw new RuntimeException("Failed to initialize MongoDB connection", e);
        }
    }

    /**
     * Get the singleton instance
     */
    public static synchronized MongoDBConnection getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MongoDB connection has not been initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Get the database
     */
    public MongoDatabase getDatabase() {
        if (database == null) {
            throw new IllegalStateException("MongoDB database is not available. Connection may not have been initialized.");
        }
        return database;
    }

    /**
     * Close the connection
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("Closed MongoDB connection");
        }
    }
}