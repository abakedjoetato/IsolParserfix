package com.deadside.bot.db.models;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Database model for a Deadside player
 */
public class Player {
    @BsonId
    private ObjectId id;
    private String playerId;     // Unique game ID for the player
    private String name;         // Player's in-game name
    private int kills;           // Total kills
    private int deaths;          // Total deaths
    private int suicides;        // Total suicides
    private String mostUsedWeapon; // Most frequently used weapon
    private int mostUsedWeaponKills; // Kills with most frequently used weapon
    private String mostKilledPlayer; // Player this player has killed the most
    private int mostKilledPlayerCount; // Number of times killed the most killed player
    private String killedByMost; // Player that has killed this player the most
    private int killedByMostCount; // Number of times killed by the player that killed the most
    private long lastUpdated;    // Timestamp of last update
    private Currency currency;   // Player's currency and economy data
    private FactionMember factionMember;  // Player's faction membership
    private ObjectId factionId;  // ID of the faction the player belongs to
    private Instant factionJoinDate; // When the player joined the faction
    
    // Critical fields for server isolation
    private long guildId;        // Discord guild ID this player belongs to
    private String serverId;     // Game server ID (name) this player belongs to
    
    // New fields for enhanced statistics
    private int longestKillDistance;  // Distance of player's longest kill in meters
    private String longestKillVictim; // Name of victim of longest kill
    private String longestKillWeapon; // Weapon used for longest kill
    private int currentKillStreak;    // Current kill streak without dying
    private int longestKillStreak;    // Longest kill streak ever achieved
    private Map<String, Integer> weaponKills;  // Map of weapon name to kill count
    private Map<String, Integer> playerMatchups; // Map of player names to kill counts against that player
    
    public Player() {
        // Required for MongoDB POJO codec
        this.currency = new Currency();
        this.factionMember = null;
        this.weaponKills = new HashMap<>();
        this.playerMatchups = new HashMap<>();
        this.longestKillDistance = 0;
        this.longestKillVictim = "";
        this.longestKillWeapon = "";
        this.currentKillStreak = 0;
        this.longestKillStreak = 0;
        this.guildId = 0;
        this.serverId = "";
    }
    
    public Player(String playerId, String name) {
        this.playerId = playerId;
        this.name = name;
        this.kills = 0;
        this.deaths = 0;
        this.suicides = 0;
        this.mostUsedWeapon = "";
        this.mostUsedWeaponKills = 0;
        this.mostKilledPlayer = "";
        this.mostKilledPlayerCount = 0;
        this.killedByMost = "";
        this.killedByMostCount = 0;
        this.lastUpdated = System.currentTimeMillis();
        this.currency = new Currency();
        this.factionMember = null;
        this.weaponKills = new HashMap<>();
        this.playerMatchups = new HashMap<>();
        this.longestKillDistance = 0;
        this.longestKillVictim = "";
        this.longestKillWeapon = "";
        this.currentKillStreak = 0;
        this.longestKillStreak = 0;
        this.guildId = 0;
        this.serverId = "";
    }
    
    public Player(String playerId, String name, long guildId, String serverId) {
        this.playerId = playerId;
        this.name = name;
        this.kills = 0;
        this.deaths = 0;
        this.suicides = 0;
        this.mostUsedWeapon = "";
        this.mostUsedWeaponKills = 0;
        this.mostKilledPlayer = "";
        this.mostKilledPlayerCount = 0;
        this.killedByMost = "";
        this.killedByMostCount = 0;
        this.lastUpdated = System.currentTimeMillis();
        this.currency = new Currency();
        this.factionMember = null;
        this.weaponKills = new HashMap<>();
        this.playerMatchups = new HashMap<>();
        this.longestKillDistance = 0;
        this.longestKillVictim = "";
        this.longestKillWeapon = "";
        this.currentKillStreak = 0;
        this.longestKillStreak = 0;
        this.guildId = guildId;
        this.serverId = serverId;
    }
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getKills() {
        return kills;
    }
    
    public void setKills(int kills) {
        this.kills = kills;
    }
    
    public void addKill() {
        this.kills++;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public int getDeaths() {
        return deaths;
    }
    
    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }
    
    public void addDeath() {
        this.deaths++;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public int getSuicides() {
        return suicides;
    }
    
    public void setSuicides(int suicides) {
        this.suicides = suicides;
    }
    
    public void addSuicide() {
        this.suicides++;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getMostUsedWeapon() {
        return mostUsedWeapon;
    }
    
    public void setMostUsedWeapon(String mostUsedWeapon) {
        this.mostUsedWeapon = mostUsedWeapon;
    }
    
    public int getMostUsedWeaponKills() {
        return mostUsedWeaponKills;
    }
    
    public void setMostUsedWeaponKills(int mostUsedWeaponKills) {
        this.mostUsedWeaponKills = mostUsedWeaponKills;
    }
    
    public String getMostKilledPlayer() {
        return mostKilledPlayer;
    }
    
    public void setMostKilledPlayer(String mostKilledPlayer) {
        this.mostKilledPlayer = mostKilledPlayer;
    }
    
    public int getMostKilledPlayerCount() {
        return mostKilledPlayerCount;
    }
    
    public void setMostKilledPlayerCount(int mostKilledPlayerCount) {
        this.mostKilledPlayerCount = mostKilledPlayerCount;
    }
    
    public String getKilledByMost() {
        return killedByMost;
    }
    
    public void setKilledByMost(String killedByMost) {
        this.killedByMost = killedByMost;
    }
    
    public int getKilledByMostCount() {
        return killedByMostCount;
    }
    
    public void setKilledByMostCount(int killedByMostCount) {
        this.killedByMostCount = killedByMostCount;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    /**
     * Calculate K/D ratio excluding suicides from death count
     * @return K/D ratio as a double
     */
    public double getKdRatio() {
        // Calculate regular deaths (total deaths minus suicides)
        int regularDeaths = deaths - suicides;
        
        if (regularDeaths <= 0) {
            return kills;
        }
        return (double) kills / regularDeaths;
    }
    
    /**
     * Update this player's weapon statistics
     */
    public void updateWeaponStats(String weapon, int killCount) {
        if (killCount > mostUsedWeaponKills) {
            mostUsedWeapon = weapon;
            mostUsedWeaponKills = killCount;
            lastUpdated = System.currentTimeMillis();
        }
    }
    
    /**
     * Update this player's victim statistics
     */
    public void updateVictimStats(String victimName, int killCount) {
        if (killCount > mostKilledPlayerCount) {
            mostKilledPlayer = victimName;
            mostKilledPlayerCount = killCount;
            lastUpdated = System.currentTimeMillis();
        }
    }
    
    /**
     * Update this player's killer statistics
     */
    public void updateKillerStats(String killerName, int deathCount) {
        if (deathCount > killedByMostCount) {
            killedByMost = killerName;
            killedByMostCount = deathCount;
            lastUpdated = System.currentTimeMillis();
        }
    }
    
    /**
     * Get player's currency
     */
    public Currency getCurrency() {
        if (currency == null) {
            currency = new Currency();
        }
        return currency;
    }
    
    /**
     * Set player's currency
     */
    public void setCurrency(Currency currency) {
        this.currency = currency;
    }
    
    /**
     * Get player's faction membership
     */
    public FactionMember getFactionMember() {
        return factionMember;
    }
    
    /**
     * Set player's faction membership
     */
    public void setFactionMember(FactionMember factionMember) {
        this.factionMember = factionMember;
    }
    
    /**
     * Check if player is in a faction
     */
    public boolean isInFaction() {
        return factionMember != null;
    }
    
    /**
     * Add a kill reward (coins and experience)
     */
    public void addKillReward(int coinsAmount, int experienceAmount) {
        // Add coins to the player's wallet
        getCurrency().addCoins(coinsAmount);
        
        // If player is in a faction, add experience
        if (isInFaction()) {
            factionMember.addContributedXp(experienceAmount);
        }
        
        lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get player's score (calculated based on kills, deaths, suicides, etc)
     */
    public int getScore() {
        // If we have an explicit score value, use it
        if (scoreValue > 0) {
            return scoreValue;
        }
        
        // Otherwise calculate based on the formula
        // Calculate regular deaths (excluding suicides)
        int regularDeaths = deaths - suicides;
        
        // Enhanced score calculation formula
        // Kills are worth 10 points
        // Regular deaths deduct 5 points
        // Suicides deduct 3 points (penalize less than being killed by an enemy)
        int score = (kills * 10) - (regularDeaths * 5) - (suicides * 3);
        
        // Ensure score doesn't go below zero
        return Math.max(0, score);
    }
    
    // Score field to store explicit score value when manually set
    private int scoreValue = 0;
    
    // Raw score value used for leaderboards - add getter/setter
    public int getScoreValue() {
        return scoreValue;
    }
    
    public void setScoreValue(int scoreValue) {
        this.scoreValue = scoreValue;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Set player's score directly
     */
    public void setScore(int score) {
        // Store the score in a dedicated field without affecting kill count
        this.scoreValue = score;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get the Deadside ID of the player
     * This is an alias for getPlayerId to maintain consistent naming
     */
    public String getDeadsideId() {
        return playerId;
    }
    
    /**
     * Set the Deadside ID of the player
     * This is an alias for setPlayerId to maintain consistent naming
     */
    public void setDeadsideId(String deadsideId) {
        this.playerId = deadsideId;
    }
    
    /**
     * Get the faction ID
     */
    public ObjectId getFactionId() {
        return factionId;
    }
    
    /**
     * Set the faction ID
     */
    public void setFactionId(ObjectId factionId) {
        this.factionId = factionId;
    }
    
    /**
     * Get the faction join date
     */
    public Instant getFactionJoinDate() {
        return factionJoinDate;
    }
    
    /**
     * Set the faction join date
     */
    public void setFactionJoinDate(Instant factionJoinDate) {
        this.factionJoinDate = factionJoinDate;
    }
    
    /**
     * Check if the player is a faction leader
     */
    public boolean isFactionLeader() {
        return isInFaction() && factionMember != null && factionMember.isLeader();
    }
    
    /**
     * Check if the player is a faction officer
     */
    public boolean isFactionOfficer() {
        return isInFaction() && factionMember != null && factionMember.isOfficer();
    }
    
    /**
     * Set faction leader status directly
     */
    public void setFactionLeader(boolean isLeader) {
        if (this.factionMember == null) {
            this.factionMember = new FactionMember();
        }
        this.factionMember.setLeader(isLeader);
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Set faction officer status directly
     */
    public void setFactionOfficer(boolean isOfficer) {
        if (this.factionMember == null) {
            this.factionMember = new FactionMember();
        }
        this.factionMember.setOfficer(isOfficer);
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get the guild ID this player belongs to
     */
    public long getGuildId() {
        return guildId;
    }
    
    /**
     * Set the guild ID this player belongs to
     */
    public void setGuildId(long guildId) {
        this.guildId = guildId;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get the server ID this player belongs to
     */
    public String getServerId() {
        return serverId;
    }
    
    /**
     * Set the server ID this player belongs to
     */
    public void setServerId(String serverId) {
        this.serverId = serverId;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get the longest kill distance in meters
     */
    public int getLongestKillDistance() {
        return longestKillDistance;
    }
    
    /**
     * Set the longest kill distance
     */
    public void setLongestKillDistance(int longestKillDistance) {
        this.longestKillDistance = longestKillDistance;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Update the longest kill distance if the new distance is greater
     * @param distance The kill distance in meters
     * @param victimName The name of the victim
     * @param weaponName The weapon used for the kill
     * @return true if updated, false if not
     */
    public boolean updateLongestKillDistance(int distance, String victimName, String weaponName) {
        if (distance > this.longestKillDistance) {
            this.longestKillDistance = distance;
            this.longestKillVictim = victimName;
            this.longestKillWeapon = weaponName;
            this.lastUpdated = System.currentTimeMillis();
            return true;
        }
        return false;
    }
    
    /**
     * Get the victim name of the longest kill
     */
    public String getLongestKillVictim() {
        return longestKillVictim;
    }
    
    /**
     * Set the victim name of the longest kill
     */
    public void setLongestKillVictim(String longestKillVictim) {
        this.longestKillVictim = longestKillVictim;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get the weapon used for the longest kill
     */
    public String getLongestKillWeapon() {
        return longestKillWeapon;
    }
    
    /**
     * Set the weapon used for the longest kill
     */
    public void setLongestKillWeapon(String longestKillWeapon) {
        this.longestKillWeapon = longestKillWeapon;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get current kill streak without dying
     */
    public int getCurrentKillStreak() {
        return currentKillStreak;
    }
    
    /**
     * Set current kill streak
     */
    public void setCurrentKillStreak(int currentKillStreak) {
        this.currentKillStreak = currentKillStreak;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Add a kill to the current streak and update longest streak if needed
     */
    public void incrementKillStreak() {
        this.currentKillStreak++;
        
        // Update longest streak if current one is longer
        if (this.currentKillStreak > this.longestKillStreak) {
            this.longestKillStreak = this.currentKillStreak;
        }
        
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Reset kill streak to zero (called when player dies)
     */
    public void resetKillStreak() {
        this.currentKillStreak = 0;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get longest kill streak ever achieved
     */
    public int getLongestKillStreak() {
        return longestKillStreak;
    }
    
    /**
     * Set longest kill streak
     */
    public void setLongestKillStreak(int longestKillStreak) {
        this.longestKillStreak = longestKillStreak;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get weapon kills mapping
     */
    public Map<String, Integer> getWeaponKills() {
        if (weaponKills == null) {
            weaponKills = new HashMap<>();
        }
        return weaponKills;
    }
    
    /**
     * Set weapon kills mapping
     */
    public void setWeaponKills(Map<String, Integer> weaponKills) {
        this.weaponKills = weaponKills;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Add a kill with a specific weapon
     * @param weaponName The weapon used for the kill
     */
    public void addWeaponKill(String weaponName) {
        if (weaponKills == null) {
            weaponKills = new HashMap<>();
        }
        
        int currentCount = weaponKills.getOrDefault(weaponName, 0);
        weaponKills.put(weaponName, currentCount + 1);
        
        // Update most used weapon if this one is now the most used
        if (currentCount + 1 > mostUsedWeaponKills) {
            mostUsedWeapon = weaponName;
            mostUsedWeaponKills = currentCount + 1;
        }
        
        // CRITICAL FIX: Ensure that total kill count is also incremented
        // This ensures leaderboards show correct kill statistics
        this.kills++; 
        
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get player matchups mapping (player names to kill counts)
     */
    public Map<String, Integer> getPlayerMatchups() {
        if (playerMatchups == null) {
            playerMatchups = new HashMap<>();
        }
        return playerMatchups;
    }
    
    /**
     * Set player matchups mapping
     */
    public void setPlayerMatchups(Map<String, Integer> playerMatchups) {
        this.playerMatchups = playerMatchups;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Add a kill against a specific player
     * @param victimName The name of the player killed
     */
    public void addPlayerMatchupKill(String victimName) {
        if (playerMatchups == null) {
            playerMatchups = new HashMap<>();
        }
        
        int currentCount = playerMatchups.getOrDefault(victimName, 0);
        playerMatchups.put(victimName, currentCount + 1);
        
        // Update most killed player if this one is now the most killed
        if (currentCount + 1 > mostKilledPlayerCount) {
            mostKilledPlayer = victimName;
            mostKilledPlayerCount = currentCount + 1;
        }
        
        this.lastUpdated = System.currentTimeMillis();
    }
}