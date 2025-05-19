package com.deadside.bot.commands.stats;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.isolation.DefaultServerInitializer;
import com.deadside.bot.premium.FeatureGate;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import com.deadside.bot.utils.EmbedThemes;
import com.deadside.bot.utils.GuildIsolationManager;
import com.deadside.bot.utils.ResourceManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command for viewing leaderboards
 */
public class LeaderboardCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommand.class);
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final DecimalFormat df = new DecimalFormat("#.##");
    
    @Override
    public String getName() {
        return "leaderboard";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "View Deadside leaderboards")
                .addOptions(
                        new OptionData(OptionType.STRING, "type", "Leaderboard type", true)
                                .addChoice("kills", "kills")
                                .addChoice("kd", "kd")
                                .addChoice("distance", "distance")
                                .addChoice("streak", "streak")
                                .addChoice("weapons", "weapons")
                                .addChoice("deaths", "deaths")
                );
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        // Check if guild has access to the leaderboards feature (premium feature)
        if (!FeatureGate.checkCommandAccess(event, FeatureGate.Feature.LEADERBOARDS)) {
            // The FeatureGate utility already sent a premium upsell message
            return;
        }
        
        String type = event.getOption("type", "kills", OptionMapping::getAsString);
        
        event.deferReply().queue();
        
        try {
            switch (type) {
                case "kills" -> displayKillsLeaderboard(event);
                case "kd" -> displayKDLeaderboard(event);
                case "distance" -> displayDistanceLeaderboard(event);
                case "streak" -> displayStreakLeaderboard(event);
                case "weapons" -> displayWeaponsLeaderboard(event);
                case "deaths" -> displayDeathsLeaderboard(event);
                default -> event.getHook().sendMessage("Unknown leaderboard type: " + type).queue();
            }
        } catch (Exception e) {
            logger.error("Error retrieving leaderboard", e);
            event.getHook().sendMessage("An error occurred while retrieving the leaderboard.").queue();
        }
    }
    
    /**
     * Helper method to get the appropriate reason message for servers with restricted isolation
     * @param server The game server to check
     * @return An appropriate reason message based on the server's isolation mode
     */
    private String getIsolationReasonMessage(GameServer server) {
        if (server == null) {
            return "No player statistics available for this server yet.";
        }
        
        if (server.isDefaultServer()) {
            return "This is a Default Server and cannot display player statistics.";
        } else if (server.isReadOnly()) {
            return "This server is in read-only mode and cannot display statistics.";
        } else if (server.isIsolationDisabled()) {
            return "This server has isolation disabled and cannot display statistics.";
        }
        
        return "No player statistics available for this server yet.";
    }
    
    private void displayKillsLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Get the active server for this guild from context
        GameServerRepository gameServerRepo = new GameServerRepository();
        String serverId = event.getGuild().getName(); // Default to guild name
        GameServer activeServer = getActiveGameServer(event);
        
        // If we have a valid server, use its ID for isolation
        if (activeServer != null) {
            serverId = activeServer.getServerId();
            
            // Set isolation context for proper data boundaries
            GuildIsolationManager.getInstance().setContext(guildId, serverId);
        }
        
        try {
            // Get top players by kills with proper isolation
            List<Player> allPlayers = playerRepository.getTopPlayersByKills(guildId, serverId, 10);
            
            if (allPlayers.isEmpty()) {
                // Use our helper method to get the appropriate message based on isolation mode
                String reason = getIsolationReasonMessage(activeServer);
                
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Top Killers Leaderboard", 
                        reason,
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Build leaderboard
            StringBuilder description = new StringBuilder();
            
            for (int i = 0; i < allPlayers.size(); i++) {
                Player player = allPlayers.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getKills()).append(" kills (")
                        .append(player.getDeaths()).append(" deaths)\n");
            }
            
            // Use our new isolation-aware embed with proper context
            event.getHook().sendMessageEmbeds(
                EmbedThemes.isolationAwareLeaderboardEmbed(
                    "Top Killers Leaderboard", 
                    description.toString(),
                    activeServer != null ? activeServer.getIsolationMode() : "standard",
                    activeServer != null ? activeServer.getName() : event.getGuild().getName()
                )
            ).queue();
        } finally {
            // Always clear context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    private void displayKDLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Get the active server for this guild from context
        String serverId = event.getGuild().getName(); // Default to guild name
        GameServer activeServer = getActiveGameServer(event);
        
        // If we have a valid server, use its ID for isolation
        if (activeServer != null) {
            serverId = activeServer.getServerId();
            
            // Set isolation context for proper data boundaries
            GuildIsolationManager.getInstance().setContext(guildId, serverId);
        }
        
        try {
            // Get top 10 players by K/D ratio (minimum 10 kills to qualify) with proper isolation
            List<Player> kdPlayers = playerRepository.getTopPlayersByKD(guildId, serverId, 10, 10);
            
            if (kdPlayers.isEmpty()) {
                // Use our helper method and add additional context for KD requirements
                String reason = getIsolationReasonMessage(activeServer);
                
                // If not a restricted server, add additional context for KD requirements
                if (activeServer == null || (!activeServer.hasRestrictedIsolation())) {
                    reason = "No player statistics available for this server yet. " +
                            "Players will appear here after they've recorded at least 10 kills.";
                }
                
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Top K/D Ratio Leaderboard", 
                        reason,
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Sort by K/D ratio with improved calculation to handle division by zero
            kdPlayers.sort((p1, p2) -> {
                double kd1 = calculateKD(p1.getKills(), p1.getDeaths());
                double kd2 = calculateKD(p2.getKills(), p2.getDeaths());
                return Double.compare(kd2, kd1);
            });
            
            // Limit to top 10
            if (kdPlayers.size() > 10) {
                kdPlayers = kdPlayers.subList(0, 10);
            }
            
            // Build leaderboard
            StringBuilder description = new StringBuilder();
            
            for (int i = 0; i < kdPlayers.size(); i++) {
                Player player = kdPlayers.get(i);
                double kd = calculateKD(player.getKills(), player.getDeaths());
                
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(df.format(kd)).append(" K/D (")
                        .append(player.getKills()).append("k/")
                        .append(player.getDeaths()).append("d)\n");
            }
            
            // Use our new isolation-aware embed with proper context
            event.getHook().sendMessageEmbeds(
                EmbedThemes.isolationAwareLeaderboardEmbed(
                    "Top K/D Ratio Leaderboard", 
                    description.toString(),
                    activeServer != null ? activeServer.getIsolationMode() : "standard",
                    activeServer != null ? activeServer.getName() : event.getGuild().getName()
                )
            ).queue();
        } finally {
            // Always clear context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Calculate K/D ratio safely handling division by zero
     */
    private double calculateKD(int kills, int deaths) {
        // If no deaths, use kills as K/D but cap at a maximum of 999 to prevent display issues
        if (deaths == 0) {
            return Math.min(kills, 999);
        }
        return (double) kills / deaths;
    }
    
    /**
     * Display leaderboard for longest kill distances
     */
    private void displayDistanceLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Get the active server for this guild from context
        String serverId = event.getGuild().getName(); // Default to guild name
        GameServer activeServer = getActiveGameServer(event);
        
        // If we have a valid server, use its ID for isolation
        if (activeServer != null) {
            serverId = activeServer.getServerId();
            
            // Set isolation context for proper data boundaries
            GuildIsolationManager.getInstance().setContext(guildId, serverId);
        }
        
        try {
            // Get top players by longest kill distance with proper isolation
            List<Player> allDistancePlayers = playerRepository.getTopPlayersByDistance(guildId, serverId, 50);
            
            if (allDistancePlayers.isEmpty()) {
                // Use our new fallback embed for empty data
                // Use our helper method and add additional context when appropriate
                String reason = getIsolationReasonMessage(activeServer);
                
                // If not a restricted server, provide a more specific message
                if (activeServer == null || (!activeServer.hasRestrictedIsolation())) {
                    reason = "No long-distance kills have been recorded yet.";
                }
                
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Longest Kill Distance Leaderboard", 
                        reason,
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Filter by minimum distance of 300m and sort by distance
            List<Player> distancePlayers = allDistancePlayers.stream()
                    .filter(p -> p.getLongestKillDistance() >= 300)
                    .sorted((p1, p2) -> Integer.compare(p2.getLongestKillDistance(), p1.getLongestKillDistance()))
                    .limit(10)
                    .collect(Collectors.toList());
            
            if (distancePlayers.isEmpty()) {
                // Use our new fallback embed for filtered data
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Longest Kill Distance Leaderboard (300m+)", 
                        "No kills beyond 300m have been recorded yet.\n\n" +
                        "Players will appear here after they've recorded kills at 300m or beyond.",
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Build leaderboard
            StringBuilder description = new StringBuilder();
            
            for (int i = 0; i < distancePlayers.size(); i++) {
                Player player = distancePlayers.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getLongestKillDistance()).append("m ")
                        .append("(Victim: ").append(player.getLongestKillVictim())
                        .append(" | Weapon: ").append(player.getLongestKillWeapon()).append(")\n");
            }
            
            // Use our new isolation-aware embed with proper context
            event.getHook().sendMessageEmbeds(
                EmbedThemes.isolationAwareLeaderboardEmbed(
                    "Longest Kill Distance Leaderboard (300m+)", 
                    description.toString(),
                    activeServer != null ? activeServer.getIsolationMode() : "standard",
                    activeServer != null ? activeServer.getName() : event.getGuild().getName()
                )
            ).queue();
        } finally {
            // Always clear context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Display leaderboard for longest kill streaks
     */
    private void displayStreakLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Get the active server for this guild from context
        String serverId = event.getGuild().getName(); // Default to guild name
        GameServer activeServer = getActiveGameServer(event);
        
        // If we have a valid server, use its ID for isolation
        if (activeServer != null) {
            serverId = activeServer.getServerId();
            
            // Set isolation context for proper data boundaries
            GuildIsolationManager.getInstance().setContext(guildId, serverId);
        }
        
        try {
            // Get top players by longest kill streak with proper isolation
            List<Player> streakPlayers = playerRepository.getTopPlayersByKillStreak(guildId, serverId, 10);
            
            if (streakPlayers.isEmpty()) {
                // Use our new fallback embed for empty data
                // Use our helper method and add additional context when appropriate
                String reason = getIsolationReasonMessage(activeServer);
                
                // If not a restricted server, provide a more specific message
                if (activeServer == null || (!activeServer.hasRestrictedIsolation())) {
                    reason = "No kill streaks have been recorded yet.";
                }
                
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Kill Streak Leaderboard", 
                        reason,
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Build leaderboard
            StringBuilder description = new StringBuilder();
            
            for (int i = 0; i < streakPlayers.size(); i++) {
                Player player = streakPlayers.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getLongestKillStreak()).append(" kills ")
                        .append("(Total Kills: ").append(player.getKills()).append(")\n");
            }
            
            // Use our new isolation-aware embed with proper context
            event.getHook().sendMessageEmbeds(
                EmbedThemes.isolationAwareLeaderboardEmbed(
                    "Kill Streak Leaderboard", 
                    description.toString(),
                    activeServer != null ? activeServer.getIsolationMode() : "standard",
                    activeServer != null ? activeServer.getName() : event.getGuild().getName()
                )
            ).queue();
        } finally {
            // Always clear context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Display leaderboard for top weapons and their users
     */
    private void displayWeaponsLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Get the active server for this guild from context
        String serverId = event.getGuild().getName(); // Default to guild name
        GameServer activeServer = getActiveGameServer(event);
        
        // If we have a valid server, use its ID for isolation
        if (activeServer != null) {
            serverId = activeServer.getServerId();
            
            // Set isolation context for proper data boundaries
            GuildIsolationManager.getInstance().setContext(guildId, serverId);
        }
        
        try {
            // Collect all players with proper isolation
            List<Player> allPlayers = playerRepository.getTopPlayersByKills(guildId, serverId, 1000);
            
            if (allPlayers.isEmpty()) {
                // Use our new fallback embed for empty data
                // Use our helper method and add additional context when appropriate
                String reason = getIsolationReasonMessage(activeServer);
                
                // If not a restricted server, provide a more specific message
                if (activeServer == null || (!activeServer.hasRestrictedIsolation())) {
                    reason = "No weapon kills have been recorded yet.";
                }
                
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Top Weapons Leaderboard", 
                        reason,
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Aggregate weapon usage across all players
            Map<String, Integer> weaponKills = new HashMap<>();
            Map<String, String> topPlayerByWeapon = new HashMap<>();
            
            for (Player player : allPlayers) {
                if (player.getWeaponKills() != null) {
                    for (Map.Entry<String, Integer> entry : player.getWeaponKills().entrySet()) {
                        String weapon = entry.getKey();
                        int kills = entry.getValue();
                        
                        // Update total kills with this weapon
                        weaponKills.put(weapon, weaponKills.getOrDefault(weapon, 0) + kills);
                        
                        // Track top player for each weapon
                        if (!topPlayerByWeapon.containsKey(weapon) || 
                            kills > getWeaponKillsForPlayer(allPlayers, topPlayerByWeapon.get(weapon), weapon)) {
                            topPlayerByWeapon.put(weapon, player.getName());
                        }
                    }
                }
            }
            
            // If we have no weapon data even with players, show specific message
            if (weaponKills.isEmpty()) {
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Top Weapons Leaderboard", 
                        "Weapon usage data has not been recorded yet.",
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Sort weapons by kill count
            List<Map.Entry<String, Integer>> sortedWeapons = new ArrayList<>(weaponKills.entrySet());
            sortedWeapons.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            
            // Limit to top 10
            if (sortedWeapons.size() > 10) {
                sortedWeapons = sortedWeapons.subList(0, 10);
            }
            
            // Build leaderboard
            StringBuilder description = new StringBuilder();
            
            for (int i = 0; i < sortedWeapons.size(); i++) {
                Map.Entry<String, Integer> entry = sortedWeapons.get(i);
                String weapon = entry.getKey();
                int kills = entry.getValue();
                String topPlayer = topPlayerByWeapon.getOrDefault(weapon, "Unknown");
                
                description.append("`").append(i + 1).append(".` **")
                        .append(weapon).append("** - ")
                        .append(kills).append(" kills ")
                        .append("(Top user: ").append(topPlayer).append(")\n");
            }
            
            // Use our new isolation-aware embed with proper context
            event.getHook().sendMessageEmbeds(
                EmbedThemes.isolationAwareLeaderboardEmbed(
                    "Top Weapons Leaderboard", 
                    description.toString(),
                    activeServer != null ? activeServer.getIsolationMode() : "standard",
                    activeServer != null ? activeServer.getName() : event.getGuild().getName()
                )
            ).queue();
        } finally {
            // Always clear context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Display leaderboard for most deaths
     */
    private void displayDeathsLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Get the active server for this guild from context
        String serverId = event.getGuild().getName(); // Default to guild name
        GameServer activeServer = getActiveGameServer(event);
        
        // If we have a valid server, use its ID for isolation
        if (activeServer != null) {
            serverId = activeServer.getServerId();
            
            // Set isolation context for proper data boundaries
            GuildIsolationManager.getInstance().setContext(guildId, serverId);
        }
        
        try {
            // Get top players by death count with proper isolation
            List<Player> deathPlayers = playerRepository.getTopPlayersByDeaths(guildId, serverId, 10);
            
            if (deathPlayers.isEmpty()) {
                // Use our new fallback embed for empty data
                // Use our helper method and add additional context when appropriate
                String reason = getIsolationReasonMessage(activeServer);
                
                // If not a restricted server, provide a more specific message
                if (activeServer == null || (!activeServer.hasRestrictedIsolation())) {
                    reason = "No deaths have been recorded yet.";
                }
                
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.fallbackLeaderboardEmbed(
                        "Most Deaths Leaderboard", 
                        reason,
                        activeServer != null ? activeServer.getName() : event.getGuild().getName()
                    )
                ).queue();
                return;
            }
            
            // Build leaderboard
            StringBuilder description = new StringBuilder();
            
            for (int i = 0; i < deathPlayers.size(); i++) {
                Player player = deathPlayers.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getDeaths()).append(" deaths ")
                        .append("(Suicides: ").append(player.getSuicides()).append(" | ")
                        .append("Killed most by: ").append(player.getKilledByMost()).append(")\n");
            }
            
            // Use our new isolation-aware embed with proper context
            event.getHook().sendMessageEmbeds(
                EmbedThemes.isolationAwareLeaderboardEmbed(
                    "Most Deaths Leaderboard", 
                    description.toString(),
                    activeServer != null ? activeServer.getIsolationMode() : "standard",
                    activeServer != null ? activeServer.getName() : event.getGuild().getName()
                )
            ).queue();
        } finally {
            // Always clear context when done
            GuildIsolationManager.getInstance().clearContext();
        }
    }
    
    /**
     * Get the active GameServer for a guild from a slash command event
     * This method checks the current context for the selected server
     * @param event The slash command event
     * @return The active GameServer or null if none selected
     */
    private GameServer getActiveGameServer(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            return null;
        }
        
        long guildId = event.getGuild().getIdLong();
        GameServerRepository serverRepo = new GameServerRepository();
        
        // Try to get the active server selection for the user
        // This would work with a server selection system if one is implemented
        
        // For now, check if guild has only one server and use that
        List<GameServer> guildServers = serverRepo.getServersByGuildId(guildId);
        
        if (guildServers.size() == 1) {
            return guildServers.get(0);
        } else if (guildServers.size() > 1) {
            // If multiple servers, try to find the primary one
            for (GameServer server : guildServers) {
                if (server.isPrimaryServer()) {
                    return server;
                }
            }
            
            // Fall back to any non-default server
            for (GameServer server : guildServers) {
                if (!DefaultServerInitializer.DEFAULT_SERVER_ID.equals(server.getServerId())) {
                    return server;
                }
            }
            
            // Last resort: just use the first server
            return guildServers.get(0);
        }
        
        return null;
    }
    
    /**
     * Helper method to get weapon kills for a specific player
     */
    private int getWeaponKillsForPlayer(List<Player> players, String playerName, String weapon) {
        for (Player player : players) {
            if (player.getName().equals(playerName)) {
                Map<String, Integer> weaponKills = player.getWeaponKills();
                if (weaponKills != null) {
                    return weaponKills.getOrDefault(weapon, 0);
                }
                return 0;
            }
        }
        return 0;
    }
}
