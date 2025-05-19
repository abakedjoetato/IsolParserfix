package com.deadside.bot.db.models;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;

/**
 * Represents the configuration for a Discord guild
 */
public class GuildConfig {
    private ObjectId id;
    private final long guildId;
    private long primaryLogChannelId;     // Default channel for all events
    private long killLogChannelId;        // Channel for kill events
    private long deathLogChannelId;       // Channel for death events
    private long connectionLogChannelId;  // Channel for player connections/disconnections
    private long eventLogChannelId;       // Channel for game events (airdrops, etc)
    private long longshotLogChannelId;    // Channel for longshot kills
    private long playerCountVoiceChannelId; // Voice channel for player count updates
    private String playerCountServerName;  // Server name to track player count for
    
    // Premium settings
    private boolean premium;
    private Date premiumUntil;
    private int premiumSlots;
    
    /**
     * Create a new guild configuration
     * 
     * @param guildId The Discord guild ID
     */
    public GuildConfig(long guildId) {
        this.guildId = guildId;
        this.premium = false;
        this.premiumSlots = 0;
    }
    
    /**
     * Create a guild configuration from a MongoDB document
     * 
     * @param document The MongoDB document
     */
    public GuildConfig(Document document) {
        this.id = document.getObjectId("_id");
        this.guildId = document.getLong("guildId");
        this.primaryLogChannelId = document.containsKey("primaryLogChannelId") ? 
                document.getLong("primaryLogChannelId") : 0;
        this.killLogChannelId = document.containsKey("killLogChannelId") ? 
                document.getLong("killLogChannelId") : 0;
        this.deathLogChannelId = document.containsKey("deathLogChannelId") ? 
                document.getLong("deathLogChannelId") : 0;
        this.connectionLogChannelId = document.containsKey("connectionLogChannelId") ? 
                document.getLong("connectionLogChannelId") : 0;
        this.eventLogChannelId = document.containsKey("eventLogChannelId") ? 
                document.getLong("eventLogChannelId") : 0;
        this.longshotLogChannelId = document.containsKey("longshotLogChannelId") ? 
                document.getLong("longshotLogChannelId") : 0;
        this.playerCountVoiceChannelId = document.containsKey("playerCountVoiceChannelId") ? 
                document.getLong("playerCountVoiceChannelId") : 0;
        this.playerCountServerName = document.containsKey("playerCountServerName") ? 
                document.getString("playerCountServerName") : null;
        
        // Premium settings
        this.premium = document.containsKey("premium") ? document.getBoolean("premium") : false;
        
        // Handle premiumUntil which might be stored as a Date or as a Long timestamp
        if (document.containsKey("premiumUntil")) {
            Object value = document.get("premiumUntil");
            if (value instanceof Date) {
                this.premiumUntil = (Date) value;
            } else if (value instanceof Long) {
                this.premiumUntil = new Date((Long) value);
            } else {
                this.premiumUntil = null;
            }
        } else {
            this.premiumUntil = null;
        }
        
        this.premiumSlots = document.containsKey("premiumSlots") ? document.getInteger("premiumSlots") : 0;
    }
    
    /**
     * Convert this object to a MongoDB document
     * 
     * @return The MongoDB document
     */
    public Document toDocument() {
        Document doc = new Document("guildId", guildId)
                .append("primaryLogChannelId", primaryLogChannelId)
                .append("killLogChannelId", killLogChannelId)
                .append("deathLogChannelId", deathLogChannelId)
                .append("connectionLogChannelId", connectionLogChannelId)
                .append("eventLogChannelId", eventLogChannelId)
                .append("longshotLogChannelId", longshotLogChannelId)
                .append("playerCountVoiceChannelId", playerCountVoiceChannelId)
                .append("playerCountServerName", playerCountServerName)
                .append("premium", premium)
                .append("premiumSlots", premiumSlots);
        
        if (premiumUntil != null) {
            doc.append("premiumUntil", premiumUntil);
        }
        
        if (id != null) {
            doc.append("_id", id);
        }
        
        return doc;
    }
    
    // Getters and setters
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public long getGuildId() {
        return guildId;
    }
    
    public long getPrimaryLogChannelId() {
        return primaryLogChannelId;
    }
    
    public void setPrimaryLogChannelId(long primaryLogChannelId) {
        this.primaryLogChannelId = primaryLogChannelId;
    }
    
    public long getKillLogChannelId() {
        return killLogChannelId;
    }
    
    public void setKillLogChannelId(long killLogChannelId) {
        this.killLogChannelId = killLogChannelId;
    }
    
    public long getDeathLogChannelId() {
        return deathLogChannelId;
    }
    
    public void setDeathLogChannelId(long deathLogChannelId) {
        this.deathLogChannelId = deathLogChannelId;
    }
    
    public long getConnectionLogChannelId() {
        return connectionLogChannelId;
    }
    
    public void setConnectionLogChannelId(long connectionLogChannelId) {
        this.connectionLogChannelId = connectionLogChannelId;
    }
    
    public long getEventLogChannelId() {
        return eventLogChannelId;
    }
    
    public void setEventLogChannelId(long eventLogChannelId) {
        this.eventLogChannelId = eventLogChannelId;
    }
    
    public long getLongshotLogChannelId() {
        return longshotLogChannelId;
    }
    
    public void setLongshotLogChannelId(long longshotLogChannelId) {
        this.longshotLogChannelId = longshotLogChannelId;
    }
    
    // Getters and setters for player count voice channel are now defined at the end of the class
    
    // Premium getters and setters
    
    public boolean isPremium() {
        return premium;
    }
    
    public void setPremium(boolean premium) {
        this.premium = premium;
    }
    
    public Date getPremiumUntil() {
        return premiumUntil;
    }
    
    public void setPremiumUntil(Date premiumUntil) {
        this.premiumUntil = premiumUntil;
    }
    
    /**
     * Sets the premium status to expire after the specified number of days
     * @param days Number of days from now
     */
    public void setPremiumUntil(int days) {
        long daysInMillis = days * 24L * 60L * 60L * 1000L;
        this.premiumUntil = new Date(System.currentTimeMillis() + daysInMillis);
    }
    
    /**
     * Sets the premium status to expire at the specified timestamp
     * @param timestamp Timestamp in milliseconds
     */
    public void setPremiumUntilTimestamp(long timestamp) {
        this.premiumUntil = new Date(timestamp);
    }
    
    /**
     * Get premium until timestamp in milliseconds
     * @return timestamp in milliseconds, or 0 if not set
     */
    public long getPremiumUntilTimestamp() {
        return premiumUntil != null ? premiumUntil.getTime() : 0;
    }
    
    /**
     * Check if premium status is active
     * @return true if premium and not expired
     */
    public boolean isPremiumActive() {
        return premium && (premiumUntil == null || premiumUntil.getTime() > System.currentTimeMillis());
    }
    
    public int getPremiumSlots() {
        return premiumSlots;
    }
    
    public void setPremiumSlots(int premiumSlots) {
        this.premiumSlots = premiumSlots;
    }
    
    /**
     * Add premium slots to the guild
     * @param slots Number of slots to add
     */
    public void addPremiumSlots(int slots) {
        this.premiumSlots += slots;
    }
    
    /**
     * Get the appropriate log channel ID for the specified event type
     * Falls back to primary channel if dedicated channel is not set
     * 
     * @param eventType The type of event
     * @return The channel ID to use, or 0 if no channel set
     */
    public long getLogChannelForEventType(String eventType) {
        switch (eventType) {
            case "kill":
                return killLogChannelId != 0 ? killLogChannelId : primaryLogChannelId;
                
            case "death":
                return deathLogChannelId != 0 ? deathLogChannelId : primaryLogChannelId;
                
            case "longshot":
                return longshotLogChannelId != 0 ? longshotLogChannelId : 
                       (killLogChannelId != 0 ? killLogChannelId : primaryLogChannelId);
                
            case "connection":
            case "disconnect":
            case "join":
            case "leave":
                return connectionLogChannelId != 0 ? connectionLogChannelId : primaryLogChannelId;
                
            case "airdrop":
            case "mission":
            case "helicrash":
            case "event":
            case "supply":
                return eventLogChannelId != 0 ? eventLogChannelId : primaryLogChannelId;
                
            default:
                return primaryLogChannelId;
        }
    }
    
    /**
     * Get the player count voice channel ID
     * @return The channel ID, or 0 if not set
     */
    public long getPlayerCountVoiceChannelId() {
        return playerCountVoiceChannelId;
    }
    
    /**
     * Set the player count voice channel ID
     * @param channelId The channel ID to set
     */
    public void setPlayerCountVoiceChannelId(long channelId) {
        this.playerCountVoiceChannelId = channelId;
    }
    
    /**
     * Get the server name to track player count for
     * @return The server name
     */
    public String getPlayerCountServerName() {
        return playerCountServerName;
    }
    
    /**
     * Set the server name to track player count for
     * @param serverName The server name
     */
    public void setPlayerCountServerName(String serverName) {
        this.playerCountServerName = serverName;
    }
    
    /**
     * Remove a server from premium tracking in this guild.
     * This method is called when a server is deleted to ensure premium slot tracking is accurate.
     * 
     * @param serverName The name of the server being removed
     */
    public void removeServerFromPremium(String serverName) {
        // If this guild config's player count tracking was set to the deleted server, clear it
        if (serverName != null && serverName.equals(playerCountServerName)) {
            this.playerCountServerName = null;
            this.playerCountVoiceChannelId = 0;
        }
        
        // Note: This method can be extended to handle other premium associations
        // as the premium system is expanded (e.g., removing server-specific feature flags)
    }
}