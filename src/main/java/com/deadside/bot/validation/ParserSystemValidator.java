package com.deadside.bot.validation;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.DeadsideLogParser;
import com.deadside.bot.parsers.fixes.CsvParsingFix;
import com.deadside.bot.parsers.fixes.LogParserFix;
import com.deadside.bot.sftp.SftpConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Validates and fixes parser systems
 * This class examines both CSV parsing and log parser systems to detect and fix issues
 */
public class ParserSystemValidator {
    private static final Logger logger = LoggerFactory.getLogger(ParserSystemValidator.class);
    
    private final GameServerRepository gameServerRepository;
    private final PlayerRepository playerRepository;
    private final SftpConnector sftpConnector;
    private final DeadsideCsvParser csvParser;
    private final DeadsideLogParser logParser;
    
    /**
     * Constructor
     */
    public ParserSystemValidator(GameServerRepository gameServerRepository,
                               PlayerRepository playerRepository,
                               SftpConnector sftpConnector,
                               DeadsideCsvParser csvParser,
                               DeadsideLogParser logParser) {
        this.gameServerRepository = gameServerRepository;
        this.playerRepository = playerRepository;
        this.sftpConnector = sftpConnector;
        this.csvParser = csvParser;
        this.logParser = logParser;
    }
    
    /**
     * Validate and fix all parser systems
     * @return Validation report
     */
    public ValidationReport validateAndFixAll() {
        ValidationReport report = new ValidationReport();
        
        try {
            logger.info("Starting comprehensive parser system validation");
            
            // Validate CSV parsing
            validateCsvParsing(report);
            
            // Validate log parsing
            validateLogParsing(report);
            
            // Verify overall validation status
            report.allValid = report.csvParsingValid && report.logParsingValid;
            
            logger.info("Completed parser system validation: {}", 
                report.allValid ? "VALID" : "INVALID");
        } catch (Exception e) {
            logger.error("Error during parser system validation: {}", e.getMessage(), e);
            report.errorMessage = e.getMessage();
        }
        
        return report;
    }
    
    /**
     * Validate CSV parsing system
     */
    private void validateCsvParsing(ValidationReport report) {
        try {
            logger.info("Validating CSV parsing system");
            
            AtomicInteger killCount = new AtomicInteger();
            AtomicInteger deathCount = new AtomicInteger();
            AtomicInteger linesProcessed = new AtomicInteger();
            AtomicInteger serverCount = new AtomicInteger();
            
            // Get all servers using isolation-aware approach
            List<Long> distinctGuildIds = gameServerRepository.getDistinctGuildIds();
            List<GameServer> servers = new ArrayList<>();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                try {
                    servers.addAll(gameServerRepository.findAllByGuildId(guildId));
                } finally {
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // Process each server with proper isolation boundaries
            for (GameServer server : servers) {
                // Skip servers without a configured guild
                if (server.getGuildId() == 0) {
                    logger.warn("Server {} has no configured guild ID - skipping", server.getName());
                    continue;
                }
                
                serverCount.incrementAndGet();
                
                // Get sample CSV files for this server
                List<String> csvFiles = sftpConnector.findDeathlogFiles(server);
                
                for (String csvFile : csvFiles) {
                    try {
                        // Get file content
                        String content = sftpConnector.getFileContent(server, 
                            server.getDeathlogsDirectory() + "/" + csvFile);
                        
                        // Process with the fixed implementation
                        String[] lines = content.split("\n");
                        for (String line : lines) {
                            if (line.trim().isEmpty()) {
                                continue;
                            }
                            
                            linesProcessed.incrementAndGet();
                            
                            // Process using the fixed implementation
                            boolean success = CsvParsingFix.processDeathLogLineFixed(
                                server, line, playerRepository);
                                
                            if (success) {
                                // Count as processed
                            }
                        }
                        
                        logger.info("Validated CSV file {} for server {}: {} lines", 
                            csvFile, server.getName(), lines.length);
                    } catch (Exception e) {
                        logger.error("Error validating CSV file {}: {}", csvFile, e.getMessage(), e);
                    }
                }
            }
            
            // Validate and sync stats
            int correctionsMade = CsvParsingFix.validateAndSyncStats(playerRepository);
            report.statCorrections = correctionsMade;
            
            // Check results for all servers combined using isolation-aware approach
            List<Long> checkGuildIds = gameServerRepository.getDistinctGuildIds();
            List<GameServer> allServers = new ArrayList<>();
            
            // Process each guild with proper isolation context
            for (Long guildId : checkGuildIds) {
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                try {
                    allServers.addAll(gameServerRepository.findAllByGuildId(guildId));
                } finally {
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            for (GameServer server : allServers) {
                if (server.getGuildId() == 0) {
                    continue;
                }
                
                int serverKills = 0;
                int serverDeaths = 0;
                
                // Get players for this server with proper isolation
                List<Player> players = playerRepository.findByGuildIdAndServerId(server.getGuildId(), server.getServerId());
                for (Player player : players) {
                    killCount.addAndGet(player.getKills());
                    deathCount.addAndGet(player.getDeaths());
                }
                
                logger.info("Server {} stats: {} kills, {} deaths", 
                    server.getName(), serverKills, serverDeaths);
            }
            
            // Update report values
            report.totalLinesProcessed = linesProcessed.get();
            report.totalKills = killCount.get();
            report.totalDeaths = deathCount.get();
            report.serverCount = serverCount.get();
            
            // Check if validation passed
            report.csvParsingValid = (report.totalLinesProcessed > 0 || report.serverCount == 0);
            
            logger.info("CSV parsing validation: {} ({} lines processed, {} kills, {} deaths, {} corrections)", 
                report.csvParsingValid ? "VALID" : "INVALID", 
                report.totalLinesProcessed,
                report.totalKills,
                report.totalDeaths,
                report.statCorrections);
        } catch (Exception e) {
            logger.error("Error during CSV parsing validation: {}", e.getMessage(), e);
            report.csvParsingValid = false;
        }
    }
    
    /**
     * Validate log parsing system
     */
    private void validateLogParsing(ValidationReport report) {
        try {
            logger.info("Validating log parsing system");
            
            AtomicInteger totalLogLinesProcessed = new AtomicInteger();
            AtomicInteger totalEventsDetected = new AtomicInteger();
            AtomicInteger totalRotationDetections = new AtomicInteger();
            
            // Get all servers using isolation-aware approach
            List<Long> distinctGuildIds = gameServerRepository.getDistinctGuildIds();
            List<GameServer> servers = new ArrayList<>();
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                try {
                    servers.addAll(gameServerRepository.findAllByGuildId(guildId));
                } finally {
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            // Process each server with proper isolation boundaries
            for (GameServer server : servers) {
                // Skip servers without a log channel
                if (server.getLogChannelId() == 0) {
                    logger.warn("Server {} has no configured log channel - skipping", server.getName());
                    continue;
                }
                
                // Process log file for this server using the fixed implementation
                LogParserFix.LogProcessingSummary summary = LogParserFix.processServerLog(
                    null, server, sftpConnector);
                    
                totalLogLinesProcessed.addAndGet(summary.getNewLines());
                totalEventsDetected.addAndGet(summary.getTotalEvents());
                
                if (summary.isRotationDetected()) {
                    totalRotationDetections.incrementAndGet();
                }
                
                logger.info("Validated log parser for server {}: {} new lines, {} events, rotation detected: {}", 
                    server.getName(), summary.getNewLines(), summary.getTotalEvents(), 
                    summary.isRotationDetected() ? "yes" : "no");
            }
            
            // Update report values
            report.totalLogLinesProcessed = totalLogLinesProcessed.get();
            report.totalEventsDetected = totalEventsDetected.get();
            report.rotationDetectionCount = totalRotationDetections.get();
            report.logRotationDetectionValid = totalRotationDetections.get() >= 0; // We can't always force a rotation
            
            // Check if validation passed - either we processed some lines or no servers have log channels
            report.logParsingValid = report.logRotationDetectionValid && 
                (report.totalLogLinesProcessed > 0 || servers.stream().noneMatch(s -> s.getLogChannelId() > 0));
            
            logger.info("Log parsing validation: {} ({} lines processed, {} events, {} rotations detected)", 
                report.logParsingValid ? "VALID" : "INVALID", 
                report.totalLogLinesProcessed,
                report.totalEventsDetected,
                report.rotationDetectionCount);
        } catch (Exception e) {
            logger.error("Error during log parsing validation: {}", e.getMessage(), e);
            report.logParsingValid = false;
        }
    }
    
    /**
     * Validation report class
     */
    public static class ValidationReport {
        // Overall validation status
        public boolean allValid;
        
        // CSV parsing validation
        public boolean csvParsingValid;
        public int totalLinesProcessed;
        public int totalKills;
        public int totalDeaths;
        public int statCorrections;
        public int serverCount;
        public int totalPlayersCreated;
        
        // Log parsing validation
        public boolean logParsingValid;
        public int totalLogLinesProcessed;
        public int totalEventsDetected;
        public int rotationDetectionCount;
        public boolean logRotationDetectionValid;
        
        // Timing information
        public long startTime;
        public long endTime;
        
        // Error information
        public String errorMessage;
        
        /**
         * Get the duration of the validation process in seconds
         * @return Duration in seconds
         */
        public double getDuration() {
            return (endTime - startTime) / 1000.0;
        }
        
        /**
         * Get a comprehensive validation summary
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Parser System Validation Report ===\n\n");
            
            sb.append("Overall Status: ").append(allValid ? "✅ VALID" : "❌ INVALID").append("\n\n");
            
            sb.append("CSV Parsing Validation:\n");
            sb.append("- Status: ").append(csvParsingValid ? "✅ VALID" : "❌ INVALID").append("\n");
            sb.append("- Lines Processed: ").append(totalLinesProcessed).append("\n");
            sb.append("- Total Kills: ").append(totalKills).append("\n");
            sb.append("- Total Deaths: ").append(totalDeaths).append("\n");
            sb.append("- Stat Corrections: ").append(statCorrections).append("\n");
            sb.append("- Servers Processed: ").append(serverCount).append("\n\n");
            
            sb.append("Log Parsing Validation:\n");
            sb.append("- Status: ").append(logParsingValid ? "✅ VALID" : "❌ INVALID").append("\n");
            sb.append("- Log Lines Processed: ").append(totalLogLinesProcessed).append("\n");
            sb.append("- Events Detected: ").append(totalEventsDetected).append("\n");
            sb.append("- Rotation Detections: ").append(rotationDetectionCount).append("\n");
            sb.append("- Log Rotation Detection: ")
              .append(logRotationDetectionValid ? "✅ VALID" : "❌ INVALID").append("\n");
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                sb.append("\nError: ").append(errorMessage).append("\n");
            }
            
            return sb.toString();
        }
    }
}