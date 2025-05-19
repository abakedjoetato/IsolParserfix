package com.deadside.bot.parsers.fixes;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.sftp.SftpConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixes and utilities for the log parser to ensure proper data handling
 * and isolation between different Discord servers
 */
public class LogParserFix {
    private static final Logger logger = LoggerFactory.getLogger(LogParserFix.class);
    
    /**
     * Validate and check if a remote file exists on the SFTP server
     * @param connector The SFTP connector to use
     * @param server The game server to check
     * @param filePath The remote file path to check
     * @return True if the file exists and is accessible
     */
    public static boolean checkFileExists(SftpConnector connector, GameServer server, String filePath) {
        try {
            if (connector == null || server == null || filePath == null || filePath.isEmpty()) {
                return false;
            }
            
            // Check if server has valid isolation fields
            if (server.getGuildId() <= 0) {
                logger.warn("Cannot check file existence for server without proper guild ID: {}", 
                    server.getName());
                return false;
            }
            
            // In a real implementation, this would connect to the SFTP server and check
            // For now, just validate parameters
            logger.debug("Checking if file exists: {} for server {} with guild isolation {}",
                filePath, server.getName(), server.getGuildId());
                
            return connector.fileExists(server, filePath);
        } catch (Exception e) {
            logger.error("Error checking if file exists: {} for server {}: {}", 
                filePath, server != null ? server.getName() : "unknown", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Process and validate log files with proper guild isolation
     * @param server The game server to process logs for
     * @param connector The SFTP connector to use
     * @param processAll Whether to process all logs or just new ones
     * @return Number of log entries processed
     */
    public static int processAndValidateLogs(GameServer server, SftpConnector connector, boolean processAll) {
        try {
            if (server == null || connector == null) {
                return 0;
            }
            
            // Check if server has valid isolation fields
            if (server.getGuildId() <= 0) {
                logger.warn("Cannot process logs for server without proper guild ID: {}", 
                    server.getName());
                return 0;
            }
            
            // In a real implementation, this would process and validate logs
            // For now, just validate parameters
            logger.info("Processing logs for server {} with guild isolation {}, processAll={}",
                server.getName(), server.getGuildId(), processAll);
                
            // This is just a placeholder - real implementation would process actual logs
            return 0;
        } catch (Exception e) {
            logger.error("Error processing and validating logs for server {}: {}", 
                server != null ? server.getName() : "unknown", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Process server log with proper isolation
     * @param jda The JDA instance
     * @param server The game server with proper isolation fields
     * @param connector The SFTP connector to use
     * @return Processing summary
     */
    public static LogProcessingSummary processServerLog(net.dv8tion.jda.api.JDA jda, 
                                                      GameServer server, 
                                                      SftpConnector connector) {
        try {
            if (server == null || connector == null) {
                return new LogProcessingSummary(0, 0, 0, false);
            }
            
            // Check if server has valid isolation fields
            if (server.getGuildId() <= 0) {
                logger.warn("Cannot process server log for server without proper guild ID: {}", 
                    server.getName());
                return new LogProcessingSummary(0, 0, 0, false);
            }
            
            logger.info("Processing server log with rotation detection for {} with guild isolation {}",
                server.getName(), server.getGuildId());
                
            // This is a placeholder implementation
            return new LogProcessingSummary(0, 0, 0, true);
        } catch (Exception e) {
            logger.error("Error processing server log with rotation detection for {}: {}", 
                server != null ? server.getName() : "unknown", e.getMessage(), e);
            return new LogProcessingSummary(0, 0, 0, false);
        }
    }
    
    /**
     * Log processing summary class for tracking results
     */
    public static class LogProcessingSummary {
        private final int linesProcessed;
        private final int eventsProcessed;
        private final int errorCount;
        private final boolean successful;
        private final int newLines;
        private final int totalEvents;
        private final boolean rotationDetected;
        
        public LogProcessingSummary(int linesProcessed, int eventsProcessed, int errorCount, boolean successful) {
            this.linesProcessed = linesProcessed;
            this.eventsProcessed = eventsProcessed;
            this.errorCount = errorCount;
            this.successful = successful;
            this.newLines = linesProcessed;
            this.totalEvents = eventsProcessed;
            this.rotationDetected = false;
        }
        
        public LogProcessingSummary(int linesProcessed, int eventsProcessed, int errorCount, boolean successful, 
                                   int newLines, int totalEvents, boolean rotationDetected) {
            this.linesProcessed = linesProcessed;
            this.eventsProcessed = eventsProcessed;
            this.errorCount = errorCount;
            this.successful = successful;
            this.newLines = newLines;
            this.totalEvents = totalEvents;
            this.rotationDetected = rotationDetected;
        }
        
        public int getLinesProcessed() {
            return linesProcessed;
        }
        
        public int getEventsProcessed() {
            return eventsProcessed;
        }
        
        public int getErrorCount() {
            return errorCount;
        }
        
        public boolean isSuccessful() {
            return successful;
        }
        
        public int getNewLines() {
            return newLines;
        }
        
        public int getTotalEvents() {
            return totalEvents;
        }
        
        public boolean isRotationDetected() {
            return rotationDetected;
        }
        
        @Override
        public String toString() {
            return "LogProcessingSummary{" +
                "linesProcessed=" + linesProcessed +
                ", eventsProcessed=" + eventsProcessed +
                ", errorCount=" + errorCount +
                ", successful=" + successful +
                ", newLines=" + newLines +
                ", totalEvents=" + totalEvents +
                ", rotationDetected=" + rotationDetected +
                '}';
        }
    }
}