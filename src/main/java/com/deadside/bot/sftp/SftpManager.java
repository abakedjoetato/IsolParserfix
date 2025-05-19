package com.deadside.bot.sftp;

import com.deadside.bot.db.models.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for SFTP operations
 */
public class SftpManager {
    private static final Logger logger = LoggerFactory.getLogger(SftpManager.class);
    private final SftpConnector connector;
    
    public SftpManager() {
        this.connector = new SftpConnector();
    }
    
    /**
     * Get the SFTP connector instance
     * @return The SftpConnector used by this manager
     */
    public SftpConnector getSftpConnector() {
        return this.connector;
    }
    
    /**
     * Test connection to an SFTP server
     */
    public boolean testConnection(GameServer server) {
        try {
            return connector.testConnection(server);
        } catch (Exception e) {
            logger.error("Error testing connection to server: {}", server.getName(), e);
            return false;
        }
    }
    
    /**
     * Get killfeed CSV files for a server
     */
    public List<String> getKillfeedFiles(GameServer server) {
        try {
            // Search all CSV files in the deathlogs directory and subdirectories
            return connector.findDeathlogFiles(server);
        } catch (Exception e) {
            logger.error("Error listing killfeed files for server: {}", server.getName(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get server log files
     */
    public List<String> getLogFiles(GameServer server) {
        try {
            String logDir = server.getLogDirectory();
            return connector.listFiles(server, logDir);
        } catch (Exception e) {
            logger.error("Error listing log files for server: {}", server.getName(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Read a killfeed file
     */
    public String readKillfeedFile(GameServer server, String filename) {
        try {
            return connector.readDeathlogFile(server, filename);
        } catch (Exception e) {
            logger.error("Error reading killfeed file {} for server: {}", filename, server.getName(), e);
            return "";
        }
    }
    
    /**
     * Read a log file
     */
    public String readLogFile(GameServer server, String filename) {
        try {
            return connector.readLogFile(server, filename);
        } catch (Exception e) {
            logger.error("Error reading log file {} for server: {}", filename, server.getName(), e);
            return "";
        }
    }
    
    /**
     * Read new lines from a killfeed file since last check
     */
    public List<String> readNewKillfeedLines(GameServer server, String filename, long lastProcessedLine) {
        try {
            return connector.readDeathlogLinesAfter(server, filename, lastProcessedLine);
        } catch (Exception e) {
            logger.error("Error reading new killfeed lines from {} for server: {}", filename, server.getName(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Read new lines from a log file since last check
     */
    public List<String> readNewLogLines(GameServer server, String filename, long lastProcessedLine) {
        try {
            return connector.readLogLinesAfter(server, filename, lastProcessedLine);
        } catch (Exception e) {
            logger.error("Error reading new log lines from {} for server: {}", filename, server.getName(), e);
            return new ArrayList<>();
        }
    }
}
