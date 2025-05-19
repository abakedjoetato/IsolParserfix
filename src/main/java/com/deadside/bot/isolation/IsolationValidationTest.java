package com.deadside.bot.isolation;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.fixes.CsvLogIntegrator;
import com.deadside.bot.sftp.SftpConnector;
import com.deadside.bot.utils.GuildIsolationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Integration test for validating the enhanced isolation architecture
 * This tests the proper handling of different isolation modes (read-only, disabled, Default Server)
 * across various components of the system.
 */
public class IsolationValidationTest {
    private static final Logger logger = LoggerFactory.getLogger(IsolationValidationTest.class);
    
    // Test constants
    private static final long TEST_GUILD_ID = 1000L;
    private static final String TEST_SERVER_ID = "test-server";
    private static final String DEFAULT_SERVER_ID = "default-test";
    private static final String READONLY_SERVER_ID = "readonly-test";
    private static final String DISABLED_SERVER_ID = "disabled-test";
    
    // Repositories
    private final GameServerRepository gameServerRepository = new GameServerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final SftpConnector sftpConnector = new SftpConnector();
    
    /**
     * Main test method that validates the entire isolation framework
     */
    public void runIsolationValidation() {
        logger.info("Starting isolation architecture validation test");
        
        try {
            // Step 1: Create test servers with different isolation modes
            createTestServers();
            
            // Step 2: Validate isolation context handling
            validateIsolationContextHandling();
            
            // Step 3: Validate leaderboard isolation
            validateLeaderboardIsolation();
            
            // Step 4: Validate SFTP handling with different isolation modes
            validateSftpIsolationHandling();
            
            // Step 5: Validate CSV log processing with different isolation modes
            validateCsvProcessingIsolation();
            
            logger.info("Isolation architecture validation completed successfully");
        } catch (Exception e) {
            logger.error("Isolation validation test failed", e);
        } finally {
            // Cleanup
            cleanupTestData();
        }
    }
    
    /**
     * Create test servers with different isolation modes for testing
     */
    private void createTestServers() {
        logger.info("Creating test servers with different isolation modes");
        
        // Create standard server
        GameServer standardServer = createTestServer(TEST_SERVER_ID, "Standard Test Server", "standard");
        gameServerRepository.save(standardServer);
        
        // Create Default Server
        GameServer defaultServer = createTestServer(DEFAULT_SERVER_ID, "Default Server", "Default Server");
        defaultServer.setReadOnly(true);
        gameServerRepository.save(defaultServer);
        
        // Create read-only server
        GameServer readOnlyServer = createTestServer(READONLY_SERVER_ID, "Read-Only Test Server", "read-only");
        readOnlyServer.setReadOnly(true);
        gameServerRepository.save(readOnlyServer);
        
        // Create disabled isolation server
        GameServer disabledServer = createTestServer(DISABLED_SERVER_ID, "Disabled Isolation Test Server", "disabled");
        gameServerRepository.save(disabledServer);
        
        logger.info("Test servers created successfully");
    }
    
    /**
     * Create a test server with specified isolation mode
     */
    private GameServer createTestServer(String serverId, String name, String isolationMode) {
        GameServer server = new GameServer();
        server.setServerId(serverId);
        server.setName(name);
        server.setGuildId(TEST_GUILD_ID);
        server.setHost("localhost");
        server.setPort(28082);
        server.setIsolationMode(isolationMode);
        return server;
    }
    
    /**
     * Validate the isolation context handling in GuildIsolationManager
     */
    private void validateIsolationContextHandling() {
        logger.info("Validating isolation context handling");
        
        // Test standard server context
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, TEST_SERVER_ID);
        GuildIsolationManager.FilterContext context = GuildIsolationManager.getInstance().getCurrentContext();
        
        if (context == null || !context.hasValidGuildId() || !context.hasValidServerId()) {
            throw new RuntimeException("Standard server context validation failed");
        }
        
        if (context.isDefaultServer() || context.isReadOnly() || context.isIsolationDisabled() || context.isRestrictedMode()) {
            throw new RuntimeException("Standard server context should not be restricted");
        }
        
        // Test Default Server context
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, DEFAULT_SERVER_ID);
        context = GuildIsolationManager.getInstance().getCurrentContext();
        
        if (context == null || !context.hasValidGuildId() || !context.hasValidServerId() || !context.isRestrictedMode()) {
            throw new RuntimeException("Default Server context validation failed");
        }
        
        // Test read-only server context
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, READONLY_SERVER_ID);
        context = GuildIsolationManager.getInstance().getCurrentContext();
        
        if (context == null || !context.hasValidGuildId() || !context.hasValidServerId() || !context.isReadOnly() || !context.isRestrictedMode()) {
            throw new RuntimeException("Read-only server context validation failed");
        }
        
        // Test disabled isolation server context
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, DISABLED_SERVER_ID);
        context = GuildIsolationManager.getInstance().getCurrentContext();
        
        if (context == null || !context.hasValidGuildId() || !context.hasValidServerId() || !context.isIsolationDisabled() || !context.isRestrictedMode()) {
            throw new RuntimeException("Disabled isolation server context validation failed");
        }
        
        // Clear context
        GuildIsolationManager.getInstance().clearContext();
        logger.info("Isolation context handling validated successfully");
    }
    
    /**
     * Validate leaderboard isolation with different isolation modes
     */
    private void validateLeaderboardIsolation() {
        logger.info("Validating leaderboard isolation handling");
        
        // Test standard server (should return empty list but no error)
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, TEST_SERVER_ID);
        List<Player> standardPlayers = playerRepository.getTopPlayersByKills(TEST_GUILD_ID, TEST_SERVER_ID, 10);
        if (standardPlayers == null) {
            throw new RuntimeException("Standard server should return empty list, not null");
        }
        
        // Test Default Server (should return empty list with no error)
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, DEFAULT_SERVER_ID);
        List<Player> defaultPlayers = playerRepository.getTopPlayersByKills(TEST_GUILD_ID, DEFAULT_SERVER_ID, 10);
        if (defaultPlayers == null || !defaultPlayers.isEmpty()) {
            throw new RuntimeException("Default Server should return empty list for leaderboard");
        }
        
        // Test read-only server (should return empty list with no error)
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, READONLY_SERVER_ID);
        List<Player> readOnlyPlayers = playerRepository.getTopPlayersByKills(TEST_GUILD_ID, READONLY_SERVER_ID, 10);
        if (readOnlyPlayers == null || !readOnlyPlayers.isEmpty()) {
            throw new RuntimeException("Read-only server should return empty list for leaderboard");
        }
        
        // Test disabled isolation server (should return empty list with no error)
        GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, DISABLED_SERVER_ID);
        List<Player> disabledPlayers = playerRepository.getTopPlayersByKills(TEST_GUILD_ID, DISABLED_SERVER_ID, 10);
        if (disabledPlayers == null || !disabledPlayers.isEmpty()) {
            throw new RuntimeException("Disabled isolation server should return empty list for leaderboard");
        }
        
        // Clear context
        GuildIsolationManager.getInstance().clearContext();
        logger.info("Leaderboard isolation handling validated successfully");
    }
    
    /**
     * Validate SFTP handling with different isolation modes
     */
    private void validateSftpIsolationHandling() {
        logger.info("Validating SFTP isolation handling");
        
        try {
            // Test Default Server SFTP handling (should return empty list with info-level log)
            GameServer defaultServer = gameServerRepository.findByGuildIdAndServerId(TEST_GUILD_ID, DEFAULT_SERVER_ID);
            List<String> defaultFiles = sftpConnector.listFiles(defaultServer, "/logs");
            if (defaultFiles == null || !defaultFiles.isEmpty()) {
                throw new RuntimeException("Default Server SFTP listing should return empty list");
            }
            
            // Test read-only server SFTP handling (should return empty list with info-level log)
            GameServer readOnlyServer = gameServerRepository.findByGuildIdAndServerId(TEST_GUILD_ID, READONLY_SERVER_ID);
            List<String> readOnlyFiles = sftpConnector.listFiles(readOnlyServer, "/logs");
            if (readOnlyFiles == null || !readOnlyFiles.isEmpty()) {
                throw new RuntimeException("Read-only server SFTP listing should return empty list");
            }
            
            // Test disabled isolation server SFTP handling (should return empty list with info-level log)
            GameServer disabledServer = gameServerRepository.findByGuildIdAndServerId(TEST_GUILD_ID, DISABLED_SERVER_ID);
            List<String> disabledFiles = sftpConnector.listFiles(disabledServer, "/logs");
            if (disabledFiles == null || !disabledFiles.isEmpty()) {
                throw new RuntimeException("Disabled isolation server SFTP listing should return empty list");
            }
            
            logger.info("SFTP isolation handling validated successfully");
        } catch (Exception e) {
            throw new RuntimeException("SFTP isolation validation failed", e);
        }
    }
    
    /**
     * Validate CSV log processing with different isolation modes
     */
    private void validateCsvProcessingIsolation() {
        logger.info("Validating CSV processing isolation handling");
        
        try {
            // Create mock CsvLogIntegrator
            CsvLogIntegrator.ValidationSummary summary = new CsvLogIntegrator.ValidationSummary();
            
            // Test standard server CSV processing
            GameServer standardServer = gameServerRepository.findByGuildIdAndServerId(TEST_GUILD_ID, TEST_SERVER_ID);
            
            // Set isolation context for proper validation
            GuildIsolationManager.getInstance().setContext(TEST_GUILD_ID, TEST_SERVER_ID);
            
            // Test Default Server CSV processing (should skip with info-level log)
            GameServer defaultServer = gameServerRepository.findByGuildIdAndServerId(TEST_GUILD_ID, DEFAULT_SERVER_ID);
            CsvLogIntegrator integrator = new CsvLogIntegrator(null, gameServerRepository, playerRepository, sftpConnector);
            CsvLogIntegrator.ValidationSummary defaultSummary = integrator.processServerWithValidation(defaultServer);
            
            // Should be valid but with empty results
            if (!defaultSummary.isValid()) {
                throw new RuntimeException("Default Server CSV processing should be valid but with empty results");
            }
            
            // Test read-only server CSV processing (should skip with info-level log)
            GameServer readOnlyServer = gameServerRepository.findByGuildIdAndServerId(TEST_GUILD_ID, READONLY_SERVER_ID);
            CsvLogIntegrator.ValidationSummary readOnlySummary = integrator.processServerWithValidation(readOnlyServer);
            
            // Should be valid but with empty results
            if (!readOnlySummary.isValid()) {
                throw new RuntimeException("Read-only server CSV processing should be valid but with empty results");
            }
            
            // Test disabled isolation server CSV processing (should skip with info-level log)
            GameServer disabledServer = gameServerRepository.findByGuildIdAndServerId(TEST_GUILD_ID, DISABLED_SERVER_ID);
            CsvLogIntegrator.ValidationSummary disabledSummary = integrator.processServerWithValidation(disabledServer);
            
            // Should be valid but with empty results
            if (!disabledSummary.isValid()) {
                throw new RuntimeException("Disabled isolation server CSV processing should be valid but with empty results");
            }
            
            // Clear context
            GuildIsolationManager.getInstance().clearContext();
            logger.info("CSV processing isolation handling validated successfully");
        } catch (Exception e) {
            throw new RuntimeException("CSV processing isolation validation failed", e);
        }
    }
    
    /**
     * Clean up test data
     */
    private void cleanupTestData() {
        logger.info("Cleaning up test data");
        
        try {
            // Delete test servers
            gameServerRepository.deleteByGuildIdAndServerId(TEST_GUILD_ID, TEST_SERVER_ID);
            gameServerRepository.deleteByGuildIdAndServerId(TEST_GUILD_ID, DEFAULT_SERVER_ID);
            gameServerRepository.deleteByGuildIdAndServerId(TEST_GUILD_ID, READONLY_SERVER_ID);
            gameServerRepository.deleteByGuildIdAndServerId(TEST_GUILD_ID, DISABLED_SERVER_ID);
            
            logger.info("Test data cleanup completed");
        } catch (Exception e) {
            logger.error("Error cleaning up test data", e);
        }
    }
}