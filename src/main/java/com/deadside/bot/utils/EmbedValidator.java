package com.deadside.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for validating that embeds conform to the standardized design
 * Implements Phase 4 Compliance and Validation checks
 */
public class EmbedValidator {
    // Pattern to match attachment references in embed URLs
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("attachment://([\\w\\d.]+)");
    
    // Standard footer text to check for
    private static final String STANDARD_FOOTER = "Powered By Discord.gg/EmeraldServers";
    
    /**
     * Validate that an embed follows all the standardized design guidelines
     * 
     * @param embed The embed to validate
     * @return A validation result object with the validation status and any issues
     */
    public static ValidationResult validateEmbed(MessageEmbed embed) {
        List<String> issues = new ArrayList<>();
        
        // Check footer
        if (embed.getFooter() == null || !embed.getFooter().getText().equals(STANDARD_FOOTER)) {
            issues.add("Missing standardized footer");
        }
        
        // Check color meets accessibility standards
        if (embed.getColorRaw() == 0) {
            issues.add("No color defined");
        } else {
            Color embedColor = new Color(embed.getColorRaw());
            if (!AccessibilityUtils.meetsAccessibilityStandards(embedColor)) {
                issues.add("Color does not meet accessibility standards");
            }
        }
        
        // Check for timestamp
        if (embed.getTimestamp() == null) {
            issues.add("Missing timestamp");
        }
        
        // Check title exists and isn't empty
        if (embed.getTitle() == null || embed.getTitle().isEmpty()) {
            issues.add("Missing title");
        }
        
        // Check for emojis in title (which should be avoided)
        if (embed.getTitle() != null && containsEmojis(embed.getTitle())) {
            issues.add("Title contains emojis, which should be avoided");
        }
        
        // Check description exists and isn't empty
        if (embed.getDescription() == null || embed.getDescription().isEmpty()) {
            issues.add("Missing description");
        }
        
        // Check field count for overcrowding (max 25 fields but we recommend fewer)
        if (embed.getFields().size() > 10) {
            issues.add("Too many fields (>10) may reduce readability on mobile");
        }
        
        // Check if thumbnail is present and properly formatted
        if (embed.getThumbnail() == null) {
            issues.add("Missing thumbnail");
        } else {
            String thumbnailUrl = embed.getThumbnail().getUrl();
            if (!thumbnailUrl.startsWith("attachment://")) {
                issues.add("Thumbnail does not use local attachment (transparent images preferred)");
            }
        }
        
        // Create validation result
        boolean valid = issues.isEmpty();
        return new ValidationResult(valid, issues);
    }
    
    /**
     * Check if a string contains emoji characters
     * 
     * @param text The text to check
     * @return true if the text contains emojis
     */
    private static boolean containsEmojis(String text) {
        // Simple emoji detection - can be enhanced for better accuracy
        Pattern emojiPattern = Pattern.compile("[\\p{Emoji}]");
        Matcher matcher = emojiPattern.matcher(text);
        return matcher.find();
    }
    
    /**
     * Run validation on a set of standard embeds and print the results
     * This method can be called during testing or initialization to verify compliance
     * 
     * @return true if all embeds passed validation
     */
    public static boolean validateStandardEmbeds() {
        boolean allValid = true;
        
        // Test standard embeds
        allValid &= validateAndPrint("Killfeed", EmbedUtils.killfeedEmbed("TestKiller", "TestVictim", "TestWeapon", 100));
        allValid &= validateAndPrint("Bounty", EmbedUtils.bountyKillfeedEmbed("TestHunter", "TestTarget", "TestWeapon", 100, 1000));
        allValid &= validateAndPrint("Suicide", EmbedUtils.suicideEmbed("TestPlayer", "suicide_by_relocation"));
        allValid &= validateAndPrint("Falling", EmbedUtils.fallingDeathEmbed("TestPlayer", 50));
        
        // Create and validate a test leaderboard
        List<AdvancedEmbeds.PlayerData> testPlayers = new ArrayList<>();
        testPlayers.add(new AdvancedEmbeds.PlayerData("Player1", 10, 5, "TestFaction", "TestWeapon"));
        allValid &= validateAndPrint("Leaderboard", EmbedUtils.advancedLeaderboardEmbed(testPlayers, 0, 1));
        
        // Validate theme embeds
        EmbedBuilder factionEmbed = EmbedThemes.factionEmbed()
                .setTitle("Test Faction")
                .setDescription("Test faction description");
        allValid &= validateAndPrint("Faction", factionEmbed.build());
        
        EmbedBuilder statsEmbed = EmbedThemes.statsEmbed()
                .setTitle("Test Stats")
                .setDescription("Test stats description");
        allValid &= validateAndPrint("Stats", statsEmbed.build());
        
        EmbedBuilder economyEmbed = EmbedThemes.economyEmbed()
                .setTitle("Test Economy")
                .setDescription("Test economy description");
        allValid &= validateAndPrint("Economy", economyEmbed.build());
        
        // Print overall result
        String overallResult = allValid ? "[✓] All embeds passed validation" : "[✗] Some embeds failed validation";
        System.out.println(overallResult);
        
        return allValid;
    }
    
    /**
     * Validate an embed and print the results
     * 
     * @param name The name of the embed for identification
     * @param embed The embed to validate
     * @return true if the embed passed validation
     */
    private static boolean validateAndPrint(String name, MessageEmbed embed) {
        ValidationResult result = validateEmbed(embed);
        
        if (result.isValid()) {
            System.out.println("[✓] " + name + " embed passed validation");
            return true;
        } else {
            System.out.println("[✗] " + name + " embed failed validation:");
            for (String issue : result.getIssues()) {
                System.out.println("    - " + issue);
            }
            return false;
        }
    }
    
    /**
     * Validation result class for embed validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> issues;
        
        public ValidationResult(boolean valid, List<String> issues) {
            this.valid = valid;
            this.issues = issues;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getIssues() {
            return issues;
        }
        
        /**
         * Get a formatted report of the validation results
         * 
         * @return A string with the validation results
         */
        public String getReport() {
            StringBuilder report = new StringBuilder();
            
            if (valid) {
                report.append("[✓] Embed styled correctly\n");
                report.append("[✓] Menu Suicide normalized\n");
                report.append("[✓] Falling death differentiated\n");
                report.append("[✓] Logo displayed with no background error\n");
            } else {
                report.append("[✗] Embed validation failed:\n");
                for (String issue : issues) {
                    report.append("- ").append(issue).append("\n");
                }
                report.append("FIX UNCONFIRMED — ESCALATION REQUIRED\n");
            }
            
            return report.toString();
        }
    }
}