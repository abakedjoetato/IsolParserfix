package com.deadside.bot;

import com.deadside.bot.bot.DeadsideBot;
import com.deadside.bot.utils.ResourceManager;
import com.deadside.bot.db.MongoDBConnection;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for the Deadside Discord Bot
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    
    public static void main(String[] args) {
        try {
            LOGGER.info("Starting Deadside Discord Bot...");
            
            // Get Discord token from environment variable
            String discordToken = System.getenv("DISCORD_TOKEN");
            // Check for BOT_TOKEN if DISCORD_TOKEN is not set
            if (discordToken == null || discordToken.isEmpty()) {
                discordToken = System.getenv("BOT_TOKEN");
                if (discordToken == null || discordToken.isEmpty()) {
                    throw new IllegalStateException("Neither DISCORD_TOKEN nor BOT_TOKEN environment variable is set");
                }
            }
            
            // Get MongoDB URI from environment variable
            String mongoUri = System.getenv("MONGO_URI");
            if (mongoUri == null || mongoUri.isEmpty()) {
                throw new IllegalStateException("MONGO_URI environment variable not set");
            }
            
            // Initialize MongoDB connection
            LOGGER.info("Initializing MongoDB connection...");
            MongoDBConnection.initialize(mongoUri);
            
            // Start the bot
            DeadsideBot bot = new DeadsideBot(discordToken);
            bot.start();
            
            LOGGER.info("Bot started successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start bot", e);
            e.printStackTrace();
        }
    }
}