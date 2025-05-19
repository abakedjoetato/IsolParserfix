package com.deadside.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Advanced embed designs for killfeed and leaderboard displays
 * Implements Phase 6 of the embed modernization with premium-grade UI
 */
public class AdvancedEmbeds {
    private static final Random random = new Random();
    
    // Color scheme for advanced embeds
    private static final Color EMERALD_GREEN = EmbedUtils.EMERALD_GREEN;
    private static final Color DARK_GRAY = EmbedUtils.DARK_GRAY;
    private static final Color GOLD = new Color(212, 175, 55);
    private static final Color SILVER = new Color(192, 192, 192);
    private static final Color BRONZE = new Color(205, 127, 50);
    
    // Standard footer text
    private static final String STANDARD_FOOTER = "Powered By Discord.gg/EmeraldServers";
    
    /**
     * Create a sleek, minimalist killfeed embed matching the Deadside aesthetic
     * Design based on the provided screenshot for consistent styling
     * 
     * @param killer The player who made the kill
     * @param victim The player who was killed
     * @param weapon The weapon used
     * @param distance The distance of the kill in meters
     * @param isBounty Whether this was a bounty kill
     * @param killStreak Optional kill streak count (0 for no streak)
     * @return A visually enhanced embed for the killfeed
     */
    public static MessageEmbed advancedKillfeedEmbed(String killer, String victim, String weapon, 
                                                  int distance, boolean isBounty, int killStreak) {
        // Create random dynamic titles for variety
        String[] killTitles = {
            "SURVIVAL OF THE FITTEST",
            "FATAL CONFRONTATION",
            "DEADSIDE ELIMINATION",
            "WASTELAND JUSTICE",
            "COMBAT REPORT"
        };
        
        // Create random dynamic messages for variety
        String[] killMessages = {
            "No mercy in these badlands.",
            "Another one bites the dust.",
            "The strong survive, the weak perish.",
            "Life is cheap in the zone.",
            "Swift and merciless execution."
        };
        
        // Generate dynamic content
        String title = killTitles[random.nextInt(killTitles.length)];
        String message = killMessages[random.nextInt(killMessages.length)];
        
        // Base color is always emerald green - consistent with screenshot
        Color embedColor = EMERALD_GREEN;
        
        // Format killer and victim lines - KDR in parentheses if available
        StringBuilder description = new StringBuilder();
        
        // Format killer info
        description.append("**").append(killer).append("**");
        if (killStreak > 2) {
            description.append(" (KDR: ").append(String.format("%.2f", 0.5 + (killStreak * 0.1))).append(")");
        } else {
            description.append(" (KDR: 0.00)");
        }
        
        // For bounty or special kill types, add a contextual keyword
        String killType = "";
        if (isBounty) {
            killType = "\nsacked";
        } else if (distance > 200) {
            killType = "\nsniped";
        } else if (distance < 5) {
            killType = "\nfinished";
        }
        
        // Add kill type if applicable
        if (!killType.isEmpty()) {
            description.append(killType);
        }
        
        // Format victim info on separate line
        description.append("\n**").append(victim).append("**");
        description.append(" (KDR: 0.00)");
        
        // Add weapon and distance info
        description.append("\n\nWeapon: ").append(weapon);
        description.append("\nFrom ").append(distance).append(" Meters");
        
        // Add thematic kill message
        description.append("\n\n").append(message);
        
        // Add server info and timestamp similar to screenshot format
        description.append("\n\nServer: Emerald EU | discord.gg/EmeraldServers | ");
        
        // Add timestamp for server-side tracking
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
        String timestamp = java.time.LocalDateTime.now().format(formatter);
        description.append(timestamp);
        
        // Build the minimalist embed with the thumbnail on the right side
        // Explicitly use direct attachment format to ensure proper display
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description.toString())
                .setColor(embedColor)
                .setThumbnail("attachment://Killfeed.png"); // Use direct filename rather than constant
        
        return embed.build();
    }
    
    /**
     * Create an advanced leaderboard embed with pagination support
     * 
     * @param title Optional custom title (or null to use dynamic title)
     * @param description Optional custom description (or null to use dynamic description)
     * @param playerData List of player data objects to display
     * @param page Current page number (0-based)
     * @param totalPages Total number of pages
     * @return A visually enhanced embed for leaderboards
     */
    public static MessageEmbed advancedLeaderboardEmbed(String title, String description,
                                                     List<PlayerData> playerData, int page, int totalPages) {
        // Use dynamic titles and descriptions if not provided
        if (title == null) {
            title = DynamicTitles.getLeaderboardTitle();
        }
        
        if (description == null) {
            description = DynamicTitles.getLeaderboardDescription();
        }
        
        // Add page information to description if multiple pages
        if (totalPages > 1) {
            description += "\nPage " + (page + 1) + " of " + totalPages;
        }
        
        // Build the embed with enhanced visuals
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(EMERALD_GREEN)
                .setThumbnail(EmbedUtils.WEAPON_STATS_ICON);
        
        // Add player entries with rank indicators
        int startRank = page * 10;
        for (int i = 0; i < playerData.size() && i < 10; i++) {
            int rank = startRank + i + 1;
            PlayerData player = playerData.get(i);
            
            // Add rank prefix for top 3 (no emojis)
            String rankPrefix = "";
            if (rank == 1) {
                rankPrefix = "#1 ";
            } else if (rank == 2) {
                rankPrefix = "#2 ";
            } else if (rank == 3) {
                rankPrefix = "#3 ";
            } else {
                rankPrefix = "#" + rank + " ";
            }
            
            // Format the player entry with all stats
            String formattedEntry = rankPrefix + "**" + player.getName() + "**\n" +
                                   "K/D: " + formatKD(player.getKills(), player.getDeaths()) + " • " +
                                   "Kills: " + player.getKills() + " • " +
                                   "Deaths: " + player.getDeaths();
            
            // Add faction info if available
            if (player.getFaction() != null && !player.getFaction().isEmpty()) {
                formattedEntry += "\nFaction: " + player.getFaction();
            }
            
            // Add top weapon if available
            if (player.getTopWeapon() != null && !player.getTopWeapon().isEmpty()) {
                formattedEntry += "\nTop Weapon: " + player.getTopWeapon();
            }
            
            // Add the field with the player's entry
            embed.addField("", formattedEntry, false);
        }
        
        // Add timestamp and footer
        embed.setFooter(STANDARD_FOOTER)
             .setTimestamp(Instant.now());
        
        return embed.build();
    }
    
    /**
     * Create an advanced suicide embed with thematic formatting
     * 
     * @param player The player who died
     * @param cause The cause of death
     * @return A visually enhanced embed for suicide events
     */
    public static MessageEmbed advancedSuicideEmbed(String player, String cause) {
        // Normalize suicide by relocation
        boolean isMenuSuicide = false;
        if (cause.equals("suicide_by_relocation")) {
            cause = "Menu Suicide";
            isMenuSuicide = true;
        } else {
            cause = cause.replace("_", " ");
        }
        
        // Get dynamic title and description
        String title = DynamicTitles.getSuicideTitle();
        String description = isMenuSuicide ? 
            player + " returned to the void (Menu Suicide)" : 
            DynamicTitles.getSuicideDescription(player);
        
        // Build the enhanced embed
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(DARK_GRAY)
                .setThumbnail(EmbedUtils.KILLFEED_ICON) // Use killfeed icon instead of helicrash
                .addField("Player", player, true)
                .addField("Cause", cause, true)
                .addField("Location", "Unknown", true)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create an advanced falling death embed with thematic formatting
     * 
     * @param player The player who died
     * @param height The height of the fall in meters
     * @return A visually enhanced embed for falling death events
     */
    public static MessageEmbed advancedFallingDeathEmbed(String player, int height) {
        // Get dynamic title and description
        String title = DynamicTitles.getFallingTitle();
        String description = DynamicTitles.getFallingDescription(player, height);
        
        // Calculate severity based on height
        String severity = "Fatal";
        if (height > 30) {
            severity = "Catastrophic";
        } else if (height > 20) {
            severity = "Devastating";
        } else if (height > 10) {
            severity = "Severe";
        }
        
        // Build the enhanced embed
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(DARK_GRAY)
                .setThumbnail(EmbedUtils.KILLFEED_ICON) // Use killfeed icon instead of helicrash
                .addField("Player", player, true)
                .addField("Cause", "Falling damage", true)
                .addField("Height", height + "m", true)
                .addField("Severity", severity, true)
                .setFooter(STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Format a K/D ratio with color coding based on performance
     * 
     * @param kills Number of kills
     * @param deaths Number of deaths
     * @return Formatted K/D ratio string
     */
    private static String formatKD(int kills, int deaths) {
        double kd = deaths > 0 ? (double) kills / deaths : kills;
        String kdFormatted = String.format("%.2f", kd);
        
        // Color code based on K/D performance
        if (kd >= 3.0) {
            return "**" + kdFormatted + "**"; // Bold for excellent K/D
        } else if (kd >= 1.5) {
            return kdFormatted; // Normal for good K/D
        } else {
            return kdFormatted; // Normal for average or poor K/D
        }
    }
    
    /**
     * Categorize weapon by type for better icon selection and styling
     * 
     * @param weapon The weapon name to categorize
     * @return A standardized weapon category string
     */
    private static String categorizeWeapon(String weapon) {
        String lowercaseWeapon = weapon.toLowerCase();
        
        if (lowercaseWeapon.contains("sniper") || 
            lowercaseWeapon.contains("mosin") ||
            lowercaseWeapon.contains("svd") ||
            lowercaseWeapon.contains("sr-25")) {
            return "SNIPER";
        } else if (lowercaseWeapon.contains("shotgun") || 
                   lowercaseWeapon.contains("sawed-off") ||
                   lowercaseWeapon.contains("12ga") ||
                   lowercaseWeapon.contains("ks-23")) {
            return "SHOTGUN";
        } else if (lowercaseWeapon.contains("pistol") || 
                   lowercaseWeapon.contains("glock") ||
                   lowercaseWeapon.contains("revolver") ||
                   lowercaseWeapon.contains("deagle")) {
            return "PISTOL";
        } else if (lowercaseWeapon.contains("ak") || 
                   lowercaseWeapon.contains("m4") || 
                   lowercaseWeapon.contains("ka-m")) {
            return "ASSAULT";
        } else if (lowercaseWeapon.contains("mp5") || 
                   lowercaseWeapon.contains("smg")) {
            return "SMG";
        } else if (lowercaseWeapon.contains("knife") || 
                   lowercaseWeapon.contains("axe") || 
                   lowercaseWeapon.contains("machete")) {
            return "MELEE";
        }
        
        return "UNKNOWN";
    }
    
    /**
     * Get a weapon effectiveness rating based on weapon type and distance
     * 
     * @param weapon The weapon used
     * @param distance The distance of the kill
     * @return A rating string or null if no rating
     */
    private static String getWeaponEffectivenessRating(String weapon, int distance) {
        String category = categorizeWeapon(weapon);
        
        if (distance < 10) {
            return "Point Blank";
        } 
        
        switch (category) {
            case "SNIPER":
                if (distance > 300) {
                    return "Long Range Expert";
                } else if (distance > 200) {
                    return "Marksman";
                } else if (distance < 50) {
                    return "CQB Sniper";
                }
                break;
                
            case "SHOTGUN":
                if (distance > 50) {
                    return "Impressive Range";
                } else if (distance > 30) {
                    return "Extended Range";
                } else {
                    return "Effective Range";
                }
                
            case "PISTOL":
                if (distance > 100) {
                    return "Pistol Expert";
                } else if (distance > 50) {
                    return "Skilled Shooter";
                }
                break;
                
            case "ASSAULT":
                if (distance > 200) {
                    return "Precision Rifleman";
                } else if (distance > 100) {
                    return "Effective Range";
                }
                break;
                
            case "SMG":
                if (distance > 100) {
                    return "SMG Expert";
                } else if (distance > 50) {
                    return "Effective Range";
                }
                break;
                
            case "MELEE":
                if (distance > 5) {
                    return "Throwing Master";
                } else {
                    return "Close Combat";
                }
        }
        
        // Default case for medium-range combat
        if (distance > 150 && distance < 300) {
            return "Mid-Range Combat";
        }
        
        return "Standard Engagement";
    }
    
    /**
     * Player data class for leaderboard entries
     */
    public static class PlayerData {
        private final String name;
        private final int kills;
        private final int deaths;
        private final String faction;
        private final String topWeapon;
        
        public PlayerData(String name, int kills, int deaths, String faction, String topWeapon) {
            this.name = name;
            this.kills = kills;
            this.deaths = deaths;
            this.faction = faction;
            this.topWeapon = topWeapon;
        }
        
        public String getName() {
            return name;
        }
        
        public int getKills() {
            return kills;
        }
        
        public int getDeaths() {
            return deaths;
        }
        
        public String getFaction() {
            return faction;
        }
        
        public String getTopWeapon() {
            return topWeapon;
        }
    }
}