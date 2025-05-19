package com.deadside.bot.utils;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages parser state to coordinate between different parser components
 */
public class ParserStateManager {
    private static final Logger logger = LoggerFactory.getLogger(ParserStateManager.class);
    private static boolean processingHistoricalData = false;
    
    // Track parser state by server (key = serverName:guildId)
    private static final Map<String, ParserState> killfeedParsers = new HashMap<>();
    private static final Map<String, ParserState> csvParsers = new HashMap<>();
    
    /**
     * Check if the parser is currently processing historical data
     */
    public static boolean isProcessingHistoricalData() {
        return processingHistoricalData;
    }
    
    /**
     * Set the historical data processing state
     */
    public static void setProcessingHistoricalData(boolean state) {
        processingHistoricalData = state;
    }
    
    /**
     * Generate a unique key for server state tracking
     */
    private static String getServerKey(String serverName, long guildId) {
        return serverName + ":" + guildId;
    }
    
    /**
     * Pause the killfeed parser for a server
     */
    public static void pauseKillfeedParser(String serverName, long guildId, String reason) {
        String key = getServerKey(serverName, guildId);
        killfeedParsers.put(key, new ParserState(false, reason));
        logger.info("Paused killfeed parser for server '{}' in guild {}: {}", 
                serverName, guildId, reason);
    }
    
    /**
     * Resume the killfeed parser for a server
     */
    public static void resumeKillfeedParser(String serverName, long guildId) {
        String key = getServerKey(serverName, guildId);
        if (killfeedParsers.containsKey(key)) {
            killfeedParsers.remove(key);
            logger.info("Resumed killfeed parser for server '{}' in guild {}", 
                    serverName, guildId);
        }
    }
    
    /**
     * Check if the killfeed parser is paused for a server
     */
    public static boolean isKillfeedParserPaused(String serverName, long guildId) {
        String key = getServerKey(serverName, guildId);
        return killfeedParsers.containsKey(key);
    }
    
    /**
     * Get the reason why a killfeed parser is paused
     */
    public static String getKillfeedPauseReason(String serverName, long guildId) {
        String key = getServerKey(serverName, guildId);
        ParserState state = killfeedParsers.get(key);
        return state != null ? state.getReason() : null;
    }
    
    /**
     * Pause the CSV parser for a server
     */
    public static void pauseCSVParser(String serverName, long guildId, String reason) {
        String key = getServerKey(serverName, guildId);
        csvParsers.put(key, new ParserState(false, reason));
        logger.info("Paused CSV parser for server '{}' in guild {}: {}", 
                serverName, guildId, reason);
    }
    
    /**
     * Resume the CSV parser for a server
     */
    public static void resumeCSVParser(String serverName, long guildId) {
        String key = getServerKey(serverName, guildId);
        if (csvParsers.containsKey(key)) {
            csvParsers.remove(key);
            logger.info("Resumed CSV parser for server '{}' in guild {}", 
                    serverName, guildId);
        }
    }
    
    /**
     * Check if the CSV parser is paused for a server
     */
    public static boolean isCSVParserPaused(String serverName, long guildId) {
        String key = getServerKey(serverName, guildId);
        return csvParsers.containsKey(key);
    }
    
    /**
     * Get the reason why a CSV parser is paused
     */
    public static String getCSVPauseReason(String serverName, long guildId) {
        String key = getServerKey(serverName, guildId);
        ParserState state = csvParsers.get(key);
        return state != null ? state.getReason() : null;
    }
    
    /**
     * Reset all parser state for a server
     */
    public static void resetParserState(String serverName, long guildId) {
        String key = getServerKey(serverName, guildId);
        killfeedParsers.remove(key);
        csvParsers.remove(key);
        logger.info("Reset parser state for server '{}' in guild {}", 
                serverName, guildId);
    }
    
    /**
     * Get overall parser status for a server
     */
    public static boolean isAnyParserPaused(String serverName, long guildId) {
        return isKillfeedParserPaused(serverName, guildId) || 
               isCSVParserPaused(serverName, guildId);
    }
    
    /**
     * Inner class to track parser state
     */
    private static class ParserState {
        private final boolean active;
        private final String reason;
        
        public ParserState(boolean active, String reason) {
            this.active = active;
            this.reason = reason;
        }
        
        public boolean isActive() {
            return active;
        }
        
        public String getReason() {
            return reason;
        }
    }
}