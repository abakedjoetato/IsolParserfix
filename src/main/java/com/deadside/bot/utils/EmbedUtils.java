package com.deadside.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;

/**
 * Utility class for creating modern Discord message embeds
 * Uses Deadside-themed emerald color palette with post-apocalyptic styling
 */
public class EmbedUtils {
    // Deadside themed color palette - Updated for modern design standards
    public static final Color EMERALD_GREEN = new Color(80, 200, 120);      // Primary emerald green (#50C878)
    public static final Color DARK_GRAY = new Color(45, 45, 45);           // Dark background
    public static final Color DARKER_GRAY = new Color(25, 25, 25);         // Even darker background
    public static final Color RUST_ACCENT = new Color(168, 85, 62);         // Rust accent
    public static final Color STEEL_BLUE = new Color(70, 97, 124);          // Steel blue accent
    
    // Standard colors for different embed types
    private static final Color SUCCESS_COLOR = EMERALD_GREEN;               // Success green
    private static final Color ERROR_COLOR = new Color(189, 59, 59);        // Desaturated error red
    private static final Color INFO_COLOR = STEEL_BLUE;                     // Info steel blue
    private static final Color WARNING_COLOR = new Color(209, 139, 71);     // Desaturated warning orange
    
    // Standard images/logos - Using ResourceManager for transparent PNG thumbnails
    public static final String MAIN_LOGO = ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO);
    public static final String KILLFEED_ICON = ResourceManager.getAttachmentString(ResourceManager.KILLFEED_ICON);
    public static final String BOUNTY_ICON = ResourceManager.getAttachmentString(ResourceManager.BOUNTY_ICON);
    public static final String MISSION_ICON = ResourceManager.getAttachmentString(ResourceManager.MISSION_ICON);
    public static final String FACTION_ICON = ResourceManager.getAttachmentString(ResourceManager.FACTION_ICON);
    public static final String AIRDROP_ICON = ResourceManager.getAttachmentString(ResourceManager.AIRDROP_ICON);
    public static final String TRADER_ICON = ResourceManager.getAttachmentString(ResourceManager.TRADER_ICON);
    public static final String CONNECTIONS_ICON = ResourceManager.getAttachmentString(ResourceManager.CONNECTIONS_ICON);
    public static final String WEAPON_STATS_ICON = ResourceManager.getAttachmentString(ResourceManager.WEAPON_STATS_ICON);
    public static final String HELICRASH_ICON = ResourceManager.getAttachmentString(ResourceManager.HELICRASH_ICON);
    
    // Standard footer text
    public static final String STANDARD_FOOTER = "Powered By Discord.gg/EmeraldServers";
    
    // Random generator for dynamic titles and messages
    private static final Random random = new Random();
    
    /**
     * Create a modern success embed with Deadside themed styling
     */
    public static MessageEmbed successEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(SUCCESS_COLOR)
                .setThumbnail(MAIN_LOGO)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern error embed with Deadside themed styling
     */
    public static MessageEmbed errorEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(ERROR_COLOR)
                .setThumbnail(MAIN_LOGO)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern info embed with Deadside themed styling
     */
    public static MessageEmbed infoEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(INFO_COLOR)
                .setThumbnail(CONNECTIONS_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern warning embed with Deadside themed styling
     */
    public static MessageEmbed warningEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(WARNING_COLOR)
                .setThumbnail(HELICRASH_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern custom colored embed with Deadside themed styling
     */
    public static MessageEmbed customEmbed(String title, String description, Color color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setThumbnail(MAIN_LOGO)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern custom colored embed with thumbnail
     */
    public static MessageEmbed customEmbedWithThumbnail(String title, String description, 
                                                       Color color, String thumbnailUrl) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setThumbnail(thumbnailUrl)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern embed with standard Deadside styling and custom thumbnail
     * This is an alias for customEmbedWithThumbnail with a simpler name
     */
    public static MessageEmbed createEmbed(String title, String description, 
                                          Color color, String thumbnailUrl) {
        return customEmbedWithThumbnail(title, description, color, thumbnailUrl);
    }
    
    /**
     * Create a modern player stats embed with themed styling
     */
    public static EmbedBuilder playerStatsEmbed(String playerName) {
        return new EmbedBuilder()
                .setTitle("Stats for " + playerName)
                .setColor(EMERALD_GREEN)
                .setThumbnail(WEAPON_STATS_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now());
    }
    
    /**
     * Dynamic title generator for killfeed embeds - Using DynamicTitles class
     */
    private static String getRandomKillfeedTitle() {
        return DynamicTitles.getKillfeedTitle();
    }
    
    /**
     * Dynamic title generator for bounty kill embeds - Using DynamicTitles class
     */
    private static String getRandomBountyTitle() {
        return DynamicTitles.getBountyTitle();
    }
    
    /**
     * Dynamic title generator for suicide embeds - Using DynamicTitles class
     */
    private static String getRandomSuicideTitle() {
        return DynamicTitles.getSuicideTitle();
    }
    
    /**
     * Dynamic title generator for falling death embeds - Using DynamicTitles class
     */
    private static String getRandomFallingTitle() {
        return DynamicTitles.getFallingTitle();
    }
    
    /**
     * Dynamic title generator for leaderboard embeds - Using DynamicTitles class
     */
    private static String getRandomLeaderboardTitle() {
        return DynamicTitles.getLeaderboardTitle();
    }
    
    /**
     * Dynamic description generator for killfeed embeds - Using DynamicTitles class
     */
    private static String getRandomKillfeedDescription(String killer, String victim, String weapon, int distance) {
        return DynamicTitles.getKillfeedDescription(killer, victim, weapon, distance);
    }
    
    /**
     * Create a modern styled killfeed embed with dynamic titles - PHASE 6 enhancement
     * Consider using AdvancedEmbeds.advancedKillfeedEmbed for more premium visuals
     */
    public static MessageEmbed killfeedEmbed(String killer, String victim, String weapon, int distance) {
        // Use the AdvancedEmbeds implementation for premium visuals
        return AdvancedEmbeds.advancedKillfeedEmbed(killer, victim, weapon, distance, false, 0);
    }
    
    /**
     * Create a PvP killfeed embed with advanced themed styling - PHASE 2 enhancement
     * This method is specifically used by historical parser outputs
     */
    public static MessageEmbed pvpKillfeedEmbed(String killer, String victim, String weapon, int distance) {
        // Use the AdvancedEmbeds implementation for premium visuals
        return AdvancedEmbeds.advancedKillfeedEmbed(killer, victim, weapon, distance, false, 0);
    }
    
    /**
     * Create a modern styled bounty killfeed embed - PHASE 6 enhancement 
     * Consider using AdvancedEmbeds.advancedKillfeedEmbed with isBounty=true for more premium visuals
     */
    public static MessageEmbed bountyKillfeedEmbed(String killer, String victim, String weapon, int distance, int bountyAmount) {
        // Use the AdvancedEmbeds implementation for premium visuals
        return AdvancedEmbeds.advancedKillfeedEmbed(killer, victim, weapon, distance, true, 0);
    }
    
    /**
     * Create a modern styled suicide embed - PHASE 5 normalization
     * Consider using AdvancedEmbeds.advancedSuicideEmbed for more premium visuals
     */
    public static MessageEmbed suicideEmbed(String player, String cause) {
        // Use the AdvancedEmbeds implementation for premium visuals
        return AdvancedEmbeds.advancedSuicideEmbed(player, cause);
    }
    
    /**
     * Get the file uploads needed for an embed with thumbnails
     * @param thumbnailNames Names of thumbnail images used in the embed
     * @return Array of FileUpload objects needed for the embed
     */
    public static FileUpload[] getFileUploadsForEmbed(String... thumbnailNames) {
        List<FileUpload> uploads = new ArrayList<>();
        
        for (String name : thumbnailNames) {
            // Extract filename from attachment:// format
            String fileName = name;
            if (name.startsWith("attachment://")) {
                fileName = name.substring("attachment://".length());
            }
            
            FileUpload upload = ResourceManager.getImageAsFileUpload(fileName);
            if (upload != null) {
                uploads.add(upload);
            }
        }
        
        return uploads.toArray(new FileUpload[0]);
    }
    
    /**
     * Create a modern styled falling death embed - PHASE 5 differentiation
     * Consider using AdvancedEmbeds.advancedFallingDeathEmbed for more premium visuals
     */
    public static MessageEmbed fallingDeathEmbed(String player, int height) {
        // Use the AdvancedEmbeds implementation for premium visuals
        return AdvancedEmbeds.advancedFallingDeathEmbed(player, height);
    }
    
    /**
     * Create a modern styled faction embed
     */
    public static MessageEmbed factionEmbed(String title, String description, Color color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color != null ? color : EMERALD_GREEN)
                .setThumbnail(FACTION_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern styled economy embed
     */
    public static MessageEmbed economyEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(EMERALD_GREEN)
                .setThumbnail(TRADER_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern styled premium feature embed
     */
    public static MessageEmbed premiumEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(EMERALD_GREEN)
                .setThumbnail(MAIN_LOGO)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern styled server event embed
     */
    public static MessageEmbed eventEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(EMERALD_GREEN)
                .setThumbnail(MISSION_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern styled airdrop event embed
     */
    public static MessageEmbed airdropEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(STEEL_BLUE)
                .setThumbnail(AIRDROP_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern styled helicopter crash event embed
     */
    public static MessageEmbed helicrashEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(RUST_ACCENT)
                .setThumbnail(HELICRASH_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a modern styled leaderboard embed - PHASE 6 enhancement
     * This returns an EmbedBuilder for further customization
     */
    public static EmbedBuilder leaderboardEmbed() {
        String title = getRandomLeaderboardTitle();
        String description = DynamicTitles.getLeaderboardDescription();
        
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(EMERALD_GREEN)
                .setThumbnail(WEAPON_STATS_ICON)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now());
    }
    
    /**
     * Create a ready-to-use advanced leaderboard with premium styling
     * @param playerData List of player data to include in the leaderboard
     * @param page Current page number (0-based)
     * @param totalPages Total number of pages
     * @return A fully built MessageEmbed for the leaderboard
     */
    public static MessageEmbed advancedLeaderboardEmbed(List<AdvancedEmbeds.PlayerData> playerData, int page, int totalPages) {
        return AdvancedEmbeds.advancedLeaderboardEmbed(null, null, playerData, page, totalPages);
    }
    
    /**
     * Create a modern styled connection log embed
     */
    public static MessageEmbed connectionLogEmbed(String player, boolean isJoining) {
        String title = isJoining ? "Player Connected" : "Player Disconnected";
        
        // Get dynamic description from dynamic thematic messaging system
        List<String> joinDescriptions = Arrays.asList(
            player + " entered the zone",
            player + " has joined the wasteland",
            "A new survivor arrives: " + player,
            player + " has appeared in Deadside",
            "The zone has a new challenger: " + player
        );
        
        List<String> leaveDescriptions = Arrays.asList(
            player + " left the zone",
            player + " has departed from the wasteland",
            player + " vanished into the fog",
            player + " has abandoned their post",
            "One less survivor: " + player + " is gone"
        );
        
        String description = isJoining ? 
                joinDescriptions.get(random.nextInt(joinDescriptions.size())) : 
                leaveDescriptions.get(random.nextInt(leaveDescriptions.size()));
        
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(isJoining ? EMERALD_GREEN : DARK_GRAY)
                .setThumbnail(CONNECTIONS_ICON)
                .addField("Player", player, true)
                .addField("Status", isJoining ? "Online" : "Offline", true)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a default embed with standard Deadside styling (returns a built MessageEmbed)
     */
    public static MessageEmbed createDefaultEmbed() {
        return new EmbedBuilder()
                .setTitle("Deadside Bot")
                .setDescription("Deadside Discord Bot")
                .setColor(EMERALD_GREEN)
                .setThumbnail(MAIN_LOGO)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create a default embed builder with standard Deadside styling
     * @return EmbedBuilder that can be further customized
     */
    public static EmbedBuilder createDefaultEmbedBuilder() {
        return new EmbedBuilder()
                .setColor(EMERALD_GREEN)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now());
    }
}