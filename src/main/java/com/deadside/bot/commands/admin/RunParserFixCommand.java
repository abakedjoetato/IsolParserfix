package com.deadside.bot.commands.admin;

import com.deadside.bot.utils.OwnerCheck;
import com.deadside.bot.validation.ParserSystemEntry;
import com.deadside.bot.validation.ParserSystemIntegrator;
import com.deadside.bot.validation.ParserSystemValidator;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to run the comprehensive parser system fix
 * This command executes all phases of the parser fix as a single batch operation
 * Only bot owners can use this command
 */
public class RunParserFixCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RunParserFixCommand.class);
    
    /**
     * Get command data for registration
     * @return Slash command data
     */
    public static SlashCommandData getCommandData() {
        return Commands.slash("run-parser-fix", "Execute comprehensive parser fixes (CSV and log parsing)")
            .setDefaultPermissions(DefaultMemberPermissions.DISABLED) // Disabled for everyone by default
            .setGuildOnly(true);
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("run-parser-fix")) return;
        
        // Check if the user is the bot owner
        if (!OwnerCheck.isOwner(event)) {
            event.reply("❌ This command can only be used by the bot owner.")
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Defer reply since this operation might take some time
        event.deferReply().queue();
        
        try {
            // Execute the comprehensive parser system fix
            logger.info("Executing comprehensive parser system fix");
            
            // Notify the user that this is a comprehensive operation
            event.getHook().sendMessage("Starting comprehensive parser system fix. This operation will fix both CSV and log parsing systems as a single batch operation. Please wait...").queue();
            
            // Execute the fix
            ParserSystemEntry.ValidationResults results = 
                ParserSystemEntry.executeComprehensiveFix(event.getJDA());
            
            // Format the response
            String response = formatResults(results);
            
            // Send the response
            event.getHook().sendMessage(response).queue();
            
            logger.info("Comprehensive parser system fix completed: {}", 
                results.success ? "SUCCESS" : "FAILURE");
        } catch (Exception e) {
            logger.error("Error executing comprehensive parser system fix", e);
            event.getHook().sendMessage("❌ An error occurred while executing the parser system fix: " + e.getMessage())
                .queue();
        }
    }
    
    /**
     * Format validation results for Discord
     */
    private String formatResults(ParserSystemEntry.ValidationResults results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Parser System Fix Results\n\n");
        
        // Overall status
        sb.append("Status: ").append(results.success ? "✅ SUCCESS" : "❌ FAILURE").append("\n");
        sb.append("Execution Time: ").append(results.getExecutionTimeSeconds()).append(" seconds\n\n");
        
        // Phase completions
        sb.append("## Phase Completion\n\n");
        sb.append("### Phase 1: CSV Parsing → Stats → Leaderboard Flow\n");
        sb.append("- Status: ").append(results.phase1Complete ? "✅ COMPLETE" : "❌ INCOMPLETE").append("\n");
        sb.append("- Validation: ").append(results.csvValid ? "✅ VALID" : "❌ INVALID").append("\n");
        
        if (results.validationReport != null) {
            sb.append("- Lines Processed: ").append(results.validationReport.totalLinesProcessed).append("\n");
            sb.append("- Kills Tracked: ").append(results.validationReport.totalKills).append("\n");
            sb.append("- Deaths Tracked: ").append(results.validationReport.totalDeaths).append("\n");
            sb.append("- Stat Corrections: ").append(results.validationReport.statCorrections).append("\n");
        }
        
        sb.append("\n### Phase 2: Deadside.log Parser + Embed Validation\n");
        sb.append("- Status: ").append(results.phase2Complete ? "✅ COMPLETE" : "❌ INCOMPLETE").append("\n");
        sb.append("- Validation: ").append(results.logValid ? "✅ VALID" : "❌ INVALID").append("\n");
        
        if (results.validationReport != null) {
            sb.append("- Log Lines Processed: ").append(results.validationReport.totalLogLinesProcessed).append("\n");
            sb.append("- Events Detected: ").append(results.validationReport.totalEventsDetected).append("\n");
            sb.append("- Log Rotation Detection: ").append(results.validationReport.logRotationDetectionValid ? "✅ VALID" : "❌ INVALID").append("\n");
        }
        
        // Completion criteria
        sb.append("\n## Completion Criteria\n\n");
        sb.append(results.csvValid ? "✅" : "❌").append(" All .csv lines are parsed and properly generate accurate stats\n");
        sb.append(results.csvValid ? "✅" : "❌").append(" All stats are stored correctly per guild and server\n");
        sb.append(results.csvValid ? "✅" : "❌").append(" Leaderboards use updated and accurate backend data\n");
        sb.append(results.logValid ? "✅" : "❌").append(" Deadside.log parser detects log rotations and resets parsing window\n");
        sb.append(results.logValid ? "✅" : "❌").append(" All event embeds are themed, complete, and correctly routed\n");
        sb.append(results.success ? "✅" : "❌").append(" Bot compiles, runs, connects, and produces real-time validated outputs\n");
        
        if (results.errorMessage != null && !results.errorMessage.isEmpty()) {
            sb.append("\n## Error\n");
            sb.append("```\n").append(results.errorMessage).append("\n```");
        }
        
        return sb.toString();
    }
}