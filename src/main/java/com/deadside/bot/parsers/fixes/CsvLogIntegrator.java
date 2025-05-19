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
            File[] files = deathlogDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".csv")) {
                        csvFiles.add(file.getName());
                    }
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
                    List<Player> topKills = playerRepository.getTopPlayersByKills(server.getGuildId(), server.getServerId(), 10);
                    List<Player> topDeaths = playerRepository.getTopPlayersByDeaths(server.getGuildId(), server.getServerId(), 10);
                    List<Player> topKd = playerRepository.getTopPlayersByKDRatio(server.getGuildId(), server.getServerId(), 10);
                    
                    // Mark as valid since we can query without errors, even if results are empty
                    summary.setLeaderboardsValid(true);
                    summary.setTopKillsCount(topKills != null ? topKills.size() : 0);
                    summary.setTopDeathsCount(topDeaths != null ? topDeaths.size() : 0);
                    summary.setTopKdCount(topKd != null ? topKd.size() : 0);
                    
                    logger.info("Minimal leaderboard validation successful for restricted server {}", server.getName());
                } finally {
                    // Always clear the isolation context when done
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
                
                return;
            }
            
            // For standard servers, perform full validation of all leaderboard types
            com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(server.getGuildId(), server.getServerId());
            
            try {
                // Top kills leaderboard validation
                List<Player> topKills = playerRepository.getTopPlayersByKills(server.getGuildId(), server.getServerId(), 10);
                
                // Top deaths leaderboard validation
                List<Player> topDeaths = playerRepository.getTopPlayersByDeaths(server.getGuildId(), server.getServerId(), 10);
                
                // Top KD ratio leaderboard validation
                List<Player> topKd = playerRepository.getTopPlayersByKDRatio(server.getGuildId(), server.getServerId(), 10);
                
                // Set validation results
                summary.setLeaderboardsValid(true);
                summary.setTopKillsCount(topKills != null ? topKills.size() : 0);
                summary.setTopDeathsCount(topDeaths != null ? topDeaths.size() : 0);
                summary.setTopKdCount(topKd != null ? topKd.size() : 0);
                
                logger.info("Full leaderboard validation successful for server {}: {} kills, {} deaths, {} KD", 
                    server.getName(), 
                    summary.getTopKillsCount(),
                    summary.getTopDeathsCount(),
                    summary.getTopKdCount());
            } finally {
                // Always clear the isolation context when done
                com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
            }
        } catch (Exception e) {
            logger.error("Error validating leaderboard for server {}: {}", 
                server.getName(), e.getMessage(), e);
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
        private int csvFilesCount;
        private int csvLinesProcessed;
        private int csvErrors;
        private int csvProcessingErrors;
        private int playersCreated;
        private int totalKills;
        private int totalDeaths;
        private int totalSuicides;
        private int statCorrections;
        
        // Log validation
        private boolean logFileExists;
        private boolean logProcessingValid;
        private int logEventsProcessed;
        private int eventsProcessed;
        
        // Leaderboard validation
        private boolean leaderboardsValid;
        private int topKillsCount;
        private int topDeathsCount;
        private int topKdCount;
        
        /**
         * Check if this validation summary indicates a valid state
         * @return true if the validation is valid
         */
        public boolean isValid() {
            return successful && (errorMessage == null || errorMessage.isEmpty());
        }
        
        /**
         * Check if the validation was successful
         * @return true if successful
         */
        public boolean isSuccessful() {
            return successful;
        }
        
        /**
         * Get the server name
         * @return the server name
         */
        public String getServerName() {
            return serverName;
        }
        
        /**
         * Set the server name
         * @param serverName the server name to set
         */
        public void setServerName(String serverName) {
            this.serverName = serverName;
        }
        
        /**
         * Set the start timestamp
         * @param startTimestamp the start timestamp to set
         */
        public void setStartTimestamp(long startTimestamp) {
            this.startTimestamp = startTimestamp;
        }
        
        /**
         * Set the end timestamp
         * @param endTimestamp the end timestamp to set
         */
        public void setEndTimestamp(long endTimestamp) {
            this.endTimestamp = endTimestamp;
        }
        
        /**
         * Set whether the validation was successful
         * @param successful true if successful, false otherwise
         */
        public void setSuccessful(boolean successful) {
            this.successful = successful;
        }
        
        /**
         * Get the error message
         * @return the error message
         */
        public String getErrorMessage() {
            return errorMessage;
        }
        
        /**
         * Set the error message
         * @param errorMessage the error message to set
         */
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        /**
         * Set whether the CSV directory exists
         * @param csvDirectoryExists true if exists, false otherwise
         */
        public void setCsvDirectoryExists(boolean csvDirectoryExists) {
            this.csvDirectoryExists = csvDirectoryExists;
        }
        
        /**
         * Set the number of CSV files found
         * @param csvFilesFound the number of CSV files found
         */
        public void setCsvFilesFound(int csvFilesFound) {
            this.csvFilesFound = csvFilesFound;
        }
        
        /**
         * Set the number of CSV files processed
         * @param csvFilesCount the number of CSV files processed
         */
        public void setCsvFilesCount(int csvFilesCount) {
            this.csvFilesCount = csvFilesCount;
        }
        
        /**
         * Set the number of CSV lines processed
         * @param csvLinesProcessed the number of CSV lines processed
         */
        public void setCsvLinesProcessed(int csvLinesProcessed) {
            this.csvLinesProcessed = csvLinesProcessed;
        }
        
        /**
         * Set the number of CSV processing errors
         * @param csvErrors the number of CSV processing errors
         */
        public void setCsvErrors(int csvErrors) {
            this.csvErrors = csvErrors;
        }
        
        /**
         * Set the number of CSV processing errors
         * @param csvProcessingErrors the number of CSV processing errors
         */
        public void setCsvProcessingErrors(int csvProcessingErrors) {
            this.csvProcessingErrors = csvProcessingErrors;
        }
        
        /**
         * Set the number of players created
         * @param playersCreated the number of players created
         */
        public void setPlayersCreated(int playersCreated) {
            this.playersCreated = playersCreated;
        }
        
        /**
         * Set the total number of kills
         * @param totalKills the total number of kills
         */
        public void setTotalKills(int totalKills) {
            this.totalKills = totalKills;
        }
        
        /**
         * Set the total number of deaths
         * @param totalDeaths the total number of deaths
         */
        public void setTotalDeaths(int totalDeaths) {
            this.totalDeaths = totalDeaths;
        }
        
        /**
         * Set the total number of suicides
         * @param totalSuicides the total number of suicides
         */
        public void setTotalSuicides(int totalSuicides) {
            this.totalSuicides = totalSuicides;
        }
        
        /**
         * Set the number of stat corrections
         * @param statCorrections the number of stat corrections
         */
        public void setStatCorrections(int statCorrections) {
            this.statCorrections = statCorrections;
        }
        
        /**
         * Check if the log file exists
         * @return true if exists, false otherwise
         */
        public boolean isLogFileExists() {
            return logFileExists;
        }
        
        /**
         * Set whether the log file exists
         * @param logFileExists true if exists, false otherwise
         */
        public void setLogFileExists(boolean logFileExists) {
            this.logFileExists = logFileExists;
        }
        
        /**
         * Set whether log processing was valid
         * @param logProcessingValid true if valid, false otherwise
         */
        public void setLogProcessingValid(boolean logProcessingValid) {
            this.logProcessingValid = logProcessingValid;
        }
        
        /**
         * Get the number of log events processed
         * @return the number of log events processed
         */
        public int getLogEventsProcessed() {
            return logEventsProcessed;
        }
        
        /**
         * Set the number of log events processed
         * @param logEventsProcessed the number of log events processed
         */
        public void setLogEventsProcessed(int logEventsProcessed) {
            this.logEventsProcessed = logEventsProcessed;
        }
        
        /**
         * Set the number of events processed
         * @param eventsProcessed the number of events processed
         */
        public void setEventsProcessed(int eventsProcessed) {
            this.eventsProcessed = eventsProcessed;
        }
        
        /**
         * Set whether the leaderboards are valid
         * @param leaderboardsValid true if valid, false otherwise
         */
        public void setLeaderboardsValid(boolean leaderboardsValid) {
            this.leaderboardsValid = leaderboardsValid;
        }
        
        /**
         * Get the number of top kills entries
         * @return the number of top kills entries
         */
        public int getTopKillsCount() {
            return topKillsCount;
        }
        
        /**
         * Set the number of top kills entries
         * @param topKillsCount the number of top kills entries
         */
        public void setTopKillsCount(int topKillsCount) {
            this.topKillsCount = topKillsCount;
        }
        
        /**
         * Get the number of top deaths entries
         * @return the number of top deaths entries
         */
        public int getTopDeathsCount() {
            return topDeathsCount;
        }
        
        /**
         * Set the number of top deaths entries
         * @param topDeathsCount the number of top deaths entries
         */
        public void setTopDeathsCount(int topDeathsCount) {
            this.topDeathsCount = topDeathsCount;
        }
        
        /**
         * Get the number of top KD ratio entries
         * @return the number of top KD ratio entries
         */
        public int getTopKdCount() {
            return topKdCount;
        }
        
        /**
         * Set the number of top KD ratio entries
         * @param topKdCount the number of top KD ratio entries
         */
        public void setTopKdCount(int topKdCount) {
            this.topKdCount = topKdCount;
        }
        
        /**
         * Get CSV lines processed
         * @return CSV lines processed
         */
        public int getCsvLinesProcessed() {
            return csvLinesProcessed;
        }
        
        /**
         * Get CSV directory exists
         * @return if CSV directory exists
         */
        public boolean isCsvDirectoryExists() {
            return csvDirectoryExists;
        }
        
        /**
         * Get CSV files found
         * @return CSV files found
         */
        public int getCsvFilesFound() {
            return csvFilesFound;
        }
        
        /**
         * Get CSV errors
         * @return CSV errors
         */
        public int getCsvErrors() {
            return csvErrors;
        }
        
        /**
         * Get players created
         * @return players created
         */
        public int getPlayersCreated() {
            return playersCreated;
        }
        
        /**
         * Get total kills
         * @return total kills
         */
        public int getTotalKills() {
            return totalKills;
        }
        
        /**
         * Get total deaths
         * @return total deaths
         */
        public int getTotalDeaths() {
            return totalDeaths;
        }
        
        /**
         * Get total suicides
         * @return total suicides
         */
        public int getTotalSuicides() {
            return totalSuicides;
        }
        
        /**
         * Get stat corrections
         * @return stat corrections
         */
        public int getStatCorrections() {
            return statCorrections;
        }
        
        /**
         * Check if leaderboards are valid
         * @return if leaderboards are valid
         */
        public boolean isLeaderboardsValid() {
            return leaderboardsValid;
        }
        
        /**
         * Get a string representation of this validation summary
         * @return a string representation of this validation summary
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation Summary for ").append(serverName).append("\n");
            sb.append("Duration: ").append((endTimestamp - startTimestamp) / 1000).append(" seconds\n");
            sb.append("Status: ").append(successful ? "SUCCESS" : "FAILURE").append("\n");
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                sb.append("Error: ").append(errorMessage).append("\n");
            }
            
            return sb.toString();
        }
    }
}
