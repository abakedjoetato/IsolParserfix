package com.deadside.bot.bot;

import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.DeadsideLogParser;
import com.deadside.bot.parsers.fixes.CsvParserFixImplementation;
import com.deadside.bot.parsers.fixes.DeadsideParserFixEntrypoint;
import com.deadside.bot.parsers.fixes.LogParserFixImplementation;
import com.deadside.bot.sftp.SftpConnector;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integrates the parser fixes into the main bot
 * This class is responsible for integrating the fixes for both CSV and log parsing systems
 * into the main bot system without disrupting existing functionality
 */
public class ParserFixIntegration {
    private static final Logger logger = LoggerFactory.getLogger(ParserFixIntegration.class);
    
    private final JDA jda;
    private final GameServerRepository gameServerRepository;
    private final PlayerRepository playerRepository;
    private final SftpConnector sftpConnector;
    private final DeadsideCsvParser csvParser;
    private final DeadsideLogParser logParser;
    
    // The new parser implementations
    private CsvParserFixImplementation csvParserImplementation;
    private LogParserFixImplementation logParserImplementation;
    
    /**
     * Constructor
     */
    public ParserFixIntegration(JDA jda, GameServerRepository gameServerRepository,
                              PlayerRepository playerRepository, SftpConnector sftpConnector,
                              DeadsideCsvParser csvParser, DeadsideLogParser logParser) {
        this.jda = jda;
        this.gameServerRepository = gameServerRepository;
        this.playerRepository = playerRepository;
        this.sftpConnector = sftpConnector;
        this.csvParser = csvParser;
        this.logParser = logParser;
    }
    
    /**
     * Initialize and apply all parser fixes
     * @return true if initialization was successful
     */
    public boolean initialize() {
        logger.info("Initializing parser fix integration");
        
        try {
            // Initialize the CSV parser implementation
            csvParserImplementation = new CsvParserFixImplementation(
                jda, playerRepository, sftpConnector);
                
            // Initialize the log parser implementation
            logParserImplementation = new LogParserFixImplementation(
                jda, gameServerRepository, sftpConnector);
                
            // Execute all fixes as a batch
            DeadsideParserFixEntrypoint fixEntrypoint = new DeadsideParserFixEntrypoint(
                jda, gameServerRepository, playerRepository, sftpConnector, csvParser, logParser);
                
            String results = fixEntrypoint.executeAllFixesAsBatch();
            logger.info("Parser fix batch execution results: {}", results);
            
            logger.info("Parser fix integration successfully initialized");
            return true;
        } catch (Exception e) {
            logger.error("Error initializing parser fix integration: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Apply the CSV parser implementation to process logs for a server
     */
    public int processCsvLogs(com.deadside.bot.db.models.GameServer server, boolean processHistorical) {
        try {
            if (csvParserImplementation != null) {
                return csvParserImplementation.processDeathLogs(server, processHistorical);
            } else {
                logger.warn("CSV parser implementation not initialized");
                return 0;
            }
        } catch (Exception e) {
            logger.error("Error applying CSV parser implementation: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Apply the log parser implementation to process logs for all servers
     */
    public void processServerLogs() {
        try {
            if (logParserImplementation != null) {
                logParserImplementation.processAllServerLogs();
            } else {
                logger.warn("Log parser implementation not initialized");
            }
        } catch (Exception e) {
            logger.error("Error applying log parser implementation: {}", e.getMessage(), e);
        }
    }
}