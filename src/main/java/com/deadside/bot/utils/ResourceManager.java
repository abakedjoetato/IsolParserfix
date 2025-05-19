package com.deadside.bot.utils;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Manages resources for the Deadside Bot including logos and images
 */
public class ResourceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);
    private static final Properties properties = new Properties();
    private static boolean initialized = false;
    
    // Paths for the resource folders
    private static final String MAIN_RESOURCES_PATH = "src/main/resources";
    private static final String CLASS_RESOURCES_PATH = "com/deadside/bot/resources";
    
    // Image resources for embeds
    private static final String IMAGES_FOLDER = "/images/";
    
    // Image filenames
    public static final String MAIN_LOGO = "Mainlogo.png";
    public static final String KILLFEED_ICON = "Killfeed.png";
    public static final String BOUNTY_ICON = "Bounty.png";
    public static final String MISSION_ICON = "Mission.png";
    public static final String FACTION_ICON = "Faction.png";
    public static final String AIRDROP_ICON = "Airdrop.png";
    public static final String TRADER_ICON = "Trader.png";
    public static final String CONNECTIONS_ICON = "Connections.png";
    public static final String WEAPON_STATS_ICON = "WeaponStats.png";
    public static final String HELICRASH_ICON = "Helicrash.png";
    
    // Cache for FileUpload objects
    private static final Map<String, FileUpload> fileUploadCache = new HashMap<>();
    
    /**
     * Initialize the ResourceManager
     * This ensures all resources are loaded and ready for use
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            // Try to load from the classpath
            try (InputStream is = ResourceManager.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is != null) {
                    properties.load(is);
                    LOGGER.info("Loaded configuration from classpath");
                    initialized = true;
                }
            }

            // Try to load from the file system if not already loaded
            if (!initialized) {
                Path configPath = Paths.get("config.properties");
                if (Files.exists(configPath)) {
                    try (InputStream is = Files.newInputStream(configPath)) {
                        properties.load(is);
                        LOGGER.info("Loaded configuration from file system");
                        initialized = true;
                    }
                }
            }

            if (!initialized) {
                LOGGER.warn("Could not find config.properties in classpath or file system");
            }
            
            // Pre-load commonly used resources into cache
            preloadResource(MAIN_LOGO);
            preloadResource(KILLFEED_ICON);
            preloadResource(BOUNTY_ICON);
            preloadResource(MISSION_ICON);
            preloadResource(FACTION_ICON);
            preloadResource(HELICRASH_ICON);
            LOGGER.info("ResourceManager initialization complete");
            
        } catch (IOException e) {
            LOGGER.error("Failed to load configuration", e);
        }
    }
    
    /**
     * Preload a resource into the cache
     * @param imageName The name of the image to preload
     */
    private static void preloadResource(String imageName) {
        try {
            FileUpload upload = getImageAsFileUpload(imageName);
            if (upload != null) {
                fileUploadCache.put(imageName, upload);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to preload resource: " + imageName, e);
        }
    }
    
    /**
     * Get a FileUpload for an image resource with enhanced support for Discord file attachments
     * @param imageName The name of the image file
     * @return FileUpload object for the image
     */
    public static FileUpload getImageAsFileUpload(String imageName) {
        try {
            // For attachment:// URLs, extract just the filename
            if (imageName.startsWith("attachment://")) {
                imageName = imageName.substring("attachment://".length());
            }
            
            // Create FileUpload from attached_assets directory (highest priority)
            File attachedFile = new File("attached_assets/" + imageName);
            if (attachedFile.exists() && attachedFile.isFile() && attachedFile.canRead()) {
                LOGGER.debug("Found image in attached_assets: {}", attachedFile.getAbsolutePath());
                return FileUpload.fromData(attachedFile, imageName);
            }
            
            // Try resources/images folder
            File resourcesFile = new File("src/main/resources/images/" + imageName);
            if (resourcesFile.exists() && resourcesFile.isFile() && resourcesFile.canRead()) {
                LOGGER.debug("Found image in resources: {}", resourcesFile.getAbsolutePath());
                return FileUpload.fromData(resourcesFile, imageName);
            }
            
            // Try target/classes/images folder (for compiled resources)
            File targetFile = new File("target/classes/images/" + imageName);
            if (targetFile.exists() && targetFile.isFile() && targetFile.canRead()) {
                LOGGER.debug("Found image in target: {}", targetFile.getAbsolutePath());
                return FileUpload.fromData(targetFile, imageName);
            }
            
            // Try absolute paths for other resources directories
            File[] possibleLocations = {
                // Try various common locations
                new File(System.getProperty("user.dir") + "/resources/images/" + imageName),
                new File(System.getProperty("user.dir") + "/images/" + imageName),
                new File(System.getProperty("user.dir") + "/" + imageName),
                new File("resources/images/" + imageName),
                new File("images/" + imageName)
            };
            
            for (File possibleFile : possibleLocations) {
                if (possibleFile.exists() && possibleFile.isFile() && possibleFile.canRead()) {
                    LOGGER.debug("Found image in alternative location: {}", possibleFile.getAbsolutePath());
                    return FileUpload.fromData(possibleFile, imageName);
                }
            }
            
            // Use classpath resource loading as last resort
            try (InputStream is = ResourceManager.class.getClassLoader().getResourceAsStream("images/" + imageName)) {
                if (is != null) {
                    // Create temporary file from stream
                    Path tempFile = Files.createTempFile("discord-image-", imageName);
                    Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.debug("Created temp file from classpath resource: {}", tempFile);
                    return FileUpload.fromData(tempFile.toFile(), imageName);
                }
            }
            
            // If we reach here, log the failure and return null
            LOGGER.warn("FAILED to find image: {} - Images will not display correctly", imageName);
            return null;
            
        } catch (Exception e) {
            LOGGER.error("Error loading image resource: {}", imageName, e);
            return null;
        }
    }
    
    /**
     * Get an array of FileUploads for the given image names
     * @param imageNames The names of the image files
     * @return Array of FileUpload objects
     */
    public static FileUpload[] getImagesAsFileUploads(String... imageNames) {
        FileUpload[] uploads = new FileUpload[imageNames.length];
        for (int i = 0; i < imageNames.length; i++) {
            uploads[i] = getImageAsFileUpload(imageNames[i]);
        }
        return uploads;
    }
    
    /**
     * Get the attachment syntax for a specific image
     * @param imageName The name of the image file
     * @return The attachment syntax string for use in embeds
     */
    public static String getAttachmentString(String imageName) {
        return "attachment://" + imageName;
    }
    
    /**
     * Gets a configuration property.
     *
     * @param key The property key
     * @return The property value, or null if not found
     */
    public static String getProperty(String key) {
        if (!initialized) {
            initialize();
        }
        return properties.getProperty(key);
    }

    /**
     * Gets a configuration property with a default value.
     *
     * @param key The property key
     * @param defaultValue The default value to return if the property is not found
     * @return The property value, or the default value if not found
     */
    public static String getProperty(String key, String defaultValue) {
        if (!initialized) {
            initialize();
        }
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Gets an image resource as an InputStream.
     *
     * @param imageName The name of the image file
     * @return An InputStream for the image, or null if not found
     */
    public static InputStream getImageResource(String imageName) {
        // Try to load from the classpath
        InputStream is = ResourceManager.class.getClassLoader().getResourceAsStream("images/" + imageName);
        if (is != null) {
            return is;
        }

        // Try to load from the file system
        Path imagePath = Paths.get("src/main/resources/images/" + imageName);
        if (Files.exists(imagePath)) {
            try {
                return Files.newInputStream(imagePath);
            } catch (IOException e) {
                LOGGER.error("Failed to load image from file system: {}", imageName, e);
            }
        }

        LOGGER.warn("Could not find image resource: {}", imageName);
        return null;
    }
}