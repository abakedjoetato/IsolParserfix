package com.deadside.bot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for the bot
 * Loads settings from config.properties and environment variables
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static Config instance;
    private final Properties properties = new Properties();

    // Config keys
    private static final String DISCORD_TOKEN = "discord.token";
    private static final String MONGO_URI = "mongodb.uri";
    private static final String MONGO_DATABASE = "mongodb.database";
    private static final String BOT_OWNER_ID = "bot.owner.id";
    private static final String HOME_GUILD_ID = "bot.home.guild.id";
    private static final String SFTP_CONNECT_TIMEOUT = "sftp.connect.timeout";
    private static final String KILLFEED_UPDATE_INTERVAL = "killfeed.update.interval";
    private static final String LOG_PARSING_INTERVAL = "log.parsing.interval";
    private static final String ECONOMY_DAILY_AMOUNT = "economy.daily.amount";
    private static final String ECONOMY_WORK_MIN_AMOUNT = "economy.work.min.amount";
    private static final String ECONOMY_WORK_MAX_AMOUNT = "economy.work.max.amount";
    private static final String ECONOMY_KILL_REWARD = "economy.kill.reward";
    private static final String TIP4SERV_API_KEY = "tip4serv.api.key";
    
    // Default values for economy
    private static final long DEFAULT_DAILY_AMOUNT = 1000;
    private static final long DEFAULT_WORK_MIN_AMOUNT = 100;
    private static final long DEFAULT_WORK_MAX_AMOUNT = 500;
    private static final long DEFAULT_KILL_REWARD = 50;
    
    // Admin IDs (could be loaded from config.properties or environment)
    private static final java.util.List<Long> ADMIN_IDS = java.util.Arrays.asList(
            462961235382763520L // Bot owner is also an admin
    );

    private Config() {
        // Load properties from file
        try (InputStream input = new FileInputStream("src/main/resources/config.properties")) {
            properties.load(input);
            logger.info("Loaded configuration from config.properties");
        } catch (IOException ex) {
            // If file not found, try to load from classpath
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (input != null) {
                    properties.load(input);
                    logger.info("Loaded configuration from classpath config.properties");
                } else {
                    logger.warn("config.properties not found in classpath, will use environment variables only");
                }
            } catch (IOException e) {
                logger.warn("Could not load config.properties, will use environment variables only", e);
            }
        }
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    /**
     * Get a property from environment variable first, then from properties file
     */
    public String getProperty(String key, String defaultValue) {
        // Environment variables use uppercase with underscores
        String envKey = key.toUpperCase().replace('.', '_');
        String value = System.getenv(envKey);
        
        if (value == null || value.isEmpty()) {
            value = properties.getProperty(key, defaultValue);
        }
        
        return value;
    }

    public String getDiscordToken() {
        // First try directly with BOT_TOKEN environment variable
        String token = System.getenv("BOT_TOKEN");
        
        // If not found, fallback to the regular property lookup
        if (token == null || token.isEmpty()) {
            token = getProperty(DISCORD_TOKEN, "");
        }
        
        if (token.isEmpty()) {
            logger.error("Discord token not found in configuration");
            throw new IllegalStateException("Discord token must be provided");
        }
        return token;
    }

    public String getMongoUri() {
        // First try directly with MONGO_URI environment variable
        String uri = System.getenv("MONGO_URI");
        
        // If not found, fallback to the regular property lookup
        if (uri == null || uri.isEmpty()) {
            uri = getProperty(MONGO_URI, "mongodb+srv://deadsidebot:${MONGODB_PASSWORD}@deadsidecluster.mongodb.net/deadsidebot?retryWrites=true&w=majority");
        }
        
        return uri;
    }

    public String getMongoDatabase() {
        return getProperty(MONGO_DATABASE, "deadsidebot");
    }

    public String getBotOwnerId() {
        // Hardcoded owner ID as requested, returned as a String for compatibility
        return "462961235382763520";
        
        // Commented out original code for reference
        /*
        String ownerId = getProperty(BOT_OWNER_ID, "0");
        try {
            return 462961235382763520L;
        } catch (NumberFormatException e) {
            logger.warn("Invalid bot owner ID in configuration", e);
            return 0L;
        }
        */
    }

    public long getHomeGuildId() {
        String guildId = getProperty(HOME_GUILD_ID, "0");
        try {
            return Long.parseLong(guildId);
        } catch (NumberFormatException e) {
            logger.warn("Invalid home guild ID in configuration", e);
            return 0L;
        }
    }

    public int getSftpConnectTimeout() {
        String timeout = getProperty(SFTP_CONNECT_TIMEOUT, "30000");
        try {
            return Integer.parseInt(timeout);
        } catch (NumberFormatException e) {
            logger.warn("Invalid SFTP connect timeout in configuration", e);
            return 30000;
        }
    }

    public int getKillfeedUpdateInterval() {
        String interval = getProperty(KILLFEED_UPDATE_INTERVAL, "300"); // Default 5 minutes in seconds
        try {
            return Integer.parseInt(interval);
        } catch (NumberFormatException e) {
            logger.warn("Invalid killfeed update interval in configuration", e);
            return 300;
        }
    }
    
    /**
     * Get the interval for parsing server logs
     * @return The interval in seconds
     */
    public int getLogParsingInterval() {
        String interval = getProperty(LOG_PARSING_INTERVAL, "300"); // Default 5 minutes in seconds
        try {
            return Integer.parseInt(interval);
        } catch (NumberFormatException e) {
            logger.warn("Invalid log parsing interval in configuration", e);
            return 300;
        }
    }
    
    /**
     * Get the daily reward amount
     * @return The amount of coins given as daily reward
     */
    public long getDailyRewardAmount() {
        String amount = getProperty(ECONOMY_DAILY_AMOUNT, String.valueOf(DEFAULT_DAILY_AMOUNT));
        try {
            return Long.parseLong(amount);
        } catch (NumberFormatException e) {
            logger.warn("Invalid daily reward amount in configuration", e);
            return DEFAULT_DAILY_AMOUNT;
        }
    }
    
    /**
     * Set the daily reward amount
     * @param amount The new daily reward amount
     */
    public void setDailyRewardAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Daily reward amount must be positive");
        }
        properties.setProperty(ECONOMY_DAILY_AMOUNT, String.valueOf(amount));
        // Could also save to file here if persistence is desired across restarts
    }
    
    /**
     * Get the minimum work reward amount
     * @return The minimum amount of coins given as work reward
     */
    public long getWorkMinAmount() {
        String amount = getProperty(ECONOMY_WORK_MIN_AMOUNT, String.valueOf(DEFAULT_WORK_MIN_AMOUNT));
        try {
            return Long.parseLong(amount);
        } catch (NumberFormatException e) {
            logger.warn("Invalid minimum work reward amount in configuration", e);
            return DEFAULT_WORK_MIN_AMOUNT;
        }
    }
    
    /**
     * Set the minimum work reward amount
     * @param amount The new minimum work reward amount
     */
    public void setWorkMinAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Minimum work reward amount must be positive");
        }
        properties.setProperty(ECONOMY_WORK_MIN_AMOUNT, String.valueOf(amount));
    }
    
    /**
     * Get the maximum work reward amount
     * @return The maximum amount of coins given as work reward
     */
    public long getWorkMaxAmount() {
        String amount = getProperty(ECONOMY_WORK_MAX_AMOUNT, String.valueOf(DEFAULT_WORK_MAX_AMOUNT));
        try {
            return Long.parseLong(amount);
        } catch (NumberFormatException e) {
            logger.warn("Invalid maximum work reward amount in configuration", e);
            return DEFAULT_WORK_MAX_AMOUNT;
        }
    }
    
    /**
     * Set the maximum work reward amount
     * @param amount The new maximum work reward amount
     */
    public void setWorkMaxAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Maximum work reward amount must be positive");
        }
        properties.setProperty(ECONOMY_WORK_MAX_AMOUNT, String.valueOf(amount));
    }
    
    /**
     * Get the kill reward amount
     * @return The amount of coins given for each kill
     */
    public long getKillRewardAmount() {
        String amount = getProperty(ECONOMY_KILL_REWARD, String.valueOf(DEFAULT_KILL_REWARD));
        try {
            return Long.parseLong(amount);
        } catch (NumberFormatException e) {
            logger.warn("Invalid kill reward amount in configuration", e);
            return DEFAULT_KILL_REWARD;
        }
    }
    
    /**
     * Set the kill reward amount
     * @param amount The new kill reward amount
     */
    public void setKillRewardAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Kill reward amount cannot be negative");
        }
        properties.setProperty(ECONOMY_KILL_REWARD, String.valueOf(amount));
    }
    
    /**
     * Get the list of admin user IDs
     * @return List of admin user IDs
     */
    public java.util.List<Long> getAdminUserIds() {
        return ADMIN_IDS;
    }
    
    /**
     * Get the Tip4Serv API key
     * @return The Tip4Serv API key
     */
    public String getTip4servApiKey() {
        String key = getProperty(TIP4SERV_API_KEY, "");
        if (key.isEmpty()) {
            logger.warn("Tip4Serv API key not found in configuration");
        }
        return key;
    }
    
    /**
     * Get the daily amount (alias for getDailyRewardAmount)
     * @return The daily reward amount
     */
    public long getDailyAmount() {
        return getDailyRewardAmount();
    }
}
