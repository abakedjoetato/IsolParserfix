package com.deadside.bot.premium;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for premium features and subscriptions
 * Handles enabling/disabling premium features based on guild and server subscription status
 */
public class PremiumManager {
    private static final Logger logger = LoggerFactory.getLogger(PremiumManager.class);
    private final GuildConfigRepository guildConfigRepository;
    private final GameServerRepository gameServerRepository;
    
    public PremiumManager() {
        this.guildConfigRepository = new GuildConfigRepository();
        this.gameServerRepository = new GameServerRepository();
    }
    
    /**
     * Check if a guild has premium status
     * @param guildId The Discord guild ID
     * @return True if the guild has an active premium subscription
     * @deprecated Use hasGuildPremium instead, as this doesn't check individual server premium status
     */
    @Deprecated
    public boolean hasPremium(long guildId) {
        return hasGuildPremium(guildId);
    }
    
    /**
     * Check if a guild has premium status (guild-wide premium)
     * @param guildId The Discord guild ID
     * @return True if the guild has an active premium subscription
     */
    public boolean hasGuildPremium(long guildId) {
        try {
            GuildConfig guildConfig = guildConfigRepository.findByGuildId(guildId);
            
            if (guildConfig == null) {
                return false;
            }
            
            // If premiumUntil is null, it means no expiration (indefinite premium)
            if (guildConfig.isPremium() && guildConfig.getPremiumUntil() == null) {
                return true;
            }
            
            // Check if premium is active and not expired
            return guildConfig.isPremiumActive();
        } catch (Exception e) {
            logger.error("Error checking premium status for guild ID: {}", guildId, e);
            return false;
        }
    }
    
    /**
     * Check if a specific game server has premium status
     * @param guildId The Discord guild ID
     * @param serverName The game server name
     * @return True if the server has an active premium subscription
     */
    public boolean hasServerPremium(long guildId, String serverName) {
        try {
            // Check if the specific server has premium - server-specific approach
            GameServer server = gameServerRepository.findByGuildIdAndName(guildId, serverName);
            if (server == null) {
                return false;
            }
            
            // Check if server has premium flag enabled
            if (server.isPremium()) {
                // If server has premium and no expiration time (or unlimited), it's premium
                if (server.getPremiumUntil() == 0) {
                    return true;
                }
                
                // Check if premium is still valid (not expired)
                return server.getPremiumUntil() > System.currentTimeMillis();
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Error checking premium status for server: {} in guild: {}", serverName, guildId, e);
            return false;
        }
    }
    
    /**
     * Count how many premium servers a guild has
     * @param guildId The Discord guild ID
     * @return The number of servers with premium status
     */
    public int countPremiumServers(long guildId) {
        try {
            // We only count individual servers with premium, not guild-wide premium
            int count = 0;
            for (GameServer server : gameServerRepository.findAllByGuildId(guildId)) {
                if (server.isPremium()) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            logger.error("Error counting premium servers for guild ID: {}", guildId, e);
            return 0;
        }
    }
    
    /**
     * Enable premium for a guild
     * @param guildId The Discord guild ID
     * @param durationDays Duration in days (0 for unlimited)
     * @deprecated Use enableGuildPremium instead
     */
    @Deprecated
    public void enablePremium(long guildId, int durationDays) {
        enableGuildPremium(guildId, durationDays);
    }
    
    /**
     * Enable premium for a guild (applies to all servers)
     * @param guildId The Discord guild ID
     * @param durationDays Duration in days (0 for unlimited)
     */
    public void enableGuildPremium(long guildId, int durationDays) {
        try {
            GuildConfig guildConfig = guildConfigRepository.findByGuildId(guildId);
            
            if (guildConfig == null) {
                guildConfig = new GuildConfig(guildId);
            }
            
            guildConfig.setPremium(true);
            
            // Set expiration time if duration is specified
            if (durationDays > 0) {
                guildConfig.setPremiumUntil(durationDays); // Uses our helper method
                logger.info("Premium enabled for guild ID: {} for {} days (until {})", 
                        guildId, durationDays, guildConfig.getPremiumUntil());
            } else {
                guildConfig.setPremiumUntil(null); // No expiration
                logger.info("Premium enabled for guild ID: {} with no expiration", guildId);
            }
            
            guildConfigRepository.save(guildConfig);
        } catch (Exception e) {
            logger.error("Error enabling premium for guild ID: {}", guildId, e);
        }
    }
    
    /**
     * Enable premium for a specific game server
     * @param guildId The Discord guild ID
     * @param serverName The game server name
     * @param durationDays Duration in days (0 for unlimited)
     * @return True if premium was enabled successfully, false otherwise
     */
    public boolean enableServerPremium(long guildId, String serverName, int durationDays) {
        try {
            GameServer server = gameServerRepository.findByGuildIdAndName(guildId, serverName);
            
            if (server == null) {
                logger.error("Cannot enable premium for non-existent server: {} in guild: {}", serverName, guildId);
                return false;
            }
            
            server.setPremium(true);
            
            // Set expiration time if duration is specified
            if (durationDays > 0) {
                long expirationTime = System.currentTimeMillis() + (durationDays * 24L * 60L * 60L * 1000L);
                server.setPremiumUntil(expirationTime);
                logger.info("Premium enabled for server: {} in guild: {} for {} days (until {})", 
                        serverName, guildId, durationDays, new java.util.Date(expirationTime));
            } else {
                server.setPremiumUntil(0); // No expiration
                logger.info("Premium enabled for server: {} in guild: {} with no expiration", serverName, guildId);
            }
            
            gameServerRepository.save(server);
            return true;
        } catch (Exception e) {
            logger.error("Error enabling premium for server: {} in guild: {}", serverName, guildId, e);
            return false;
        }
    }
    
    /**
     * Disable premium for a guild
     * @param guildId The Discord guild ID
     * @deprecated Use disableGuildPremium instead
     */
    @Deprecated
    public void disablePremium(long guildId) {
        disableGuildPremium(guildId);
    }
    
    /**
     * Disable premium for a guild (applies to all servers)
     * @param guildId The Discord guild ID
     */
    public void disableGuildPremium(long guildId) {
        try {
            GuildConfig guildConfig = guildConfigRepository.findByGuildId(guildId);
            
            if (guildConfig != null) {
                guildConfig.setPremium(false);
                guildConfig.setPremiumUntil(0);
                guildConfigRepository.save(guildConfig);
                logger.info("Premium disabled for guild ID: {}", guildId);
            }
        } catch (Exception e) {
            logger.error("Error disabling premium for guild ID: {}", guildId, e);
        }
    }
    
    /**
     * Disable premium for a specific game server
     * @param guildId The Discord guild ID
     * @param serverName The game server name
     * @return True if premium was disabled successfully, false otherwise
     */
    public boolean disableServerPremium(long guildId, String serverName) {
        try {
            GameServer server = gameServerRepository.findByGuildIdAndName(guildId, serverName);
            
            if (server == null) {
                logger.error("Cannot disable premium for non-existent server: {} in guild: {}", serverName, guildId);
                return false;
            }
            
            server.setPremium(false);
            server.setPremiumUntil(0);
            gameServerRepository.save(server);
            
            logger.info("Premium disabled for server: {} in guild: {}", serverName, guildId);
            return true;
        } catch (Exception e) {
            logger.error("Error disabling premium for server: {} in guild: {}", serverName, guildId, e);
            return false;
        }
    }
    
    /**
     * Get the number of available premium slots for a guild
     * @param guildId The Discord guild ID
     * @return The number of premium slots available (1 by default for the free slot)
     */
    public int getAvailablePremiumSlots(long guildId) {
        try {
            // If the guild has guild-wide premium, return a large number (unlimited)
            if (hasGuildPremium(guildId)) {
                return 999;
            }
            
            // Count the number of premium payments made (one server per payment)
            // For now, using a simple count based on configuration - 1 premium payment = 1 premium slot
            // In a real implementation, this would check a payments database or payment history
            
            GuildConfig guildConfig = guildConfigRepository.findByGuildId(guildId);
            if (guildConfig == null) {
                return 1; // Default to 1 free slot
            }
            
            // Check if this guild has made premium payments
            // For now, we'll just check the premiumSlots setting that would be set when payments are made
            // This is just a placeholder - in real implementation, this would query payment records
            int premiumSlots = guildConfig.getPremiumSlots();
            
            // Always provide at least 1 slot (the free slot)
            return Math.max(1, premiumSlots);
        } catch (Exception e) {
            logger.error("Error getting available premium slots for guild ID: {}", guildId, e);
            return 1; // Default to 1 free slot if an error occurs
        }
    }
    
    /**
     * Check and update expired premium subscriptions
     */
    public void checkExpiredSubscriptions() {
        try {
            logger.debug("Checking for expired premium subscriptions");
            
            // Find all premium guilds
            for (GuildConfig config : guildConfigRepository.findAllPremium()) {
                // Skip configs with no expiration
                if (config.getPremiumUntil() == null) {
                    continue;
                }
                
                if (config.getPremiumUntil().getTime() < System.currentTimeMillis()) {
                    // Premium has expired
                    config.setPremium(false);
                    guildConfigRepository.save(config);
                    
                    logger.info("Premium subscription expired for guild ID: {}", config.getGuildId());
                }
            }
        } catch (Exception e) {
            logger.error("Error checking expired premium subscriptions", e);
        }
    }
    
    /**
     * Get premium status details for a guild
     * @param guildId The Discord guild ID
     * @return A string with premium status information
     */
    public String getPremiumStatusDetails(long guildId) {
        try {
            GuildConfig guildConfig = guildConfigRepository.findByGuildId(guildId);
            
            if (guildConfig == null || !guildConfig.isPremium()) {
                return "No active premium subscription";
            }
            
            if (guildConfig.getPremiumUntil() == null) {
                return "Premium subscription active (no expiration)";
            } else {
                long remaining = guildConfig.getPremiumUntil().getTime() - System.currentTimeMillis();
                if (remaining <= 0) {
                    return "Premium subscription expired";
                }
                
                // Calculate days remaining
                long daysRemaining = remaining / (24L * 60L * 60L * 1000L);
                return String.format("Premium subscription active (expires in %d days)", daysRemaining);
            }
        } catch (Exception e) {
            logger.error("Error getting premium status details for guild ID: {}", guildId, e);
            return "Error retrieving premium status";
        }
    }
    
    /**
     * Check if Tip4serv payment should be verified
     * Integrates with Tip4serv API to verify premium payments
     * @param guildId The Discord guild ID
     * @param userId The Discord user ID
     * @return True if payment is verified
     */
    public boolean verifyTip4servPayment(long guildId, long userId) {
        try {
            logger.info("Verifying Tip4serv payment for guild ID: {} and user ID: {}", guildId, userId);
            
            // Get configuration from environment or config file
            String tip4servApiKey = System.getenv("TIP4SERV_API_KEY");
            if (tip4servApiKey == null || tip4servApiKey.isEmpty()) {
                logger.warn("Tip4serv API key not configured. Payment verification will be skipped.");
                return false;
            }
            
            // Prepare HTTP client for Tip4serv API call
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
            String apiUrl = "https://tip4serv.com/api/v2/payment/verify";
            
            // Create JSON payload for API request
            String jsonPayload = String.format(
                "{\"api_key\": \"%s\", \"guild_id\": \"%d\", \"user_id\": \"%d\"}", 
                tip4servApiKey, guildId, userId
            );
            
            // Build the request
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
                
            // Send the request and process the response
            java.net.http.HttpResponse<String> response = client.send(
                request, java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            // Check response status
            if (response.statusCode() == 200) {
                // Parse JSON response
                // For production use, use a proper JSON parser like Jackson or Gson
                String responseBody = response.body();
                
                // Simple check for success in response (in production, use proper JSON parsing)
                if (responseBody.contains("\"success\":true")) {
                    logger.info("Tip4serv payment verified for guild ID: {} and user ID: {}", guildId, userId);
                    
                    // Enable premium for the guild (default 30 days)
                    enablePremium(guildId, 30);
                    return true;
                } else {
                    logger.info("Tip4serv payment verification failed for guild ID: {} and user ID: {}", 
                            guildId, userId);
                    return false;
                }
            } else {
                logger.error("Tip4serv API request failed with status code: {}", response.statusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error verifying Tip4serv payment", e);
            return false;
        }
    }
    
    /**
     * Add a premium slot to a guild
     * @param guildId The Discord guild ID
     * @param durationDays Duration in days (0 for unlimited)
     * @return True if the slot was added successfully
     */
    public boolean addPremiumSlot(long guildId, int durationDays) {
        try {
            GuildConfig guildConfig = guildConfigRepository.findByGuildId(guildId);
            
            if (guildConfig == null) {
                guildConfig = new GuildConfig(guildId);
            }
            
            // Add one premium slot
            guildConfig.addPremiumSlots(1);
            
            logger.info("Added premium slot to guild ID: {}, now has {} slots", 
                    guildId, guildConfig.getPremiumSlots());
            
            guildConfigRepository.save(guildConfig);
            return true;
        } catch (Exception e) {
            logger.error("Error adding premium slot for guild ID: {}", guildId, e);
            return false;
        }
    }
    
    /**
     * Process a webhook from Tip4serv
     * @param webhookData JSON data from Tip4serv webhook
     * @return True if webhook was processed successfully
     */
    public boolean processTip4servWebhook(String webhookData) {
        try {
            logger.info("Processing Tip4serv webhook: {}", webhookData);
            
            // For production, use a proper JSON parser like Jackson or Gson
            // Simple example parsing (would use proper JSON parsing in production)
            if (webhookData.contains("\"event\":\"payment_successful\"")) {
                // Extract guild_id and user_id from webhook data
                // This is simplified - use proper JSON parsing in production
                
                // Extract guild_id using regex
                java.util.regex.Pattern guildPattern = java.util.regex.Pattern.compile("\"guild_id\":\"(\\d+)\"");
                java.util.regex.Matcher guildMatcher = guildPattern.matcher(webhookData);
                
                // Extract user_id using regex
                java.util.regex.Pattern userPattern = java.util.regex.Pattern.compile("\"user_id\":\"(\\d+)\"");
                java.util.regex.Matcher userMatcher = userPattern.matcher(webhookData);
                
                if (guildMatcher.find() && userMatcher.find()) {
                    long guildId = Long.parseLong(guildMatcher.group(1));
                    long userId = Long.parseLong(userMatcher.group(1));
                    
                    logger.info("Payment webhook received for guild ID: {} and user ID: {}", guildId, userId);
                    
                    // Extract duration in days (default to 30 if not found)
                    int durationDays = 30;
                    java.util.regex.Pattern durationPattern = java.util.regex.Pattern.compile("\"duration_days\":(\\d+)");
                    java.util.regex.Matcher durationMatcher = durationPattern.matcher(webhookData);
                    
                    if (durationMatcher.find()) {
                        durationDays = Integer.parseInt(durationMatcher.group(1));
                    }
                    
                    // Check if the webhook contains server_name for per-server premium
                    java.util.regex.Pattern serverNamePattern = java.util.regex.Pattern.compile("\"server_name\":\"([^\"]+)\"");
                    java.util.regex.Matcher serverNameMatcher = serverNamePattern.matcher(webhookData);
                    
                    if (serverNameMatcher.find()) {
                        // This is a server-specific premium purchase
                        String serverName = serverNameMatcher.group(1);
                        logger.info("Server-specific premium purchase for server: {} in guild: {}", serverName, guildId);
                        
                        // First add a premium slot to the guild
                        boolean slotAdded = addPremiumSlot(guildId, durationDays);
                        
                        if (!slotAdded) {
                            logger.error("Failed to add premium slot for guild ID: {}", guildId);
                            return false;
                        }
                        
                        // Enable premium for the specific server
                        return enableServerPremium(guildId, serverName, durationDays);
                    } else {
                        // Just add a premium slot without assigning to a specific server
                        logger.info("Premium slot purchase for guild: {}", guildId);
                        return addPremiumSlot(guildId, durationDays);
                    }
                } else {
                    logger.error("Could not extract guild or user ID from webhook data");
                    return false;
                }
            } else {
                logger.info("Ignoring non-payment webhook event");
                return true;
            }
        } catch (Exception e) {
            logger.error("Error processing Tip4serv webhook", e);
            return false;
        }
    }
}
