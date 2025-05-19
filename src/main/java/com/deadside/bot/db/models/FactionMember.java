package com.deadside.bot.db.models;

import org.bson.types.ObjectId;

/**
 * Represents a player's faction membership
 * This is embedded in the Player document
 */
public class FactionMember {
    private ObjectId factionId;  // ID of the faction
    private String factionName;  // Cached faction name for quick display
    private String factionTag;   // Cached faction tag for quick display
    private int role;            // 0 = member, 1 = officer, 2 = owner
    private long joinedAt;       // When the player joined the faction
    private int contributedXp;   // How much XP the player has contributed to the faction
    
    public FactionMember() {
        // Required for MongoDB POJO codec
    }
    
    public FactionMember(ObjectId factionId, String factionName, String factionTag, int role) {
        this.factionId = factionId;
        this.factionName = factionName;
        this.factionTag = factionTag;
        this.role = role;
        this.joinedAt = System.currentTimeMillis();
        this.contributedXp = 0;
    }
    
    // Getters and Setters
    
    public ObjectId getFactionId() {
        return factionId;
    }
    
    public void setFactionId(ObjectId factionId) {
        this.factionId = factionId;
    }
    
    public String getFactionName() {
        return factionName;
    }
    
    public void setFactionName(String factionName) {
        this.factionName = factionName;
    }
    
    public String getFactionTag() {
        return factionTag;
    }
    
    public void setFactionTag(String factionTag) {
        this.factionTag = factionTag;
    }
    
    public int getRole() {
        return role;
    }
    
    public void setRole(int role) {
        this.role = role;
    }
    
    public long getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    public int getContributedXp() {
        return contributedXp;
    }
    
    public void setContributedXp(int contributedXp) {
        this.contributedXp = contributedXp;
    }
    
    public void addContributedXp(int amount) {
        this.contributedXp += amount;
    }
    
    // Helper methods
    
    public boolean isOwner() {
        return role == 2;
    }
    
    public boolean isLeader() {
        return role == 2;  // Alias for isOwner for code readability
    }
    
    public void setLeader(boolean isLeader) {
        if (isLeader) {
            this.role = 2;
        } else if (this.role == 2) {
            // If currently a leader, demote to officer
            this.role = 1;
        }
    }
    
    public boolean isOfficer() {
        return role == 1;
    }
    
    public void setOfficer(boolean isOfficer) {
        if (isOfficer) {
            // Only set to officer if not already a leader
            if (role != 2) {
                this.role = 1;
            }
        } else if (role == 1) {
            // If currently an officer, demote to member
            this.role = 0;
        }
    }
    
    public boolean isMember() {
        return role == 0;
    }
    
    public String getRoleName() {
        return switch (role) {
            case 0 -> "Member";
            case 1 -> "Officer";
            case 2 -> "Owner";
            default -> "Unknown";
        };
    }
}