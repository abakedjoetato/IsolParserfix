package com.deadside.bot.isolation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aspect for enforcing data isolation boundaries
 * This class contains utilities for boundary enforcement and violation handling
 */
public class DataIsolationAspect {
    private static final Logger logger = LoggerFactory.getLogger(DataIsolationAspect.class);
    
    /**
     * Exception thrown when data isolation boundaries are violated
     */
    public static class DataIsolationException extends RuntimeException {
        public DataIsolationException(String message) {
            super(message);
        }
        
        public DataIsolationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Enforces data isolation boundaries by throwing an exception if they are violated
     * @param condition The condition that must be true for the boundary to be respected
     * @param message The error message if the condition is false
     * @throws DataIsolationException If the condition is false
     */
    public static void enforce(boolean condition, String message) {
        if (!condition) {
            logger.warn("DATA ISOLATION VIOLATION: {}", message);
            throw new DataIsolationException(message);
        }
    }
    
    /**
     * Warns about a potential data isolation boundary violation without throwing an exception
     * @param condition The condition that should be true for the boundary to be respected
     * @param message The warning message if the condition is false
     * @return The condition value
     */
    public static boolean warn(boolean condition, String message) {
        if (!condition) {
            logger.warn("DATA ISOLATION WARNING: {}", message);
        }
        return condition;
    }
    
    /**
     * Log a data access attempt for auditing purposes
     * @param operation The operation being performed
     * @param entityType The type of entity being accessed
     * @param entityId The ID of the entity
     * @param guildId The guild ID making the request
     * @param serverId The server ID making the request
     */
    public static void logAccess(String operation, String entityType, String entityId, 
                                 long guildId, String serverId) {
        if (logger.isDebugEnabled()) {
            logger.debug("DATA ACCESS: op={}, type={}, id={}, guild={}, server={}",
                operation, entityType, entityId, guildId, serverId);
        }
    }
}