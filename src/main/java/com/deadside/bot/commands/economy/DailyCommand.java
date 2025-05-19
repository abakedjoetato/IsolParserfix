package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Random;

/**
 * Command for claiming daily rewards
 */
public class DailyCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(DailyCommand.class);
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final Random random = new Random();
    
    // Daily reward amounts
    private static final int BASE_REWARD = 100;
    private static final int MAX_STREAK_BONUS = 500;
    private static final int MAX_STREAK_DAYS = 7;
    
    @Override
    public String getName() {
        return "daily";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "Claim your daily reward of coins");
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            // Defer reply to give us time to process
            event.deferReply().queue();
            
            // Get linked player information
            long userId = event.getUser().getIdLong();
            LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(userId);
            
            if (linkedPlayer == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Not Linked", 
                                "You don't have a linked Deadside account. Use `/link` to connect your Discord and Deadside accounts.")
                ).queue();
                return;
            }
            
            // Get player stats
            Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
            
            if (player == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Player Not Found", 
                                "Unable to find player data. This could be because the player hasn't been active yet.")
                ).queue();
                return;
            }
            
            // Check if player can claim daily reward
            if (!player.getCurrency().isDailyRewardAvailable()) {
                // Calculate time until next daily is available
                long lastDaily = player.getCurrency().getLastDailyReward();
                long nextDailyTime = lastDaily + (24 * 60 * 60 * 1000);
                long timeUntilNext = nextDailyTime - System.currentTimeMillis();
                
                // Convert to hours/minutes
                long hoursLeft = timeUntilNext / (60 * 60 * 1000);
                long minutesLeft = (timeUntilNext % (60 * 60 * 1000)) / (60 * 1000);
                
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.warningEmbed("Already Claimed", 
                                "You've already claimed your daily reward today. You can claim again in " + 
                                hoursLeft + " hours and " + minutesLeft + " minutes.")
                ).queue();
                return;
            }
            
            // Calculate streak and bonus
            int streak = player.getCurrency().calculateStreak();
            int streakBonus = calculateStreakBonus(streak);
            
            // Calculate reward with random bonus (90-110% of base)
            int randomFactor = 90 + random.nextInt(21); // 90-110
            int baseReward = BASE_REWARD * randomFactor / 100;
            int totalReward = baseReward + streakBonus;
            
            // Give the reward
            boolean success = player.getCurrency().claimDailyReward(totalReward);
            
            if (!success) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Claim Failed", 
                                "Failed to claim daily reward. Please try again later.")
                ).queue();
                return;
            }
            
            // Save player
            playerRepository.save(player);
            
            // Send success message
            displayRewardMessage(event, player, baseReward, streakBonus, totalReward, streak + 1);
            
            logger.info("User {} claimed daily reward of {} coins (streak: {})", 
                    event.getUser().getName(), totalReward, streak + 1);
            
        } catch (Exception e) {
            logger.error("Error executing daily command", e);
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Error", "An error occurred while claiming your daily reward.")
            ).queue();
        }
    }
    
    /**
     * Calculate streak bonus based on consecutive days
     */
    private int calculateStreakBonus(int currentStreak) {
        // Calculate the next day's streak (current + 1)
        int nextStreak = Math.min(currentStreak + 1, MAX_STREAK_DAYS);
        
        // Bonus increases with streak days
        return (nextStreak * MAX_STREAK_BONUS) / MAX_STREAK_DAYS;
    }
    
    /**
     * Display the reward message
     */
    private void displayRewardMessage(SlashCommandInteractionEvent event, Player player, 
                                     int baseAmount, int streakBonus, int totalAmount, int streak) {
        StringBuilder description = new StringBuilder();
        
        description.append("💰 **Daily Reward**: `").append(formatAmount(baseAmount)).append(" coins`\n");
        
        if (streakBonus > 0) {
            description.append("🔥 **Streak Bonus**: `").append(formatAmount(streakBonus)).append(" coins`\n");
        }
        
        description.append("\n**Total Reward**: `").append(formatAmount(totalAmount)).append(" coins`\n\n");
        
        description.append("Your streak is now **").append(streak).append(" day")
                  .append(streak != 1 ? "s" : "").append("**! ");
        
        if (streak < MAX_STREAK_DAYS) {
            int nextBonus = calculateStreakBonus(streak);
            description.append("Come back tomorrow for a `").append(formatAmount(nextBonus))
                      .append(" coin` streak bonus!");
        } else {
            description.append("You've reached the maximum streak bonus!");
        }
        
        description.append("\n\n**New Balance**: `")
                  .append(formatAmount(player.getCurrency().getCoins())).append(" coins`");
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.customEmbed("Daily Reward Claimed", description.toString(), Color.YELLOW)
        ).queue();
    }
    
    /**
     * Format a currency amount with commas
     */
    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }
}