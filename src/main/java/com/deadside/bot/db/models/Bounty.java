package com.deadside.bot.db.models;

import org.bson.types.ObjectId;

/**
 * Represents a bounty with proper isolation between guilds and servers
 */
public class Bounty {
    private ObjectId id;             // MongoDB document ID
    private long placerId;           // Discord user ID of the bounty placer
    private String placerName;       // Name of the bounty placer
    private String targetId;         // Game ID of the bounty target
    private String targetName;       // Name of the bounty target
    private long amount;             // Bounty amount in coins
    private long placedAt;           // Timestamp when the bounty was placed
    private long guildId;            // Discord guild (server) ID for isolation
    private String serverId;         // Game server ID for isolation
    private boolean active;          // Whether the bounty is active or claimed
    private String claimerId;        // Game ID of the player who claimed the bounty (if claimed)
    private String claimerName;      // Name of the player who claimed the bounty (if claimed)
    private long claimedAt;          // Timestamp when the bounty was claimed (if claimed)
    
    public Bounty() {
        // Required for MongoDB POJO codec
        this.placedAt = System.currentTimeMillis();
        this.active = true;
    }
    
    // Getters and Setters
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public long getPlacerId() {
        return placerId;
    }
    
    public void setPlacerId(long placerId) {
        this.placerId = placerId;
    }
    
    public String getPlacerName() {
        return placerName;
    }
    
    public void setPlacerName(String placerName) {
        this.placerName = placerName;
    }
    
    public String getTargetId() {
        return targetId;
    }
    
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
    
    public String getTargetName() {
        return targetName;
    }
    
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }
    
    public long getAmount() {
        return amount;
    }
    
    public void setAmount(long amount) {
        this.amount = amount;
    }
    
    public long getPlacedAt() {
        return placedAt;
    }
    
    public void setPlacedAt(long placedAt) {
        this.placedAt = placedAt;
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
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public String getClaimerId() {
        return claimerId;
    }
    
    public void setClaimerId(String claimerId) {
        this.claimerId = claimerId;
    }
    
    public String getClaimerName() {
        return claimerName;
    }
    
    public void setClaimerName(String claimerName) {
        this.claimerName = claimerName;
    }
    
    public long getClaimedAt() {
        return claimedAt;
    }
    
    public void setClaimedAt(long claimedAt) {
        this.claimedAt = claimedAt;
    }
}