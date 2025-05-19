package com.deadside.bot.bot;

import com.deadside.bot.commands.CommandManager;
import com.deadside.bot.config.Config;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.isolation.IsolationBootstrap;
import com.deadside.bot.bot.AutoStartupCleanup;
import com.deadside.bot.bot.ParserFixIntegration;
import com.deadside.bot.listeners.ButtonListener;
import com.deadside.bot.listeners.CommandListener;
import com.deadside.bot.listeners.ModalListener;
import com.deadside.bot.listeners.StringSelectMenuListener;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.DeadsideLogParser;
import com.deadside.bot.premium.PremiumManager;
import com.deadside.bot.premium.Tip4servWebhookController;
import com.deadside.bot.schedulers.KillfeedScheduler;
import com.deadside.bot.schedulers.PlayerCountVoiceChannelUpdater;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.sftp.SftpConnector;
import com.deadside.bot.utils.GuildIsolationManager;
import com.deadside.bot.utils.DataIsolationMigration;

import java.util.ArrayList;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main bot class that initializes JDA and sets up listeners and commands
 */
public class DeadsideBot {
    private static final Logger logger = LoggerFactory.getLogger(DeadsideBot.class);
    
    private final String token;
    private JDA jda;
    private CommandManager commandManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private KillfeedScheduler killfeedScheduler;
    private DeadsideLogParser logParser;
    private DeadsideCsvParser csvParser;
    private PremiumManager premiumManager;
    private Tip4servWebhookController webhookController;
    private PlayerCountVoiceChannelUpdater playerCountUpdater;
    private ParserFixIntegration parserFixIntegration;
    private IsolationBootstrap isolationBootstrap;
    
    public DeadsideBot(String token) {
        this.token = token;
    }
    
    /**
     * Start the bot and initialize all components
     */
    public void start() {
        try {
            // Initialize data isolation system
            logger.info("Initializing data isolation system...");
            isolationBootstrap = IsolationBootstrap.getInstance();
            isolationBootstrap.initialize();
            
            // Initialize the premium manager
            premiumManager = new PremiumManager();
            
            // Initialize the command manager
            commandManager = new CommandManager();
            
            // Build the JDA instance with necessary intents
            jda = JDABuilder.createDefault(token)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.playing("Deadside"))
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .enableCache(EnumSet.of(
                            CacheFlag.MEMBER_OVERRIDES,
                            CacheFlag.ROLE_TAGS,
                            CacheFlag.EMOJI,
                            CacheFlag.VOICE_STATE
                    ))
                    .addEventListeners(
                            new CommandListener(commandManager),
                            new ButtonListener(),
                            new StringSelectMenuListener(),
                            new ModalListener()
                    )
                    .build();
            
            // Wait for JDA to be ready
            jda.awaitReady();
            logger.info("JDA initialized and connected to Discord gateway");

            // Register slash commands
            commandManager.registerCommands(jda);
            
            // Start schedulers
            startSchedulers();
            
            // Initialize and start premium feature components
            initializePremiumSystem();
            
            // Run data isolation migration if needed
            runDataIsolationMigrationIfNeeded();
            
            // Run automatic cleanup if enabled
            AutoStartupCleanup.runIfEnabled();
            
            // Initialize and apply parser fixes
            initializeParserFixes();
            
            logger.info("Bot is now online and all systems operational with data isolation enforced!");
        } catch (Exception e) {
            logger.error("Failed to initialize bot", e);
            throw new RuntimeException("Failed to initialize bot", e);
        }
    }
    
    /**
     * Initialize premium system components
     */
    private void initializePremiumSystem() {
        try {
            Config config = Config.getInstance();
            
            // Get webhook configuration from environment or config
            String webhookSecret = System.getenv("TIP4SERV_WEBHOOK_SECRET");
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                webhookSecret = config.getProperty("tip4serv.webhook.secret", "");
            }
            
            int webhookPort = 8080; // Default port
            String portStr = System.getenv("TIP4SERV_WEBHOOK_PORT");
            if (portStr != null && !portStr.isEmpty()) {
                try {
                    webhookPort = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid Tip4serv webhook port: {}, using default 8080", portStr);
                }
            } else {
                String configPort = config.getProperty("tip4serv.webhook.port", "8080");
                try {
                    webhookPort = Integer.parseInt(configPort);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid Tip4serv webhook port in config: {}, using default 8080", configPort);
                }
            }
            
            String webhookPath = System.getenv("TIP4SERV_WEBHOOK_PATH");
            if (webhookPath == null || webhookPath.isEmpty()) {
                webhookPath = config.getProperty("tip4serv.webhook.path", "/webhook/tip4serv");
            }
            
            // Initialize and start the webhook controller
            webhookController = new Tip4servWebhookController(
                    premiumManager, 
                    webhookPort, 
                    webhookPath, 
                    webhookSecret
            );
            webhookController.start();
            
            // Schedule regular check for expired premium subscriptions
            scheduler.scheduleAtFixedRate(
                    premiumManager::checkExpiredSubscriptions,
                    1, // Initial delay of 1 hour to allow bot to fully initialize
                    24, // Check daily
                    TimeUnit.HOURS
            );
            
            logger.info("Premium system initialized with webhook listening on port {} at path {}", 
                    webhookPort, webhookPath);
        } catch (Exception e) {
            logger.error("Failed to initialize premium system", e);
        }
    }
    
    /**
     * Start all scheduled tasks
     */
    private void startSchedulers() {
        Config config = Config.getInstance();
        
        // Initialize killfeed scheduler
        killfeedScheduler = new KillfeedScheduler();
        killfeedScheduler.initialize(jda);
        int killfeedInterval = config.getKillfeedUpdateInterval();
        
        // Schedule killfeed updates
        scheduler.scheduleAtFixedRate(
                killfeedScheduler::processAllServers,
                1, // Initial delay of 1 second to allow bot to fully initialize
                killfeedInterval,
                TimeUnit.SECONDS
        );
        
        // Initialize common dependencies for parsers
        GameServerRepository gameServerRepository = new GameServerRepository();
        PlayerRepository playerRepository = new PlayerRepository();
        SftpConnector sftpConnector = new SftpConnector();
        
        // Initialize and start log parser
        logParser = new DeadsideLogParser(jda, gameServerRepository, sftpConnector);
        int logParserInterval = 180; // Fixed to 180 seconds as required
        
        // Schedule log parsing
        scheduler.scheduleAtFixedRate(
                logParser::processAllServerLogs,
                5, // Initial delay of 5 seconds to allow full initialization
                logParserInterval,
                TimeUnit.SECONDS
        );
        
        // Initialize and start CSV death log parser with proper isolation support
        csvParser = new DeadsideCsvParser(jda, sftpConnector, playerRepository, gameServerRepository);
        int csvParserInterval = 300; // Fixed to 300 seconds as required
        
        // Schedule CSV death log parsing
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        // Process servers using isolation-aware method
                        List<Long> guildIds = fetchAllGuildIds();
                        for (Long guildId : guildIds) {
                            if (guildId != null && guildId > 0) {
                                List<GameServer> guildServers = gameServerRepository.findAllByGuildId(guildId);
                                for (GameServer server : guildServers) {
                                    // Set isolation context for the current server
                                    GuildIsolationManager.getInstance().setContext(guildId, server.getServerId());
                                    csvParser.processDeathLogs(server);
                                    // Clear context after processing
                                    GuildIsolationManager.getInstance().clearContext();
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing CSV death logs: {}", e.getMessage(), e);
                    }
                },
                10, // Initial delay of 10 seconds
                csvParserInterval,
                TimeUnit.SECONDS
        );
        
        // Initialize and start player count voice channel updater
        playerCountUpdater = new PlayerCountVoiceChannelUpdater(jda, scheduler);
        
        logger.info("Scheduled killfeed updates every {} seconds", killfeedInterval);
        logger.info("Scheduled log parsing every {} seconds", logParserInterval);
        logger.info("Scheduled CSV death log parsing every {} seconds", csvParserInterval);
        logger.info("Scheduled player count voice channel updates every 5 minutes");
    }
    
    /**
     * Shutdown the bot gracefully
     */
    public void shutdown() {
        logger.info("Shutting down Tip4serv webhook controller...");
        if (webhookController != null) {
            webhookController.stop();
        }
        
        logger.info("Shutting down schedulers...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Shutting down JDA...");
        if (jda != null) {
            jda.shutdown();
            try {
                // Wait for JDA to shutdown properly
                if (!jda.awaitShutdown(30, TimeUnit.SECONDS)) {
                    jda.shutdownNow();
                    jda.awaitStatus(JDA.Status.SHUTDOWN);
                }
            } catch (InterruptedException e) {
                jda.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public JDA getJda() {
        return jda;
    }
    
    /**
     * Helper method to fetch all guild IDs for isolation-aware operations
     * @return List of all guild IDs in the system
     */
    private List<Long> fetchAllGuildIds() {
        try {
            // If connected to Discord, get guild IDs from JDA
            if (jda != null) {
                return jda.getGuilds().stream()
                        .map(guild -> guild.getIdLong())
                        .toList();
            }
            
            // Fallback to database if JDA is not ready, using isolation-aware methods
            GameServerRepository serverRepo = new GameServerRepository();
            List<Long> distinctGuildIds = serverRepo.getDistinctGuildIds();
            
            return distinctGuildIds.stream()
                    .filter(guildId -> guildId > 0) // Only include valid guild IDs
                    .toList();
        } catch (Exception e) {
            logger.error("Error fetching guild IDs for isolation: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Run data isolation migration if needed
     * This ensures all database records have proper guild and server isolation fields
     */
    private void runDataIsolationMigrationIfNeeded() {
        try {
            logger.info("Verifying data isolation boundaries");
            
            // Use isolation bootstrap to verify proper boundary enforcement
            IsolationBootstrap isolationInstance = IsolationBootstrap.getInstance();
            if (isolationInstance != null) {
                isolationInstance.verifyIsolationIntegrity();
            }
            
            // Reset any caches if needed
            logger.info("Data isolation verification complete");
        } catch (Exception e) {
            logger.error("Error during data isolation migration", e);
        }
    }
    
    /**
     * Initialize and apply parser fixes for both CSV and log processing systems
     * This ensures proper data isolation, accurate stats, and correct embed formatting
     */
    private void initializeParserFixes() {
        try {
            logger.info("Initializing comprehensive parser fixes for CSV and log systems");
            
            // Create the parser fix integration with all necessary dependencies
            GameServerRepository gameServerRepository = new GameServerRepository();
            PlayerRepository playerRepository = new PlayerRepository();
            SftpConnector sftpConnector = new SftpConnector();
            
            parserFixIntegration = new ParserFixIntegration(
                jda, gameServerRepository, playerRepository, sftpConnector, csvParser, logParser);
            
            // Initialize and apply all fixes as a single batch
            boolean success = parserFixIntegration.initialize();
            
            if (success) {
                logger.info("Successfully initialized and applied all parser fixes");
            } else {
                logger.warn("Parser fix initialization completed with warnings");
            }
        } catch (Exception e) {
            logger.error("Error initializing parser fixes: {}", e.getMessage(), e);
        }
    }
}
