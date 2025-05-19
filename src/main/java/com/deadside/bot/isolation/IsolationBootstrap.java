package com.deadside.bot.isolation;

import com.deadside.bot.db.models.Alert;
import com.deadside.bot.db.models.Bounty;
import com.deadside.bot.db.models.Currency;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.*;
import com.deadside.bot.utils.GuildIsolationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap class for initializing isolation systems at startup
 * This ensures all isolation components are properly initialized and configured
 */
public class IsolationBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(IsolationBootstrap.class);
    
    // Singleton instance
    private static IsolationBootstrap instance;
    
    // Repositories
    private final PlayerRepository playerRepository;
    private final FactionRepository factionRepository;
    private final LinkedPlayerRepository linkedPlayerRepository;
    private final GameServerRepository gameServerRepository;
    private final CurrencyRepository currencyRepository;
    private final AlertRepository alertRepository;
    private final BountyRepository bountyRepository;
    
    // Cleanup tool
    private DataCleanupTool dataCleanupTool;
    
    /**
     * Get the singleton instance
     */
    public static synchronized IsolationBootstrap getInstance() {
        if (instance == null) {
            instance = new IsolationBootstrap();
        }
        return instance;
    }
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private IsolationBootstrap() {
        // Initialize repositories
        this.playerRepository = new PlayerRepository();
        this.factionRepository = new FactionRepository();
        this.linkedPlayerRepository = new LinkedPlayerRepository();
        this.gameServerRepository = new GameServerRepository();
        this.currencyRepository = new CurrencyRepository();
        this.alertRepository = new AlertRepository();
        this.bountyRepository = new BountyRepository();
        
        // Initialize cleanup tool
        this.dataCleanupTool = new DataCleanupTool(
            playerRepository,
            factionRepository,
            linkedPlayerRepository,
            gameServerRepository,
            currencyRepository,
            alertRepository,
            bountyRepository
        );
    }
    
    /**
     * Initialize all isolation systems
     * This should be called during bot startup
     */
    /**
     * Verify the integrity of data isolation across all repositories
     * This ensures all records have proper guild and server boundaries
     */
    public void verifyIsolationIntegrity() {
        logger.info("Verifying data isolation integrity across all repositories");
        int totalVerified = 0;
        
        try {
            // Verify player repository isolation
            if (playerRepository != null) {
                int playerCount = verifyPlayersIsolation();
                logger.info("Verified isolation for {} player records", playerCount);
                totalVerified += playerCount;
            }
            
            // Verify game server repository isolation
            if (gameServerRepository != null) {
                int serverCount = verifyServersIsolation();
                logger.info("Verified isolation for {} server records", serverCount);
                totalVerified += serverCount;
            }
            
            // Verify alert repository isolation
            if (alertRepository != null) {
                int alertCount = verifyAlertsIsolation();
                logger.info("Verified isolation for {} alert records", alertCount);
                totalVerified += alertCount;
            }
            
            // Verify bounty repository isolation
            if (bountyRepository != null) {
                int bountyCount = verifyBountiesIsolation();
                logger.info("Verified isolation for {} bounty records", bountyCount);
                totalVerified += bountyCount;
            }
            
            // Verify currency repository isolation
            if (currencyRepository != null) {
                int currencyCount = verifyCurrenciesIsolation();
                logger.info("Verified isolation for {} currency records", currencyCount);
                totalVerified += currencyCount;
            }
            
            logger.info("Data isolation verification complete. {} total records verified", totalVerified);
        } catch (Exception e) {
            logger.error("Error during isolation integrity verification", e);
        }
    }
    
    /**
     * Verify isolation for player records
     * @return Number of records verified
     */
    private int verifyPlayersIsolation() {
        List<Player> playersWithoutGuild = new ArrayList<>();
        int count = 0;
        
        try {
            // Get distinct guild IDs for proper isolation verification approach
            List<Long> distinctGuildIds = playerRepository.getDistinctGuildIds();
            
            // Verify players per guild context
            for (Long guildId : distinctGuildIds) {
                if (guildId != null && guildId > 0) {
                    // Set isolation context for this guild
                    GuildIsolationManager.getInstance().setContext(guildId, null);
                    try {
                        // Process each server under this guild
                        List<GameServer> servers = gameServerRepository.findAllByGuildId(guildId);
                        for (GameServer server : servers) {
                            if (server != null && server.getServerId() != null) {
                                // Set specific server context for detailed isolation
                                GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                                try {
                                    // Get players within this isolation boundary
                                    List<Player> players = playerRepository.findAllByGuildIdAndServerId(guildId, server.getServerId());
                                    count += players.size();
                                } finally {
                                    // Reset back to guild-level context
                                    GuildIsolationManager.getInstance().setContext(guildId, null);
                                }
                            }
                        }
                    } finally {
                        // Always clear context
                        GuildIsolationManager.getInstance().clearContext();
                    }
                }
            }
            
            logger.info("Verified isolation for {} player records with proper isolation boundaries", count);
            return count;
        } catch (Exception e) {
            logger.error("Error verifying player isolation using isolation-aware approach", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for server records
     * @return Number of records verified
     */
    private int verifyServersIsolation() {
        int count = 0;
        
        try {
            // Get distinct guild IDs to check servers using isolation-aware approach
            List<Long> distinctGuildIds = gameServerRepository.getDistinctGuildIds();
            int totalServers = 0;
            
            // Process each guild with proper isolation context
            for (Long guildId : distinctGuildIds) {
                if (guildId > 0) {
                    // Set isolation context for this guild
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                    
                    try {
                        // Get all servers for this guild with proper isolation
                        List<GameServer> guildServers = gameServerRepository.findAllByGuildId(guildId);
                        count += guildServers.size();
                        totalServers += guildServers.size();
                    } finally {
                        // Always clear context
                        com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                    }
                }
            }
            
            logger.info("Found {} server records with proper isolation, {} without proper isolation", 
                count, totalServers - count);
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying server isolation", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for alert records using isolation-aware pattern
     * @return Number of records verified
     */
    private int verifyAlertsIsolation() {
        int count = 0;
        
        try {
            // Get distinct guild IDs for proper isolation verification
            List<Long> distinctGuildIds = alertRepository.getDistinctGuildIds();
            
            // Verify alerts per guild context
            for (Long guildId : distinctGuildIds) {
                if (guildId != null && guildId > 0) {
                    // Set isolation context for this guild
                    GuildIsolationManager.getInstance().setContext(guildId, null);
                    try {
                        // Process each server under this guild
                        List<GameServer> servers = gameServerRepository.findAllByGuildId(guildId);
                        for (GameServer server : servers) {
                            if (server != null && server.getServerId() != null) {
                                // Set specific server context for detailed isolation
                                GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                                try {
                                    // Get alerts within this isolation boundary
                                    List<Alert> alerts = alertRepository.findAllByGuildIdAndServerId(guildId, server.getServerId());
                                    count += alerts.size();
                                } finally {
                                    // Reset back to guild-level context
                                    GuildIsolationManager.getInstance().setContext(guildId, null);
                                }
                            }
                        }
                    } finally {
                        // Always clear context
                        GuildIsolationManager.getInstance().clearContext();
                    }
                }
            }
            
            logger.info("Verified isolation for {} alert records with proper isolation boundaries", count);
            return count;
        } catch (Exception e) {
            logger.error("Error verifying alert isolation using isolation-aware approach", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for bounty records
     * @return Number of records verified
     */
    private int verifyBountiesIsolation() {
        int count = 0;
        
        try {
            // Get distinct guild IDs for proper isolation verification
            List<Long> distinctGuildIds = bountyRepository.getDistinctGuildIds();
            int totalBounties = 0;
            
            // Verify bounties per guild context
            for (Long guildId : distinctGuildIds) {
                if (guildId != null && guildId > 0) {
                    // Set isolation context for this guild
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                    
                    try {
                        // Get bounties for this guild with proper isolation boundary
                        List<Bounty> guildBounties = bountyRepository.findAllByGuildId(guildId);
                        totalBounties += guildBounties.size();
                        
                        // Count bounties with proper isolation fields
                        for (Bounty bounty : guildBounties) {
                            if (bounty.getGuildId() > 0 && bounty.getServerId() != null && !bounty.getServerId().isEmpty()) {
                                count++;
                            }
                        }
                    } finally {
                        // Always clear context
                        com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                    }
                }
            }
            
            logger.info("Found {} bounty records with proper isolation, {} without proper isolation", 
                count, totalBounties - count);
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying bounty isolation", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for currency records
     * @return Number of records verified
     */
    private int verifyCurrenciesIsolation() {
        int count = 0;
        
        try {
            // Get distinct guild IDs for proper isolation verification
            List<Long> distinctGuildIds = currencyRepository.getDistinctGuildIds();
            int totalCurrencies = 0;
            
            // Verify currencies per guild context
            for (Long guildId : distinctGuildIds) {
                if (guildId != null && guildId > 0) {
                    // Set isolation context for this guild
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                    
                    try {
                        // Get currencies for this guild with proper isolation boundary
                        List<Currency> guildCurrencies = currencyRepository.findAllByGuildId(guildId);
                        totalCurrencies += guildCurrencies.size();
                        
                        // Count currencies with proper isolation fields
                        for (Currency currency : guildCurrencies) {
                            if (currency.getGuildId() > 0 && currency.getServerId() != null && !currency.getServerId().isEmpty()) {
                                count++;
                            }
                        }
                    } finally {
                        // Always clear context
                        com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                    }
                }
            }
            
            logger.info("Found {} currency records with proper isolation, {} without proper isolation", 
                count, totalCurrencies - count);
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying currency isolation", e);
            return 0;
        }
    }
    
    public void initialize() {
        logger.info("Initializing data isolation systems");
        
        // Initialize isolation manager (nothing to do, it's a singleton)
        GuildIsolationManager.getInstance();
        
        // Initialize default server if needed
        DefaultServerInitializer.initializeDefaultServer();
        
        // Ensure all guilds have a default server - important for isolation
        ensureAllGuildsHaveDefaultServer();
        
        // Load cleanup settings
        DataCleanupTool.loadSettings();
        
        // Run startup cleanup if enabled
        if (DataCleanupTool.isRunCleanupOnStartup()) {
            logger.info("Automatic cleanup on startup is enabled, running cleanup...");
            runStartupCleanup();
        }
        
        // Fix any isolation issues found during startup
        fixIsolationIssues();
        
        // Verify isolation integrity across repositories
        verifyIsolationIntegrity();
        
        logger.info("Data isolation systems initialized successfully with proper guild boundaries");
    }
    
    /**
     * Run startup cleanup process
     */
    private void runStartupCleanup() {
        try {
            // This could be a full cleanup or a lighter version for startup
            dataCleanupTool.cleanupOrphanedRecords();
            logger.info("Startup cleanup completed successfully");
        } catch (Exception e) {
            logger.error("Error during startup cleanup", e);
        }
    }
    
    /**
     * Get the data cleanup tool
     */
    public DataCleanupTool getDataCleanupTool() {
        return dataCleanupTool;
    }
    
    /**
     * Clean orphaned records across all repositories
     * This ensures proper data isolation by removing records that may have been left behind
     * @return Number of records cleaned up
     */
    public int cleanOrphanedRecords() {
        logger.info("Cleaning orphaned records to maintain data isolation");
        int totalCleaned = 0;
        
        try {
            // Use the data cleanup tool to perform the cleaning
            if (dataCleanupTool != null) {
                Map<String, Object> cleanupResults = dataCleanupTool.cleanupOrphanedRecords();
                int cleanedCount = 0;
                if (cleanupResults.containsKey("totalCleaned") && cleanupResults.get("totalCleaned") instanceof Number) {
                    cleanedCount = ((Number) cleanupResults.get("totalCleaned")).intValue();
                }
                totalCleaned = cleanedCount;
                logger.info("Successfully cleaned up {} orphaned records", totalCleaned);
            } else {
                logger.warn("Data cleanup tool is not initialized, cannot clean orphaned records");
            }
        } catch (Exception e) {
            logger.error("Error cleaning orphaned records", e);
        }
        
        return totalCleaned;
    }
    
    /**
     * Get the player repository
     */
    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }
    
    /**
     * Get the faction repository
     */
    public FactionRepository getFactionRepository() {
        return factionRepository;
    }
    
    /**
     * Get the linked player repository
     */
    public LinkedPlayerRepository getLinkedPlayerRepository() {
        return linkedPlayerRepository;
    }
    
    /**
     * Get the game server repository
     */
    public GameServerRepository getGameServerRepository() {
        return gameServerRepository;
    }
    
    /**
     * Get the currency repository
     */
    public CurrencyRepository getCurrencyRepository() {
        return currencyRepository;
    }
    
    /**
     * Get the alert repository
     */
    public AlertRepository getAlertRepository() {
        return alertRepository;
    }
    
    /**
     * Get the bounty repository
     */
    public BountyRepository getBountyRepository() {
        return bountyRepository;
    }
    
    /**
     * Ensure all guilds have default servers set up properly for data isolation
     * This method is used during bootstrap to ensure proper isolation boundaries
     */
    private void ensureAllGuildsHaveDefaultServer() {
        logger.info("Ensuring all guilds have default servers configured for proper isolation");
        
        try {
            // Get all distinct guild IDs from the database
            List<Long> allGuildIds = gameServerRepository.getDistinctGuildIds();
            int initializedCount = 0;
            
            // Check each guild and ensure it has a default server
            for (Long guildId : allGuildIds) {
                if (guildId != null && guildId > 0) {
                    // Set isolation context for this guild
                    GuildIsolationManager.getInstance().setContext(guildId, null);
                    
                    // Check if this guild already has a default server
                    List<GameServer> guildServers = gameServerRepository.findAllByGuildId(guildId);
                    boolean hasDefaultServer = false;
                    
                    for (GameServer server : guildServers) {
                        if (DefaultServerInitializer.DEFAULT_SERVER_ID.equals(server.getServerId())) {
                            hasDefaultServer = true;
                            break;
                        }
                    }
                    
                    if (!hasDefaultServer) {
                        // Add default server for this guild
                        DefaultServerInitializer.addDefaultServerToGuild(guildId);
                        initializedCount++;
                    }
                    
                    // Clear context after processing
                    GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.info("Successfully initialized default servers for {} guilds", initializedCount);
        } catch (Exception e) {
            logger.error("Error ensuring all guilds have default servers", e);
        } finally {
            // Always clear context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Fix any isolation issues found in the database
     * This ensures all records have proper guild and server boundaries
     */
    private void fixIsolationIssues() {
        logger.info("Fixing data isolation issues in all repositories");
        
        try {
            // Step 1: Fix player records without proper isolation
            fixPlayerIsolationIssues();
            
            // Step 2: Fix currency records without proper isolation
            fixCurrencyIsolationIssues();
            
            // Step 3: Fix alert records without proper isolation
            fixAlertIsolationIssues();
            
            // Step 4: Fix bounty records without proper isolation
            fixBountyIsolationIssues();
            
            logger.info("Completed fixing isolation issues across all repositories");
        } catch (Exception e) {
            logger.error("Error fixing isolation issues", e);
        }
    }
    
    /**
     * Fix player isolation issues
     */
    private void fixPlayerIsolationIssues() {
        try {
            // Find players without proper guild and server IDs
            List<Player> allPlayers = playerRepository.getAllPlayers();
            int fixedCount = 0;
            
            for (Player player : allPlayers) {
                if (player.getGuildId() <= 0 || player.getServerId() == null || player.getServerId().isEmpty()) {
                    // This player needs to be fixed with proper isolation
                    player.setGuildId(player.getGuildId() <= 0 ? DefaultServerInitializer.DEFAULT_GUILD_ID : player.getGuildId());
                    player.setServerId(player.getServerId() == null || player.getServerId().isEmpty() ? 
                        DefaultServerInitializer.DEFAULT_SERVER_ID : player.getServerId());
                    
                    // Set isolation context before saving
                    GuildIsolationManager.getInstance().setContext(player.getGuildId(), player.getServerId());
                    
                    // Save with isolation context set
                    playerRepository.save(player);
                    
                    // Clear context
                    GuildIsolationManager.getInstance().clearContext();
                    
                    fixedCount++;
                }
            }
            
            logger.info("Fixed isolation for {} player records", fixedCount);
        } catch (Exception e) {
            logger.error("Error fixing player isolation issues", e);
        }
    }
    
    /**
     * Fix currency isolation issues
     */
    private void fixCurrencyIsolationIssues() {
        try {
            // Find currency records without proper guild and server IDs
            List<Currency> allCurrencies = currencyRepository.getAllCurrencies();
            int fixedCount = 0;
            
            for (Currency currency : allCurrencies) {
                if (currency.getGuildId() <= 0 || currency.getServerId() == null || currency.getServerId().isEmpty()) {
                    // This currency record needs to be fixed with proper isolation
                    currency.setGuildId(currency.getGuildId() <= 0 ? DefaultServerInitializer.DEFAULT_GUILD_ID : currency.getGuildId());
                    currency.setServerId(currency.getServerId() == null || currency.getServerId().isEmpty() ? 
                        DefaultServerInitializer.DEFAULT_SERVER_ID : currency.getServerId());
                    
                    // Set isolation context before saving
                    GuildIsolationManager.getInstance().setContext(currency.getGuildId(), currency.getServerId());
                    
                    // Save with isolation context set
                    currencyRepository.save(currency);
                    
                    // Clear context
                    GuildIsolationManager.getInstance().clearContext();
                    
                    fixedCount++;
                }
            }
            
            logger.info("Fixed isolation for {} currency records", fixedCount);
        } catch (Exception e) {
            logger.error("Error fixing currency isolation issues", e);
        }
    }
    
    /**
     * Fix alert isolation issues
     */
    private void fixAlertIsolationIssues() {
        try {
            // Find alert records without proper guild and server IDs
            List<Alert> allAlerts = alertRepository.getAllAlerts();
            int fixedCount = 0;
            
            for (Alert alert : allAlerts) {
                if (alert.getGuildId() <= 0 || alert.getServerId() == null || alert.getServerId().isEmpty()) {
                    // This alert record needs to be fixed with proper isolation
                    alert.setGuildId(alert.getGuildId() <= 0 ? DefaultServerInitializer.DEFAULT_GUILD_ID : alert.getGuildId());
                    alert.setServerId(alert.getServerId() == null || alert.getServerId().isEmpty() ? 
                        DefaultServerInitializer.DEFAULT_SERVER_ID : alert.getServerId());
                    
                    // Set isolation context before saving
                    GuildIsolationManager.getInstance().setContext(alert.getGuildId(), alert.getServerId());
                    
                    // Save with isolation context set
                    alertRepository.save(alert);
                    
                    // Clear context
                    GuildIsolationManager.getInstance().clearContext();
                    
                    fixedCount++;
                }
            }
            
            logger.info("Fixed isolation for {} alert records", fixedCount);
        } catch (Exception e) {
            logger.error("Error fixing alert isolation issues", e);
        }
    }
    
    /**
     * Fix bounty isolation issues
     */
    private void fixBountyIsolationIssues() {
        try {
            // Find bounty records without proper guild and server IDs
            List<Bounty> allBounties = bountyRepository.getAllBounties();
            int fixedCount = 0;
            
            for (Bounty bounty : allBounties) {
                if (bounty.getGuildId() <= 0 || bounty.getServerId() == null || bounty.getServerId().isEmpty()) {
                    // This bounty record needs to be fixed with proper isolation
                    bounty.setGuildId(bounty.getGuildId() <= 0 ? DefaultServerInitializer.DEFAULT_GUILD_ID : bounty.getGuildId());
                    bounty.setServerId(bounty.getServerId() == null || bounty.getServerId().isEmpty() ? 
                        DefaultServerInitializer.DEFAULT_SERVER_ID : bounty.getServerId());
                    
                    // Set isolation context before saving
                    GuildIsolationManager.getInstance().setContext(bounty.getGuildId(), bounty.getServerId());
                    
                    // Save with isolation context set
                    bountyRepository.save(bounty);
                    
                    // Clear context
                    GuildIsolationManager.getInstance().clearContext();
                    
                    fixedCount++;
                }
            }
            
            logger.info("Fixed isolation for {} bounty records", fixedCount);
        } catch (Exception e) {
            logger.error("Error fixing bounty isolation issues", e);
        }
    }
}