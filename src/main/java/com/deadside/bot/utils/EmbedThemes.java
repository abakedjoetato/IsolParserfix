package com.deadside.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced themed embed system for Deadside's Discord messages
 * Implements consistent styling across all bot embed outputs
 * Each embed type has specific color coding, icons, and thumbnails
 */
public class EmbedThemes {
    // Deadside themed color palette
    public static final Color DEADSIDE_PRIMARY = new Color(39, 174, 96);      // Primary emerald green
    public static final Color DEADSIDE_SECONDARY = new Color(46, 204, 113);   // Secondary emerald green
    public static final Color DEADSIDE_DARK = new Color(24, 106, 59);         // Darker emerald shade
    public static final Color DEADSIDE_LIGHT = new Color(88, 214, 141);       // Lighter emerald shade
    public static final Color DEADSIDE_ACCENT = new Color(26, 188, 156);      // Accent turquoise
    public static final Color DEADSIDE_SUCCESS = DEADSIDE_SECONDARY;          // Success green
    public static final Color DEADSIDE_ERROR = new Color(231, 76, 60);        // Error red
    public static final Color DEADSIDE_INFO = DEADSIDE_PRIMARY;               // Info emerald
    public static final Color DEADSIDE_WARNING = new Color(243, 156, 18);     // Warning orange
    public static final Color DEADSIDE_KILLFEED = new Color(160, 30, 30);     // Killfeed red
    
    // Additionally maintain compatibility with the color naming scheme used elsewhere
    public static final Color PRIMARY_COLOR = DEADSIDE_PRIMARY;      
    public static final Color SECONDARY_COLOR = DEADSIDE_DARK;    
    public static final Color ACCENT_COLOR = DEADSIDE_LIGHT;     
    
    // Event colors
    public static final Color EVENT_COLOR = new Color(230, 126, 34);       // Orange for events
    public static final Color KILL_COLOR = DEADSIDE_KILLFEED;              // Red for kills
    public static final Color DEATH_COLOR = new Color(149, 165, 166);      // Gray for deaths
    public static final Color JOIN_COLOR = DEADSIDE_SUCCESS;               // Green for joins
    public static final Color LEAVE_COLOR = new Color(155, 89, 182);       // Purple for leaves
    
    // Status colors (maintain compatibility with both naming schemes)
    public static final Color SUCCESS_COLOR = DEADSIDE_SUCCESS;            // Green for success
    public static final Color WARNING_COLOR = DEADSIDE_WARNING;            // Yellow for warnings
    public static final Color ERROR_COLOR = DEADSIDE_ERROR;                // Red for errors
    public static final Color INFO_COLOR = DEADSIDE_INFO;                  // Blue for info
    
    // Standard footer text
    private static final String DEFAULT_FOOTER = "Powered by Discord.gg/EmeraldServers";
    
    // Logo attachments - Using ResourceManager for transparent PNG attachments
    private static final String MAIN_LOGO = ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO);
    private static final String KILLFEED_LOGO = ResourceManager.getAttachmentString(ResourceManager.KILLFEED_ICON);
    private static final String MISSION_LOGO = ResourceManager.getAttachmentString(ResourceManager.MISSION_ICON);
    private static final String BOUNTY_LOGO = ResourceManager.getAttachmentString(ResourceManager.BOUNTY_ICON);
    private static final String HELICRASH_LOGO = ResourceManager.getAttachmentString(ResourceManager.HELICRASH_ICON);
    private static final String TRADER_LOGO = ResourceManager.getAttachmentString(ResourceManager.TRADER_ICON);
    private static final String FACTION_LOGO = ResourceManager.getAttachmentString(ResourceManager.FACTION_ICON);
    private static final String CONNECTIONS_LOGO = ResourceManager.getAttachmentString(ResourceManager.CONNECTIONS_ICON);
    
    /**
     * Create a base themed embed with Deadside style
     */
    public static EmbedBuilder baseEmbed() {
        return new EmbedBuilder()
                .setColor(DEADSIDE_PRIMARY)
                .setTimestamp(Instant.now())
                .setFooter(DEFAULT_FOOTER);
    }
    
    /**
     * Create an event embed (mission, airdrop, etc.)
     */
    public static MessageEmbed eventEmbed(String title, String description) {
        return baseEmbed()
                .setColor(EVENT_COLOR)
                .setTitle(title)
                .setDescription(description)
                .setThumbnail(MISSION_LOGO)
                .build();
    }
    
    /**
     * Create a kill event embed
     */
    public static MessageEmbed killEmbed(String killer, String victim, String weapon, int distance) {
        return baseEmbed()
                .setColor(KILL_COLOR)
                .setTitle("Kill Feed")
                .setDescription(String.format("**%s** killed **%s**\nWeapon: **%s**\nDistance: **%dm**", 
                        killer, victim, weapon, distance))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.KILLFEED_ICON))
                .build();
    }
    
    /**
     * Create a death event embed (suicide or environment death)
     */
    public static MessageEmbed deathEmbed(String victim, String cause) {
        return baseEmbed()
                .setColor(DEATH_COLOR)
                .setTitle("Death Feed")
                .setDescription(String.format("**%s** died from **%s**", victim, cause))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.HELICRASH_ICON))
                .build();
    }
    
    /**
     * Create a player join embed
     */
    public static MessageEmbed joinEmbed(String playerName) {
        return baseEmbed()
                .setColor(JOIN_COLOR)
                .setTitle("Player Joined")
                .setDescription(String.format("**%s** joined the server", playerName))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.CONNECTIONS_ICON))
                .build();
    }
    
    /**
     * Create a player leave embed
     */
    public static MessageEmbed leaveEmbed(String playerName) {
        return baseEmbed()
                .setColor(LEAVE_COLOR)
                .setTitle("Player Left")
                .setDescription(String.format("**%s** left the server", playerName))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.CONNECTIONS_ICON))
                .build();
    }
    
    /**
     * Create a success embed
     */
    public static MessageEmbed successEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_SUCCESS)
                .setTitle("✅ " + title)
                .setDescription(description)
                .setThumbnail(MISSION_LOGO)
                .build();
    }
    
    /**
     * Create a warning embed
     */
    public static MessageEmbed warningEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_WARNING)
                .setTitle("⚠️ " + title)
                .setDescription(description)
                .setThumbnail(BOUNTY_LOGO)
                .build();
    }
    
    /**
     * Create an error embed
     */
    public static MessageEmbed errorEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_ERROR)
                .setTitle("❌ " + title)
                .setDescription(description)
                .setThumbnail(HELICRASH_LOGO)
                .build();
    }
    
    /**
     * Create an info embed
     */
    public static MessageEmbed infoEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_INFO)
                .setTitle("ℹ️ " + title)
                .setDescription(description)
                .setThumbnail(CONNECTIONS_LOGO)
                .build();
    }
    
    // Note: historicalDataEmbed method is already defined below
    
    /**
     * Create a faction embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder factionEmbed() {
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(SECONDARY_COLOR))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.FACTION_ICON));
    }
    
    /**
     * Create a stats embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder statsEmbed() {
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(ACCENT_COLOR))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.WEAPON_STATS_ICON));
    }
    
    /**
     * Create an economy embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder economyEmbed() {
        Color goldColor = new Color(241, 196, 15); // Gold color for economy
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(goldColor))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.TRADER_ICON));
    }
    
    /**
     * Create a premium embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder premiumEmbed() {
        Color purpleColor = new Color(156, 89, 182); // Purple color for premium
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(purpleColor))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
    }
    
    /**
     * Create a server embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder serverEmbed() {
        Color blueColor = new Color(52, 152, 219); // Blue color for server info
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(blueColor))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.CONNECTIONS_ICON));
    }
    
    /**
     * Create an airdrop embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder airdropEmbed() {
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(EVENT_COLOR))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.AIRDROP_ICON))
                .setTitle(DynamicTitles.getAirdropTitle());
    }
    
    /**
     * Create a mission embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder missionEmbed() {
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(EVENT_COLOR))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MISSION_ICON))
                .setTitle(DynamicTitles.getMissionTitle());
    }
    
    /**
     * Create a helicrash embed with Phase 3 structure enhancements
     */
    public static EmbedBuilder helicrashEmbed() {
        return baseEmbed()
                .setColor(AccessibilityUtils.getAccessibleColor(ERROR_COLOR))
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.HELICRASH_ICON))
                .setTitle(DynamicTitles.getHelicrashTitle());
    }
    
    /**
     * Create a bounty embed with consistent Phase 3 styling
     */
    public static MessageEmbed bountyEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_WARNING)
                .setTitle("💰 " + title)
                .setDescription(description)
                .setThumbnail(BOUNTY_LOGO)
                .build();
    }
    
    /**
     * Create a killfeed embed for historical parser
     */
    public static MessageEmbed killfeedEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_KILLFEED)
                .setTitle("☠️ " + title)
                .setDescription(description)
                .setThumbnail(KILLFEED_LOGO)
                .build();
    }
    
    /**
     * Create a historical data processing embed with progress stats
     */
    public static MessageEmbed historicalDataEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_SUCCESS)
                .setTitle("📊 " + title)
                .setDescription(description)
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.KILLFEED_ICON))
                .setFooter("Historical data processing complete", ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO))
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a historical processing progress embed
     */
    public static MessageEmbed progressEmbed(String title, String description) {
        return baseEmbed()
                .setColor(DEADSIDE_INFO)
                .setTitle("🔄 " + title)
                .setDescription(description)
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.KILLFEED_ICON))
                .setFooter("Historical data processing in progress...", ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO))
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create an isolation-aware leaderboard embed with proper formatting
     * Ensures consistent presentation even with partial or missing data
     * @param title The leaderboard title
     * @param description The leaderboard description or content
     * @param isolationMode The isolation mode setting (read-only, strict, or disabled)
     * @param serverName The name of the game server
     * @return A formatted leaderboard embed with isolation context information
     */
    public static MessageEmbed isolationAwareLeaderboardEmbed(String title, String description, 
                                                          String isolationMode, String serverName) {
        EmbedBuilder embed = baseEmbed()
                .setColor(DEADSIDE_SUCCESS)
                .setTitle("📊 " + title)
                .setDescription(description)
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.WEAPON_STATS_ICON));
        
        // Add isolation context information in the footer
        StringBuilder footerText = new StringBuilder("Data from: ").append(serverName);
        
        // Show isolation mode info with improved formatting for different modes
        if (isolationMode != null) {
            if (isolationMode.equals("Default Server")) {
                footerText.append(" (Default Server)");
            } else if (isolationMode.equals("read-only") || isolationMode.contains("read-only")) {
                footerText.append(" (Read-Only Mode)");
            } else if (isolationMode.equals("disabled") || isolationMode.contains("disabled")) {
                footerText.append(" (Isolation Disabled)");
            } else if (!isolationMode.equals("standard")) {
                footerText.append(" (").append(isolationMode).append(" mode)");
            }
        }
        
        embed.setFooter(footerText.toString(), ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create a fallback embed for missing leaderboard data
     * Used when a server is in read-only mode or data access is limited
     * @param title The leaderboard title
     * @param reason The reason for limited data (read-only mode, isolation setting, etc.)
     * @param serverName The name of the game server
     * @return A formatted embed explaining the data limitation
     */
    public static MessageEmbed fallbackLeaderboardEmbed(String title, String reason, String serverName) {
        StringBuilder description = new StringBuilder();
        
        // Determine if this is a restricted server by examining the reason text
        boolean isRestrictedMode = reason.contains("Default Server") || 
                                  reason.contains("read-only") || 
                                  reason.contains("isolation disabled");
        
        if (isRestrictedMode) {
            // For restricted servers, provide a clearer explanation about the isolation mode
            description.append("**This leaderboard is unavailable due to server configuration.**\n\n");
            description.append("**Details:** ").append(reason).append("\n\n");
            
            // If it's a Default Server, provide specific guidance
            if (reason.contains("Default Server")) {
                description.append("The Default Server is a placeholder and doesn't provide player statistics. ");
                description.append("Configure a game server to enable leaderboards.");
            } else {
                description.append("Current isolation settings prevent displaying statistics. ");
                description.append("An administrator can change this in server settings.");
            }
        } else {
            // For normal data availability issues (no data yet), use the standard message
            description.append("No data is currently available for this leaderboard.\n\n");
            description.append("**Reason:** ").append(reason).append("\n\n");
            description.append("Statistics will appear here as players accumulate game data.");
        }
        
        Color embedColor = isRestrictedMode ? DEADSIDE_INFO : DEADSIDE_WARNING;
        String embedIcon = isRestrictedMode ? "ℹ️" : "⚠️";
        
        return baseEmbed()
                .setColor(embedColor)
                .setTitle(embedIcon + " " + title)
                .setDescription(description.toString())
                .setThumbnail(ResourceManager.getAttachmentString(ResourceManager.WEAPON_STATS_ICON))
                .setFooter("Server: " + serverName, ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO))
                .build();
    }
}