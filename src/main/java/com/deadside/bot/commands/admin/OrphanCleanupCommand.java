package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.isolation.DataCleanupTool;
import com.deadside.bot.isolation.IsolationBootstrap;
import com.deadside.bot.utils.OwnerCheck;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Admin command to clean up orphaned records in the database
 * This can only be executed by the bot owner
 */
public class OrphanCleanupCommand implements ICommand {
    
    @Override
    public String getName() {
        return "cleanup-orphans";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash("cleanup-orphans", "Clean up orphaned records [Bot Owner Only]")
            .setGuildOnly(true);
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        return List.of();
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is bot owner
        if (!OwnerCheck.isOwner(event.getUser().getIdLong())) {
            event.reply("This command can only be used by the bot owner.").setEphemeral(true).queue();
            return;
        }
        
        // Defer reply since this might take a while
        event.deferReply().queue();
        
        try {
            // Get the data cleanup tool
            DataCleanupTool cleanupTool = IsolationBootstrap.getInstance().getDataCleanupTool();
            
            // Run the cleanup process
            Map<String, Object> results = cleanupTool.cleanupOrphanedRecords();
            
            // Process results and send reply
            if ((boolean) results.get("success")) {
                int totalOrphaned = (int) results.get("totalOrphanedRecords");
                
                @SuppressWarnings("unchecked")
                Map<String, Object> orphanCounts = (Map<String, Object>) results.get("orphanCounts");
                
                // Create embed with results
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Orphaned Records Cleanup")
                    .setDescription("The cleanup process has completed successfully.")
                    .setColor(Color.GREEN)
                    .addField("Total Orphaned Records", String.valueOf(totalOrphaned), false);
                
                // Add details for each collection
                orphanCounts.forEach((collection, count) -> 
                    embed.addField(collection, String.valueOf(count), true));
                
                // Add a warning if there were no orphaned records
                if (totalOrphaned == 0) {
                    embed.addField("Note", "No orphaned records were found. All records have proper isolation.", false);
                }
                
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            } else {
                // Something went wrong
                String errorMessage = (String) results.getOrDefault("message", "Unknown error occurred during cleanup");
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Cleanup Failed")
                    .setDescription("The cleanup process encountered an error.")
                    .setColor(Color.RED)
                    .addField("Error", errorMessage, false);
                
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            // Handle any unexpected errors
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Cleanup Failed")
                .setDescription("An unexpected error occurred during the cleanup process.")
                .setColor(Color.RED)
                .addField("Error", e.getMessage(), false);
            
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }
    }
}