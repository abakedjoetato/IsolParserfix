package com.deadside.bot.parsers;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.sftp.SftpManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Deadside server log files
 */
public class LogParser {
    private static final Logger logger = LoggerFactory.getLogger(LogParser.class);
    private final SftpManager sftpManager;
    
    // Log patterns
    private static final Pattern JOIN_PATTERN = Pattern.compile("\\[(\\d+\\.\\d+\\.\\d+)-(\\d+:\\d+:\\d+)\\] Player (.*) joined the server");
    private static final Pattern LEAVE_PATTERN = Pattern.compile("\\[(\\d+\\.\\d+\\.\\d+)-(\\d+:\\d+:\\d+)\\] Player (.*) left the server");
    private static final Pattern EVENT_PATTERN = Pattern.compile("\\[(\\d+\\.\\d+\\.\\d+)-(\\d+:\\d+:\\d+)\\] (Mission|Event|Trader|Crash) (.*)");
    
    public LogParser() {
        this.sftpManager = new SftpManager();
    }
    
    /**
     * Process logs for a server
     * @param server The game server to process
     * @return Number of new log events processed
     */
    public int processServer(GameServer server) {
        try {
            // Get log files
            List<String> files = sftpManager.getLogFiles(server);
            if (files.isEmpty()) {
                logger.warn("No log files found for server: {}", server.getName());
                return 0;
            }
            
            // Filter to just Deadside.log files
            List<String> logFiles = new ArrayList<>();
            for (String file : files) {
                if (file.equals("Deadside.log")) {
                    logFiles.add(file);
                }
            }
            
            if (logFiles.isEmpty()) {
                logger.warn("No Deadside.log found for server: {}", server.getName());
                return 0;
            }
            
            // Sort files (should only be one, but in case there are more)
            Collections.sort(logFiles);
            
            String lastProcessedFile = server.getLastProcessedLogFile();
            long lastProcessedLine = server.getLastProcessedLogLine();
            
            // If no file has been processed yet, start with the newest file
            if (lastProcessedFile.isEmpty()) {
                lastProcessedFile = logFiles.get(logFiles.size() - 1);
                lastProcessedLine = -1;
            }
            
            // Check if we need to move to a newer file
            int fileIndex = logFiles.indexOf(lastProcessedFile);
            if (fileIndex < 0) {
                // File no longer exists, start with the newest file
                lastProcessedFile = logFiles.get(logFiles.size() - 1);
                lastProcessedLine = -1;
            } else if (fileIndex < logFiles.size() - 1) {
                // Newer files available, move to the next one
                lastProcessedFile = logFiles.get(fileIndex + 1);
                lastProcessedLine = -1;
            }
            
            // Process the file
            String fileContent = sftpManager.readLogFile(server, lastProcessedFile);
            if (fileContent.isEmpty()) {
                logger.warn("Empty or unreadable log file: {} for server: {}", 
                        lastProcessedFile, server.getName());
                return 0;
            }
            
            String[] lines = fileContent.split("\n");
            int processedEvents = 0;
            
            // Process each line after the last processed line
            for (int i = 0; i < lines.length; i++) {
                if (i <= lastProcessedLine) continue;
                
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                
                if (parseLogLine(line)) {
                    processedEvents++;
                }
                
                lastProcessedLine = i;
            }
            
            // Update server progress
            server.updateLogProgress(lastProcessedFile, lastProcessedLine);
            
            logger.info("Processed {} new log events for server: {}", processedEvents, server.getName());
            return processedEvents;
        } catch (Exception e) {
            logger.error("Error processing logs for server: {}", server.getName(), e);
            return 0;
        }
    }
    
    /**
     * Parse a log line and extract events
     * @param line The log line
     * @return True if an event was found
     */
    private boolean parseLogLine(String line) {
        // Check for joins
        Matcher joinMatcher = JOIN_PATTERN.matcher(line);
        if (joinMatcher.matches()) {
            String date = joinMatcher.group(1);
            String time = joinMatcher.group(2);
            String playerName = joinMatcher.group(3);
            
            logger.debug("Player joined: {} at {}-{}", playerName, date, time);
            return true;
        }
        
        // Check for leaves
        Matcher leaveMatcher = LEAVE_PATTERN.matcher(line);
        if (leaveMatcher.matches()) {
            String date = leaveMatcher.group(1);
            String time = leaveMatcher.group(2);
            String playerName = leaveMatcher.group(3);
            
            logger.debug("Player left: {} at {}-{}", playerName, date, time);
            return true;
        }
        
        // Check for events
        Matcher eventMatcher = EVENT_PATTERN.matcher(line);
        if (eventMatcher.matches()) {
            String date = eventMatcher.group(1);
            String time = eventMatcher.group(2);
            String eventType = eventMatcher.group(3);
            String eventDetails = eventMatcher.group(4);
            
            logger.debug("{} event: {} at {}-{}", eventType, eventDetails, date, time);
            return true;
        }
        
        return false;
    }
}
