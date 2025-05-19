package com.deadside.bot.commands.admin;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.isolation.DataCleanupTool;
import com.deadside.bot.isolation.IsolationBootstrap;
import com.deadside.bot.utils.OwnerCheck;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin command to reset database data for a guild/server
 * This is a destructive operation and can only be executed by the bot owner
 */
public class DatabaseResetCommand {
    
    /**
     * Create the slash command data for this command
     */
    public static SlashCommandData getCommandData() {
        return Commands.slash("dbreset", "Reset database data [Bot Owner Only]")
            .setGuildOnly(true)
            .addSubcommands(
                new SubcommandData("server", "Reset data for a specific server in this guild")
                    .addOption(OptionType.STRING, "server_id", "The ID of the server to reset", true),
                new SubcommandData("allservers", "Reset data for all servers in this guild")
                    .addOption(OptionType.BOOLEAN, "confirm", "Confirm this destructive operation", true)
            );
    }
    
    /**
     * Execute the command
     */
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is bot owner
        if (!OwnerCheck.isOwner(event)) {
            event.reply("This command can only be used by the bot owner.").setEphemeral(true).queue();
            return;
        }
        
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a guild.").setEphemeral(true).queue();
            return;
        }
        
        long guildId = guild.getIdLong();
        
        // Handle subcommands
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Invalid command usage.").setEphemeral(true).queue();
            return;
        }
        
        // Defer reply since this might take a while
        event.deferReply().queue();
        
        try {
            DataCleanupTool cleanupTool = IsolationBootstrap.getInstance().getDataCleanupTool();
            
            switch (subcommand) {
                case "server":
                    handleServerReset(event, cleanupTool, guildId);
                    break;
                case "allservers":
                    handleAllServersReset(event, cleanupTool, guildId);
                    break;
                default:
                    event.getHook().sendMessage("Unknown subcommand: " + subcommand).queue();
            }
        } catch (Exception e) {
            // Handle any unexpected errors
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Database Reset Failed")
                .setDescription("An unexpected error occurred during the database reset process.")
                .setColor(Color.RED)
                .addField("Error", e.getMessage(), false);
            
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }
    }
    
    /**
     * Handle the reset for a specific server
     */
    private void handleServerReset(SlashCommandInteractionEvent event, DataCleanupTool cleanupTool, long guildId) {
        String serverId = event.getOption("server_id", "", OptionMapping::getAsString);
        
        if (serverId.isEmpty()) {
            event.getHook().sendMessage("Server ID is required.").queue();
            return;
        }
        
        // Verify the server exists in this guild
        GameServerRepository gameServerRepo = IsolationBootstrap.getInstance().getGameServerRepository();
        GameServer gameServer = gameServerRepo.findByServerIdAndGuildId(serverId, guildId);
        
        if (gameServer == null) {
            event.getHook().sendMessage("Server with ID `" + serverId + "` does not exist in this guild.").queue();
            return;
        }
        
        // Execute the reset
        Map<String, Object> results = cleanupTool.resetDatabaseForGuildAndServer(guildId, serverId);
        
        if ((boolean) results.get("success")) {
            int totalDeleted = (int) results.get("totalDeletedRecords");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> deleteCounts = (Map<String, Object>) results.get("deleteCounts");
            
            // Create embed with results
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Database Reset Completed")
                .setDescription("Reset completed for server: " + gameServer.getName() + " (" + serverId + ")")
                .setColor(Color.GREEN)
                .addField("Total Deleted Records", String.valueOf(totalDeleted), false);
            
            // Add details for each collection
            deleteCounts.forEach((collection, count) -> 
                embed.addField(collection, String.valueOf(count), true));
            
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        } else {
            // Something went wrong
            String errorMessage = (String) results.getOrDefault("message", "Unknown error occurred during reset");
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Database Reset Failed")
                .setDescription("The reset process encountered an error.")
                .setColor(Color.RED)
                .addField("Error", errorMessage, false);
            
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }
    }
    
    /**
     * Handle the reset for all servers in a guild
     */
    private void handleAllServersReset(SlashCommandInteractionEvent event, DataCleanupTool cleanupTool, long guildId) {
        boolean confirmed = event.getOption("confirm", false, OptionMapping::getAsBoolean);
        
        if (!confirmed) {
            event.getHook().sendMessage("This operation requires confirmation. Please set the `confirm` option to `true` to proceed.").queue();
            return;
        }
        
        // List all servers in this guild
        GameServerRepository gameServerRepo = IsolationBootstrap.getInstance().getGameServerRepository();
        List<GameServer> servers = gameServerRepo.findAllByGuildId(guildId);
        
        if (servers.isEmpty()) {
            event.getHook().sendMessage("No servers found in this guild.").queue();
            return;
        }
        
        // Execute the reset
        Map<String, Object> results = cleanupTool.resetDatabaseForGuild(guildId);
        
        if ((boolean) results.get("success")) {
            // Create embed with results
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Database Reset Completed")
                .setDescription("Reset completed for all servers in this guild")
                .setColor(Color.GREEN);
            
            // Add server list
            String serverList = servers.stream()
                .map(server -> server.getName() + " (" + server.getServerId() + ")")
                .collect(Collectors.joining("\n"));
            
            embed.addField("Affected Servers", serverList, false);
            
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        } else {
            // Something went wrong
            String errorMessage = (String) results.getOrDefault("message", "Unknown error occurred during reset");
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Database Reset Failed")
                .setDescription("The reset process encountered an error.")
                .setColor(Color.RED)
                .addField("Error", errorMessage, false);
            
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }
    }
}