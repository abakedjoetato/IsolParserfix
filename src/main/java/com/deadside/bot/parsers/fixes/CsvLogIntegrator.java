package com.deadside.bot.parsers.fixes;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.sftp.SftpConnector;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration and validation controller for both CSV and log parsing fixes
 * Implements comprehensive validation of the entire data pipeline:
 * - CSV ingestion → Stat creation → Storage → Leaderboard display
 * - Log file monitoring → Event detection → Embed routing
 */
public class CsvLogIntegrator {
    private static final Logger logger = LoggerFactory.getLogger(CsvLogIntegrator.class);
    
    private final JDA jda;
    private final GameServerRepository gameServerRepository;
    private final PlayerRepository playerRepository;
    private final SftpConnector sftpConnector;
    
    // Tracking maps to ensure data consistency across servers
    private final Map<String, Long> lastFileCheckTimestamp = new HashMap<>();
    private final Map<String, String> lastProcessedFile = new HashMap<>();
    private final Map<String, Long> lastSyncTimestamp = new HashMap<>();
    
    /**
     * Constructor
     */
    public CsvLogIntegrator(JDA jda, GameServerRepository gameServerRepository, 
                          PlayerRepository playerRepository, SftpConnector sftpConnector) {
        this.jda = jda;
        this.gameServerRepository = gameServerRepository;
        this.playerRepository = playerRepository;
        this.sftpConnector = sftpConnector;
    }
    
    /**
     * Process all data sources for a server with comprehensive validation
     * This is the main entry point that orchestrates the entire data flow
     * @param server The game server to process
     * @return A validation summary
     */
    public ValidationSummary processServerWithValidation(GameServer server) {
        ValidationSummary summary = new ValidationSummary();
        summary.setServerName(server.getName());
        summary.setStartTimestamp(System.currentTimeMillis());
        
        try {
            // Phase 1: CSV log processing validation
            processCsvLogsWithValidation(server, summary);
            
            // Phase 2: Log file processing validation
            processServerLogsWithValidation(server, summary);
            
            // Validate database stats
            int correctionCount = CsvParsingFix.validateAndSyncStats(playerRepository);
            summary.setStatCorrections(correctionCount);
            
            // Validate leaderboard data consistency
            validateLeaderboardDataConsistency(server, summary);
            
            summary.setEndTimestamp(System.currentTimeMillis());
            summary.setSuccessful(true);
        } catch (Exception e) {
            logger.error("Error processing server {} with validation: {}", server.getName(), e.getMessage(), e);
            summary.setErrorMessage(e.getMessage());
            summary.setEndTimestamp(System.currentTimeMillis());
            summary.setSuccessful(false);
        }
        
        return summary;
    }
    
    /**
     * Process CSV logs with comprehensive validation
     */
    private void processCsvLogsWithValidation(GameServer server, ValidationSummary summary) {
        try {
            // First check if this is a server with restricted isolation mode
            if (server.hasRestrictedIsolation()) {
                String serverMode = server.isDefaultServer() ? "Default Server" :
                                  server.isReadOnly() ? "read-only" :
                                  server.isIsolationDisabled() ? "disabled isolation" :
                                  server.getIsolationMode() + " isolation";
                
                // Use informational logging instead of warnings for intentionally restricted servers
                logger.info("Skipping CSV processing for {} server: {}", serverMode, server.getName());
                
                // Mark validation as successful but with empty results
                summary.setCsvDirectoryExists(true);
                summary.setCsvFilesCount(0);
                summary.setCsvLinesProcessed(0);
                summary.setCsvProcessingErrors(0);
                
                return;
            }
            
            // Check if the server deathlog directory exists
            File deathlogDir = new File(server.getDeathlogsDirectory());
            if (!deathlogDir.exists() || !deathlogDir.isDirectory()) {
                // Use appropriate logging level based on context
                if ("Default Server".equals(server.getName())) {
                    // For Default Server, this is expected behavior
                    logger.info("Deathlog directory does not exist for Default Server: {}", 
                        server.getDeathlogsDirectory());
                } else {
                    logger.warn("Deathlog directory does not exist for server {}: {}", 
                        server.getName(), server.getDeathlogsDirectory());
                }
                summary.setCsvDirectoryExists(false);
                return;
            }
            summary.setCsvDirectoryExists(true);
            
            // List all CSV files
            List<String> csvFiles = new ArrayList<>();
            for (File file : deathlogDir.listFiles()) {
                if (file.getName().endsWith(".csv")) {
                    csvFiles.add(file.getName());
                }
            }
            
            summary.setCsvFilesFound(csvFiles.size());
            if (csvFiles.isEmpty()) {
                logger.info("No CSV files found for server {}", server.getName());
                return;
            }
            
            // Track stats before processing
            long totalPlayersBefore = playerRepository.countPlayersByGuildIdAndServerId(
                server.getGuildId(), server.getServerId());
            
            Map<String, Player> playersBefore = new HashMap<>();
            List<Player> allPlayersBefore = playerRepository.findByGuildIdAndServerId(
                server.getGuildId(), server.getServerId());
            
            for (Player player : allPlayersBefore) {
                playersBefore.put(player.getDeadsideId(), player);
            }
            
            // Process each CSV file
            AtomicInteger killCount = new AtomicInteger();
            AtomicInteger deathCount = new AtomicInteger();
            AtomicInteger errorCount = new AtomicInteger();
            AtomicInteger lineCount = new AtomicInteger();
            
            for (String csvFile : csvFiles) {
                String csvPath = server.getDeathlogsDirectory() + "/" + csvFile;
                File csvFileObj = new File(csvPath);
                
                if (!csvFileObj.exists()) {
                    logger.warn("CSV file does not exist: {}", csvPath);
                    continue;
                }
                
                // Read and parse each line
                try (BufferedReader reader = new BufferedReader(new FileReader(csvFileObj))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        
                        lineCount.incrementAndGet();
                        
                        // Use the fixed CSV parser
                        boolean processedSuccessfully = CsvParsingFix.processDeathLogLineFixed(
                            server, line, playerRepository);
                        
                        if (!processedSuccessfully) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing CSV file {}: {}", csvFile, e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            }
            
            // Track stats after processing
            long totalPlayersAfter = playerRepository.countPlayersByGuildIdAndServerId(
                server.getGuildId(), server.getServerId());
            
            Map<String, Player> playersAfter = new HashMap<>();
            List<Player> allPlayersAfter = playerRepository.findByGuildIdAndServerId(
                server.getGuildId(), server.getServerId());
            
            for (Player player : allPlayersAfter) {
                playersAfter.put(player.getDeadsideId(), player);
            }
            
            // Calculate total kills and deaths from player records
            int totalKills = 0;
            int totalDeaths = 0;
            int totalSuicides = 0;
            
            for (Player player : allPlayersAfter) {
                totalKills += player.getKills();
                totalDeaths += player.getDeaths();
                totalSuicides += player.getSuicides();
            }
            
            // Set summary data
            summary.setCsvLinesProcessed(lineCount.get());
            summary.setCsvErrors(errorCount.get());
            summary.setPlayersCreated((int)(totalPlayersAfter - totalPlayersBefore));
            summary.setTotalKills(totalKills);
            summary.setTotalDeaths(totalDeaths);
            summary.setTotalSuicides(totalSuicides);
            
            // Update the server's last processed timestamp
            server.setLastProcessedTimestamp(System.currentTimeMillis());
            gameServerRepository.save(server);
            
            logger.info("Validated CSV processing for server {}: {} files, {} lines, {} errors", 
                server.getName(), csvFiles.size(), lineCount.get(), errorCount.get());
                
        } catch (Exception e) {
            logger.error("Error validating CSV processing for server {}: {}", 
                server.getName(), e.getMessage(), e);
            summary.setErrorMessage("CSV validation error: " + e.getMessage());
        }
    }
    
    /**
     * Process server logs with comprehensive validation
     */
    private void processServerLogsWithValidation(GameServer server, ValidationSummary summary) {
        try {
            // First check if this is a server with restricted isolation mode
            if (server.hasRestrictedIsolation()) {
                String serverMode = server.isDefaultServer() ? "Default Server" :
                                  server.isReadOnly() ? "read-only" :
                                  server.isIsolationDisabled() ? "disabled isolation" :
                                  server.getIsolationMode() + " isolation";
                
                // Use informational logging instead of warnings for intentionally restricted servers
                logger.info("Skipping server log processing for {} server: {}", serverMode, server.getName());
                
                // Mark validation as successful but with empty results
                summary.setLogProcessingValid(true);
                summary.setEventsProcessed(0);
                
                return;
            }
            
            // Validate log directory
            String logPath = server.getLogDirectory() + "/Deadside.log";
            
            boolean logExists = false;
            try {
                logExists = sftpConnector.fileExists(server, logPath);
            } catch (Exception e) {
                logger.warn("Error checking if log file exists: {}", e.getMessage());
            }
            
            summary.setLogFileExists(logExists);
            
            if (!logExists) {
                logger.warn("Log file does not exist for server {}: {}", server.getName(), logPath);
                return;
            }
            
            // Process server logs with rotation detection and proper isolation
            LogParserFix.LogProcessingSummary processSummary = LogParserFix.processServerLog(jda, server, sftpConnector);
            int eventsProcessed = processSummary.getEventsProcessed();
            summary.setLogEventsProcessed(eventsProcessed);
            
            logger.info("Validated log processing for server {}: {} events processed", 
                server.getName(), eventsProcessed);
                
        } catch (Exception e) {
            logger.error("Error validating log processing for server {}: {}", 
                server.getName(), e.getMessage(), e);
            summary.setErrorMessage("Log validation error: " + e.getMessage());
        }
    }
    
    /**
     * Validate leaderboard data consistency using proper isolation
     * This method ensures that leaderboard validation respects guild and server boundaries
     * with graceful handling for servers in read-only or disabled mode
     */
    private void validateLeaderboardDataConsistency(GameServer server, ValidationSummary summary) {
        try {
            // Handle case when server lacks proper isolation fields
            if (server.getGuildId() <= 0 || server.getServerId() == null || server.getServerId().isEmpty()) {
                // Use informational logging instead of warning for Default Server
                if ("Default Server".equals(server.getName())) {
                    logger.info("Skipping leaderboard validation for Default Server - no isolation required");
                } else {
                    logger.info("Cannot validate leaderboard for server lacking proper isolation: {}", server.getName());
                }
                
                // Set valid fields in summary to indicate intentional skipping, not an error
                summary.setLeaderboardsValid(true);
                summary.setTopKillsCount(0);
                summary.setTopDeathsCount(0);
                summary.setTopKdCount(0);
                summary.setErrorMessage(null);
                
                // Log info-level message about the validation being skipped
                logger.info("Leaderboard validation skipped for server {}", server.getName());
                return;
            }
            
            // Check if server is intentionally operating in a limited isolation mode
            // Use the enhanced hasRestrictedIsolation method to check all possible restriction types
            boolean isRestrictedMode = server.hasRestrictedIsolation();
                                    
            if (isRestrictedMode) {
                // Use consistent mode naming from the enhanced GameServer model
                String serverMode = server.isDefaultServer() ? "Default Server" :
                                  server.isReadOnly() ? "read-only" :
                                  server.isIsolationDisabled() ? "disabled isolation" :
                                  server.getIsolationMode() + " isolation";
                                    
                logger.info("Server {} is in {} mode - skipping detailed leaderboard validation", 
                    server.getName(), serverMode);
                
                // For restricted mode servers, we perform a minimal validation instead of skipping entirely
                // This ensures we verify the server can be queried without expecting data
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(server.getGuildId(), server.getServerId());
                
                try {
                    // Just verify we can query without errors, not expecting results
                    playerRepository.getTopPlayersByKills(server.getGuildId(), server.getServerId(), 1);
                    
                    // Mark as valid with zero counts
                    summary.setLeaderboardsValid(true);
                    summary.setTopKillsCount(0);
                    summary.setTopDeathsCount(0);
                    summary.setTopKdCount(0);
                    summary.setErrorMessage(null);
                    
                    logger.info("Minimal leaderboard validation successful for restricted server {}", 
                        server.getName());
                } catch (Exception e) {
                    // Log but don't fail the validation
                    logger.info("Minimal validation for restricted server {} threw exception: {}", 
                        server.getName(), e.getMessage());
                    summary.setLeaderboardsValid(false);
                    summary.setErrorMessage("Restricted server validation error: " + e.getMessage());
                } finally {
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
                
                return;
            }
            
            // Set isolation context for this operation
            com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(server.getGuildId(), server.getServerId());
            
            try {
                // Validate kills leaderboard with proper isolation
                List<Player> topKills = playerRepository.getTopPlayersByKills(
                    server.getGuildId(), server.getServerId(), 10);
                    
                // Validate deaths leaderboard with proper isolation
                List<Player> topDeaths = playerRepository.getTopPlayersByDeaths(
                    server.getGuildId(), server.getServerId(), 10);
                    
                // Validate KD ratio leaderboard with proper isolation
                List<Player> topKD = playerRepository.getTopPlayersByKDRatio(
                    server.getGuildId(), server.getServerId(), 10);
                
                // Mark as valid even if empty - empty is valid, just means no data yet
                summary.setLeaderboardsValid(true);
                summary.setTopKillsCount(topKills.size());
                summary.setTopDeathsCount(topDeaths.size());
                summary.setTopKdCount(topKD.size());
                
                logger.info("Validated leaderboard data for server {}: kills={}, deaths={}, kd={}", 
                    server.getName(), topKills.size(), topDeaths.size(), topKD.size());
            } finally {
                // Always clear isolation context when done
                com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
            }
        } catch (Exception e) {
            logger.error("Error validating leaderboard data for server {}: {}", 
                server.getName(), e.getMessage(), e);
            summary.setLeaderboardsValid(false);
            summary.setErrorMessage("Leaderboard validation error: " + e.getMessage());
        }
    }
    
    /**
     * Class to hold validation results
     */
    public static class ValidationSummary {
        private String serverName;
        private long startTimestamp;
        private long endTimestamp;
        private boolean successful;
        private String errorMessage;
        
        // CSV validation
        private boolean csvDirectoryExists;
        private int csvFilesFound;
        private int csvLinesProcessed;
        private int csvErrors;
        private int playersCreated;
        private int totalKills;
        private int totalDeaths;
        private int totalSuicides;
        private int statCorrections;
        
        // Log validation
        private boolean logFileExists;
        private int logEventsProcessed;
        
        // Leaderboard validation
        private boolean leaderboardsValid;
        private int topKillsCount;
        private int topDeathsCount;
        private int topKdCount;
        
        // Getters and setters
        
        public String getServerName() { return serverName; }
        public void setServerName(String serverName) { this.serverName = serverName; }
        
        public long getStartTimestamp() { return startTimestamp; }
        public void setStartTimestamp(long startTimestamp) { this.startTimestamp = startTimestamp; }
        
        public long getEndTimestamp() { return endTimestamp; }
        public void setEndTimestamp(long endTimestamp) { this.endTimestamp = endTimestamp; }
        
        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public boolean isCsvDirectoryExists() { return csvDirectoryExists; }
        public void setCsvDirectoryExists(boolean csvDirectoryExists) { this.csvDirectoryExists = csvDirectoryExists; }
        
        public int getCsvFilesFound() { return csvFilesFound; }
        public void setCsvFilesFound(int csvFilesFound) { this.csvFilesFound = csvFilesFound; }
        
        public int getCsvLinesProcessed() { return csvLinesProcessed; }
        public void setCsvLinesProcessed(int csvLinesProcessed) { this.csvLinesProcessed = csvLinesProcessed; }
        
        public int getCsvErrors() { return csvErrors; }
        public void setCsvErrors(int csvErrors) { this.csvErrors = csvErrors; }
        
        public int getPlayersCreated() { return playersCreated; }
        public void setPlayersCreated(int playersCreated) { this.playersCreated = playersCreated; }
        
        public int getTotalKills() { return totalKills; }
        public void setTotalKills(int totalKills) { this.totalKills = totalKills; }
        
        public int getTotalDeaths() { return totalDeaths; }
        public void setTotalDeaths(int totalDeaths) { this.totalDeaths = totalDeaths; }
        
        public int getTotalSuicides() { return totalSuicides; }
        public void setTotalSuicides(int totalSuicides) { this.totalSuicides = totalSuicides; }
        
        public int getStatCorrections() { return statCorrections; }
        public void setStatCorrections(int statCorrections) { this.statCorrections = statCorrections; }
        
        public boolean isLogFileExists() { return logFileExists; }
        public void setLogFileExists(boolean logFileExists) { this.logFileExists = logFileExists; }
        
        public int getLogEventsProcessed() { return logEventsProcessed; }
        public void setLogEventsProcessed(int logEventsProcessed) { this.logEventsProcessed = logEventsProcessed; }
        
        public boolean isLeaderboardsValid() { return leaderboardsValid; }
        public void setLeaderboardsValid(boolean leaderboardsValid) { this.leaderboardsValid = leaderboardsValid; }
        
        public int getTopKillsCount() { return topKillsCount; }
        public void setTopKillsCount(int topKillsCount) { this.topKillsCount = topKillsCount; }
        
        public int getTopDeathsCount() { return topDeathsCount; }
        public void setTopDeathsCount(int topDeathsCount) { this.topDeathsCount = topDeathsCount; }
        
        public int getTopKdCount() { return topKdCount; }
        public void setTopKdCount(int topKdCount) { this.topKdCount = topKdCount; }
        
        @Override
        public String toString() {
            long duration = endTimestamp - startTimestamp;
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== Validation Summary for ").append(serverName).append(" ===\n");
            sb.append("Duration: ").append(duration / 1000).append(" seconds\n");
            sb.append("Status: ").append(successful ? "SUCCESS" : "FAILURE").append("\n");
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                sb.append("Error: ").append(errorMessage).append("\n");
            }
            
            sb.append("\nCSV Validation:\n");
            sb.append("- Directory exists: ").append(csvDirectoryExists).append("\n");
            sb.append("- Files found: ").append(csvFilesFound).append("\n");
            sb.append("- Lines processed: ").append(csvLinesProcessed).append("\n");
            sb.append("- Errors: ").append(csvErrors).append("\n");
            sb.append("- Players created: ").append(playersCreated).append("\n");
            sb.append("- Total kills: ").append(totalKills).append("\n");
            sb.append("- Total deaths: ").append(totalDeaths).append("\n");
            sb.append("- Total suicides: ").append(totalSuicides).append("\n");
            sb.append("- Stat corrections: ").append(statCorrections).append("\n");
            
            sb.append("\nLog Validation:\n");
            sb.append("- Log file exists: ").append(logFileExists).append("\n");
            sb.append("- Log events processed: ").append(logEventsProcessed).append("\n");
            
            sb.append("\nLeaderboard Validation:\n");
            sb.append("- Leaderboards valid: ").append(leaderboardsValid).append("\n");
            sb.append("- Top kills count: ").append(topKillsCount).append("\n");
            sb.append("- Top deaths count: ").append(topDeathsCount).append("\n");
            sb.append("- Top KD count: ").append(topKdCount).append("\n");
            
            return sb.toString();
        }
    }
}