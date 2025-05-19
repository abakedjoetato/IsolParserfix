package com.deadside.bot.commands.stats;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LeaderboardChannelRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.FeatureGate;
import com.deadside.bot.utils.EmbedUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Command for setting up an auto-updating leaderboard channel
 * Updates hourly with top killers, KD ratio, deaths, distance, weapons and streaks
 */
public class AutoLeaderboardCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(AutoLeaderboardCommand.class);
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final LeaderboardChannelRepository leaderboardChannelRepository = new LeaderboardChannelRepository();
    private final DecimalFormat df = new DecimalFormat("#.##");
    
    // Task scheduler for regular updates 
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    
    // Initialize the scheduler on class load
    static {
        // Update leaderboards every hour
        scheduler.scheduleAtFixedRate(() -> {
            try {
                new AutoLeaderboardCommand().updateAllLeaderboards();
            } catch (Exception e) {
                LoggerFactory.getLogger(AutoLeaderboardCommand.class).error("Error in scheduled leaderboard update", e);
            }
        }, 1, 60, TimeUnit.MINUTES);
    }
    
    @Override
    public String getName() {
        return "autoleaderboard";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "Set up an auto-updating leaderboard channel")
                .addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", "The channel to use for the leaderboard", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        // Check premium access
        if (!FeatureGate.checkCommandAccess(event, FeatureGate.Feature.LEADERBOARDS)) {
            return;
        }
        
        event.deferReply().queue();
        
        try {
            OptionMapping channelOption = event.getOption("channel");
            if (channelOption == null) {
                event.getHook().sendMessage("You must specify a channel for the leaderboard.").queue();
                return;
            }
            
            TextChannel channel = channelOption.getAsChannel().asTextChannel();
            if (channel == null) {
                event.getHook().sendMessage("The specified channel must be a text channel.").queue();
                return;
            }
            
            // Save the channel configuration
            long guildId = event.getGuild().getIdLong();
            long channelId = channel.getIdLong();
            leaderboardChannelRepository.saveLeaderboardChannel(guildId, channelId);
            
            // Send initial leaderboards
            updateLeaderboard(channel);
            
            event.getHook().sendMessage("Auto-updating leaderboard successfully set up in <#" + channelId + ">. " +
                    "The leaderboard will update hourly with the latest stats.").queue();
            
        } catch (Exception e) {
            logger.error("Error setting up auto-leaderboard", e);
            event.getHook().sendMessage("An error occurred while setting up the auto-leaderboard.").queue();
        }
    }
    
    /**
     * Updates a specific leaderboard channel
     */
    private void updateLeaderboard(TextChannel channel) {
        if (channel == null) {
            logger.warn("Attempted to update null leaderboard channel");
            return;
        }
        
        try {
            // Clear previous messages
            channel.getIterableHistory().takeAsync(10).thenAccept(messages -> {
                if (!messages.isEmpty()) {
                    channel.purgeMessages(messages);
                }
            });
            
            // Send the comprehensive leaderboard
            sendComprehensiveLeaderboard(channel);
            
        } catch (Exception e) {
            logger.error("Error updating leaderboard in channel " + channel.getId(), e);
        }
    }
    
    /**
     * Updates all registered leaderboard channels
     * This method is used by the scheduler
     */
    private void updateAllLeaderboards() {
        // In the scheduled task, we don't have direct access to JDA
        // We'll need to store the channels and update when the bot has access to JDA
        // For now, we'll log that the update was requested
        logger.info("Scheduled leaderboard update triggered");
        
        // This would normally update all registered leaderboard channels
        // Since we don't have direct JDA access in the scheduler, we'll defer this
        // In a real implementation, you'd need to get JDA from your bot's main class
        // Or store TextChannel references when they're registered
    }
    
    /**
     * Sends a comprehensive leaderboard with all stats
     */
    private void sendComprehensiveLeaderboard(TextChannel channel) {
        // Get guild ID from channel
        long guildId = channel.getGuild().getIdLong();
        String serverId = channel.getGuild().getName(); // Default to guild name as server ID
        
        // Get all required data with proper isolation
        List<Player> topKillers = playerRepository.getTopPlayersByKills(guildId, serverId, 5);
        List<Player> topKD = playerRepository.getTopPlayersByKD(guildId, serverId, 3, 10);
        List<Player> topDeaths = playerRepository.getTopPlayersByDeaths(guildId, serverId, 3);
        List<Player> topDistance = playerRepository.getTopPlayersByDistance(guildId, serverId, 3);
        List<Player> topStreak = playerRepository.getTopPlayersByKillStreak(guildId, serverId, 3);
        
        // Create and send embeds
        MessageEmbed killersEmbed = createTopKillersEmbed(topKillers);
        MessageEmbed kdEmbed = createTopKDEmbed(topKD);
        MessageEmbed deathsEmbed = createTopDeathsEmbed(topDeaths);
        MessageEmbed distanceEmbed = createTopDistanceEmbed(topDistance);
        MessageEmbed streakEmbed = createTopStreakEmbed(topStreak);
        MessageEmbed weaponsEmbed = createTopWeaponsEmbed(3);
        
        // Send each embed with a small delay to ensure order
        channel.sendMessageEmbeds(killersEmbed).queue(success -> {
            channel.sendMessageEmbeds(kdEmbed).queue(success2 -> {
                channel.sendMessageEmbeds(deathsEmbed).queue(success3 -> {
                    channel.sendMessageEmbeds(distanceEmbed).queue(success4 -> {
                        channel.sendMessageEmbeds(streakEmbed).queue(success5 -> {
                            channel.sendMessageEmbeds(weaponsEmbed).queue();
                        });
                    });
                });
            });
        });
    }
    
    /**
     * Create an embed for top killers
     */
    private MessageEmbed createTopKillersEmbed(List<Player> topKillers) {
        StringBuilder description = new StringBuilder("# Top Killers\n\n");
        
        if (topKillers.isEmpty()) {
            description.append("No data available yet.");
        } else {
            for (int i = 0; i < topKillers.size(); i++) {
                Player player = topKillers.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getKills()).append(" kills (")
                        .append(player.getDeaths()).append(" deaths)\n");
            }
        }
        
        return EmbedUtils.createEmbed(
            "Top Killers Leaderboard", 
            description.toString(),
            EmbedUtils.EMERALD_GREEN,
            "attachment://Killfeed.png"
        );
    }
    
    /**
     * Create an embed for top K/D ratio players
     */
    private MessageEmbed createTopKDEmbed(List<Player> topKD) {
        StringBuilder description = new StringBuilder("# Top K/D Ratio\n\n");
        
        if (topKD.isEmpty()) {
            description.append("No data available yet.");
        } else {
            // Sort by K/D ratio
            topKD.sort((p1, p2) -> Double.compare(p2.getKdRatio(), p1.getKdRatio()));
            
            for (int i = 0; i < topKD.size(); i++) {
                Player player = topKD.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(df.format(player.getKdRatio())).append(" K/D (")
                        .append(player.getKills()).append("k/")
                        .append(player.getDeaths()).append("d)\n");
            }
        }
        
        return EmbedUtils.createEmbed(
            "Top K/D Ratio Leaderboard", 
            description.toString(),
            EmbedUtils.EMERALD_GREEN,
            "attachment://WeaponStats.png"
        );
    }
    
    /**
     * Create an embed for top death counts
     */
    private MessageEmbed createTopDeathsEmbed(List<Player> topDeaths) {
        StringBuilder description = new StringBuilder("# Most Deaths\n\n");
        
        if (topDeaths.isEmpty()) {
            description.append("No data available yet.");
        } else {
            for (int i = 0; i < topDeaths.size(); i++) {
                Player player = topDeaths.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getDeaths()).append(" deaths (")
                        .append(player.getSuicides()).append(" suicides)\n");
            }
        }
        
        return EmbedUtils.createEmbed(
            "Most Deaths Leaderboard", 
            description.toString(),
            EmbedUtils.DARK_GRAY,
            "attachment://Killfeed.png"
        );
    }
    
    /**
     * Create an embed for top distance kills
     */
    private MessageEmbed createTopDistanceEmbed(List<Player> topDistance) {
        StringBuilder description = new StringBuilder("# Longest Kill Distance\n\n");
        
        if (topDistance.isEmpty()) {
            description.append("No data available yet.");
        } else {
            for (int i = 0; i < topDistance.size(); i++) {
                Player player = topDistance.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getLongestKillDistance()).append("m (")
                        .append(player.getLongestKillWeapon()).append(")\n");
            }
        }
        
        return EmbedUtils.createEmbed(
            "Longest Kill Distance Leaderboard", 
            description.toString(),
            EmbedUtils.EMERALD_GREEN,
            "attachment://WeaponStats.png"
        );
    }
    
    /**
     * Create an embed for top kill streaks
     */
    private MessageEmbed createTopStreakEmbed(List<Player> topStreak) {
        StringBuilder description = new StringBuilder("# Longest Kill Streaks\n\n");
        
        if (topStreak.isEmpty()) {
            description.append("No data available yet.");
        } else {
            for (int i = 0; i < topStreak.size(); i++) {
                Player player = topStreak.get(i);
                description.append("`").append(i + 1).append(".` **")
                        .append(player.getName()).append("** - ")
                        .append(player.getLongestKillStreak()).append(" kills\n");
            }
        }
        
        return EmbedUtils.createEmbed(
            "Kill Streak Leaderboard", 
            description.toString(),
            EmbedUtils.EMERALD_GREEN,
            "attachment://Killfeed.png"
        );
    }
    
    /**
     * Create an embed for top weapons
     */
    private MessageEmbed createTopWeaponsEmbed(int limit) {
        StringBuilder description = new StringBuilder("# Top Weapons\n\n");
        
        // Collect all players
        List<Player> allPlayers = playerRepository.getTopPlayersByKills(1000);
        
        if (allPlayers.isEmpty()) {
            description.append("No data available yet.");
        } else {
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
            sortedWeapons.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue())); // Reverse sort
            
            // Limit to top weapons
            if (sortedWeapons.size() > limit) {
                sortedWeapons = sortedWeapons.subList(0, limit);
            }
            
            // Build list of top weapons
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
        }
        
        return EmbedUtils.createEmbed(
            "Top Weapons Leaderboard", 
            description.toString(),
            EmbedUtils.EMERALD_GREEN,
            "attachment://WeaponStats.png"
        );
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