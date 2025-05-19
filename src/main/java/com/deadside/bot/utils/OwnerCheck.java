package com.deadside.bot.utils;

import com.deadside.bot.config.Config;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to check if a user is the bot owner
 */
public class OwnerCheck {
    private static final Logger logger = LoggerFactory.getLogger(OwnerCheck.class);
    
    /**
     * Check if the user executing the command is the bot owner
     * @param event The slash command event
     * @return True if the user is the bot owner, false otherwise
     */
    public static boolean isOwner(SlashCommandInteractionEvent event) {
        try {
            String ownerId = Config.getInstance().getBotOwnerId();
            if (ownerId == null || ownerId.isEmpty()) {
                logger.warn("Bot owner ID is not configured. Using application owner instead.");
                return event.getUser().getId().equals(event.getJDA().retrieveApplicationInfo().complete().getOwner().getId());
            }
            
            return event.getUser().getId().equals(ownerId);
        } catch (Exception e) {
            logger.error("Error checking if user is bot owner", e);
            return false;
        }
    }
    
    /**
     * Check if the specified user ID belongs to the bot owner
     * @param userId The user ID to check
     * @return True if the user ID belongs to the bot owner, false otherwise
     */
    public static boolean isOwner(long userId) {
        try {
            String ownerId = Config.getInstance().getBotOwnerId();
            if (ownerId == null || ownerId.isEmpty()) {
                logger.warn("Bot owner ID is not configured. Cannot verify owner status by ID alone.");
                return false;
            }
            
            // Convert userId to String for comparison with ownerId
            String userIdStr = String.valueOf(userId);
            return userIdStr.equals(ownerId);
        } catch (Exception e) {
            logger.error("Error checking if user ID {} is bot owner", userId, e);
            return false;
        }
    }
}