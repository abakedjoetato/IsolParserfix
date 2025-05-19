package com.deadside.bot.commands;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.utils.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command to select the current game server for all operations
 * This enforces proper data isolation between different game servers
 */
public class ServerSelectCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ServerSelectCommand.class);
    private static final String COMMAND_NAME = "server";
    private static final String SELECT_MENU_ID = "server_select";
    private static final Map<Long, String> userServerSelections = new ConcurrentHashMap<>();
    
    // Guild-based server cache to avoid redundant database queries
    private static final Map<Long, List<GameServer>> guildServersCache = new HashMap<>();
    private static final Map<Long, Long> guildServersCacheTimestamp = new HashMap<>();
    private static final long CACHE_TTL = 60 * 1000; // 1 minute
    
    private final GameServerRepository serverRepository;
    
    public ServerSelectCommand() {
        this.serverRepository = new GameServerRepository();
    }
    
    /**
     * Get the slash command data for this command
     */
    public static SlashCommandData getCommandData() {
        return Commands.slash(COMMAND_NAME, "Select which game server to view stats for");
    }
    
    /**
     * Handle the slash command interaction
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(COMMAND_NAME)) {
            return;
        }
        
        // Create context to ensure proper isolation
        long guildId = event.getGuild().getIdLong();
        
        // Get servers for this guild
        List<GameServer> servers = getServersForGuild(guildId);
        
        if (servers.isEmpty()) {
            event.reply("⚠️ No game servers are configured for this Discord server yet. " +
                "Please set up a server first with `/server-add`.").setEphemeral(true).queue();
            return;
        }
        
        // Create select menu
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(SELECT_MENU_ID);
        
        // Add options for each server
        for (GameServer server : servers) {
            String serverLabel = server.getName();
            String serverId = server.getServerId();
            
            // Add player count if available
            if (server.getPlayerCount() > 0) {
                serverLabel += " (" + server.getPlayerCount() + "/" + server.getMaxPlayers() + ")";
            }
            
            // Add online status indicator
            if (server.isOnline()) {
                serverLabel = "🟢 " + serverLabel;
            } else {
                serverLabel = "🔴 " + serverLabel;
            }
            
            menuBuilder.addOption(serverLabel, serverId, "Select " + server.getName() + " server");
        }
        
        // Get current selection
        String currentServerId = userServerSelections.get(event.getUser().getIdLong());
        if (currentServerId != null) {
            menuBuilder.setDefaultValues(currentServerId);
        }
        
        // Reply with server selection menu
        MessageEmbed embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("Select Game Server")
            .setDescription("Please select which game server you want to view statistics for.\n" +
                "This will filter all commands to show data only for the selected server.")
            .build();
        
        event.replyEmbeds(embed)
            .addActionRow(menuBuilder.build())
            .setEphemeral(true)
            .queue();
    }
    
    /**
     * Handle the server selection event
     */
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals(SELECT_MENU_ID)) {
            return;
        }
        
        String serverId = event.getValues().get(0);
        long userId = event.getUser().getIdLong();
        long guildId = event.getGuild().getIdLong();
        
        // Store user's selection
        userServerSelections.put(userId, serverId);
        
        // Find the selected server
        GameServer selectedServer = null;
        for (GameServer server : getServersForGuild(guildId)) {
            if (server.getServerId().equals(serverId)) {
                selectedServer = server;
                break;
            }
        }
        
        if (selectedServer == null) {
            event.reply("⚠️ Error: Could not find the selected server.").setEphemeral(true).queue();
            return;
        }
        
        // Reply with confirmation
        MessageEmbed embed = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("Game Server Selected")
            .setDescription("You have selected **" + selectedServer.getName() + "**.\n" +
                "All commands will now show data only for this server.")
            .addField("Server ID", selectedServer.getServerId(), true)
            .addField("Status", selectedServer.isOnline() ? "🟢 Online" : "🔴 Offline", true)
            .build();
        
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }
    
    /**
     * Get the currently selected server for a user in a guild
     * @param userId The Discord user ID
     * @param guildId The Discord guild ID
     * @return The selected game server, or null if none selected
     */
    public static GameServer getSelectedServer(long userId, long guildId) {
        // Get the selected server ID for this user
        String serverId = userServerSelections.get(userId);
        if (serverId == null) {
            return null;
        }
        
        // Find the server in the cache
        List<GameServer> servers = guildServersCache.get(guildId);
        if (servers != null) {
            for (GameServer server : servers) {
                if (server.getServerId().equals(serverId)) {
                    return server;
                }
            }
        }
        
        // If not found, try to load from database
        GameServerRepository repo = new GameServerRepository();
        return repo.findById(serverId);
    }
    
    /**
     * Get all servers for a guild, with caching for performance
     * @param guildId The Discord guild ID
     * @return List of game servers for this guild
     */
    private List<GameServer> getServersForGuild(long guildId) {
        // Check if cache is valid
        Long lastUpdated = guildServersCacheTimestamp.get(guildId);
        if (lastUpdated != null && System.currentTimeMillis() - lastUpdated < CACHE_TTL) {
            List<GameServer> cachedServers = guildServersCache.get(guildId);
            if (cachedServers != null) {
                return cachedServers;
            }
        }
        
        // Get fresh data from database
        List<GameServer> servers = serverRepository.findAllByGuildId(guildId);
        
        // Update cache
        guildServersCache.put(guildId, servers);
        guildServersCacheTimestamp.put(guildId, System.currentTimeMillis());
        
        return servers;
    }
    
    /**
     * Clear the server selection for a user
     * @param userId The Discord user ID
     */
    public static void clearSelection(long userId) {
        userServerSelections.remove(userId);
    }
}