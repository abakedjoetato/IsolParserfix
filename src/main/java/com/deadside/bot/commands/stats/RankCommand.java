package com.deadside.bot.commands.stats;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.PremiumManager;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Command for checking a player's rank in various statistics
 */
public class RankCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(RankCommand.class);
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final PremiumManager premiumManager = new PremiumManager();
    private final DecimalFormat df = new DecimalFormat("#.##");
    
    @Override
    public String getName() {
        return "rank";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "View a player's ranking in various statistics")
                .addOption(OptionType.STRING, "player", "In-game player name to check rank for", false)
                .addOption(OptionType.USER, "user", "Discord user to check rank for", false);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        // Check for premium if feature is restricted
        long guildId = event.getGuild().getIdLong();
        String serverId = event.getGuild().getName(); // Use guild name as server ID
        
        if (!premiumManager.hasPremium(guildId)) {
            event.reply("This command is only available with premium. The killfeed is available for free.").setEphemeral(true).queue();
            return;
        }
        
        String playerName = event.getOption("player", OptionMapping::getAsString);
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        
        // If neither option is provided, default to the command user
        if (playerName == null && targetUser == null) {
            targetUser = event.getUser();
        }
        
        event.deferReply().queue();
        
        try {
            Player player = null;
            
            if (playerName != null) {
                // Find player by in-game name with server isolation
                List<Player> matchingPlayers = playerRepository.findByNameLikeAndGuildIdAndServerId(playerName, guildId, serverId);
                if (!matchingPlayers.isEmpty()) {
                    // Try to find exact match first
                    for (Player p : matchingPlayers) {
                        if (p.getName().equalsIgnoreCase(playerName)) {
                            player = p;
                            break;
                        }
                    }
                    
                    // If no exact match, use first result
                    if (player == null) {
                        player = matchingPlayers.get(0);
                    }
                }
            } else if (targetUser != null) {
                // Find linked player by Discord user ID
                com.deadside.bot.db.repositories.LinkedPlayerRepository linkedPlayerRepo = 
                    new com.deadside.bot.db.repositories.LinkedPlayerRepository();
                com.deadside.bot.db.models.LinkedPlayer linkedPlayer = linkedPlayerRepo.findByDiscordId(targetUser.getIdLong());
                
                if (linkedPlayer != null) {
                    // Use server-aware player lookup for proper data isolation
                    player = playerRepository.findByPlayerIdAndGuildIdAndServerId(
                        linkedPlayer.getMainPlayerId(), guildId, serverId);
                }
            }
            
            if (player == null) {
                String errorMessage = playerName != null 
                    ? "No player found with name: " + playerName
                    : targetUser != null 
                        ? targetUser.getName() + " hasn't linked a Deadside account yet."
                        : "No player information found.";
                
                event.getHook().sendMessage(errorMessage).queue();
                return;
            }
            
            // Get all ranked players to compute ranks with server isolation
            List<Player> allPlayers = playerRepository.getTopPlayersByKills(guildId, serverId, 1000); // Get a lot of players with server isolation
            
            // Calculate ranks and send embed
            event.getHook().sendMessageEmbeds(buildRankEmbed(player, allPlayers)).queue();
            
        } catch (Exception e) {
            logger.error("Error retrieving player rank", e);
            event.getHook().sendMessage("An error occurred while retrieving player rank.").queue();
        }
    }
    
    /**
     * Build the player rank embed with various stat rankings
     */
    private net.dv8tion.jda.api.entities.MessageEmbed buildRankEmbed(Player player, List<Player> allPlayers) {
        // Minimum threshold to be included in ranking
        final int MIN_KILLS = 5;
        
        // Filter players with minimum kills for ranking and exclude invalid player records
        List<Player> rankablePlayers = allPlayers.stream()
                .filter(p -> p.getKills() >= MIN_KILLS)
                .filter(p -> p.getName() != null && !p.getName().isEmpty() && !"**".equals(p.getName()))
                .collect(Collectors.toList());
        
        // Total number of ranked players
        int totalPlayers = rankablePlayers.size();
        
        // If player doesn't meet minimum threshold, still show stats but indicate not ranked
        boolean isRanked = player.getKills() >= MIN_KILLS;
        
        // Calculate kills rank
        int killsRank = isRanked ? calculateRank(player, rankablePlayers, 
                Comparator.comparingInt(Player::getKills).reversed()) : -1;
        
        // Calculate K/D rank (only for players with kills)
        int kdRank = isRanked ? calculateRank(player, rankablePlayers,
                Comparator.comparingDouble(Player::getKdRatio).reversed()) : -1;
        
        // Calculate score rank
        int scoreRank = isRanked ? calculateRank(player, rankablePlayers,
                Comparator.comparingInt(Player::getScore).reversed()) : -1;
                
        // Calculate distance rank (if available)
        int distanceRank = -1;
        if (player.getLongestKillDistance() > 0) {
            distanceRank = calculateRank(player, allPlayers.stream()
                .filter(p -> p.getLongestKillDistance() > 0)
                .filter(p -> p.getName() != null && !p.getName().isEmpty() && !"**".equals(p.getName()))
                .collect(Collectors.toList()),
                Comparator.comparingInt(Player::getLongestKillDistance).reversed());
        }
        
        // Calculate streak rank (if available)
        int streakRank = -1;
        if (player.getLongestKillStreak() > 0) {
            streakRank = calculateRank(player, allPlayers.stream()
                .filter(p -> p.getLongestKillStreak() > 0)
                .filter(p -> p.getName() != null && !p.getName().isEmpty() && !"**".equals(p.getName()))
                .collect(Collectors.toList()),
                Comparator.comparingInt(Player::getLongestKillStreak).reversed());
        }
                
        // Build embed description
        StringBuilder description = new StringBuilder();
        description.append("# ").append(player.getName()).append("'s Rankings\n\n");
        
        // Player stats summary
        description.append("## Player Stats\n");
        description.append("Kills: **").append(player.getKills()).append("**\n");
        description.append("Deaths: **").append(player.getDeaths()).append("**\n");
        description.append("K/D Ratio: **").append(df.format(player.getKdRatio())).append("**\n");
        description.append("Score: **").append(player.getScore()).append("**\n");
        
        // Add enhanced stats if available
        if (player.getLongestKillDistance() > 0) {
            description.append("Longest Kill: **").append(player.getLongestKillDistance()).append("m** ");
            description.append("(Victim: ").append(player.getLongestKillVictim()).append(", ");
            description.append("Weapon: ").append(player.getLongestKillWeapon()).append(")\n");
        }
        
        if (player.getLongestKillStreak() > 0) {
            description.append("Best Streak: **").append(player.getLongestKillStreak()).append(" kills**\n");
            if (player.getCurrentKillStreak() > 0) {
                description.append("Current Streak: **").append(player.getCurrentKillStreak()).append(" kills**\n");
            }
        }
        
        description.append("\n");
        
        // Rankings section
        description.append("## Rankings ");
        if (!isRanked) {
            description.append("(Needs ").append(MIN_KILLS).append("+ kills to be ranked)\n");
        } else {
            description.append("(Out of ").append(totalPlayers).append(" players)\n");
        }
        
        // Show ranking for each category
        if (isRanked) {
            description.append("Kills Rank: **#").append(killsRank).append("** (Top ")
                     .append(calculatePercentile(killsRank, totalPlayers)).append("%)\n");
                     
            description.append("K/D Ratio Rank: **#").append(kdRank).append("** (Top ")
                     .append(calculatePercentile(kdRank, totalPlayers)).append("%)\n");
                     
            description.append("Score Rank: **#").append(scoreRank).append("** (Top ")
                     .append(calculatePercentile(scoreRank, totalPlayers)).append("%)\n");
                     
            // Add distance and streak rankings if available
            if (distanceRank > 0) {
                int distanceTotalPlayers = (int)allPlayers.stream()
                    .filter(p -> p.getLongestKillDistance() > 0).count();
                    
                description.append("Longest Kill Rank: **#").append(distanceRank).append("** (Top ")
                         .append(calculatePercentile(distanceRank, distanceTotalPlayers)).append("%)\n");
            }
            
            if (streakRank > 0) {
                int streakTotalPlayers = (int)allPlayers.stream()
                    .filter(p -> p.getLongestKillStreak() > 0).count();
                    
                description.append("Kill Streak Rank: **#").append(streakRank).append("** (Top ")
                         .append(calculatePercentile(streakRank, streakTotalPlayers)).append("%)\n");
            }
        } else {
            description.append("Not enough kills to be ranked yet. Get ").append(MIN_KILLS - player.getKills())
                     .append(" more kills to be included in rankings.\n");
        }
        
        // Weapon information and detailed stats
        if (player.getMostUsedWeapon() != null && !player.getMostUsedWeapon().isEmpty()) {
            description.append("\n## Weapon Stats\n");
            description.append("Favorite Weapon: **").append(player.getMostUsedWeapon()).append("** (")
                     .append(player.getMostUsedWeaponKills()).append(" kills)\n");
            
            // Show more detailed weapon usage if available
            if (player.getWeaponKills() != null && !player.getWeaponKills().isEmpty()) {
                // Get top 3 weapons by usage
                player.getWeaponKills().entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Reverse sort
                    .limit(3)
                    .forEach(entry -> {
                        if (!entry.getKey().equals(player.getMostUsedWeapon())) {
                            description.append("• **").append(entry.getKey()).append("**: ")
                                .append(entry.getValue()).append(" kills\n");
                        }
                    });
            }
        }
        
        // Player matchups information
        if (player.getPlayerMatchups() != null && !player.getPlayerMatchups().isEmpty()) {
            description.append("\n## Player Matchups\n");
            
            // Most killed player
            if (player.getMostKilledPlayer() != null && !player.getMostKilledPlayer().isEmpty()) {
                description.append("Favorite Target: **").append(player.getMostKilledPlayer())
                    .append("** (").append(player.getMostKilledPlayerCount()).append(" kills)\n");
            }
            
            // Player that killed this player the most
            if (player.getKilledByMost() != null && !player.getKilledByMost().isEmpty()) {
                description.append("Nemesis: **").append(player.getKilledByMost())
                    .append("** (killed you ").append(player.getKilledByMostCount()).append(" times)\n");
            }
            
            // Show top 3 matchups by kills (excluding the most killed player)
            if (player.getPlayerMatchups().size() > 1) {
                description.append("\nOther top matchups:\n");
                player.getPlayerMatchups().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(player.getMostKilledPlayer()))
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Reverse sort
                    .limit(3)
                    .forEach(entry -> {
                        description.append("• **").append(entry.getKey()).append("**: ")
                            .append(entry.getValue()).append(" kills\n");
                    });
            }
        }
        
        // Last updated timestamp
        description.append("\nLast updated: <t:").append(player.getLastUpdated() / 1000).append(":R>");
        
        // Use our custom Deadside themed embed with weapon stats thumbnail
        return EmbedUtils.createEmbed(
            "Player Ranking - " + player.getName(),
            description.toString(),
            EmbedUtils.EMERALD_GREEN,
            "attachment://WeaponStats.png"
        );
    }
    
    /**
     * Calculate the rank of a player among all players using the provided comparator
     */
    private int calculateRank(Player player, List<Player> allPlayers, Comparator<Player> comparator) {
        // Sort players by the given comparator
        List<Player> sortedPlayers = allPlayers.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
        
        // Find player's position (0-based index)
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (sortedPlayers.get(i).getPlayerId().equals(player.getPlayerId())) {
                return i + 1; // Convert to 1-based index for display
            }
        }
        
        return sortedPlayers.size() + 1; // If not found, place at bottom
    }
    
    /**
     * Calculate percentile (lower is better) based on rank and total count
     */
    private int calculatePercentile(int rank, int totalPlayers) {
        if (totalPlayers <= 0) return 100;
        return Math.max(1, Math.min(100, (int)((double)rank / totalPlayers * 100)));
    }
}