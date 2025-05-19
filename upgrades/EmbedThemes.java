package com.deadside.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.Instant;

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
    
    // Standard footer text
    private static final String DEFAULT_FOOTER = "Powered by Discord.gg/EmeraldServers";

    /**
     * Create a success themed embed with consistent styling
     */
    public static MessageEmbed successEmbed(String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ " + title)
                .setDescription(description)
                .setColor(DEADSIDE_SUCCESS)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add logo thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create an error themed embed with consistent styling
     */
    public static MessageEmbed errorEmbed(String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("❌ " + title)
                .setDescription(description)
                .setColor(DEADSIDE_ERROR)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add logo thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create an info themed embed with consistent styling
     */
    public static MessageEmbed infoEmbed(String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("ℹ️ " + title)
                .setDescription(description)
                .setColor(DEADSIDE_INFO)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add logo thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create a warning themed embed with consistent styling
     */
    public static MessageEmbed warningEmbed(String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚠️ " + title)
                .setDescription(description)
                .setColor(DEADSIDE_WARNING)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add logo thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create a custom themed embed with consistent styling
     */
    public static MessageEmbed customEmbed(String title, String description, Color color) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add logo thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create a killfeed themed embed with consistent styling
     */
    public static MessageEmbed killfeedEmbed(String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("☠️ " + title)
                .setDescription(description)
                .setColor(DEADSIDE_KILLFEED)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add killfeed specific thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.KILLFEED_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create a PvP killfeed embed with enhanced styling
     */
    public static MessageEmbed pvpKillfeedEmbed(String killer, String victim, String weapon, double distance) {
        String title = "Player Kill";
        String description = String.format("**%s** killed **%s**\nWeapon: **%s**\nDistance: **%.1f meters**", 
                killer, victim, weapon, distance);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("☠️ " + title)
                .setDescription(description)
                .setColor(DEADSIDE_KILLFEED)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add killfeed specific thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.KILLFEED_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create a falling death embed with enhanced styling
     */
    public static MessageEmbed fallingDeathEmbed(String victim, int height) {
        String title = "Falling Death";
        String description = String.format("**%s** died from fall damage", victim);
        if (height > 0) {
            description += String.format("\nFell from **%d meters**", height);
        }
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💀 " + title)
                .setDescription(description)
                .setColor(DEADSIDE_WARNING)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add killfeed specific thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.KILLFEED_LOGO));
        
        return embed.build();
    }
    
    /**
     * Create a suicide embed with enhanced styling
     */
    public static MessageEmbed suicideEmbed(String victim, String cause) {
        String title = "Suicide";
        String description = String.format("**%s** died from %s", victim, cause);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💀 " + title)
                .setDescription(description)
                .setColor(DEADSIDE_WARNING)
                .setFooter(DEFAULT_FOOTER, null)
                .setTimestamp(Instant.now());
        
        // Add killfeed specific thumbnail
        embed.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.KILLFEED_LOGO));
        
        return embed.build();
    }
}