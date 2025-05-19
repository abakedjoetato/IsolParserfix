package com.deadside.bot.validation;

import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.DeadsideLogParser;
import com.deadside.bot.parsers.fixes.CsvParsingFix;
import com.deadside.bot.parsers.fixes.LogParserFix;
import com.deadside.bot.sftp.SftpConnector;
import com.deadside.bot.utils.OwnerCheck;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to validate and fix parsers
 * Runs the full validation and fix process for CSV and log parsers
 */
public class ParserValidationCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ParserValidationCommand.class);
    
    /**
     * Get command data for registration
     * @return Slash command data
     */
    public static SlashCommandData getCommandData() {
        return Commands.slash("validate-parser-system", "Run a comprehensive validation of CSV and log parser systems")
            .setDefaultPermissions(DefaultMemberPermissions.DISABLED) // Disabled for everyone by default
            .setGuildOnly(true);
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("validate-parser-system")) return;
        
        // Check if user is bot owner
        if (!OwnerCheck.isOwner(event)) {
            event.reply("❌ This command can only be used by the bot owner.")
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Defer reply since this operation might take some time
        event.deferReply().queue();
        
        try {
            // Create dependencies
            GameServerRepository gameServerRepository = new GameServerRepository();
            PlayerRepository playerRepository = new PlayerRepository();
            SftpConnector sftpConnector = new SftpConnector();
            DeadsideCsvParser csvParser = new DeadsideCsvParser(event.getJDA(), sftpConnector, playerRepository, gameServerRepository);
            DeadsideLogParser logParser = new DeadsideLogParser(event.getJDA(), gameServerRepository, sftpConnector);
            
            // Create validator
            ParserSystemValidator validator = new ParserSystemValidator(
                gameServerRepository, playerRepository, sftpConnector, csvParser, logParser);
                
            // Run validation and fixes
            ParserSystemValidator.ValidationReport report = validator.validateAndFixAll();
            
            // Format response
            String response = formatValidationReport(report);
            
            // Send response
            event.getHook().sendMessage(response).queue();
            
            logger.info("Parser validation executed by {}: {}",
                event.getUser().getName(), report.allValid ? "PASSED" : "FAILED");
        } catch (Exception e) {
            logger.error("Error executing parser validation", e);
            event.getHook().sendMessage("❌ An error occurred during validation: " + e.getMessage())
                .queue();
        }
    }
    
    /**
     * Format validation report for Discord
     */
    private String formatValidationReport(ParserSystemValidator.ValidationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("## CSV Parser & Log Parser Validation\n\n");
        
        // Overall status
        sb.append("Status: ").append(report.allValid ? "✅ PASSED" : "❌ FAILED").append("\n");
        sb.append("Duration: ").append(report.getDuration()).append(" seconds\n\n");
        
        // Phase 1: CSV parsing
        sb.append("### Phase 1: CSV Parsing Validation\n");
        sb.append("- Status: ").append(report.csvParsingValid ? "✅ PASSED" : "❌ FAILED").append("\n");
        sb.append("- Lines processed: ").append(report.totalLinesProcessed).append("\n");
        sb.append("- Kills tracked: ").append(report.totalKills).append("\n");
        sb.append("- Deaths tracked: ").append(report.totalDeaths).append("\n");
        sb.append("- Players created: ").append(report.totalPlayersCreated).append("\n");
        sb.append("- Stat corrections: ").append(report.statCorrections).append("\n\n");
        
        // Phase 2: Log parsing
        sb.append("### Phase 2: Log Parsing Validation\n");
        sb.append("- Status: ").append(report.logParsingValid ? "✅ PASSED" : "❌ FAILED").append("\n");
        sb.append("- Log lines processed: ").append(report.totalLogLinesProcessed).append("\n");
        sb.append("- Events detected: ").append(report.totalEventsDetected).append("\n");
        sb.append("- Log rotation detection: ").append(report.logRotationDetectionValid ? "✅ VALID" : "❌ INVALID").append("\n\n");
        
        // Validation checklist
        sb.append("### Validation Checklist\n\n");
        
        // Phase 1 checks
        sb.append("**CSV Parsing**\n");
        sb.append(report.csvParsingValid ? "✅" : "❌").append(" All CSV fields (timestamp, killer, victim, weapon, distance) correctly parsed\n");
        sb.append(report.csvParsingValid ? "✅" : "❌").append(" Parser distinguishes suicides, environmental deaths correctly\n");
        sb.append(report.csvParsingValid ? "✅" : "❌").append(" Parser handles blank entries without data loss\n");
        sb.append(report.csvParsingValid ? "✅" : "❌").append(" All stat categories are tracked with correct isolation\n");
        sb.append(report.csvParsingValid ? "✅" : "❌").append(" Leaderboards display accurate backend data\n\n");
        
        // Phase 2 checks
        sb.append("**Log Parsing**\n");
        sb.append(report.logParsingValid ? "✅" : "❌").append(" Log parser tracks last processed line correctly\n");
        sb.append(report.logRotationDetectionValid ? "✅" : "❌").append(" Parser detects log rotations and resets accordingly\n");
        sb.append(report.logParsingValid ? "✅" : "❌").append(" Parser handles all event types correctly\n");
        sb.append(report.logParsingValid ? "✅" : "❌").append(" All embeds are properly formatted with modern styling\n");
        
        // Error info if present
        if (report.errorMessage != null && !report.errorMessage.isEmpty()) {
            sb.append("\n### Errors\n");
            sb.append("```\n").append(report.errorMessage).append("\n```");
        }
        
        return sb.toString();
    }
}