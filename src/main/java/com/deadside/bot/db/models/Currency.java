package com.deadside.bot.db.models;

import org.bson.types.ObjectId;

/**
 * Represents a player's currency balance with proper isolation between guilds and servers
 */
public class Currency {
    private ObjectId id;             // MongoDB document ID
    private long userId;             // Discord user ID
    private long guildId;            // Discord guild (server) ID for isolation
    private String serverId;         // Game server ID for isolation
    private long coins;              // Main currency
    private long bankCoins;          // Stored in bank (safe from death)
    private int bountyPoints;        // Special currency earned from completing bounties
    private int prestigePoints;      // Premium currency earned from prestige or purchases
    private long lastDailyReward;    // Timestamp of last daily reward
    private long lastWork;           // Timestamp of last work command
    private long totalEarned;        // Total amount of coins earned (lifetime)
    private long totalSpent;         // Total amount of coins spent (lifetime)
    private long lastUpdated;        // Timestamp of last update
    
    public Currency() {
        // Required for MongoDB POJO codec
        this.coins = 0;
        this.bankCoins = 0;
        this.bountyPoints = 0;
        this.prestigePoints = 0;
        this.lastDailyReward = 0;
        this.lastWork = 0;
        this.totalEarned = 0;
        this.totalSpent = 0;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    // Getters and Setters
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public long getUserId() {
        return userId;
    }
    
    public void setUserId(long userId) {
        this.userId = userId;
    }
    
    public long getGuildId() {
        return guildId;
    }
    
    public void setGuildId(long guildId) {
        this.guildId = guildId;
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    public long getCoins() {
        return coins;
    }
    
    public void setCoins(long coins) {
        this.coins = coins;
    }
    
    public long getBankCoins() {
        return bankCoins;
    }
    
    public void setBankCoins(long bankCoins) {
        this.bankCoins = bankCoins;
    }
    
    public int getBountyPoints() {
        return bountyPoints;
    }
    
    public void setBountyPoints(int bountyPoints) {
        this.bountyPoints = bountyPoints;
    }
    
    public int getPrestigePoints() {
        return prestigePoints;
    }
    
    public void setPrestigePoints(int prestigePoints) {
        this.prestigePoints = prestigePoints;
    }
    
    public long getLastDailyReward() {
        return lastDailyReward;
    }
    
    public void setLastDailyReward(long lastDailyReward) {
        this.lastDailyReward = lastDailyReward;
    }
    
    /**
     * Alias for setLastDailyReward for backward compatibility
     */
    public void setLastDailyClaim(long lastDailyClaimTimestamp) {
        this.lastDailyReward = lastDailyClaimTimestamp;
    }
    
    public long getLastWork() {
        return lastWork;
    }
    
    public void setLastWork(long lastWork) {
        this.lastWork = lastWork;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public long getTotalEarned() {
        return totalEarned;
    }
    
    public void setTotalEarned(long totalEarned) {
        this.totalEarned = totalEarned;
    }
    
    public long getTotalSpent() {
        return totalSpent;
    }
    
    public void setTotalSpent(long totalSpent) {
        this.totalSpent = totalSpent;
    }
    
    // Helper methods
    
    /**
     * Get total balance (wallet + bank)
     */
    public long getTotalBalance() {
        return coins + bankCoins;
    }
    
    /**
     * Add coins to wallet
     */
    public void addCoins(long amount) {
        if (amount <= 0) return;
        
        coins += amount;
        totalEarned += amount;
    }
    
    /**
     * Remove coins from wallet
     */
    public boolean removeCoins(long amount) {
        if (amount <= 0) return true;
        if (coins < amount) return false;
        
        coins -= amount;
        totalSpent += amount;
        return true;
    }
    
    /**
     * Transfer coins from wallet to bank
     */
    public boolean depositToBank(long amount) {
        if (amount <= 0) return true;
        if (coins < amount) return false;
        
        coins -= amount;
        bankCoins += amount;
        return true;
    }
    
    /**
     * Transfer coins from bank to wallet
     */
    public boolean withdrawFromBank(long amount) {
        if (amount <= 0) return true;
        if (bankCoins < amount) return false;
        
        bankCoins -= amount;
        coins += amount;
        return true;
    }
    
    /**
     * Add bounty points
     */
    public void addBountyPoints(int amount) {
        if (amount <= 0) return;
        bountyPoints += amount;
    }
    
    /**
     * Remove bounty points
     */
    public boolean removeBountyPoints(int amount) {
        if (amount <= 0) return true;
        if (bountyPoints < amount) return false;
        
        bountyPoints -= amount;
        return true;
    }
    
    /**
     * Add prestige points
     */
    public void addPrestigePoints(int amount) {
        if (amount <= 0) return;
        prestigePoints += amount;
    }
    
    /**
     * Remove prestige points
     */
    public boolean removePrestigePoints(int amount) {
        if (amount <= 0) return true;
        if (prestigePoints < amount) return false;
        
        prestigePoints -= amount;
        return true;
    }
    
    /**
     * Check if daily reward is available
     */
    public boolean isDailyRewardAvailable() {
        long now = System.currentTimeMillis();
        long oneDayMs = 24 * 60 * 60 * 1000;
        return now - lastDailyReward >= oneDayMs;
    }
    
    /**
     * Claim daily reward
     */
    public boolean claimDailyReward(long rewardAmount) {
        if (!isDailyRewardAvailable()) {
            return false;
        }
        
        addCoins(rewardAmount);
        lastDailyReward = System.currentTimeMillis();
        return true;
    }
    
    /**
     * Calculate streak days
     */
    public int calculateStreak() {
        long now = System.currentTimeMillis();
        long oneDayMs = 24 * 60 * 60 * 1000;
        long twoDaysMs = 2 * oneDayMs;
        
        // If it's been more than 2 days, streak is broken
        if (now - lastDailyReward >= twoDaysMs) {
            return 0;
        }
        
        // Otherwise calculate days since the epoch
        return (int) ((now / oneDayMs) - (lastDailyReward / oneDayMs));
    }
}