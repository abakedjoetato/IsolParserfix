package com.deadside.bot.commands.admin;

import com.deadside.bot.isolation.DataCleanupTool;
import com.deadside.bot.utils.OwnerCheck;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.awt.*;

/**
 * Admin command to control automatic cleanup on bot startup
 * This can only be executed by the bot owner
 */
public class RunCleanupOnStartupCommand {
    // Configuration settings
    private static boolean runCleanupOnStartup = true;
    
    /**
     * Create the slash command data for this command
     */
    public static SlashCommandData getCommandData() {
        return Commands.slash("autocleanup", "Configure automatic cleanup on startup [Bot Owner Only]")
            .setGuildOnly(true)
            .addOption(OptionType.BOOLEAN, "enabled", "Enable or disable automatic cleanup on startup", true);
    }
    
    /**
     * Check if cleanup should run on startup
     * @return True if cleanup should run on startup, false otherwise
     */
    public static boolean shouldRunCleanupOnStartup() {
        return runCleanupOnStartup;
    }
    
    /**
     * Execute the command
     */
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is bot owner
        if (!OwnerCheck.isOwner(event.getUser().getIdLong())) {
            event.reply("This command can only be used by the bot owner.").setEphemeral(true).queue();
            return;
        }
        
        // Get the enabled option
        boolean enabled = event.getOption("enabled", false, OptionMapping::getAsBoolean);
        
        try {
            // Set the setting
            DataCleanupTool.setRunCleanupOnStartup(enabled);
            
            // Create embed with results
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Automatic Cleanup Configuration")
                .setDescription("The automatic cleanup setting has been updated.")
                .setColor(Color.GREEN)
                .addField("Setting", "Automatic Cleanup on Startup", false)
                .addField("Value", enabled ? "Enabled" : "Disabled", false);
            
            if (enabled) {
                embed.addField("Note", "The cleanup will run when the bot starts up next time.", false);
            }
            
            event.replyEmbeds(embed.build()).queue();
        } catch (Exception e) {
            // Handle any unexpected errors
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Configuration Failed")
                .setDescription("An unexpected error occurred while updating the setting.")
                .setColor(Color.RED)
                .addField("Error", e.getMessage(), false);
            
            event.replyEmbeds(embed.build()).queue();
        }
    }
}