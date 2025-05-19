package com.deadside.bot.listeners;

import com.deadside.bot.commands.economy.RouletteCommand;
import com.deadside.bot.db.models.Faction;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.FactionRepository;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.FeatureGate;
import com.deadside.bot.premium.PremiumManager;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.GuildIsolationManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for string select menu interactions
 */
public class StringSelectMenuListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(StringSelectMenuListener.class);
    private final RouletteCommand rouletteCommand = new RouletteCommand();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final FactionRepository factionRepository = new FactionRepository();
    private final GameServerRepository gameServerRepository = new GameServerRepository();
    private final PremiumManager premiumManager = new PremiumManager();
    private final GuildIsolationManager guildIsolationManager = GuildIsolationManager.getInstance();
    
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        try {
            // Format: componentId = type:action:userId
            String[] menuData = event.getComponentId().split(":");
            
            if (menuData.length < 2) {
                return;
            }
            
            String menuType = menuData[0];
            
            // Check for premium requirements
            Guild guild = event.getGuild();
            if (guild != null && menuType.startsWith("premium_")) {
                if (!premiumManager.hasPremium(guild.getIdLong())) {
                    event.reply("This feature requires an active premium subscription.").setEphemeral(true).queue();
                    return;
                }
            }
            
            switch (menuType) {
                // Game and economy menus
                case "roulette" -> rouletteCommand.handleSelectMenuInteraction(event, menuData);
                
                // Server and stat menus
                case "server" -> handleServerSelection(event, menuData);
                case "stats" -> handleStatsSelection(event, menuData);
                case "leaderboard" -> handleLeaderboardSelection(event, menuData);
                
                // Faction menus
                case "faction" -> handleFactionSelection(event, menuData);
                case "faction_role" -> handleFactionRoleSelection(event, menuData);
                case "faction_member" -> handleFactionMemberSelection(event, menuData);
                
                // Premium menus
                case "premium_duration" -> handlePremiumDurationSelection(event, menuData);
                case "premium_feature" -> handlePremiumFeatureSelection(event, menuData);
                
                // Settings menus
                case "settings" -> handleSettingsSelection(event, menuData);
                case "channel" -> handleChannelSelection(event, menuData);
                
                default -> {
                    // Unknown menu type
                    logger.warn("Unknown select menu type: {}", menuType);
                    event.reply("This menu type is not yet implemented.").setEphemeral(true).queue();
                }
            }
        } catch (Exception e) {
            logger.error("Error handling select menu interaction", e);
            event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle server selection menu
     */
    private void handleServerSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedServerId = event.getValues().get(0);
        
        try {
            long serverId = Long.parseLong(selectedServerId);
            GameServer server = gameServerRepository.findById(selectedServerId);
            
            if (server == null) {
                event.reply("Server not found. It may have been deleted.").setEphemeral(true).queue();
                return;
            }
            
            // Verify guild isolation
            Guild guild = event.getGuild();
            if (guild == null || server.getGuildId() != guild.getIdLong()) {
                event.reply("Access denied. This server belongs to a different Discord server.").setEphemeral(true).queue();
                return;
            }
            
            event.deferReply().queue();
            
            // Build server information embed
            StringBuilder description = new StringBuilder();
            description.append("**Name**: ").append(server.getName()).append("\n");
            description.append("**Host**: ").append(server.getHost()).append("\n");
            description.append("**Status**: ").append(server.isOnline() ? "🟢 Online" : "🔴 Offline").append("\n\n");
            
            // Add server statistics
            description.append("**Player Count**: ").append(server.getPlayerCount()).append("/").append(server.getMaxPlayers()).append("\n");
            description.append("**Uptime**: ").append(formatDuration(server.getUptime())).append("\n");
            description.append("**Last Updated**: <t:").append(server.getLastUpdated() / 1000).append(":R>\n\n");
            
            // Add server settings
            description.append("**Settings**\n");
            description.append("• Event Notifications: ").append(server.isEventNotificationsEnabled() ? "Enabled" : "Disabled").append("\n");
            description.append("• Killfeed: ").append(server.isKillfeedEnabled() ? "Enabled" : "Disabled").append("\n");
            description.append("• Join/Leave Notifications: ").append(server.isJoinLeaveNotificationsEnabled() ? "Enabled" : "Disabled").append("\n");
            
            // Send the message with action buttons
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.customEmbed("Server Information: " + server.getName(), 
                            description.toString(), 
                            EmbedUtils.EMERALD_GREEN)
            ).addActionRow(
                    Button.primary("server:refresh:" + serverId, "Refresh"),
                    Button.secondary("server:edit:" + serverId, "Edit Settings"),
                    Button.danger("server:remove:" + serverId, "Remove Server")
            ).queue();
        } catch (NumberFormatException e) {
            event.reply("Invalid server ID.").setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Error handling server selection", e);
            event.reply("An error occurred while retrieving server information.").setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle stats selection menu
     */
    private void handleStatsSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedType = event.getValues().get(0);
        
        // Check for premium access if needed
        Guild guild = event.getGuild();
        if (guild != null && !FeatureGate.hasAccess(guild.getIdLong(), FeatureGate.Feature.BASIC_STATS)) {
            event.reply("Advanced statistics require a premium subscription.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        // Placeholder for stats display
        // In a full implementation, this would fetch detailed stats based on selection
        event.getHook().sendMessageEmbeds(
                EmbedUtils.infoEmbed("Stats: " + selectedType,
                        "This would display " + selectedType + " statistics in a full implementation.")
        ).queue();
    }
    
    /**
     * Handle leaderboard selection menu
     */
    private void handleLeaderboardSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedType = event.getValues().get(0);
        
        // Check for premium access
        Guild guild = event.getGuild();
        if (guild != null && !FeatureGate.hasAccess(guild.getIdLong(), FeatureGate.Feature.LEADERBOARDS)) {
            event.reply("Leaderboards require a premium subscription.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        // Placeholder for leaderboard display
        // In a full implementation, this would fetch the leaderboard data
        event.getHook().sendMessageEmbeds(
                EmbedUtils.infoEmbed("Leaderboard: " + selectedType,
                        "This would display the " + selectedType + " leaderboard in a full implementation.")
        ).addActionRow(
                Button.primary("leaderboard:refresh:" + selectedType, "Refresh")
        ).queue();
    }
    
    /**
     * Handle faction selection menu
     */
    private void handleFactionSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedFactionId = event.getValues().get(0);
        
        try {
            // Check for premium access
            Guild guild = event.getGuild();
            if (guild != null && !FeatureGate.hasAccess(guild.getIdLong(), FeatureGate.Feature.FACTIONS)) {
                event.reply("Factions require a premium subscription.").setEphemeral(true).queue();
                return;
            }
            
            long factionId = Long.parseLong(selectedFactionId);
            Faction faction = factionRepository.findById(new org.bson.types.ObjectId(selectedFactionId));
            
            if (faction == null) {
                event.reply("Faction not found. It may have been disbanded.").setEphemeral(true).queue();
                return;
            }
            
            // Verify guild isolation
            if (guild == null || faction.getGuildId() != guild.getIdLong()) {
                event.reply("Access denied. This faction belongs to a different Discord server.").setEphemeral(true).queue();
                return;
            }
            
            event.deferReply().queue();
            
            // Show faction details
            // Parse color for embed
            java.awt.Color embedColor;
            try {
                embedColor = java.awt.Color.decode(faction.getColor());
            } catch (NumberFormatException e) {
                embedColor = EmbedUtils.EMERALD_GREEN;
            }
            
            // Build faction info
            StringBuilder description = new StringBuilder();
            description.append("**Tag**: ").append(faction.getTag()).append("\n");
            description.append("**Description**: ").append(faction.getDescription()).append("\n\n");
            
            description.append("**Level**: ").append(faction.getLevel()).append("\n");
            description.append("**Experience**: ").append(faction.getExperience()).append("/")
                    .append(faction.getLevel() * 1000).append("\n");
            description.append("**Members**: ").append(faction.getTotalMemberCount()).append("/")
                    .append(faction.getMaxMembers()).append("\n\n");
            
            description.append("**Owner**: <@").append(faction.getOwnerId()).append(">\n");
            
            if (!faction.getOfficerIds().isEmpty()) {
                description.append("**Officers**:\n");
                for (long officerId : faction.getOfficerIds()) {
                    description.append("• <@").append(officerId).append(">\n");
                }
                description.append("\n");
            }
            
            if (!faction.getMemberIds().isEmpty()) {
                description.append("**Members**:\n");
                for (long memberId : faction.getMemberIds()) {
                    description.append("• <@").append(memberId).append(">\n");
                }
            }
            
            description.append("\n**Created**: <t:").append(faction.getCreated() / 1000).append(":R>");
            
            // Send faction info with action buttons
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.customEmbed("Faction: " + faction.getName(), description.toString(), embedColor)
            ).addActionRow(
                    Button.primary("faction:stats:" + factionId, "View Stats"),
                    Button.secondary("faction:members:" + factionId, "Manage Members"),
                    Button.danger("faction:leave:" + factionId, "Leave Faction")
            ).queue();
        } catch (NumberFormatException e) {
            event.reply("Invalid faction ID.").setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Error handling faction selection", e);
            event.reply("An error occurred while retrieving faction information.").setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle faction role selection menu
     */
    private void handleFactionRoleSelection(StringSelectInteractionEvent event, String[] menuData) {
        // Placeholder for faction role selection
        event.reply("Faction role selection would be implemented here.").setEphemeral(true).queue();
    }
    
    /**
     * Handle faction member selection menu
     */
    private void handleFactionMemberSelection(StringSelectInteractionEvent event, String[] menuData) {
        // Placeholder for faction member selection
        event.reply("Faction member selection would be implemented here.").setEphemeral(true).queue();
    }
    
    /**
     * Handle premium duration selection menu
     */
    private void handlePremiumDurationSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedDuration = event.getValues().get(0);
        
        try {
            int days = Integer.parseInt(selectedDuration);
            double price = calculatePremiumPrice(days);
            
            // Show premium purchase confirmation
            event.reply("You've selected a premium subscription for " + days + " days.\n" +
                    "Price: $" + String.format("%.2f", price) + "\n\n" +
                    "Please use the buttons below to proceed.").setEphemeral(true)
                    .addActionRow(
                            Button.success("premium:purchase:" + days, "Purchase"),
                            Button.secondary("premium:cancel", "Cancel")
                    ).queue();
        } catch (NumberFormatException e) {
            event.reply("Invalid duration.").setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle premium feature selection menu
     */
    private void handlePremiumFeatureSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedFeature = event.getValues().get(0);
        
        // Show detailed information about the selected premium feature
        event.reply("You've selected the " + selectedFeature + " premium feature.\n\n" +
                "This feature would be described in detail here in a full implementation.").setEphemeral(true).queue();
    }
    
    /**
     * Handle settings selection menu
     */
    private void handleSettingsSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedSetting = event.getValues().get(0);
        Guild guild = event.getGuild();
        
        if (guild == null) {
            event.reply("This menu can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply(true).queue(); // Ephemeral response
        
        // Placeholder for settings management
        event.getHook().sendMessageEmbeds(
                EmbedUtils.infoEmbed("Settings: " + selectedSetting,
                        "This would allow configuring the " + selectedSetting + " setting in a full implementation.")
        ).queue();
    }
    
    /**
     * Handle channel selection menu
     */
    private void handleChannelSelection(StringSelectInteractionEvent event, String[] menuData) {
        if (event.getValues().isEmpty()) {
            return;
        }
        
        String selectedChannelId = event.getValues().get(0);
        String channelPurpose = menuData.length > 1 ? menuData[1] : "unknown";
        
        // Store the selected channel for the specified purpose
        event.reply("Channel <#" + selectedChannelId + "> has been set as the " + channelPurpose + " channel.")
                .setEphemeral(true).queue();
    }
    
    /**
     * Calculate premium price based on duration
     * @param days Number of days
     * @return Price in USD
     */
    private double calculatePremiumPrice(int days) {
        double basePrice = 4.99;
        
        // Apply bulk discount
        if (days >= 90) {
            return basePrice * 0.7 * days; // 30% off for 90+ days
        } else if (days >= 30) {
            return basePrice * 0.8 * days; // 20% off for 30+ days
        } else {
            return basePrice * 0.9 * days; // 10% off for multiple days
        }
    }
    
    /**
     * Format a duration in milliseconds to a human-readable string
     * @param millis Duration in milliseconds
     * @return Formatted duration string
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}