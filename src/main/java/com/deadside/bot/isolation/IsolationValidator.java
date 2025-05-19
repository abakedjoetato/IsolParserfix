package com.deadside.bot.isolation;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.utils.GuildIsolationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility class for validating isolation mode consistency
 * across different components of the system.
 * 
 * This ensures proper data isolation between Discord guilds and game servers,
 * while maintaining consistent behavior for servers with restricted isolation
 * modes (read-only, disabled, Default Server).
 */
public class IsolationValidator {
    private static final Logger logger = LoggerFactory.getLogger(IsolationValidator.class);
    
    /**
     * Validate a server's isolation mode is consistent with the current context
     * @param server The game server to validate
     * @return True if the isolation mode is consistent
     */
    public static boolean validateServerIsolationConsistency(GameServer server) {
        if (server == null) {
            logger.debug("Cannot validate null server");
            return false;
        }
        
        // Get current isolation context
        GuildIsolationManager.FilterContext context = 
            GuildIsolationManager.getInstance().getCurrentContext();
        
        if (context == null) {
            logger.debug("No active isolation context to validate against");
            return false;
        }
        
        // Check if server IDs match
        if (!context.getServerId().equals(server.getServerId())) {
            logger.debug("Server ID mismatch: context={}, server={}", 
                context.getServerId(), server.getServerId());
            return false;
        }
        
        // Check if guild IDs match
        if (context.getGuildId() != server.getGuildId()) {
            logger.debug("Guild ID mismatch: context={}, server={}", 
                context.getGuildId(), server.getGuildId());
            return false;
        }
        
        // Check if isolation modes are consistent
        boolean contextRestricted = context.isRestrictedMode();
        boolean serverRestricted = server.hasRestrictedIsolation();
        
        if (contextRestricted != serverRestricted) {
            logger.warn("Isolation mode inconsistency: context restricted={}, server restricted={}", 
                contextRestricted, serverRestricted);
            return false;
        }
        
        // Specific isolation mode checks
        if (context.isDefaultServer() != server.isDefaultServer()) {
            logger.warn("Default Server status inconsistency between context and server");
            return false;
        }
        
        if (context.isReadOnly() != server.isReadOnly()) {
            logger.warn("Read-only status inconsistency between context and server");
            return false;
        }
        
        if (context.isIsolationDisabled() != server.isIsolationDisabled()) {
            logger.warn("Isolation disabled status inconsistency between context and server");
            return false;
        }
        
        logger.debug("Server isolation consistency validated successfully");
        return true;
    }
    
    /**
     * Get a standardized isolation mode string for logging and display
     * @param server The game server to get the mode from
     * @return A standardized isolation mode string
     */
    public static String getStandardizedIsolationMode(GameServer server) {
        if (server == null) {
            return "unknown";
        }
        
        if (server.isDefaultServer()) {
            return "Default Server";
        } else if (server.isReadOnly()) {
            return "read-only";
        } else if (server.isIsolationDisabled()) {
            return "isolation-disabled";
        } else {
            return server.getIsolationMode() != null ? server.getIsolationMode() : "standard";
        }
    }
    
    /**
     * Check if data access should be restricted based on isolation mode
     * @param server The game server to check
     * @return True if data access should be restricted
     */
    public static boolean shouldRestrictDataAccess(GameServer server) {
        return server != null && server.hasRestrictedIsolation();
    }
    
    /**
     * Determine if an informational log should be used instead of a warning
     * based on isolation mode (for expected vs unexpected conditions)
     * @param server The game server to check
     * @return True if informational logging should be used
     */
    public static boolean shouldUseInfoLogging(GameServer server) {
        // For Default Server, read-only, or disabled isolation,
        // certain "error" conditions are expected, so use info logging
        return server != null && server.hasRestrictedIsolation();
    }
    
    /**
     * Get an appropriate reason message for data unavailability
     * based on isolation mode
     * @param server The game server to check
     * @return An appropriate reason message
     */
    public static String getUnavailabilityReason(GameServer server) {
        if (server == null) {
            return "No data available yet for this server.";
        }
        
        if (server.isDefaultServer()) {
            return "This is a Default Server and cannot display player statistics.";
        } else if (server.isReadOnly()) {
            return "This server is in read-only mode and cannot display statistics.";
        } else if (server.isIsolationDisabled()) {
            return "This server has isolation disabled and cannot display statistics.";
        } else {
            return "No data available yet for this server.";
        }
    }
}