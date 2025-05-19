package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.FeatureGate;
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

import java.awt.Color;

/**
 * Command for checking player balance
 */
public class BalanceCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(BalanceCommand.class);
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    
    @Override
    public String getName() {
        return "balance";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "Check your balance or another player's balance")
                .addOption(OptionType.USER, "user", "User to check balance for", false);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            // Check if guild has access to the economy feature (premium feature)
            if (!FeatureGate.checkCommandAccess(event, FeatureGate.Feature.ECONOMY)) {
                // The FeatureGate utility already sent a premium upsell message
                return;
            }
            
            // Defer reply to give us time to process
            event.deferReply().queue();
            
            // Check if we're looking up another user or self
            User targetUser = event.getOption("user", event.getUser(), OptionMapping::getAsUser);
            boolean isSelf = targetUser.getId().equals(event.getUser().getId());
            
            // Get linked player information
            LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
            
            if (linkedPlayer == null) {
                // Not linked
                if (isSelf) {
                    event.getHook().sendMessageEmbeds(
                            EmbedUtils.errorEmbed("Not Linked", 
                                    "You don't have a linked Deadside account. Use `/link` to connect your Discord and Deadside accounts.")
                    ).queue();
                } else {
                    event.getHook().sendMessageEmbeds(
                            EmbedUtils.errorEmbed("Not Linked", 
                                    targetUser.getName() + " doesn't have a linked Deadside account.")
                    ).queue();
                }
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
            
            // Display balance information
            displayBalance(event, player, targetUser, isSelf);
            
        } catch (Exception e) {
            logger.error("Error executing balance command", e);
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Error", "An error occurred while retrieving balance information.")
            ).queue();
        }
    }
    
    /**
     * Display player balance information
     */
    private void displayBalance(SlashCommandInteractionEvent event, Player player, User targetUser, boolean isSelf) {
        String title = isSelf ? "Your Balance" : targetUser.getName() + "'s Balance";
        
        StringBuilder description = new StringBuilder();
        
        // Wallet balance
        description.append("💰 **Wallet**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`\n");
        
        // Bank balance
        description.append("🏦 **Bank**: `").append(formatAmount(player.getCurrency().getBankCoins())).append(" coins`\n");
        
        // Total balance
        description.append("💸 **Total**: `").append(formatAmount(player.getCurrency().getTotalBalance())).append(" coins`\n\n");
        
        // Special currencies
        description.append("🎯 **Bounty Points**: `").append(player.getCurrency().getBountyPoints()).append("`\n");
        description.append("⭐ **Prestige Points**: `").append(player.getCurrency().getPrestigePoints()).append("`\n\n");
        
        // Add lifetime stats
        description.append("**Lifetime Stats**\n");
        description.append("💵 Earned: `").append(formatAmount(player.getCurrency().getTotalEarned())).append(" coins`\n");
        description.append("💳 Spent: `").append(formatAmount(player.getCurrency().getTotalSpent())).append(" coins`\n");
        
        // Add daily reward info
        boolean dailyAvailable = player.getCurrency().isDailyRewardAvailable();
        if (isSelf) {
            description.append("\n🎁 **Daily Reward**: ")
                    .append(dailyAvailable ? "Available! Use `/daily` to claim." : "Already claimed today.");
        }
        
        // Send the embed
        event.getHook().sendMessageEmbeds(
                EmbedUtils.customEmbed(title, description.toString(), Color.GREEN)
        ).queue();
    }
    
    /**
     * Format a currency amount with commas
     */
    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }
}