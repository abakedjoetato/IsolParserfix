package com.deadside.bot.sftp;

import com.deadside.bot.config.Config;
import com.deadside.bot.db.models.GameServer;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * SFTP connection handler
 */
public class SftpConnector {
    private static final Logger logger = LoggerFactory.getLogger(SftpConnector.class);
    private final int timeout;
    
    public SftpConnector() {
        Config config = Config.getInstance();
        this.timeout = config.getSftpConnectTimeout();
    }
    
    /**
     * Connect to an SFTP server
     * @param server The server config
     * @return The SFTP channel and session
     * @throws JSchException If connection fails
     */
    private SftpConnection connect(GameServer server) throws JSchException {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        
        try {
            // First try with SFTP-specific credentials if available
            if (server.isUseSftpForLogs() && server.getSftpHost() != null && !server.getSftpHost().isEmpty()) {
                try {
                    String sftp_user = server.getSftpUsername() != null && !server.getSftpUsername().isEmpty() ? 
                        server.getSftpUsername() : server.getUsername();
                    
                    String sftp_password = server.getSftpPassword() != null && !server.getSftpPassword().isEmpty() ? 
                        server.getSftpPassword() : server.getPassword();
                        
                    int sftp_port = server.getSftpPort() > 0 ? server.getSftpPort() : 22;
                    
                    logger.info("Attempting SFTP connection to {} using dedicated SFTP credentials", server.getSftpHost());
                    
                    session = jsch.getSession(sftp_user, server.getSftpHost(), sftp_port);
                    session.setPassword(sftp_password);
                    
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    session.setTimeout(timeout);
                    
                    session.connect();
                } catch (JSchException e) {
                    // Log the error and try with fallback credentials
                    logger.warn("SFTP connection with dedicated credentials failed: {}. Trying fallback credentials.", e.getMessage());
                    
                    // Close the session if it was created
                    if (session != null && session.isConnected()) {
                        session.disconnect();
                        session = null;
                    }
                    
                    // Throw if this is a dedicated SFTP configuration without fallback
                    if (!server.hasSftpConfig()) {
                        throw e;
                    }
                }
            }
            
            // If first attempt failed or no SFTP-specific credentials, try regular credentials
            if (session == null || !session.isConnected()) {
                if (server.getUsername() != null && !server.getUsername().isEmpty() && 
                    server.getHost() != null && !server.getHost().isEmpty()) {
                    
                    logger.info("Attempting fallback SFTP connection to {} using regular credentials", server.getHost());
                    
                    session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
                    session.setPassword(server.getPassword());
                    
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    session.setTimeout(timeout);
                    
                    session.connect();
                } else {
                    throw new JSchException("No valid SFTP configuration available");
                }
            }
            
            // Open SFTP channel
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            return new SftpConnection(session, channel);
        } catch (JSchException e) {
            // Make sure to close session if an error occurs
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            throw e;
        }
    }
    
    /**
     * Test connection to an SFTP server
     * @param server The server config
     * @return True if connection is successful
     */
    public boolean testConnection(GameServer server) {
        // For special isolation modes, return expected values without testing
        if (server != null) {
            if ("Default Server".equals(server.getName())) {
                logger.info("SFTP connection test skipped for Default Server - returning success by design");
                return true;
            } else if (server.isReadOnly() || "disabled".equalsIgnoreCase(server.getIsolationMode())) {
                logger.info("SFTP connection test skipped for {} server: {} - returning success by design",
                    server.isReadOnly() ? "read-only" : "disabled isolation", server.getName());
                return true;
            }
        }

        // Set isolation context for this operation if we have a valid server
        if (server != null && server.getGuildId() > 0) {
            com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(server.getGuildId(), server.getServerId());
        }
        
        try {
            // Attempt to create a connection using the server credentials
            SftpConnection connection = connect(server);
            if (connection != null) {
                try {
                    // Successfully connected, now properly close the connection
                    if (connection.getChannel() != null && connection.getChannel().isConnected()) {
                        connection.getChannel().disconnect();
                    }
                    if (connection.getSession() != null && connection.getSession().isConnected()) {
                        connection.getSession().disconnect();
                    }
                    
                    logger.info("SFTP connection test successful for server: {}", 
                        server != null ? server.getName() : "unknown");
                    return true;
                } catch (Exception closeEx) {
                    logger.warn("Error while closing SFTP connection during test: {}", closeEx.getMessage());
                    return true; // Connection was successful even if closing had issues
                }
            }
            return false;
        } catch (Exception e) {
            // Check if this is a read-only or disabled server before logging an error
            if (server != null && (server.isReadOnly() || "disabled".equalsIgnoreCase(server.getIsolationMode()))) {
                logger.info("SFTP connection not available for {} server: {}. This is expected and non-blocking.",
                    server.isReadOnly() ? "read-only" : "disabled isolation", server.getName());
            } else if (server != null && "Default Server".equals(server.getName())) {
                logger.info("SFTP connection not available for Default Server. This is expected and non-blocking.");
            } else {
                // Log the detailed error message for better debugging
                logger.warn("SFTP connection failed for server: {}. Error: {}", 
                    server != null ? server.getName() : "unknown", e.getMessage());
                
                // Log complete diagnostic information
                logger.info("SFTP connection failed for server: {}. Settings used for connection attempt:", 
                    server != null ? server.getName() : "unknown");
                    
                if (server != null) {
                    logger.info("  Primary SFTP: host={}, port={}, username={}", 
                        server.getSftpHost() != null ? server.getSftpHost() : "(not set)",
                        server.getSftpPort() > 0 ? server.getSftpPort() : 22,
                        server.getSftpUsername() != null ? server.getSftpUsername() : "(not set)");
                    
                    logger.info("  Fallback: host={}, port={}, username={}", 
                        server.getHost() != null ? server.getHost() : "(not set)",
                        server.getPort(),
                        server.getUsername() != null ? server.getUsername() : "(not set)");
                }
            }
            return false;
        } finally {
            // Always clear isolation context
            if (server != null && server.getGuildId() > 0) {
                com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
            }
        }
    }
    
    /**
     * Ensures the base directory exists on the SFTP server
     * @param server The server config
     * @return True if directory exists or was created
     */
    private boolean ensureDirectoryExists(SftpConnection connection, String directory) {
        try {
            try {
                // Check if directory exists
                connection.getChannel().stat(directory);
                return true;
            } catch (Exception e) {
                // Directory doesn't exist, create it
                connection.getChannel().mkdir(directory);
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to create directory: {}", directory, e);
            return false;
        }
    }
    
    /**
     * List files in a directory
     * @param server The server config
     * @param directory Directory to list
     * @return List of file names
     */
    public List<String> listFiles(GameServer server, String directory) throws Exception {
        // Server validity check
        if (server == null) {
            logger.info("Cannot list files: null server reference");
            return new ArrayList<>();
        }
        
        // Handle restricted isolation mode servers (read-only, disabled, or Default Server)
        if (server.hasRestrictedIsolation()) {
            String isolationMode = server.isDefaultServer() ? "Default Server" :
                                  server.isReadOnly() ? "read-only" :
                                  "disabled isolation";
            
            logger.info("Skipping file listing for {} server: {}",
                isolationMode, server.getName());
            return new ArrayList<>();
        }
        
        // Special handling for Default Server
        if ("Default Server".equals(server.getName())) {
            logger.info("Skipping file listing for Default Server - no SFTP connection required");
            return new ArrayList<>();
        }
        
        try (SftpConnection connection = connect(server)) {
            // If connection is null, return empty list without throwing exception
            if (connection == null) {
                logger.info("Unable to establish SFTP connection for {}, returning empty file list", server.getName());
                return new ArrayList<>();
            }
            
            List<String> files = new ArrayList<>();
            
            // Try to list the directory, create it if it doesn't exist
            try {
                Vector<ChannelSftp.LsEntry> entries = connection.getChannel().ls(directory);
                for (ChannelSftp.LsEntry entry : entries) {
                    String filename = entry.getFilename();
                    if (!filename.equals(".") && !filename.equals("..") && !entry.getAttrs().isDir()) {
                        files.add(filename);
                    }
                }
            } catch (Exception e) {
                // Directory might not exist yet, try to create it
                if (ensureDirectoryExists(connection, directory)) {
                    // Directory created successfully, return empty list
                    logger.info("Created directory {} for server {}", directory, server.getName());
                } else {
                    // Directory creation failed, log but don't throw exception
                    logger.info("Could not access or create directory {} for server {}", 
                        directory, server.getName());
                }
                // Return empty list in both cases to allow processing to continue
                return files;
            }
            
            return files;
        } catch (Exception e) {
            // Log error but don't propagate exception to allow processing to continue
            logger.info("Cannot list files for server {}: {}. Features requiring file access will be skipped.", 
                server.getName(), e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * List all CSV files in the deathlogs directory and subdirectories
     * @param server The server config
     * @return List of CSV file paths
     */
    public List<String> findDeathlogFiles(GameServer server) throws Exception {
        // Server validity check
        if (server == null) {
            logger.info("Cannot find deathlog files: null server reference");
            return new ArrayList<>();
        }
        
        // Handle restricted isolation mode servers (read-only, disabled, or Default Server)
        if (server.hasRestrictedIsolation()) {
            String isolationMode = server.isDefaultServer() ? "Default Server" :
                                  server.isReadOnly() ? "read-only" :
                                  "disabled isolation";
            
            logger.info("Skipping deathlog file search for {} server: {}",
                isolationMode, server.getName());
            return new ArrayList<>();
        }
        
        // Special handling for Default Server
        if ("Default Server".equals(server.getName())) {
            logger.info("Skipping deathlog file search for Default Server - no SFTP connection required");
            return new ArrayList<>();
        }
        
        try (SftpConnection connection = connect(server)) {
            // If connection is null, return empty list without throwing exception
            if (connection == null) {
                logger.info("Unable to establish SFTP connection for {}, returning empty deathlog file list", 
                    server.getName());
                return new ArrayList<>();
            }
            
            String baseDir = server.getDeathlogsDirectory();
            List<String> csvFiles = new ArrayList<>();
            
            // Ensure base directory exists
            try {
                if (ensureDirectoryExists(connection, baseDir)) {
                    // Find all csv files in the directory and subdirectories
                    findCsvFilesRecursively(connection, baseDir, "", csvFiles);
                } else {
                    logger.info("Could not access or create deathlogs directory for server {}", server.getName());
                }
            } catch (Exception e) {
                // Log as info instead of warning to avoid alarming the user
                logger.info("Could not search for deathlog files for server {}: {}", 
                    server.getName(), e.getMessage());
            }
            
            return csvFiles;
        } catch (Exception e) {
            // Log error but don't propagate exception to allow processing to continue
            logger.info("Cannot search for deathlog files for server {}: {}. Features requiring file access will be skipped.", 
                server.getName(), e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * List recent CSV files in the deathlogs directory by date
     * This method is used internally by findRecentDeathlogFiles(server, count)
     */
    private List<String> findRecentCsvFilesByDate(GameServer server, int daysToInclude) throws Exception {
        try (SftpConnection connection = connect(server)) {
            String baseDir = server.getDeathlogsDirectory();
            List<String> recentCsvFiles = new ArrayList<>();
            
            // Calculate cutoff date (default to 7 days if not specified)
            if (daysToInclude <= 0) {
                daysToInclude = 7;
            }
            
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -daysToInclude);
            Date cutoffDate = calendar.getTime();
            
            // Format to match filename date format
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd");
            String cutoffDateStr = dateFormat.format(cutoffDate);
            
            logger.info("Finding recent deathlogs for server {} since {}", server.getName(), cutoffDateStr);
            
            // Ensure base directory exists
            try {
                ensureDirectoryExists(connection, baseDir);
                
                // Find recent csv files in the directory and subdirectories (implementation below)
                findRecentCsvFilesByDate(connection, baseDir, "", recentCsvFiles, cutoffDateStr);
            } catch (Exception e) {
                logger.warn("Could not search for recent deathlog files: {}", e.getMessage());
            }
            
            logger.info("Found {} recent CSV files for server {}", recentCsvFiles.size(), server.getName());
            return recentCsvFiles;
        }
    }
    
    /**
     * Recursively find CSV files in a directory and its subdirectories
     */
    private void findCsvFilesRecursively(SftpConnection connection, String baseDir, String currentPath, List<String> csvFiles) throws Exception {
        String currentDir = currentPath.isEmpty() ? baseDir : baseDir + "/" + currentPath;
        
        Vector<ChannelSftp.LsEntry> entries = connection.getChannel().ls(currentDir);
        for (ChannelSftp.LsEntry entry : entries) {
            String filename = entry.getFilename();
            
            // Skip parent directory entries
            if (filename.equals(".") || filename.equals("..")) {
                continue;
            }
            
            String relativePath = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
            
            if (entry.getAttrs().isDir()) {
                // Recursively search subdirectory
                findCsvFilesRecursively(connection, baseDir, relativePath, csvFiles);
            } else if (filename.toLowerCase().endsWith(".csv")) {
                // Add CSV file to the list
                csvFiles.add(relativePath);
            }
        }
    }
    
    /**
     * Recursively find recent CSV files in a directory and its subdirectories
     * Filters files based on date in the filename (format: yyyy.MM.dd-HH.mm.ss.csv)
     */
    private void findRecentCsvFilesByDate(SftpConnection connection, String baseDir, String currentPath, 
                                            List<String> csvFiles, String cutoffDateStr) throws Exception {
        String currentDir = currentPath.isEmpty() ? baseDir : baseDir + "/" + currentPath;
        
        Vector<ChannelSftp.LsEntry> entries = connection.getChannel().ls(currentDir);
        for (ChannelSftp.LsEntry entry : entries) {
            String filename = entry.getFilename();
            
            // Skip parent directory entries
            if (filename.equals(".") || filename.equals("..")) {
                continue;
            }
            
            String relativePath = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
            
            if (entry.getAttrs().isDir()) {
                // Recursively search subdirectory
                findRecentCsvFilesByDate(connection, baseDir, relativePath, csvFiles, cutoffDateStr);
            } else if (filename.toLowerCase().endsWith(".csv")) {
                // Check if this is a date-formatted CSV file and if it's recent enough
                if (isRecentCsvFile(filename, cutoffDateStr)) {
                    csvFiles.add(relativePath);
                }
            }
        }
    }
    
    /**
     * Check if a CSV file is recent enough based on its filename date format
     * Expected format: yyyy.MM.dd-HH.mm.ss.csv
     * 
     * @param filename The filename to check
     * @param cutoffDateStr The cutoff date in yyyy.MM.dd format
     * @return true if the file is recent enough to include
     */
    private boolean isRecentCsvFile(String filename, String cutoffDateStr) {
        try {
            // Extract date from filename (expected format: yyyy.MM.dd-HH.mm.ss.csv)
            if (filename.length() < 10) {
                return false; // Not long enough to contain a date
            }
            
            // Try to get the date part (expected at start of filename)
            String datePartOfFilename = filename.substring(0, 10); // yyyy.MM.dd
            
            // If date part is before cutoff, it's too old
            return datePartOfFilename.compareTo(cutoffDateStr) >= 0;
        } catch (Exception e) {
            // If we can't parse the date, include the file to be safe
            return true;
        }
    }
    
    /**
     * Read a file from SFTP
     * @param server The server config
     * @param filePath Path to the file
     * @return The file content as a string
     */
    public String readFile(GameServer server, String filePath) throws Exception {
        // Server validity check
        if (server == null) {
            logger.info("Cannot read file: null server reference");
            return "";
        }
        
        // Handle restricted isolation mode servers (read-only, disabled, or Default Server)
        if (server.hasRestrictedIsolation()) {
            String isolationMode = server.isDefaultServer() ? "Default Server" :
                                  server.isReadOnly() ? "read-only" :
                                  "disabled isolation";
            
            logger.info("Skipping file read for {} server: {}",
                isolationMode, server.getName());
            return "";
        }
        
        // Special handling for Default Server
        if ("Default Server".equals(server.getName())) {
            logger.info("Skipping file read for Default Server - no SFTP connection required");
            return "";
        }
        
        try (SftpConnection connection = connect(server)) {
            // If connection is null, return empty string without throwing exception
            if (connection == null) {
                logger.info("Unable to establish SFTP connection for {}, cannot read file {}", 
                    server.getName(), filePath);
                return "";
            }
            
            try (InputStream inputStream = connection.getChannel().get(filePath);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                IOUtils.copy(inputStream, outputStream);
                return outputStream.toString(StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Log as info instead of error to avoid alarming the user
                logger.info("Could not read file {} for server {}: {}", 
                    filePath, server.getName(), e.getMessage());
                return "";
            }
        } catch (Exception e) {
            // Log error but don't propagate exception to allow processing to continue
            logger.info("Cannot access SFTP for server {} to read file {}: {}. Features requiring file access will be skipped.", 
                server.getName(), filePath, e.getMessage());
            return "";
        }
    }
    
    /**
     * Write content to a file on the server
     * @param server The server config
     * @param filePath Path to the file
     * @param content Content to write
     * @throws Exception If an error occurs
     */
    public void writeFile(GameServer server, String filePath, String content) throws Exception {
        try (SftpConnection connection = connect(server)) {
            // Ensure parent directory exists
            String parentPath = new java.io.File(filePath).getParent().replace("\\", "/");
            try {
                connection.getChannel().mkdir(parentPath);
            } catch (Exception e) {
                // Directory may already exist, or we need to create parent directories
                if (e.getMessage() != null && e.getMessage().contains("No such file")) {
                    // Try to create parent directories recursively
                    String[] pathParts = parentPath.split("/");
                    StringBuilder currentPath = new StringBuilder();
                    for (String part : pathParts) {
                        if (!part.isEmpty()) {
                            currentPath.append("/").append(part);
                            try {
                                connection.getChannel().mkdir(currentPath.toString());
                            } catch (Exception ex) {
                                // Ignore if directory already exists
                            }
                        }
                    }
                }
            }
            
            // Write the file
            try (InputStream inputStream = new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                connection.getChannel().put(inputStream, filePath);
            }
        }
    }
    
    /**
     * Read a file from the logs directory
     * @param server The server config
     * @param filename Name of the log file
     * @return The file content as a string
     */
    public String readLogFile(GameServer server, String filename) throws Exception {
        String filePath = server.getLogDirectory() + "/" + filename;
        return readFile(server, filePath);
    }
    
    /**
     * Read a deathlog CSV file
     * @param server The server config
     * @param filename Name of the CSV file (including subdirectory path)
     * @return The file content as a string
     */
    public String readDeathlogFile(GameServer server, String filename) throws Exception {
        String filePath = server.getDeathlogsDirectory() + "/" + filename;
        return readFile(server, filePath);
    }
    
    /**
     * Read lines from a file after a specific line number
     * @param server The server config
     * @param filePath Path to the file
     * @param afterLine Only read lines after this line number
     * @return The new lines
     */
    public List<String> readLinesAfter(GameServer server, String filePath, long afterLine) throws Exception {
        String content = readFile(server, filePath);
        String[] allLines = content.split("\n");
        
        List<String> newLines = new ArrayList<>();
        for (int i = 0; i < allLines.length; i++) {
            if (i > afterLine) {
                newLines.add(allLines[i]);
            }
        }
        
        return newLines;
    }
    
    /**
     * Read lines from a log file after a specific line number
     * @param server The server config
     * @param filename Name of the log file
     * @param afterLine Only read lines after this line number
     * @return The new lines
     */
    public List<String> readLogLinesAfter(GameServer server, String filename, long afterLine) throws Exception {
        String filePath = server.getLogDirectory() + "/" + filename;
        return readLinesAfter(server, filePath, afterLine);
    }
    
    /**
     * List only the most recent CSV files in the deathlogs directory and subdirectories
     * This can filter by count (most recent N files) or by date range
     * 
     * @param server The server config
     * @param count Number of most recent files to return, or if negative, the number of days to include
     * @return List of CSV file paths
     */
    public List<String> findRecentDeathlogFiles(GameServer server, int count) throws Exception {
        // If count is negative, use date-based approach (days back)
        if (count < 0) {
            return findRecentCsvFilesByDate(server, Math.abs(count));
        }
        
        // Otherwise use count-based approach (most recent N files)
        List<String> allFiles = findDeathlogFiles(server);
        if (allFiles.isEmpty() || count >= allFiles.size()) {
            return allFiles;
        }
        
        // Sort all files (should be date-based names)
        Collections.sort(allFiles);
        
        // Return only the most recent files
        return allFiles.subList(allFiles.size() - count, allFiles.size());
    }
    
    /**
     * Read lines from a deathlog CSV file after a specific line number
     * @param server The server config
     * @param filename Name of the CSV file (including subdirectory path)
     * @param afterLine Only read lines after this line number
     * @return The new lines
     */
    public List<String> readDeathlogLinesAfter(GameServer server, String filename, long afterLine) throws Exception {
        String filePath = server.getDeathlogsDirectory() + "/" + filename;
        return readLinesAfter(server, filePath, afterLine);
    }
    
    /**
     * Holder class for SFTP session and channel
     */
    private static class SftpConnection implements AutoCloseable {
        private final Session session;
        private final ChannelSftp channel;
        
        public SftpConnection(Session session, ChannelSftp channel) {
            this.session = session;
            this.channel = channel;
        }
        
        public Session getSession() {
            return session;
        }
        
        public ChannelSftp getChannel() {
            return channel;
        }
        
        @Override
        public void close() {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * Check if a file exists on the remote server
     * @param server The game server configuration with proper isolation metadata
     * @param remotePath The path to the file on the remote server
     * @return True if the file exists, false otherwise
     */
    public boolean fileExists(GameServer server, String remotePath) {
        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            session = createSession(server);
            if (session == null) {
                return false;
            }
            
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(timeout);
            
            // Try to get the file's attributes - if it doesn't exist, an exception will be thrown
            channelSftp.lstat(remotePath);
            return true;
        } catch (Exception e) {
            // If exception is thrown, file doesn't exist or is not accessible
            logger.debug("File does not exist or is not accessible: {}", remotePath);
            return false;
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * Get the content of a file as a string with proper isolation
     * @param server The game server with proper isolation metadata
     * @param remotePath The path to the file on the remote server
     * @return The file content as a string, or null if the file doesn't exist
     */
    /**
     * Create an SFTP session with proper guild isolation
     * This method enforces proper data boundaries between Discord servers
     * 
     * @param server The server to connect to
     * @return The SFTP session, or null if connection failed or server lacks proper isolation
     */
    private Session createSession(GameServer server) {
        try {
            // Verify server has proper isolation fields
            if (server == null || server.getGuildId() <= 0) {
                logger.warn("Attempted to create SFTP session without proper guild isolation for server {}", 
                    server != null ? server.getName() : "null");
                return null;
            }
            
            // Set isolation context for this operation
            long guildId = server.getGuildId();
            String serverId = server.getServerId();
            
            // Set isolation context if possible
            if (guildId > 0) {
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, serverId);
                try {
                    // Special handling for Default Server or restricted servers
                    if ("Default Server".equals(server.getName())) {
                        logger.info("Server Default Server has proper isolation but SFTP connections are not needed (Guild={})", guildId);
                        return null;
                    } else if (server.isReadOnly() || "disabled".equalsIgnoreCase(server.getIsolationMode())) {
                        logger.info("Server {} is in {} mode - SFTP connection skipped intentionally (Guild={})",
                            server.getName(), 
                            server.isReadOnly() ? "read-only" : "disabled isolation", 
                            guildId);
                        return null;
                    }
                    
                    // First try to connect with dedicated SFTP credentials if available
                    Session session = null;
                    if (server.isUseSftpForLogs() && server.getSftpHost() != null && !server.getSftpHost().trim().isEmpty()) {
                        try {
                            String sftp_user = server.getSftpUsername() != null && !server.getSftpUsername().trim().isEmpty() ? 
                                server.getSftpUsername() : server.getUsername();
                            
                            String sftp_password = server.getSftpPassword() != null && !server.getSftpPassword().trim().isEmpty() ? 
                                server.getSftpPassword() : server.getPassword();
                                
                            int sftp_port = server.getSftpPort() > 0 ? server.getSftpPort() : 22;
                            
                            logger.info("Attempting SFTP connection to {} using dedicated SFTP credentials for server {}", 
                                server.getSftpHost(), server.getName());
                            
                            JSch jsch = new JSch();
                            session = jsch.getSession(sftp_user, server.getSftpHost(), sftp_port);
                            session.setPassword(sftp_password);
                            
                            Properties config = new Properties();
                            config.put("StrictHostKeyChecking", "no");
                            session.setConfig(config);
                            
                            session.connect(timeout);
                            logger.info("Successfully connected to SFTP server {} with dedicated credentials", server.getSftpHost());
                            
                            return session;
                        } catch (Exception e) {
                            logger.warn("Failed to connect with SFTP-specific credentials: {}. Will try fallback credentials.", e.getMessage());
                            
                            // Close session if it was created but not connected
                            if (session != null && session.isConnected()) {
                                session.disconnect();
                                session = null;
                            }
                        }
                    }
                    
                    // Fallback to standard credentials if SFTP-specific credentials failed or weren't provided
                    if (server.getHost() != null && !server.getHost().trim().isEmpty() && 
                        server.getUsername() != null && !server.getUsername().trim().isEmpty()) {
                        
                        try {
                            logger.info("Attempting fallback SFTP connection to {}:{} with username {} for server {}",
                                server.getHost(), server.getPort(), server.getUsername(), server.getName());
                            
                            JSch jsch = new JSch();
                            session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
                            
                            if (server.getPassword() != null && !server.getPassword().isEmpty()) {
                                session.setPassword(server.getPassword());
                            }
                            
                            Properties config = new Properties();
                            config.put("StrictHostKeyChecking", "no");
                            session.setConfig(config);
                            
                            session.connect(timeout);
                            logger.info("Successfully connected to SFTP server {}:{} with fallback credentials", 
                                server.getHost(), server.getPort());
                            
                            return session;
                        } catch (Exception e) {
                            logger.error("All SFTP connection attempts failed for server {}: {}", 
                                server.getName(), e.getMessage());
                            return null;
                        }
                    } else {
                        logger.warn("No valid SFTP credentials available for server {}", server.getName());
                        return null;
                    }
                } finally {
                    // Always clear isolation context when done to prevent leaks
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            } else {
                logger.error("Cannot create SFTP session for server {} due to missing guild ID", server.getName());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error creating SFTP session for server {}: {}", 
                server != null ? server.getName() : "null", e.getMessage());
            return null;
        }
    }
    
    public String getFileContent(GameServer server, String remotePath) {
        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            // Verify server has proper isolation fields
            if (server.getGuildId() <= 0) {
                logger.warn("Attempted to access file without proper guild isolation: {} in server {}", 
                    remotePath, server.getName());
                return null;
            }
            
            session = createSession(server);
            if (session == null) {
                return null;
            }
            
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(timeout);
            
            try (InputStream inputStream = channelSftp.get(remotePath);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                IOUtils.copy(inputStream, outputStream);
                return outputStream.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            logger.error("Error getting content of file: {} from server {}", 
                remotePath, server.getName(), e);
            return null;
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * Get the size of a file with proper isolation
     * @param server The game server with proper isolation metadata
     * @param remotePath The path to the file on the remote server
     * @return The file size in bytes, or -1 if the file doesn't exist
     */
    public long getFileSize(GameServer server, String remotePath) {
        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            // Verify server has proper isolation fields
            if (server.getGuildId() <= 0) {
                logger.warn("Attempted to access file without proper guild isolation: {} in server {}", 
                    remotePath, server.getName());
                return -1;
            }
            
            session = createSession(server);
            if (session == null) {
                return -1;
            }
            
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(timeout);
            
            // Get file attributes to get the size
            return channelSftp.lstat(remotePath).getSize();
        } catch (Exception e) {
            logger.error("Error getting size of file: {} from server {}", 
                remotePath, server.getName(), e);
            return -1;
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * Get the last modified timestamp of a file with proper isolation
     * @param server The game server with proper isolation metadata
     * @param remotePath The path to the file on the remote server
     * @return The last modified timestamp in milliseconds, or -1 if the file doesn't exist
     */
    public long getLastModified(GameServer server, String remotePath) {
        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            // Verify server has proper isolation fields
            if (server.getGuildId() <= 0) {
                logger.warn("Attempted to access file without proper guild isolation: {} in server {}", 
                    remotePath, server.getName());
                return -1;
            }
            
            session = createSession(server);
            if (session == null) {
                return -1;
            }
            
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(timeout);
            
            // Get file attributes to get the modified time
            return channelSftp.lstat(remotePath).getMTime() * 1000L; // Convert from seconds to milliseconds
        } catch (Exception e) {
            logger.error("Error getting last modified time of file: {} from server {}", 
                remotePath, server.getName(), e);
            return -1;
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}
