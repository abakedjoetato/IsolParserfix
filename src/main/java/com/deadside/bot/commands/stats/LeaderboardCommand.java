package com.deadside.bot.commands.stats;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.FeatureGate;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import com.deadside.bot.utils.EmbedThemes;
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
    
    private void displayKillsLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Get top players by kills filtered by guild ID - use server-aware method
        // Default to a generic serverId for all servers in this guild
        String serverId = event.getGuild().getName(); // Use guild name as default server ID
        List<Player> allPlayers = playerRepository.getTopPlayersByKills(guildId, serverId, 10);
        
        if (allPlayers.isEmpty()) {
            // Use an embed with informational message instead of plain text
            event.getHook().sendMessageEmbeds(
                EmbedThemes.killfeedEmbed("Top Killers Leaderboard", 
                    "No player statistics available for this server yet.\n\n" +
                    "Players will appear here after they've been recorded with at least one kill.")
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
        
        event.getHook().sendMessageEmbeds(
                EmbedThemes.killfeedEmbed("Top Killers Leaderboard", description.toString())
        ).queue();
    }
    
    private void displayKDLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        
        // Default to guild name as server ID for filtering
        String serverId = event.getGuild().getName();
        
        // Get top 10 players by K/D ratio (minimum 10 kills to qualify) with server filtering
        List<Player> kdPlayers = playerRepository.getTopPlayersByKD(guildId, serverId, 10, 10);
        
        if (kdPlayers.isEmpty()) {
            // Use an embed with informational message instead of plain text
            event.getHook().sendMessageEmbeds(
                EmbedThemes.killfeedEmbed("Top K/D Ratio Leaderboard", 
                    "No player statistics available for this server yet.\n\n" +
                    "Players will appear here after they've recorded at least 10 kills.")
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
        
        event.getHook().sendMessageEmbeds(
                EmbedThemes.killfeedEmbed("Top K/D Ratio Leaderboard", description.toString())
        ).queue();
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
        String serverId = event.getGuild().getName();
        
        // Get top players by longest kill distance with server isolation
        List<Player> allDistancePlayers = playerRepository.getTopPlayersByDistance(guildId, serverId, 50);
        
        if (allDistancePlayers.isEmpty()) {
            event.getHook().sendMessage("No long-distance kills have been recorded yet.").queue();
            return;
        }
        
        // Filter by minimum distance of 300m and sort by distance
        List<Player> distancePlayers = allDistancePlayers.stream()
                .filter(p -> p.getLongestKillDistance() >= 300)
                .sorted((p1, p2) -> Integer.compare(p2.getLongestKillDistance(), p1.getLongestKillDistance()))
                .limit(10)
                .collect(Collectors.toList());
        
        if (distancePlayers.isEmpty()) {
            // Use an embed with informational message instead of plain text
            event.getHook().sendMessageEmbeds(
                EmbedThemes.killfeedEmbed("Longest Kill Distance Leaderboard", 
                    "No long-distance kills have been recorded yet.\n\n" +
                    "Players will appear here after they've recorded kills at 300m or beyond.")
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
        
        event.getHook().sendMessageEmbeds(
                EmbedThemes.killfeedEmbed("Longest Kill Distance Leaderboard (300m+)", 
                                      description.toString())
        ).queue();
    }
    
    /**
     * Display leaderboard for longest kill streaks
     */
    private void displayStreakLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        String serverId = event.getGuild().getName();
        
        // Get top players by longest kill streak with server isolation
        List<Player> streakPlayers = playerRepository.getTopPlayersByKillStreak(guildId, serverId, 10);
        
        if (streakPlayers.isEmpty()) {
            event.getHook().sendMessage("No kill streaks have been recorded yet.").queue();
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
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.createEmbed("Kill Streak Leaderboard", 
                                      description.toString(),
                                      EmbedUtils.EMERALD_GREEN,
                                      "attachment://Killfeed.png")
        ).queue();
    }
    
    /**
     * Display leaderboard for top weapons and their users
     */
    private void displayWeaponsLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        String serverId = event.getGuild().getName();
        
        // Collect all players with server isolation
        List<Player> allPlayers = playerRepository.getTopPlayersByKills(guildId, serverId, 1000);
        
        if (allPlayers.isEmpty()) {
            event.getHook().sendMessage("No weapon kills have been recorded yet.").queue();
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
        
        event.getHook().sendMessageEmbeds(
                EmbedThemes.killfeedEmbed("Top Weapons Leaderboard", 
                                      description.toString())
        ).queue();
    }
    
    /**
     * Display leaderboard for most deaths
     */
    private void displayDeathsLeaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        String serverId = event.getGuild().getName();
        
        // Get top players by death count with server isolation
        List<Player> deathPlayers = playerRepository.getTopPlayersByDeaths(guildId, serverId, 10);
        
        if (deathPlayers.isEmpty()) {
            event.getHook().sendMessage("No deaths have been recorded yet.").queue();
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
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.createEmbed("Most Deaths Leaderboard", 
                                      description.toString(),
                                      EmbedUtils.DARK_GRAY,
                                      "attachment://Killfeed.png")
        ).queue();
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
