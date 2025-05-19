package com.deadside.bot.db.models;

import org.bson.types.ObjectId;

/**
 * Represents a player alert with proper isolation between guilds and servers
 */
public class Alert {
    private ObjectId id;             // MongoDB document ID
    private long userId;             // Discord user ID of the alert creator
    private long guildId;            // Discord guild (server) ID for isolation
    private String serverId;         // Game server ID for isolation
    private String playerName;       // Name of the player to watch for
    private String playerId;         // ID of the player to watch for (optional)
    private long createdAt;          // Timestamp when the alert was created
    private boolean active;          // Whether the alert is currently active
    private String alertType;        // Type of alert: "KILL", "DEATH", "JOIN", "LEAVE"
    private String channelId;        // Discord channel ID where to send the alert (optional)
    
    public Alert() {
        // Required for MongoDB POJO codec
        this.createdAt = System.currentTimeMillis();
        this.active = true;
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
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public String getAlertType() {
        return alertType;
    }
    
    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }
    
    public String getChannelId() {
        return channelId;
    }
    
    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }
}