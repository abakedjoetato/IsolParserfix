package com.deadside.bot.db.models;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

/**
 * Database model for a Deadside game server
 * This is a core component of the data isolation system
 */
public class GameServer {
    @BsonId
    private ObjectId id;
    private String serverId;       // Unique identifier for this server
    private String name;           // Display name of the server
    private String ipAddress;      // IP address of the server
    private int port;              // Port number of the server
    private long guildId;          // Discord guild ID this server is associated with
    private int playerCount;       // Current number of players
    private int maxPlayers;        // Maximum number of players
    private boolean isOnline;      // Whether the server is currently online
    private String status;         // Status message
    private long lastUpdated;      // When the server was last updated
    private long lastFetch;        // When stats were last fetched from this server
    private String logFormat;      // Format of the server logs
    private String logPath;        // Path to the server logs directory
    private boolean useSftpForLogs; // Whether to use SFTP for log retrieval
    private String sftpHost;       // SFTP host for log retrieval
    private int sftpPort;          // SFTP port for log retrieval
    private String sftpUsername;   // SFTP username for log retrieval
    private String sftpPassword;   // SFTP password for log retrieval (encrypted)
    private String sftpPath;       // SFTP path to logs directory
    private long lastLogRotation;  // Timestamp of the last detected log rotation
    
    // Additional properties required by other components
    private String host;           // Alternative reference to ipAddress 
    private long logChannelId;     // Discord channel ID for logs
    private long killfeedChannelId; // Discord channel ID for kill feed
    private String logDirectory;   // Directory for storing logs
    private String deathlogsDirectory; // Directory for storing death logs
    private boolean isPrimaryServer; // Whether this is the primary server
    
    // Premium status fields
    private boolean isPremium;     // Whether this server has premium features
    private long premiumUntil;     // Timestamp when premium expires
    
    // Log processing fields
    private String lastProcessedLogFile;    // Last log file that was processed
    private int lastProcessedLogLine;       // Last line number processed in the log
    private String lastProcessedKillfeedFile; // Last killfeed file that was processed
    private int lastProcessedKillfeedLine;    // Last line number processed in killfeed
    private long lastProcessedTimestamp;      // Timestamp of last processed log
    
    // Authentication fields
    private String username;       // Username for authentication
    private String password;       // Password for authentication
    
    // Feature flags
    private boolean killfeedEnabled = true;
    private boolean eventNotificationsEnabled = true;
    private boolean joinLeaveNotificationsEnabled = true;
    private long uptime = 0;
    private boolean readOnly = false;
    private String isolationMode = "standard"; // Can be "standard", "read-only", or "disabled"
    
    public GameServer() {
        // Required for MongoDB POJO codec
        this.isOnline = false;
        this.playerCount = 0;
        this.maxPlayers = 0;
        this.lastUpdated = System.currentTimeMillis();
        this.lastFetch = 0;
        this.useSftpForLogs = false;
        this.sftpPort = 22; // Default SFTP port
        this.isPremium = false;
        this.premiumUntil = 0;
        this.lastProcessedTimestamp = 0;
        this.lastProcessedLogLine = 0;
        this.lastProcessedKillfeedLine = 0;
        this.isPrimaryServer = false;
        this.lastLogRotation = 0;
    }
    
    public GameServer(String serverId, String name, String ipAddress, int port, long guildId) {
        this.serverId = serverId;
        this.name = name;
        this.ipAddress = ipAddress;
        this.host = ipAddress; // For backward compatibility
        this.port = port;
        this.guildId = guildId;
        this.playerCount = 0;
        this.maxPlayers = 0;
        this.isOnline = false;
        this.status = "Inactive";
        this.lastUpdated = System.currentTimeMillis();
        this.lastFetch = 0;
        this.logFormat = "csv";
        this.logPath = "";
        this.logDirectory = "logs";
        this.deathlogsDirectory = "data/deathlogs";
        this.useSftpForLogs = false;
        this.sftpHost = "";
        this.sftpPort = 22;
        this.sftpUsername = "";
        this.sftpPassword = "";
        this.sftpPath = "";
        this.logChannelId = 0;
        this.killfeedChannelId = 0;
        this.isPrimaryServer = false;
        this.isPremium = false;
        this.premiumUntil = 0;
        this.lastProcessedLogFile = "";
        this.lastProcessedLogLine = 0;
        this.lastProcessedKillfeedFile = "";
        this.lastProcessedKillfeedLine = 0;
        this.lastProcessedTimestamp = 0;
        this.username = "";
        this.password = "";
        this.lastLogRotation = 0;
    }
    
    /**
     * Constructor for compatibility with existing code
     */
    public GameServer(long guildId, String serverId, String name, int port, String ipAddress, String type, int maxPlayers) {
        this(serverId, name, ipAddress, port, guildId);
        this.maxPlayers = maxPlayers;
        // 'type' parameter is not used in this version
    }
    
    /**
     * Constructor for compatibility with existing code that includes credentials
     */
    public GameServer(String serverId, String name, int port, String ipAddress, String username, String password, long guildId) {
        this(serverId, name, ipAddress, port, guildId);
        this.username = username;
        this.password = password;
        // Also set SFTP credentials to match the main credentials
        this.sftpUsername = username;
        this.sftpPassword = password;
        this.sftpHost = ipAddress;
        this.sftpPort = port;
        this.useSftpForLogs = true;
    }
    
    /**
     * Constructor for specific compatibility with ModalListener
     */
    public GameServer(String serverId, String name, int port, String ipAddress, String username, long guildId) {
        this(serverId, name, ipAddress, port, guildId);
        this.username = username;
        // Also set SFTP credentials to match
        this.sftpUsername = username;
        this.sftpHost = ipAddress;
        this.sftpPort = port;
        this.useSftpForLogs = true;
    }
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public long getGuildId() {
        return guildId;
    }
    
    public void setGuildId(long guildId) {
        this.guildId = guildId;
    }
    
    public int getPlayerCount() {
        return playerCount;
    }
    
    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public int getMaxPlayers() {
        return maxPlayers;
    }
    
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public boolean isOnline() {
        return isOnline;
    }
    
    public void setOnline(boolean isOnline) {
        this.isOnline = isOnline;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public long getLastFetch() {
        return lastFetch;
    }
    
    public void setLastFetch(long lastFetch) {
        this.lastFetch = lastFetch;
    }
    
    public String getLogFormat() {
        return logFormat;
    }
    
    public void setLogFormat(String logFormat) {
        this.logFormat = logFormat;
    }
    
    public String getLogPath() {
        return logPath;
    }
    
    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
    
    public boolean isUseSftpForLogs() {
        return useSftpForLogs;
    }
    
    public void setUseSftpForLogs(boolean useSftpForLogs) {
        this.useSftpForLogs = useSftpForLogs;
    }
    
    public String getSftpHost() {
        return sftpHost;
    }
    
    public void setSftpHost(String sftpHost) {
        this.sftpHost = sftpHost;
    }
    
    public int getSftpPort() {
        return sftpPort;
    }
    
    public void setSftpPort(int sftpPort) {
        this.sftpPort = sftpPort;
    }
    
    public String getSftpUsername() {
        return sftpUsername;
    }
    
    public void setSftpUsername(String sftpUsername) {
        this.sftpUsername = sftpUsername;
    }
    
    public String getSftpPassword() {
        return sftpPassword;
    }
    
    public void setSftpPassword(String sftpPassword) {
        this.sftpPassword = sftpPassword;
    }
    
    public String getSftpPath() {
        return sftpPath;
    }
    
    public void setSftpPath(String sftpPath) {
        this.sftpPath = sftpPath;
    }
    
    /**
     * Get the timestamp of the last detected log rotation
     * @return The timestamp in milliseconds
     */
    public long getLastLogRotation() {
        return lastLogRotation;
    }
    
    /**
     * Set the timestamp of the last detected log rotation
     * @param lastLogRotation The timestamp in milliseconds
     */
    public void setLastLogRotation(long lastLogRotation) {
        this.lastLogRotation = lastLogRotation;
    }
    
    /**
     * Get the server connection string (ip:port)
     */
    public String getConnectionString() {
        return ipAddress + ":" + port;
    }
    
    /**
     * Check if this server has SFTP configuration
     */
    public boolean hasSftpConfig() {
        boolean hasSftpSpecificConfig = useSftpForLogs && 
                                       sftpHost != null && !sftpHost.isEmpty() && 
                                       sftpUsername != null && !sftpUsername.isEmpty() && 
                                       sftpPassword != null && !sftpPassword.isEmpty();
                                       
        boolean hasFallbackConfig = host != null && !host.isEmpty() && 
                                   username != null && !username.isEmpty() && 
                                   password != null && !password.isEmpty();
        
        return hasSftpSpecificConfig || hasFallbackConfig;
    }
    
    /**
     * Mark the server as having completed a log fetch
     */
    public void markFetchComplete() {
        this.lastFetch = System.currentTimeMillis();
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Update server status information
     */
    public void updateStatus(boolean isOnline, int playerCount, int maxPlayers) {
        this.isOnline = isOnline;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.status = isOnline ? "Online" : "Offline";
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Get the server type name (for display purposes)
     */
    public String getServerTypeName() {
        return "Deadside";
    }
    
    /**
     * Get host (alias for ipAddress)
     */
    public String getHost() {
        return host != null ? host : ipAddress;
    }
    
    /**
     * Set host (alias for ipAddress)
     */
    public void setHost(String host) {
        this.host = host;
    }
    
    /**
     * Get the log channel ID
     */
    public long getLogChannelId() {
        return logChannelId;
    }
    
    /**
     * Set the log channel ID
     */
    public void setLogChannelId(long logChannelId) {
        this.logChannelId = logChannelId;
    }
    
    /**
     * Get killfeed channel ID
     */
    public long getKillfeedChannelId() {
        return killfeedChannelId;
    }
    
    /**
     * Set killfeed channel ID
     */
    public void setKillfeedChannelId(long killfeedChannelId) {
        this.killfeedChannelId = killfeedChannelId;
    }
    
    /**
     * Get log directory
     */
    public String getLogDirectory() {
        return logDirectory != null ? logDirectory : (logPath != null ? logPath : "");
    }
    
    /**
     * Set log directory
     */
    public void setLogDirectory(String logDirectory) {
        this.logDirectory = logDirectory;
    }
    
    /**
     * Get deathlogs directory
     */
    public String getDeathlogsDirectory() {
        return deathlogsDirectory != null ? deathlogsDirectory : "data/deathlogs";
    }
    
    /**
     * Set deathlogs directory
     */
    public void setDeathlogsDirectory(String deathlogsDirectory) {
        this.deathlogsDirectory = deathlogsDirectory;
    }
    
    /**
     * Check if this is the primary server for its guild
     */
    public boolean isPrimaryServer() {
        return isPrimaryServer;
    }
    
    /**
     * Set whether this is the primary server for its guild
     */
    public void setPrimaryServer(boolean isPrimaryServer) {
        this.isPrimaryServer = isPrimaryServer;
    }
    
    /**
     * Check if premium features are enabled
     */
    public boolean isPremium() {
        return isPremium;
    }
    
    /**
     * Set premium status
     */
    public void setPremium(boolean isPremium) {
        this.isPremium = isPremium;
    }
    
    /**
     * Get premium expiration timestamp
     */
    public long getPremiumUntil() {
        return premiumUntil;
    }
    
    /**
     * Set premium expiration timestamp
     */
    public void setPremiumUntil(long premiumUntil) {
        this.premiumUntil = premiumUntil;
    }
    
    /**
     * Get the last processed log file
     */
    public String getLastProcessedLogFile() {
        return lastProcessedLogFile;
    }
    
    /**
     * Set the last processed log file
     */
    public void setLastProcessedLogFile(String lastProcessedLogFile) {
        this.lastProcessedLogFile = lastProcessedLogFile;
    }
    
    /**
     * Get the last processed log line
     */
    public int getLastProcessedLogLine() {
        return lastProcessedLogLine;
    }
    
    /**
     * Set the last processed log line
     */
    public void setLastProcessedLogLine(int lastProcessedLogLine) {
        this.lastProcessedLogLine = lastProcessedLogLine;
    }
    
    /**
     * Get the last processed killfeed file
     */
    public String getLastProcessedKillfeedFile() {
        return lastProcessedKillfeedFile;
    }
    
    /**
     * Set the last processed killfeed file
     */
    public void setLastProcessedKillfeedFile(String lastProcessedKillfeedFile) {
        this.lastProcessedKillfeedFile = lastProcessedKillfeedFile;
    }
    
    /**
     * Get the last processed killfeed line
     */
    public int getLastProcessedKillfeedLine() {
        return lastProcessedKillfeedLine;
    }
    
    /**
     * Set the last processed killfeed line
     */
    public void setLastProcessedKillfeedLine(int lastProcessedKillfeedLine) {
        this.lastProcessedKillfeedLine = lastProcessedKillfeedLine;
    }
    
    /**
     * Get the timestamp of the last processing
     */
    public long getLastProcessedTimestamp() {
        return lastProcessedTimestamp;
    }
    
    /**
     * Set the last processed timestamp
     */
    public void setLastProcessedTimestamp(long lastProcessedTimestamp) {
        this.lastProcessedTimestamp = lastProcessedTimestamp;
    }
    
    /**
     * Get username for authentication
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Set username for authentication
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * Get password for authentication
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * Set password for authentication
     */
    public void setPassword(String password) {
        this.password = password;
    }
    
    /**
     * Check if this server is in read-only mode
     * @return True if the server is in read-only mode
     */
    public boolean isReadOnly() {
        return readOnly;
    }
    
    /**
     * Set whether this server is in read-only mode
     * @param readOnly True to set the server to read-only mode
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
    
    /**
     * Get the isolation mode for this server
     * @return The isolation mode (standard, read-only, or disabled)
     */
    public String getIsolationMode() {
        return isolationMode != null ? isolationMode : "standard";
    }
    
    /**
     * Set the isolation mode for this server
     * @param isolationMode The isolation mode (standard, read-only, or disabled)
     */
    public void setIsolationMode(String isolationMode) {
        this.isolationMode = isolationMode;
        
        // Keep readOnly flag in sync with isolation mode for backward compatibility
        if ("read-only".equalsIgnoreCase(isolationMode)) {
            this.readOnly = true;
        } else if ("standard".equalsIgnoreCase(isolationMode)) {
            this.readOnly = false;
        }
        // If "disabled", we leave readOnly as-is
    }
    
    /**
     * Check if this server has isolation disabled
     * @return True if isolation is disabled for this server
     */
    public boolean isIsolationDisabled() {
        return "disabled".equalsIgnoreCase(isolationMode);
    }
    
    /**
     * Check if this is a Default Server
     * This is a special case for the fallback server
     * @return True if this is a Default Server
     */
    public boolean isDefaultServer() {
        return "Default Server".equals(name);
    }
    
    /**
     * Check if this server has any restricted isolation mode
     * (read-only, disabled, or Default Server)
     * @return True if the server has restricted isolation
     */
    public boolean hasRestrictedIsolation() {
        return isReadOnly() || isIsolationDisabled() || isDefaultServer();
    }
    
    /**
     * Update log progress for tracking processed log files
     */
    public void updateLogProgress(String filename, long lineNumber) {
        this.lastProcessedLogFile = filename;
        this.lastProcessedLogLine = (int) lineNumber;
        this.lastProcessedTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Update killfeed progress for tracking processed files
     */
    public void updateKillfeedProgress(String filename, long lineNumber) {
        this.lastProcessedKillfeedFile = filename;
        this.lastProcessedKillfeedLine = (int) lineNumber;
        this.lastProcessedTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Get the uptime in milliseconds
     */
    public long getUptime() {
        return uptime;
    }
    
    /**
     * Set the uptime in milliseconds
     */
    public void setUptime(long uptime) {
        this.uptime = uptime;
    }
    
    /**
     * Check if killfeed is enabled
     */
    public boolean isKillfeedEnabled() {
        return killfeedEnabled;
    }
    
    /**
     * Set whether killfeed is enabled
     */
    public void setKillfeedEnabled(boolean killfeedEnabled) {
        this.killfeedEnabled = killfeedEnabled;
    }
    
    /**
     * Check if event notifications are enabled
     */
    public boolean isEventNotificationsEnabled() {
        return eventNotificationsEnabled;
    }
    
    /**
     * Set whether event notifications are enabled
     */
    public void setEventNotificationsEnabled(boolean eventNotificationsEnabled) {
        this.eventNotificationsEnabled = eventNotificationsEnabled;
    }
    
    /**
     * Check if join/leave notifications are enabled
     */
    public boolean isJoinLeaveNotificationsEnabled() {
        return joinLeaveNotificationsEnabled;
    }
    
    /**
     * Set whether join/leave notifications are enabled
     */
    public void setJoinLeaveNotificationsEnabled(boolean joinLeaveNotificationsEnabled) {
        this.joinLeaveNotificationsEnabled = joinLeaveNotificationsEnabled;
    }
    
    /**
     * Synchronizes credentials between standard and SFTP-specific fields
     * This ensures that if one set of credentials is missing, it falls back to the other
     * @return True if credentials were synchronized
     */
    public boolean synchronizeCredentials() {
        boolean updated = false;
        
        // If SFTP credentials are missing but standard credentials exist, copy them
        if ((getSftpHost() == null || getSftpHost().trim().isEmpty()) && 
            (getHost() != null && !getHost().trim().isEmpty())) {
            setSftpHost(getHost());
            updated = true;
        }
        
        if ((getSftpUsername() == null || getSftpUsername().trim().isEmpty()) && 
            (getUsername() != null && !getUsername().trim().isEmpty())) {
            setSftpUsername(getUsername());
            updated = true;
        }
        
        if ((getSftpPassword() == null || getSftpPassword().trim().isEmpty()) && 
            (getPassword() != null && !getPassword().trim().isEmpty())) {
            setSftpPassword(getPassword());
            updated = true;
        }
        
        if (getSftpPort() <= 0 && getPort() > 0) {
            setSftpPort(getPort());
            updated = true;
        }
        
        // If standard credentials are missing but SFTP credentials exist, copy them
        if ((getHost() == null || getHost().trim().isEmpty()) && 
            (getSftpHost() != null && !getSftpHost().trim().isEmpty())) {
            setHost(getSftpHost());
            updated = true;
        }
        
        if ((getUsername() == null || getUsername().trim().isEmpty()) && 
            (getSftpUsername() != null && !getSftpUsername().trim().isEmpty())) {
            setUsername(getSftpUsername());
            updated = true;
        }
        
        if ((getPassword() == null || getPassword().trim().isEmpty()) && 
            (getSftpPassword() != null && !getSftpPassword().trim().isEmpty())) {
            setPassword(getSftpPassword());
            updated = true;
        }
        
        return updated;
    }
}
