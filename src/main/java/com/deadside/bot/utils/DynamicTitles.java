package com.deadside.bot.utils;

import java.util.Random;

/**
 * Utility class for generating dynamic titles for embeds
 * This adds variety to the embed titles for a more engaging experience
 */
public class DynamicTitles {
    private static final Random random = new Random();
    
    // Airdrop titles
    private static final String[] AIRDROP_TITLES = {
        "Supply Drop Incoming",
        "Airdrop Alert",
        "Emergency Supplies",
        "Care Package Spotted",
        "Supplies from Above"
    };
    
    // Mission titles
    private static final String[] MISSION_TITLES = {
        "Mission Alert",
        "Priority Target",
        "High Value Objective",
        "Contract Available",
        "Special Assignment"
    };
    
    // Helicrash titles
    private static final String[] HELICRASH_TITLES = {
        "Helicopter Down",
        "Crash Site Located",
        "Wreckage Spotted",
        "Downed Aircraft",
        "Emergency Landing"
    };
    
    // Killfeed titles
    private static final String[] KILLFEED_TITLES = {
        "Elimination Report",
        "Death Notice",
        "Combat Outcome",
        "Battlefield Update",
        "Kill Confirmed"
    };
    
    // Bounty titles
    private static final String[] BOUNTY_TITLES = {
        "Bounty Available",
        "Contract Open",
        "Target Marked",
        "Wanted Notice",
        "High-Value Target"
    };
    
    // Leaderboard titles
    private static final String[] LEADERBOARD_TITLES = {
        "Top Survivors",
        "Hall of Fame",
        "Kill Leaders",
        "Deadliest Players",
        "Top Killers"
    };
    
    // Leaderboard descriptions
    private static final String[] LEADERBOARD_DESCRIPTIONS = {
        "The most feared players in the zone",
        "These survivors have claimed the most lives",
        "Nobody wants to cross paths with these killers",
        "The deadliest marksmen in the wasteland",
        "Their kill counts speak for themselves"
    };
    
    // Suicide titles
    private static final String[] SUICIDE_TITLES = {
        "Self-Elimination",
        "Self-Inflicted",
        "Suicide Reported",
        "Self-Destruction",
        "Unfortunate End"
    };
    
    // Falling titles
    private static final String[] FALLING_TITLES = {
        "Gravity Claims Another",
        "Fatal Fall",
        "Falling Death",
        "Fell to Death",
        "Gravity Check Failed"
    };
    
    /**
     * Get a random airdrop title
     */
    public static String getAirdropTitle() {
        return AIRDROP_TITLES[random.nextInt(AIRDROP_TITLES.length)];
    }
    
    /**
     * Get a random mission title
     */
    public static String getMissionTitle() {
        return MISSION_TITLES[random.nextInt(MISSION_TITLES.length)];
    }
    
    /**
     * Get a random helicrash title
     */
    public static String getHelicrashTitle() {
        return HELICRASH_TITLES[random.nextInt(HELICRASH_TITLES.length)];
    }
    
    /**
     * Get a random killfeed title
     */
    public static String getKillfeedTitle() {
        return KILLFEED_TITLES[random.nextInt(KILLFEED_TITLES.length)];
    }
    
    /**
     * Get a random bounty title
     */
    public static String getBountyTitle() {
        return BOUNTY_TITLES[random.nextInt(BOUNTY_TITLES.length)];
    }
    
    /**
     * Get a killfeed description for a kill event
     */
    public static String getKillfeedDescription(String killer, String victim, String weapon, int distance) {
        String[] formats = {
            "**%s** eliminated **%s** with %s from %d meters",
            "**%s** took down **%s** using %s (%d meters)",
            "**%s** defeated **%s** with %s at %d meters",
            "**%s** claimed **%s** using %s from a distance of %d meters"
        };
        
        String format = formats[random.nextInt(formats.length)];
        return String.format(format, killer, victim, weapon, distance);
    }
    
    /**
     * Get a random leaderboard title
     */
    public static String getLeaderboardTitle() {
        return LEADERBOARD_TITLES[random.nextInt(LEADERBOARD_TITLES.length)];
    }
    
    /**
     * Get a random leaderboard description
     */
    public static String getLeaderboardDescription() {
        return LEADERBOARD_DESCRIPTIONS[random.nextInt(LEADERBOARD_DESCRIPTIONS.length)];
    }
    
    /**
     * Get a random suicide title
     */
    public static String getSuicideTitle() {
        return SUICIDE_TITLES[random.nextInt(SUICIDE_TITLES.length)];
    }
    
    /**
     * Get a suicide description for a player
     */
    public static String getSuicideDescription(String playerName) {
        String[] formats = {
            "**%s** died by their own hand",
            "**%s** couldn't take it anymore",
            "**%s** eliminated themselves",
            "**%s** chose a permanent end"
        };
        
        String format = formats[random.nextInt(formats.length)];
        return String.format(format, playerName);
    }
    
    /**
     * Get a random falling title
     */
    public static String getFallingTitle() {
        return FALLING_TITLES[random.nextInt(FALLING_TITLES.length)];
    }
    
    /**
     * Get a falling description for a player and height
     */
    public static String getFallingDescription(String playerName, int height) {
        String[] formats = {
            "**%s** fell %d meters to their death",
            "**%s** didn't survive a %d meter drop",
            "**%s** tested gravity from %d meters up",
            "**%s** attempted flying and failed from %d meters"
        };
        
        String format = formats[random.nextInt(formats.length)];
        return String.format(format, playerName, height);
    }
}