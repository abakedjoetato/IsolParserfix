package com.deadside.bot.premium;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class that controls access to premium features
 * Ensures proper guild isolation by checking premium status per guild
 */
public class FeatureGate {
    private static final Logger logger = LoggerFactory.getLogger(FeatureGate.class);
    private static final PremiumManager premiumManager = new PremiumManager();
    
    // Define premium features
    public enum Feature {
        KILLFEED(false),            // Free feature
        BASIC_STATS(true),          // Premium feature
        LEADERBOARDS(true),         // Premium feature
        FACTIONS(true),             // Premium feature
        ECONOMY(true),              // Premium feature
        EVENT_NOTIFICATIONS(true),  // Premium feature
        SERVER_MONITORING(true),    // Premium feature
        PLAYER_COUNT(true);         // Premium feature
        
        private final boolean premiumRequired;
        
        Feature(boolean premiumRequired) {
            this.premiumRequired = premiumRequired;
        }
        
        public boolean isPremiumRequired() {
            return premiumRequired;
        }
    }
    
    // Cache feature check results to reduce database queries (refresh every few minutes)
    private static final Map<Long, Map<Feature, Boolean>> featureCache = new HashMap<>();
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static final Map<Long, Long> cacheTimes = new HashMap<>();
    

    
    /**
     * Check if a feature is available for a guild (any server)
     * @param guildId The Discord guild ID
     * @param feature The feature to check
     * @return True if the feature is available
     */
    public static boolean hasAccess(long guildId, Feature feature) {
        try {
            // If feature doesn't require premium, always grant access
            if (!feature.isPremiumRequired()) {
                return true;
            }
            
            // Check cache first
            if (isValidCache(guildId)) {
                Map<Feature, Boolean> guildFeatures = featureCache.get(guildId);
                if (guildFeatures != null && guildFeatures.containsKey(feature)) {
                    return guildFeatures.get(feature);
                }
            }
            
            // For guild-level access, any server with premium grants access
            int premiumServers = premiumManager.countPremiumServers(guildId);
            boolean hasPremium = (premiumServers > 0);
            
            // Update cache
            updateCache(guildId, feature, hasPremium);
            
            return hasPremium;
        } catch (Exception e) {
            logger.error("Error checking feature access for guild ID: {} and feature: {}", 
                    guildId, feature, e);
            return false;
        }
    }
    
    /**
     * Check if a feature is available for a guild
     * @param guild The Discord guild
     * @param feature The feature to check
     * @return True if the feature is available
     */
    public static boolean hasAccess(Guild guild, Feature feature) {
        if (guild == null) {
            return false;
        }
        return hasAccess(guild.getIdLong(), feature);
    }
    
    /**
     * Check if a slash command event has access to a premium feature
     * Replies with a premium upsell message if access is denied
     * @param event The slash command interaction event
     * @param feature The feature to check
     * @return True if the feature is available and execution can continue
     */
    public static boolean checkCommandAccess(SlashCommandInteractionEvent event, Feature feature) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return false;
        }
        
        // Check for server parameter to enforce server-specific premium
        String serverName = null;
        if (event.getOption("server") != null) {
            serverName = event.getOption("server").getAsString();
            
            // For commands with a server parameter, check that specific server's premium status
            if (premiumManager.hasServerPremium(event.getGuild().getIdLong(), serverName)) {
                return true;
            }
        } else {
            // For general guild commands with no server specified,
            // check if the guild has any premium servers at all
            if (hasAccess(event.getGuild().getIdLong(), feature)) {
                return true;
            }
        }
        
        // If this is a premium feature and server doesn't have premium, show upsell message
        if (feature.isPremiumRequired()) {
            String premiumMessage;
            
            if (serverName != null) {
                // For server-specific commands
                premiumMessage = "The `" + event.getCommandString() + "` command requires a premium subscription for server **" + serverName + "**.\n\n" +
                            "Use `/premium assign " + serverName + "` to enable premium for this specific server.";
            } else {
                // For guild-level commands
                premiumMessage = "The `" + event.getCommandString() + "` command requires a premium subscription for at least one server in this Discord.\n\n" +
                            "Use `/premium assign [server]` to enable premium for any server.";
            }
            
            MessageCreateBuilder message = new MessageCreateBuilder()
                .setContent("🔒 **This is a premium feature!**\n\n" + 
                            premiumMessage + "\n\n" +
                            "Premium features include:\n" +
                            "• Advanced statistics and leaderboards\n" +
                            "• Faction system\n" +
                            "• Economy features\n" +
                            "• Real-time event notifications\n" +
                            "• Server monitoring and detailed logs")
                .addActionRow(
                    Button.link("https://emeraldskillfeed.tip4serv.com/", "Get Premium"),
                    Button.primary("premium_info", "Learn More")
                );
                
            event.reply(message.build()).setEphemeral(true).queue();
            
            // Log the access attempt
            logger.info("Premium feature access denied: guild={}, user={}, feature={}", 
                    event.getGuild().getId(), event.getUser().getId(), feature);
            
            return false;
        }
        
        return true;
    }
    
    /**
     * Clear the feature cache for a guild
     * Use this when premium status changes
     * @param guildId The Discord guild ID
     */
    public static void clearCache(long guildId) {
        featureCache.remove(guildId);
        cacheTimes.remove(guildId);
    }
    
    /**
     * Clear all feature caches
     */
    public static void clearAllCaches() {
        featureCache.clear();
        cacheTimes.clear();
    }
    
    /**
     * Check if the cache for a guild is still valid
     */
    private static boolean isValidCache(long guildId) {
        Long cacheTime = cacheTimes.get(guildId);
        if (cacheTime == null) {
            return false;
        }
        
        return (System.currentTimeMillis() - cacheTime) < CACHE_EXPIRY_MS;
    }
    
    /**
     * Update the feature cache for a guild
     */
    private static void updateCache(long guildId, Feature feature, boolean hasAccess) {
        Map<Feature, Boolean> guildFeatures = featureCache.computeIfAbsent(guildId, k -> new HashMap<>());
        guildFeatures.put(feature, hasAccess);
        cacheTimes.put(guildId, System.currentTimeMillis());
    }
}