package com.deadside.bot.validation;

import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.DeadsideLogParser;
import com.deadside.bot.sftp.SftpConnector;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the parser system fix and validation
 * This class provides methods to execute the comprehensive fix for both
 * CSV parsing and log processing systems as a single integrated batch
 */
public class ParserSystemEntry {
    private static final Logger logger = LoggerFactory.getLogger(ParserSystemEntry.class);
    
    /**
     * Execute the comprehensive parser system fix
     * This method runs all phases of the fix in a single batch operation:
     * 1. CSV parsing → stats → leaderboard flow validation
     * 2. Deadside.log parser + embed validation
     * 
     * @param jda JDA instance
     * @return Results of the operation
     */
    public static ValidationResults executeComprehensiveFix(JDA jda) {
        long startTime = System.currentTimeMillis();
        ValidationResults results = new ValidationResults();
        
        try {
            logger.info("Executing comprehensive parser system fix");
            
            // Create required dependencies
            GameServerRepository gameServerRepository = new GameServerRepository();
            PlayerRepository playerRepository = new PlayerRepository();
            SftpConnector sftpConnector = new SftpConnector();
            DeadsideCsvParser csvParser = new DeadsideCsvParser(jda, sftpConnector, playerRepository, gameServerRepository);
            DeadsideLogParser logParser = new DeadsideLogParser(jda, gameServerRepository, sftpConnector);
            
            // Create integrator
            ParserSystemIntegrator integrator = new ParserSystemIntegrator(
                gameServerRepository, playerRepository, sftpConnector, csvParser, logParser);
                
            // Execute the complete fix
            ParserSystemIntegrator.IntegrationStatus status = integrator.executeCompleteFix();
            
            // Update results
            results.integrationStatus = status;
            results.phase1Complete = status.csvFixStatus;
            results.phase2Complete = status.logFixStatus;
            results.allComplete = status.allFixed;
            
            // Run validation
            ParserSystemValidator validator = new ParserSystemValidator(
                gameServerRepository, playerRepository, sftpConnector, csvParser, logParser);
                
            ParserSystemValidator.ValidationReport validationReport = validator.validateAndFixAll();
            
            // Update validation results
            results.validationReport = validationReport;
            results.csvValid = validationReport.csvParsingValid;
            results.logValid = validationReport.logParsingValid;
            results.allValid = validationReport.allValid;
            
            // Update completion status
            results.success = results.allComplete && results.allValid;
            
            // Log completion
            logger.info("Comprehensive parser system fix completed: {}", results.success ? "SUCCESS" : "FAILURE");
        } catch (Exception e) {
            logger.error("Error executing comprehensive parser system fix: {}", e.getMessage(), e);
            results.errorMessage = e.getMessage();
            results.success = false;
        }
        
        // Calculate duration
        results.executionTime = System.currentTimeMillis() - startTime;
        
        return results;
    }
    
    /**
     * Validation results class
     */
    public static class ValidationResults {
        public boolean success;
        public boolean phase1Complete;
        public boolean phase2Complete;
        public boolean allComplete;
        public boolean csvValid;
        public boolean logValid;
        public boolean allValid;
        public String errorMessage;
        public long executionTime;
        public ParserSystemIntegrator.IntegrationStatus integrationStatus;
        public ParserSystemValidator.ValidationReport validationReport;
        
        /**
         * Get validation completion status
         */
        public ValidationStatus getStatus() {
            if (success) {
                return ValidationStatus.SUCCESS;
            } else if (errorMessage != null && !errorMessage.isEmpty()) {
                return ValidationStatus.ERROR;
            } else if (!allComplete) {
                return ValidationStatus.INCOMPLETE;
            } else {
                return ValidationStatus.FAILURE;
            }
        }
        
        /**
         * Get execution time in seconds
         */
        public long getExecutionTimeSeconds() {
            return executionTime / 1000;
        }
        
        /**
         * Validation status enum
         */
        public enum ValidationStatus {
            SUCCESS,
            FAILURE,
            INCOMPLETE,
            ERROR
        }
        
        /**
         * Get detailed completion information
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== COMPREHENSIVE PARSER SYSTEM VALIDATION RESULTS ===\n\n");
            
            sb.append("Status: ").append(getStatus()).append("\n");
            sb.append("Execution Time: ").append(getExecutionTimeSeconds()).append(" seconds\n");
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                sb.append("Error: ").append(errorMessage).append("\n\n");
            }
            
            sb.append("Phase 1 (CSV Parsing → Stats → Leaderboard): ")
              .append(phase1Complete ? "✓ COMPLETE" : "✗ INCOMPLETE")
              .append(", Validation: ")
              .append(csvValid ? "✓ VALID" : "✗ INVALID")
              .append("\n");
              
            sb.append("Phase 2 (Log Parser + Embed Validation): ")
              .append(phase2Complete ? "✓ COMPLETE" : "✗ INCOMPLETE")
              .append(", Validation: ")
              .append(logValid ? "✓ VALID" : "✗ INVALID")
              .append("\n\n");
              
            sb.append("Completion Criteria:\n");
            sb.append("[").append(csvValid ? "✓" : "✗").append("] All .csv lines are parsed and properly generate accurate stats\n");
            sb.append("[").append(csvValid ? "✓" : "✗").append("] All stats are stored correctly per guild and server\n");
            sb.append("[").append(csvValid ? "✓" : "✗").append("] Leaderboards use updated and accurate backend data\n");
            sb.append("[").append(logValid ? "✓" : "✗").append("] Deadside.log parser detects log rotations and resets parsing window\n");
            sb.append("[").append(logValid ? "✓" : "✗").append("] All event embeds are themed, complete, and correctly routed\n");
            sb.append("[").append(success ? "✓" : "✗").append("] Bot compiles, runs, connects, and produces real-time validated outputs\n");
            
            return sb.toString();
        }
    }
}