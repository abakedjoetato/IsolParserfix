package com.deadside.bot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for bot settings
 * Dedicated to handling data isolation and cleanup configurations
 */
public class BotConfig {
    private static final Logger logger = LoggerFactory.getLogger(BotConfig.class);
    
    private static BotConfig instance;
    private final Properties properties = new Properties();
    private static final String CONFIG_FILE = "config.properties";
    
    // Config keys
    private static final String RUN_CLEANUP_ON_STARTUP = "run_cleanup_on_startup";
    
    /**
     * Private constructor for singleton pattern
     */
    private BotConfig() {
        loadProperties();
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized BotConfig getInstance() {
        if (instance == null) {
            instance = new BotConfig();
        }
        return instance;
    }
    
    /**
     * Load properties from the config file
     */
    private void loadProperties() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
            logger.info("Loaded configuration from {}", CONFIG_FILE);
        } catch (IOException ex) {
            // If file not found, try to load from classpath
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (input != null) {
                    properties.load(input);
                    logger.info("Loaded configuration from classpath {}", CONFIG_FILE);
                } else {
                    logger.warn("{} not found in classpath, will use default settings", CONFIG_FILE);
                }
            } catch (IOException e) {
                logger.warn("Could not load {}, will use default settings", CONFIG_FILE, e);
            }
        }
    }
    
    /**
     * Save properties to the config file
     */
    public void saveConfig() throws IOException {
        try (FileOutputStream output = new FileOutputStream(CONFIG_FILE)) {
            properties.store(output, "DeadSide Bot Configuration");
            logger.info("Saved configuration to {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save configuration to {}", CONFIG_FILE, e);
            throw e;
        }
    }
    
    /**
     * Get a property with a default value
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Get a property
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Set a property
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    /**
     * Get a boolean property
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    /**
     * Get an integer property
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property {}: {}", key, value);
            return defaultValue;
        }
    }
    
    /**
     * Get a long property
     */
    public long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for property {}: {}", key, value);
            return defaultValue;
        }
    }
}