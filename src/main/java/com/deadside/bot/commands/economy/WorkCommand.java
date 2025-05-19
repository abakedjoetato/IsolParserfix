package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.config.Config;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Command for players to earn coins by working
 */
public class WorkCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(WorkCommand.class);
    private final Config config = Config.getInstance();
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final Random random = new Random();
    
    // Work cooldown tracking - userid -> last work time
    private final Map<Long, Long> workCooldowns = new HashMap<>();
    private static final long WORK_COOLDOWN_HOURS = 1; // 1 hour cooldown
    
    // List of possible work tasks for flavor text
    private static final String[] WORK_TASKS = {
            "scavenged for supplies in the abandoned city",
            "patrolled the perimeter fence",
            "repaired broken equipment",
            "hunted wildlife for food",
            "traded with local merchants",
            "escorted a supply convoy",
            "cleared zombies from a building",
            "collected medical supplies",
            "scouted a new area for resources",
            "helped repair the community generators",
            "reinforced defensive positions",
            "rescued a stranded survivor",
            "fixed up some vehicles",
            "harvested crops from the community farm",
            "stood guard at the main gate",
            "ambushed a group of bandits",
            "recovered supplies from a crashed helicopter",
            "helped train new recruits",
            "cooked meals for the community",
            "crafted ammunition at the workbench"
    };
    
    @Override
    public String getName() {
        return "work";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "Work to earn coins");
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        long userId = user.getIdLong();
        
        // Check cooldown
        if (isOnCooldown(userId)) {
            long remainingTime = getCooldownRemaining(userId);
            
            event.replyEmbeds(EmbedUtils.errorEmbed(
                    "Cooldown Active",
                    "You're still tired from your last shift!\n" +
                    "You can work again in **" + formatTime(remainingTime) + "**."
            )).setEphemeral(true).queue();
            
            return;
        }
        
        // Defer reply for database operations
        event.deferReply().queue();
        
        // Get linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(userId);
        if (linkedPlayer == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Account Not Linked",
                    "You need to link your Discord account to your Deadside character first!\n" +
                    "Use the `/link` command to get started."
            )).setEphemeral(true).queue();
            
            return;
        }
        
        // Get player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        if (player == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Player Not Found",
                    "Could not find your player data. Have you played on the server recently?"
            )).setEphemeral(true).queue();
            
            return;
        }
        
        // Calculate random work reward
        long minReward = config.getWorkMinAmount();
        long maxReward = config.getWorkMaxAmount();
        long reward = ThreadLocalRandom.current().nextLong(minReward, maxReward + 1);
        
        // Choose a random work task
        String workTask = WORK_TASKS[random.nextInt(WORK_TASKS.length)];
        
        // Add coins to player
        player.getCurrency().addCoins(reward);
        playerRepository.save(player);
        
        // Set cooldown
        setWorkCooldown(userId);
        
        // Create embed response
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💼 Work Completed")
                .setDescription("You " + workTask + " and earned **" + String.format("%,d", reward) + " coins**!")
                .setColor(new Color(58, 198, 134)) // Teal green
                .addField("Balance", String.format("%,d coins", player.getCurrency().getCoins()), true)
                .addField("Next Work", "Available in **" + formatTime(TimeUnit.HOURS.toMillis(WORK_COOLDOWN_HOURS)) + "**", true)
                .setFooter("💡 Use /balance to check your current balance");
        
        // Send response (through hook since we deferred)
        event.getHook().sendMessageEmbeds(embed.build()).queue();
        
        // Log the transaction
        logger.info("User {} completed work and earned {} coins", userId, reward);
    }
    
    /**
     * Check if user is on work cooldown
     */
    private boolean isOnCooldown(long userId) {
        if (!workCooldowns.containsKey(userId)) {
            return false;
        }
        
        long lastWorkTime = workCooldowns.get(userId);
        long cooldownTime = TimeUnit.HOURS.toMillis(WORK_COOLDOWN_HOURS);
        return System.currentTimeMillis() - lastWorkTime < cooldownTime;
    }
    
    /**
     * Get remaining cooldown time in milliseconds
     */
    private long getCooldownRemaining(long userId) {
        if (!workCooldowns.containsKey(userId)) {
            return 0L;
        }
        
        long lastWorkTime = workCooldowns.get(userId);
        long cooldownTime = TimeUnit.HOURS.toMillis(WORK_COOLDOWN_HOURS);
        long elapsedTime = System.currentTimeMillis() - lastWorkTime;
        
        return Math.max(0, cooldownTime - elapsedTime);
    }
    
    /**
     * Set work cooldown for user
     */
    private void setWorkCooldown(long userId) {
        workCooldowns.put(userId, System.currentTimeMillis());
    }
    
    /**
     * Format time in milliseconds to readable format
     */
    private String formatTime(long timeMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(timeMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60;
        
        if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, seconds);
        } else {
            return String.format("%d seconds", seconds);
        }
    }
}