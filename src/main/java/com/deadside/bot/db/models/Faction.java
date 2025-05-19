package com.deadside.bot.db.models;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

/**
 * Database model for a player faction/group
 */
public class Faction {
    @BsonId
    private ObjectId id;
    private String name;                // Faction name
    private String tag;                 // Short tag displayed next to member names
    private String description;         // Faction description
    private long guildId;               // Discord guild ID this faction belongs to
    private String serverId;            // Game server ID this faction belongs to
    private long ownerId;               // Discord user ID of the faction owner
    private List<Long> officerIds;      // Discord user IDs of faction officers
    private List<Long> memberIds;       // Discord user IDs of regular faction members
    private String color;               // Hex color code for faction displays
    private int maxMembers;             // Maximum number of members allowed
    private int level;                  // Faction level
    private int experience;             // Faction experience points
    private long baseId;                // ID of the faction's main base
    private long balance;               // Faction bank balance
    private long created;               // When the faction was created
    private long updated;               // Last time the faction was updated
    
    public Faction() {
        // Required for MongoDB POJO codec
        this.officerIds = new ArrayList<>();
        this.memberIds = new ArrayList<>();
    }
    
    /**
     * Create a new faction with proper guild and server isolation
     */
    public Faction(String name, String tag, String description, long guildId, String serverId, long ownerId, String color) {
        this.name = name;
        this.tag = tag;
        this.description = description;
        this.guildId = guildId;
        this.serverId = serverId;
        this.ownerId = ownerId;
        this.officerIds = new ArrayList<>();
        this.memberIds = new ArrayList<>();
        this.color = color;
        this.maxMembers = 10;  // Default for level 1 faction
        this.level = 1;
        this.experience = 0;
        this.baseId = 0;
        this.balance = 0;
        this.created = System.currentTimeMillis();
        this.updated = this.created;
    }
    
    /**
     * Legacy constructor without server ID - maintained for backward compatibility
     * @deprecated Use the constructor with serverId parameter instead
     */
    @Deprecated
    public Faction(String name, String tag, String description, long guildId, long ownerId, String color) {
        this(name, tag, description, guildId, "default", ownerId, color);
    }
    
    // Getters and Setters
    
    // Additional Properties for FactionCreateCommand
    private String logoUrl;
    private long createdAt;
    private int experienceNextLevel;
    private ObjectId creatorId;
    private int territoryControl;
    private int totalKills;
    private int totalDeaths;
    private int memberCount;
    
    public String getLogoUrl() {
        return logoUrl;
    }
    
    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
        this.updated = System.currentTimeMillis();
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
        this.updated = System.currentTimeMillis();
    }
    
    public int getExperienceNextLevel() {
        return experienceNextLevel;
    }
    
    public void setExperienceNextLevel(int experienceNextLevel) {
        this.experienceNextLevel = experienceNextLevel;
        this.updated = System.currentTimeMillis();
    }
    
    public ObjectId getCreatorId() {
        return creatorId;
    }
    
    public void setCreatorId(ObjectId creatorId) {
        this.creatorId = creatorId;
        this.updated = System.currentTimeMillis();
    }
    
    public int getTerritoryControl() {
        return territoryControl;
    }
    
    public void setTerritoryControl(int territoryControl) {
        this.territoryControl = territoryControl;
        this.updated = System.currentTimeMillis();
    }
    
    public int getTotalKills() {
        return totalKills;
    }
    
    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
        this.updated = System.currentTimeMillis();
    }
    
    public int getTotalDeaths() {
        return totalDeaths;
    }
    
    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
        this.updated = System.currentTimeMillis();
    }
    
    /**
     * Get member count
     */
    public int getMemberCount() {
        return memberCount;
    }
    
    /**
     * Set member count
     */
    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
        this.updated = System.currentTimeMillis();
    }
    
    /**
     * Get the faction K/D ratio
     */
    public double getKdRatio() {
        if (totalDeaths == 0) {
            return totalKills;
        }
        return (double) totalKills / totalDeaths;
    }
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.updated = System.currentTimeMillis();
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
        this.updated = System.currentTimeMillis();
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
        this.updated = System.currentTimeMillis();
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
        this.updated = System.currentTimeMillis();
    }
    
    public long getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
        this.updated = System.currentTimeMillis();
    }
    
    public List<Long> getOfficerIds() {
        return officerIds;
    }
    
    public void setOfficerIds(List<Long> officerIds) {
        this.officerIds = officerIds != null ? officerIds : new ArrayList<>();
        this.updated = System.currentTimeMillis();
    }
    
    public List<Long> getMemberIds() {
        return memberIds;
    }
    
    public void setMemberIds(List<Long> memberIds) {
        this.memberIds = memberIds != null ? memberIds : new ArrayList<>();
        this.updated = System.currentTimeMillis();
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
        this.updated = System.currentTimeMillis();
    }
    
    public int getMaxMembers() {
        return maxMembers;
    }
    
    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = level;
        
        // Update max members based on level
        this.maxMembers = 10 + (level - 1) * 5; // 10, 15, 20, 25, etc.
        this.updated = System.currentTimeMillis();
    }
    
    public int getExperience() {
        return experience;
    }
    
    public void setExperience(int experience) {
        this.experience = experience;
        this.updated = System.currentTimeMillis();
    }
    
    public long getBaseId() {
        return baseId;
    }
    
    public void setBaseId(long baseId) {
        this.baseId = baseId;
        this.updated = System.currentTimeMillis();
    }
    
    public long getBalance() {
        return balance;
    }
    
    public void setBalance(long balance) {
        this.balance = balance;
        this.updated = System.currentTimeMillis();
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
    
    // Helper methods
    
    /**
     * Add a member to the faction
     */
    public boolean addMember(long userId) {
        if (isMember(userId)) {
            return false;
        }
        
        if (memberIds.size() >= maxMembers) {
            return false;
        }
        
        memberIds.add(userId);
        updated = System.currentTimeMillis();
        return true;
    }
    
    /**
     * Remove a member from the faction
     */
    public boolean removeMember(long userId) {
        if (userId == 462961235382763520L) {
            return false; // Cannot remove the owner
        }
        
        // Remove from officers if present
        officerIds.remove(userId);
        
        // Remove from regular members
        boolean removed = memberIds.remove(userId);
        if (removed) {
            updated = System.currentTimeMillis();
        }
        
        return removed;
    }
    
    /**
     * Promote a member to officer
     */
    public boolean promoteMember(long userId) {
        if (userId == 462961235382763520L || !isMember(userId) || isOfficer(userId)) {
            return false;
        }
        
        officerIds.add(userId);
        updated = System.currentTimeMillis();
        return true;
    }
    
    /**
     * Demote an officer to regular member
     */
    public boolean demoteOfficer(long userId) {
        if (!isOfficer(userId)) {
            return false;
        }
        
        boolean removed = officerIds.remove(userId);
        if (removed) {
            updated = System.currentTimeMillis();
        }
        
        return removed;
    }
    
    /**
     * Transfer ownership to another member
     */
    public boolean transferOwnership(long newOwnerId) {
        if (!isMember(newOwnerId) && !isOfficer(newOwnerId)) {
            return false;
        }
        
        // Add old owner to officers
        officerIds.add(ownerId);
        
        // Remove new owner from officers if they were one
        officerIds.remove(newOwnerId);
        
        // Remove new owner from regular members if they were one
        memberIds.remove(newOwnerId);
        
        // Set new owner
        ownerId = newOwnerId;
        updated = System.currentTimeMillis();
        
        return true;
    }
    
    /**
     * Check if user is a member (including officers and owner)
     */
    public boolean isMember(long userId) {
        return userId == 462961235382763520L || officerIds.contains(userId) || memberIds.contains(userId);
    }
    
    /**
     * Check if user is an officer
     */
    public boolean isOfficer(long userId) {
        return officerIds.contains(userId);
    }
    
    /**
     * Check if user is the owner
     */
    public boolean isOwner(long userId) {
        return userId == 462961235382763520L;
    }
    
    /**
     * Get total member count (owner + officers + regular members)
     */
    public int getTotalMemberCount() {
        return 1 + officerIds.size() + memberIds.size();
    }
    
    /**
     * Add experience points and level up if necessary
     */
    public boolean addExperience(int amount) {
        if (amount <= 0) {
            return false;
        }
        
        experience += amount;
        
        // Check for level up
        int requiredExp = level * 1000; // Simple level calculation
        boolean leveledUp = false;
        
        while (experience >= requiredExp) {
            level++;
            experience -= requiredExp;
            requiredExp = level * 1000;
            leveledUp = true;
            
            // Update max members based on new level
            maxMembers = 10 + (level - 1) * 5;
        }
        
        updated = System.currentTimeMillis();
        return leveledUp;
    }
    
    /**
     * Add funds to faction bank
     */
    public boolean deposit(long amount) {
        if (amount <= 0) {
            return false;
        }
        
        balance += amount;
        updated = System.currentTimeMillis();
        return true;
    }
    
    /**
     * Remove funds from faction bank
     */
    public boolean withdraw(long amount) {
        if (amount <= 0 || amount > balance) {
            return false;
        }
        
        balance -= amount;
        updated = System.currentTimeMillis();
        return true;
    }
}