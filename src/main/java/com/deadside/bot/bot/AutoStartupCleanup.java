package com.deadside.bot.bot;

import com.deadside.bot.commands.admin.RunCleanupOnStartupCommand;
import com.deadside.bot.isolation.IsolationBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles automatic cleanup of orphaned records during bot startup
 * Only runs if enabled in the database settings
 */
public class AutoStartupCleanup {
    private static final Logger logger = LoggerFactory.getLogger(AutoStartupCleanup.class);
    
    /**
     * Run cleanup if it's enabled in the settings
     */
    public static void runIfEnabled() {
        try {
            // Check if automatic cleanup is enabled
            boolean shouldRun = RunCleanupOnStartupCommand.shouldRunCleanupOnStartup();
            
            if (shouldRun) {
                logger.info("Automatic cleanup is enabled. Running database cleanup...");
                
                // Execute the cleanup
                IsolationBootstrap.getInstance().cleanOrphanedRecords();
                logger.info("Automatic startup cleanup complete");
            } else {
                logger.info("Automatic cleanup is disabled. Skipping database cleanup.");
            }
        } catch (Exception e) {
            logger.error("Error during automatic startup cleanup", e);
        }
    }
}