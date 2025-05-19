package com.deadside.bot.parsers.fixes;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.sftp.SftpConnector;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of fixes for the CSV parser
 * This class provides a fixed implementation of the CSV parser
 * that addresses all the issues identified in the validation
 */
public class CsvParserFixImplementation {
    private static final Logger logger = LoggerFactory.getLogger(CsvParserFixImplementation.class);
    
    private final JDA jda;
    private final PlayerRepository playerRepository;
    private final SftpConnector sftpConnector;
    
    // Tracking maps for ensuring state consistency
    private final Map<String, Integer> lastLinesByServer = new HashMap<>();
    private final Map<String, Set<String>> processedFiles = new HashMap<>();
    
    // Flag for historical processing
    private boolean isProcessingHistoricalData = false;
    
    /**
     * Constructor
     */
    public CsvParserFixImplementation(JDA jda, PlayerRepository playerRepository, SftpConnector sftpConnector) {
        this.jda = jda;
        this.playerRepository = playerRepository;
        this.sftpConnector = sftpConnector;
    }
    
    /**
     * Process death logs for a server with complete tracking and validation
     * @param server The game server to process
     * @param processHistorical Whether to process all files regardless of previous processing
     * @return Number of deaths processed
     */
    public int processDeathLogs(GameServer server, boolean processHistorical) {
        int totalProcessed = 0;
        
        try {
            // Set the historical processing flag
            this.isProcessingHistoricalData = processHistorical;
            
            // Skip servers without a configured guild
            if (server.getGuildId() == 0) {
                logger.warn("Server {} has no configured Discord guild", server.getName());
                return 0;
            }
            
            // Get or create the set of processed files for this server
            Set<String> processed = processedFiles.computeIfAbsent(
                server.getName(), k -> new HashSet<>());
            
            // Get CSV files from the server
            List<String> csvFiles;
            if (processHistorical) {
                // For historical processing, get all files
                csvFiles = sftpConnector.findDeathlogFiles(server);
            } else {
                // For regular processing, focus on recent files
                csvFiles = sftpConnector.findRecentDeathlogFiles(server, 1);
            }
            
            if (csvFiles.isEmpty()) {
                return 0;
            }
            
            // Sort files by name (which should include date)
            Collections.sort(csvFiles);
            
            // Process each file
            for (String csvFile : csvFiles) {
                // Skip already processed files unless reprocessing historical data
                if (!processHistorical && processed.contains(csvFile)) {
                    continue;
                }
                
                logger.info("Processing CSV file: {} for server: {} (historical: {})", 
                    csvFile, server.getName(), processHistorical ? "yes" : "no");
                
                try {
                    // Get file content
                    String content = sftpConnector.getFileContent(server, 
                        server.getDeathlogsDirectory() + "/" + csvFile);
                    
                    // Process the content with comprehensive validation
                    int deathsProcessed = processValidatedContent(server, content);
                    totalProcessed += deathsProcessed;
                    
                    // Mark as processed
                    processed.add(csvFile);
                    
                    // Update the server's last processed file
                    server.setLastProcessedKillfeedFile(csvFile);
                    server.setLastProcessedKillfeedLine(deathsProcessed);
                    
                    logger.info("Processed {} deaths from file {} for server {}", 
                        deathsProcessed, csvFile, server.getName());
                } catch (Exception e) {
                    logger.error("Error processing CSV file {}: {}", csvFile, e.getMessage(), e);
                }
            }
            
            // Update the server's last processed timestamp
            if (totalProcessed > 0) {
                server.setLastProcessedTimestamp(System.currentTimeMillis());
            }
            
            logger.info("Completed death log processing for server {}: {} total deaths processed", 
                server.getName(), totalProcessed);
        } catch (Exception e) {
            logger.error("Error processing death logs for server {}: {}", 
                server.getName(), e.getMessage(), e);
        } finally {
            // Reset the historical processing flag
            this.isProcessingHistoricalData = false;
        }
        
        return totalProcessed;
    }
    
    /**
     * Process content from CSV file with comprehensive validation
     * @param server The game server
     * @param content The file content
     * @return Number of deaths processed
     */
    private int processValidatedContent(GameServer server, String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        
        // Split into lines
        String[] lines = content.split("\\n");
        AtomicInteger processedCount = new AtomicInteger(0);
        
        // Stats counters
        AtomicInteger killCount = new AtomicInteger(0);
        AtomicInteger deathCount = new AtomicInteger(0);
        AtomicInteger suicideCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // Process each line
        Arrays.stream(lines)
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .forEach(line -> {
                try {
                    // Process with the fixed implementation
                    boolean success = CsvParsingFix.processDeathLogLineFixed(
                        server, line, playerRepository);
                    
                    if (success) {
                        processedCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    logger.error("Error processing death log line: {}", e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            });
        
        // Ensure stats integrity
        if (processedCount.get() > 0) {
            CsvParsingFix.validateAndSyncStats(playerRepository);
        }
        
        logger.info("Processed {} lines from CSV content for server {} (errors: {})", 
            processedCount.get(), server.getName(), errorCount.get());
            
        return processedCount.get();
    }
    
    /**
     * Process historical data with validation and tracking
     * @param server The game server
     * @param killCount Counter for kills
     * @param deathCount Counter for deaths
     * @param errorCount Counter for errors
     */
    public void processHistoricalData(GameServer server, 
                                     AtomicInteger killCount,
                                     AtomicInteger deathCount,
                                     AtomicInteger errorCount) {
        try {
            // Set historical processing flag
            isProcessingHistoricalData = true;
            
            logger.info("Processing historical data for server: {}", server.getName());
            
            // Get CSV files
            File deathlogsDir = new File(server.getDeathlogsDirectory());
            if (!deathlogsDir.exists() || !deathlogsDir.isDirectory()) {
                logger.warn("Deathlog directory does not exist: {}", server.getDeathlogsDirectory());
                errorCount.incrementAndGet();
                return;
            }
            
            // List and sort CSV files
            List<String> csvFiles = new ArrayList<>();
            for (File file : deathlogsDir.listFiles()) {
                if (file.getName().endsWith(".csv")) {
                    csvFiles.add(file.getName());
                }
            }
            Collections.sort(csvFiles);
            
            logger.info("Found {} CSV files for historical processing", csvFiles.size());
            
            // Process each file
            for (String csvFile : csvFiles) {
                String filePath = server.getDeathlogsDirectory() + "/" + csvFile;
                File file = new File(filePath);
                
                if (!file.exists()) {
                    logger.warn("CSV file does not exist: {}", filePath);
                    continue;
                }
                
                logger.info("Processing historical file: {}", filePath);
                
                // Read file content
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
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
                } catch (Exception e) {
                    logger.error("Error reading historical CSV file: {}", e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            }
            
            // Compute kill and death counts
            List<Player> players = playerRepository.findByGuildIdAndServerId(
                server.getGuildId(), server.getServerId());
                
            for (Player player : players) {
                killCount.addAndGet(player.getKills());
                deathCount.addAndGet(player.getDeaths());
            }
            
            // Ensure stats integrity
            CsvParsingFix.validateAndSyncStats(playerRepository);
            
            logger.info("Completed historical data processing for server {}: {} kills, {} deaths, {} errors", 
                server.getName(), killCount.get(), deathCount.get(), errorCount.get());
        } catch (Exception e) {
            logger.error("Error processing historical data: {}", e.getMessage(), e);
            errorCount.incrementAndGet();
        } finally {
            // Reset historical processing flag
            isProcessingHistoricalData = false;
        }
    }
}