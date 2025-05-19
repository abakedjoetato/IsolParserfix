package com.deadside.bot.listeners;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.DataBoundary;
import com.deadside.bot.utils.GuildIsolationManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

/**
 * Listener for button interactions
 * Implements proper data isolation to prevent data leakage between guilds/servers
 */
public class ButtonListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ButtonListener.class);
    
    private final PlayerRepository playerRepository;
    private final GameServerRepository gameServerRepository;
    
    public ButtonListener() {
        this.playerRepository = new PlayerRepository();
        this.gameServerRepository = new GameServerRepository();
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        // Create isolation context for this interaction
        long guildId = event.getGuild() != null ? event.getGuild().getIdLong() : 0;
        
        // Get the server for this guild
        GameServer server = gameServerRepository.findByGuildId(guildId);
        if (server == null) {
            event.reply("⚠️ No game server is configured for this Discord server yet.")
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Create filter context for isolation
        GuildIsolationManager.FilterContext filterContext = 
            GuildIsolationManager.getInstance().createFilterContext(guildId, server.getServerId());
        
        if (filterContext == null) {
            event.reply("⚠️ Failed to create data isolation context.")
                .setEphemeral(true)
                .queue();
            return;
        }
        
        try {
            // Handle different button types
            if (buttonId.startsWith("leaderboard_")) {
                handleLeaderboardButton(event, buttonId, filterContext);
            } else if (buttonId.startsWith("confirm_")) {
                handleConfirmationButton(event, buttonId, filterContext);
            } else if (buttonId.startsWith("cancel_")) {
                handleCancellationButton(event, buttonId);
            } else {
                logger.warn("Unknown button ID: {}", buttonId);
                event.reply("⚠️ This button is not supported.")
                    .setEphemeral(true)
                    .queue();
            }
        } catch (Exception e) {
            logger.error("Error handling button interaction", e);
            event.reply("❌ An error occurred while processing your request.")
                .setEphemeral(true)
                .queue();
        }
    }
    
    /**
     * Handle leaderboard buttons with proper data isolation
     */
    private void handleLeaderboardButton(ButtonInteractionEvent event, String buttonId, 
                                        GuildIsolationManager.FilterContext filterContext) {
        String type = buttonId.replace("leaderboard_", "");
        int limit = 10;
        
        // Create an embedded message
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("Deadside Leaderboard - " + type.toUpperCase());
        
        // Fetch the isolated data based on the button type
        switch (type) {
            case "kills":
                List<Player> topKills = playerRepository.getTopPlayersByKills(
                    filterContext.getGuildId(), filterContext.getServerId(), limit);
                
                if (topKills.isEmpty()) {
                    embed.setDescription("No player data available yet.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < topKills.size(); i++) {
                        Player p = topKills.get(i);
                        sb.append(String.format("%d. **%s** - %d kills\n", 
                            i + 1, p.getName(), p.getKills()));
                    }
                    embed.setDescription(sb.toString());
                }
                break;
                
            case "deaths":
                List<Player> topDeaths = playerRepository.getTopPlayersByDeaths(
                    filterContext.getGuildId(), filterContext.getServerId(), limit);
                
                if (topDeaths.isEmpty()) {
                    embed.setDescription("No player data available yet.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < topDeaths.size(); i++) {
                        Player p = topDeaths.get(i);
                        sb.append(String.format("%d. **%s** - %d deaths\n", 
                            i + 1, p.getName(), p.getDeaths()));
                    }
                    embed.setDescription(sb.toString());
                }
                break;
                
            case "kd":
                List<Player> topKD = playerRepository.getTopPlayersByKD(
                    filterContext.getGuildId(), filterContext.getServerId(), limit, 10);
                
                if (topKD.isEmpty()) {
                    embed.setDescription("No player data available yet.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < topKD.size(); i++) {
                        Player p = topKD.get(i);
                        sb.append(String.format("%d. **%s** - %.2f K/D\n", 
                            i + 1, p.getName(), p.getKdRatio()));
                    }
                    embed.setDescription(sb.toString());
                }
                break;
                
            default:
                embed.setDescription("Unknown leaderboard type.");
                break;
        }
        
        // Add isolation information to the footer
        embed.setFooter("Data isolated to this Discord server and game server");
        
        // Reply with the embed
        event.replyEmbeds(embed.build()).queue();
    }
    
    /**
     * Handle confirmation buttons
     */
    private void handleConfirmationButton(ButtonInteractionEvent event, String buttonId,
                                         GuildIsolationManager.FilterContext filterContext) {
        String actionId = buttonId.replace("confirm_", "");
        
        // Handle different confirmation actions
        switch (actionId) {
            case "reset_stats":
                // This would need proper privilege checks in a real implementation
                event.reply("⚠️ Stats reset is disabled for safety reasons.")
                    .setEphemeral(true)
                    .queue();
                break;
                
            case "delete_server":
                // This would need proper privilege checks in a real implementation
                event.reply("⚠️ Server deletion is disabled for safety reasons.")
                    .setEphemeral(true)
                    .queue();
                break;
                
            default:
                event.reply("Unknown confirmation action.")
                    .setEphemeral(true)
                    .queue();
                break;
        }
    }
    
    /**
     * Handle cancellation buttons
     */
    private void handleCancellationButton(ButtonInteractionEvent event, String buttonId) {
        // Delete the message with the buttons
        event.getMessage().delete().queue();
        
        // Send a confirmation that the action was cancelled
        event.reply("Action cancelled.")
            .setEphemeral(true)
            .queue();
    }
}