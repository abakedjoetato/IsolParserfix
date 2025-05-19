package com.deadside.bot.db.models;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

/**
 * Database model for linking Discord users to Deadside players
 * With proper guild and server isolation to prevent data leakage between guilds and servers
 */
public class LinkedPlayer {
    @BsonId
    private ObjectId id;
    private Long discordId;          // Discord user ID 
    private String mainPlayerName;    // Main player's name (for easy reference)
    private String mainPlayerId;      // Main player's ID
    private List<String> altPlayerIds; // List of alt account player IDs
    private long guildId;            // Discord guild/server ID for isolation
    private String serverId;         // Game server ID for isolation
    private long created;            // When the link was created
    private long updated;            // Last time the link was updated
    
    public LinkedPlayer() {
        // Required for MongoDB POJO codec
        this.altPlayerIds = new ArrayList<>();
    }
    
    public LinkedPlayer(Long discordId, String mainPlayerName, String mainPlayerId, long guildId, String serverId) {
        this.discordId = discordId;
        this.mainPlayerName = mainPlayerName;
        this.mainPlayerId = mainPlayerId;
        this.guildId = guildId;
        this.serverId = serverId;
        this.altPlayerIds = new ArrayList<>();
        this.created = System.currentTimeMillis();
        this.updated = this.created;
    }
    
    // Maintained for backward compatibility but marked as deprecated
    @Deprecated
    public LinkedPlayer(Long discordId, String mainPlayerName, String mainPlayerId) {
        this.discordId = discordId;
        this.mainPlayerName = mainPlayerName;
        this.mainPlayerId = mainPlayerId;
        this.altPlayerIds = new ArrayList<>();
        this.created = System.currentTimeMillis();
        this.updated = this.created;
        // Default values for isolation, should be set properly before saving
        this.guildId = 0;
        this.serverId = "default";
    }
    
    public LinkedPlayer(long discordId, ObjectId playerId, long guildId, String serverId) {
        this.discordId = discordId;
        this.mainPlayerId = playerId.toString();
        this.mainPlayerName = "Player-" + playerId.toString();
        this.guildId = guildId;
        this.serverId = serverId;
        this.altPlayerIds = new ArrayList<>();
        this.created = System.currentTimeMillis();
        this.updated = this.created;
    }
    
    // Maintained for backward compatibility but marked as deprecated
    @Deprecated
    public LinkedPlayer(long discordId, ObjectId playerId) {
        this.discordId = discordId;
        this.mainPlayerId = playerId.toString();
        this.mainPlayerName = "Player-" + playerId.toString();
        this.altPlayerIds = new ArrayList<>();
        this.created = System.currentTimeMillis();
        this.updated = this.created;
        // Default values for isolation, should be set properly before saving
        this.guildId = 0;
        this.serverId = "default";
    }
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public Long getDiscordId() {
        return discordId;
    }
    
    public void setDiscordId(Long discordId) {
        this.discordId = discordId;
    }
    
    public String getMainPlayerName() {
        return mainPlayerName;
    }
    
    public void setMainPlayerName(String mainPlayerName) {
        this.mainPlayerName = mainPlayerName;
    }
    
    public String getMainPlayerId() {
        return mainPlayerId;
    }
    
    public void setMainPlayerId(String mainPlayerId) {
        this.mainPlayerId = mainPlayerId;
    }
    
    public void setMainPlayerId(ObjectId mainPlayerId) {
        this.mainPlayerId = mainPlayerId.toString();
    }
    
    public List<String> getAltPlayerIds() {
        return altPlayerIds;
    }
    
    public void setAltPlayerIds(List<String> altPlayerIds) {
        this.altPlayerIds = altPlayerIds != null ? altPlayerIds : new ArrayList<>();
    }
    
    public long getCreated() {
        return created;
    }
    
    public void setCreated(long created) {
        this.created = created;
    }
    
    public long getUpdated() {
        return updated;
    }
    
    public void setUpdated(long updated) {
        this.updated = updated;
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
    
    /**
     * Add an alt player ID to this linked player
     */
    public void addAltPlayerId(String playerId) {
        if (this.altPlayerIds == null) {
            this.altPlayerIds = new ArrayList<>();
        }
        
        if (!this.altPlayerIds.contains(playerId)) {
            this.altPlayerIds.add(playerId);
            this.updated = System.currentTimeMillis();
        }
    }
    
    /**
     * Remove an alt player ID from this linked player
     */
    public void removeAltPlayerId(String playerId) {
        if (this.altPlayerIds != null && this.altPlayerIds.contains(playerId)) {
            this.altPlayerIds.remove(playerId);
            this.updated = System.currentTimeMillis();
        }
    }
    
    /**
     * Check if this linked player contains a specific player ID (main or alt)
     */
    public boolean hasPlayerId(String playerId) {
        if (this.mainPlayerId != null && this.mainPlayerId.equals(playerId)) {
            return true;
        }
        
        return this.altPlayerIds != null && this.altPlayerIds.contains(playerId);
    }
    
    /**
     * Get all player IDs (main + alts)
     */
    public List<String> getAllPlayerIds() {
        List<String> allIds = new ArrayList<>();
        
        if (this.mainPlayerId != null) {
            allIds.add(this.mainPlayerId);
        }
        
        if (this.altPlayerIds != null) {
            allIds.addAll(this.altPlayerIds);
        }
        
        return allIds;
    }
}