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
 * Integrates all parser system components into the main application
 * This class is the entry point for the comprehensive CSV parsing and log processing fix
 */
public class ParserSystemIntegrator {
    private static final Logger logger = LoggerFactory.getLogger(ParserSystemIntegrator.class);
    
    private final GameServerRepository gameServerRepository;
    private final PlayerRepository playerRepository;
    private final SftpConnector sftpConnector;
    private final DeadsideCsvParser csvParser;
    private final DeadsideLogParser logParser;
    
    /**
     * Constructor
     */
    public ParserSystemIntegrator(GameServerRepository gameServerRepository,
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
     * Execute the complete parser fix
     * @return Integration status
     */
    public IntegrationStatus executeCompleteFix() {
        IntegrationStatus status = new IntegrationStatus();
        status.startTime = System.currentTimeMillis();
        
        try {
            logger.info("Starting comprehensive parser system fix");
            
            // Run validator to identify and fix issues
            ParserSystemValidator validator = new ParserSystemValidator(
                gameServerRepository, playerRepository, sftpConnector, csvParser, logParser);
                
            ParserSystemValidator.ValidationReport validationReport = validator.validateAndFixAll();
            status.validationReport = validationReport;
            
            // Apply CSV parser fixes to all servers
            status.csvFixStatus = applyCsvFixes();
            
            // Apply log parser fixes to all servers
            status.logFixStatus = applyLogFixes();
            
            // Verify overall fix status
            status.allFixed = validationReport.allValid && 
                            status.csvFixStatus && 
                            status.logFixStatus;
            
            status.endTime = System.currentTimeMillis();
            logger.info("Completed comprehensive parser system fix: {}", 
                status.allFixed ? "SUCCESS" : "FAILURE");
        } catch (Exception e) {
            logger.error("Error executing comprehensive parser fix: {}", e.getMessage(), e);
            status.errorMessage = e.getMessage();
            status.endTime = System.currentTimeMillis();
        }
        
        return status;
    }
    
    /**
     * Apply CSV parser fixes
     */
    private boolean applyCsvFixes() {
        try {
            logger.info("Applying CSV parser fixes");
            
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
            
            int totalProcessed = 0;
            
            // Process each server with proper isolation boundaries
            for (GameServer server : servers) {
                try {
                    // Skip servers without a configured guild
                    if (server.getGuildId() == 0) {
                        logger.warn("Server {} has no configured guild ID", server.getName());
                        continue;
                    }
                    
                    // Process deaths for this server with auto-correction
                    AtomicInteger killCount = new AtomicInteger();
                    AtomicInteger deathCount = new AtomicInteger();
                    AtomicInteger errorCount = new AtomicInteger();
                    
                    processServerCsvFiles(server, killCount, deathCount, errorCount);
                    
                    // Correct any discrepancies in stats
                    int corrections = CsvParsingFix.validateAndSyncStats(playerRepository);
                    
                    logger.info("Applied CSV fixes to server {}: {} kills, {} deaths, {} errors, {} corrections", 
                        server.getName(), killCount.get(), deathCount.get(), errorCount.get(), corrections);
                        
                    totalProcessed++;
                } catch (Exception e) {
                    logger.error("Error applying CSV fixes to server {}: {}", 
                        server.getName(), e.getMessage(), e);
                }
            }
            
            boolean success = totalProcessed > 0;
            logger.info("CSV parser fixes applied to {} servers: {}", 
                totalProcessed, success ? "SUCCESS" : "FAILURE");
                
            return success;
        } catch (Exception e) {
            logger.error("Error applying CSV parser fixes: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Process CSV files for a server
     */
    private void processServerCsvFiles(GameServer server, 
                                      AtomicInteger killCount, 
                                      AtomicInteger deathCount, 
                                      AtomicInteger errorCount) {
        try {
            // Get CSV files for this server
            List<String> csvFiles = sftpConnector.findDeathlogFiles(server);
            
            for (String csvFile : csvFiles) {
                try {
                    String content = sftpConnector.getFileContent(server, 
                        server.getDeathlogsDirectory() + "/" + csvFile);
                    
                    String[] lines = content.split("\n");
                    
                    for (String line : lines) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        
                        // Process with the fixed implementation
                        boolean success = CsvParsingFix.processDeathLogLineFixed(
                            server, line, playerRepository);
                            
                        if (!success) {
                            errorCount.incrementAndGet();
                        }
                    }
                    
                    logger.info("Processed CSV file {} for server {}: {} lines", 
                        csvFile, server.getName(), lines.length);
                } catch (Exception e) {
                    logger.error("Error processing CSV file {}: {}", csvFile, e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            }
            
            // Count kills and deaths from the database
            List<Player> players = playerRepository.findByGuildIdAndServerId(
                server.getGuildId(), server.getServerId());
                
            for (Player player : players) {
                killCount.addAndGet(player.getKills());
                deathCount.addAndGet(player.getDeaths());
            }
        } catch (Exception e) {
            logger.error("Error processing server CSV files: {}", e.getMessage(), e);
            errorCount.incrementAndGet();
        }
    }
    
    /**
     * Apply log parser fixes
     */
    private boolean applyLogFixes() {
        try {
            logger.info("Applying log parser fixes");
            
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
            
            int totalProcessed = 0;
            
            // Process each server with proper isolation boundaries
            for (GameServer server : servers) {
                try {
                    // Skip servers without a log channel
                    if (server.getLogChannelId() == 0) {
                        logger.warn("Server {} has no configured log channel", server.getName());
                        continue;
                    }
                    
                    // Process log file for this server
                    LogParserFix.LogProcessingSummary summary = LogParserFix.processServerLog(
                        null, server, sftpConnector);
                        
                    logger.info("Applied log parser fixes to server {}: {} new lines, {} events, rotation detected: {}", 
                        server.getName(), summary.getNewLines(), summary.getTotalEvents(), 
                        summary.isRotationDetected() ? "yes" : "no");
                        
                    totalProcessed++;
                } catch (Exception e) {
                    logger.error("Error applying log parser fixes to server {}: {}", 
                        server.getName(), e.getMessage(), e);
                }
            }
            
            boolean success = totalProcessed > 0;
            logger.info("Log parser fixes applied to {} servers: {}", 
                totalProcessed, success ? "SUCCESS" : "FAILURE");
                
            return success;
        } catch (Exception e) {
            logger.error("Error applying log parser fixes: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Integration status
     */
    public static class IntegrationStatus {
        public long startTime;
        public long endTime;
        public boolean allFixed;
        public boolean csvFixStatus;
        public boolean logFixStatus;
        public String errorMessage;
        public ParserSystemValidator.ValidationReport validationReport;
        
        /**
         * Get integration duration in seconds
         */
        public long getDuration() {
            return (endTime - startTime) / 1000;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Parser System Integration Status ===\n\n");
            
            sb.append("Overall Status: ").append(allFixed ? "✅ SUCCESS" : "❌ FAILURE").append("\n");
            sb.append("Duration: ").append(getDuration()).append(" seconds\n");
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                sb.append("Error: ").append(errorMessage).append("\n");
            }
            
            sb.append("\nIntegration Details:\n");
            sb.append("- CSV Parser Fixes: ").append(csvFixStatus ? "✅ APPLIED" : "❌ FAILED").append("\n");
            sb.append("- Log Parser Fixes: ").append(logFixStatus ? "✅ APPLIED" : "❌ FAILED").append("\n");
            
            if (validationReport != null) {
                sb.append("\nValidation Summary:\n");
                sb.append("- CSV Parsing: ").append(validationReport.csvParsingValid ? "✅ VALID" : "❌ INVALID").append("\n");
                sb.append("- Log Parsing: ").append(validationReport.logParsingValid ? "✅ VALID" : "❌ INVALID").append("\n");
                sb.append("- Lines Processed: ").append(validationReport.totalLinesProcessed).append("\n");
                sb.append("- Events Detected: ").append(validationReport.totalEventsDetected).append("\n");
            }
            
            sb.append("\nPHASE 1 — CSV PARSING → STATS → LEADERBOARD FLOW: ").append(csvFixStatus ? "✅ COMPLETE" : "❌ INCOMPLETE").append("\n");
            sb.append("PHASE 2 — DEADSIDE.LOG PARSER + EMBED VALIDATION: ").append(logFixStatus ? "✅ COMPLETE" : "❌ INCOMPLETE").append("\n");
            
            return sb.toString();
        }
    }
}