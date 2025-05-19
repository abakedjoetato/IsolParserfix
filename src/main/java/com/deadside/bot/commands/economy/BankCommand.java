package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for banking operations
 */
public class BankCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(BankCommand.class);
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    
    @Override
    public String getName() {
        return "bank";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option with autocomplete for amounts
        OptionData depositAmountOption = new OptionData(OptionType.INTEGER, "amount", "Amount to deposit", true)
                .setAutoComplete(true);
        
        OptionData withdrawAmountOption = new OptionData(OptionType.INTEGER, "amount", "Amount to withdraw", true)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "Manage your bank account")
                .addSubcommands(
                        new SubcommandData("deposit", "Deposit coins into your bank account")
                                .addOptions(depositAmountOption),
                        new SubcommandData("withdraw", "Withdraw coins from your bank account")
                                .addOptions(withdrawAmountOption),
                        new SubcommandData("info", "View information about your bank account")
                );
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            String subCommand = event.getSubcommandName();
            if (subCommand == null) {
                event.reply("Invalid command usage.").setEphemeral(true).queue();
                return;
            }
            
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
            
            // Process the appropriate subcommand
            switch (subCommand) {
                case "deposit" -> handleDeposit(event, player);
                case "withdraw" -> handleWithdraw(event, player);
                case "info" -> handleInfo(event, player);
                default -> event.getHook().sendMessage("Unknown subcommand: " + subCommand).queue();
            }
            
        } catch (Exception e) {
            logger.error("Error executing bank command", e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "An error occurred while processing your bank operation.")
                ).queue();
            } else {
                event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
            }
        }
    }
    
    /**
     * Handle deposit operation
     */
    private void handleDeposit(SlashCommandInteractionEvent event, Player player) {
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        
        // Validate amount
        if (amount <= 0) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Amount", 
                            "Please specify a positive amount to deposit.")
            ).queue();
            return;
        }
        
        // Check if player has enough funds
        if (player.getCurrency().getCoins() < amount) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Insufficient Funds", 
                            "You don't have enough coins in your wallet. You currently have " + 
                            formatAmount(player.getCurrency().getCoins()) + " coins.")
            ).queue();
            return;
        }
        
        // Deposit the amount
        boolean success = player.getCurrency().depositToBank(amount);
        
        if (!success) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Transaction Failed", 
                            "Failed to deposit the coins. Please try again later.")
            ).queue();
            return;
        }
        
        // Save the changes
        playerRepository.save(player);
        
        // Send success message
        StringBuilder message = new StringBuilder();
        message.append("Successfully deposited ").append(formatAmount(amount)).append(" coins into your bank account.\n\n");
        message.append("**New Balances**\n");
        message.append("💰 Wallet: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`\n");
        message.append("🏦 Bank: `").append(formatAmount(player.getCurrency().getBankCoins())).append(" coins`\n");
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Deposit Successful", message.toString())
        ).queue();
        
        logger.info("User {} deposited {} coins to bank", event.getUser().getName(), amount);
    }
    
    /**
     * Handle withdraw operation
     */
    private void handleWithdraw(SlashCommandInteractionEvent event, Player player) {
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        
        // Validate amount
        if (amount <= 0) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Amount", 
                            "Please specify a positive amount to withdraw.")
            ).queue();
            return;
        }
        
        // Check if player has enough funds in bank
        if (player.getCurrency().getBankCoins() < amount) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Insufficient Funds", 
                            "You don't have enough coins in your bank account. You currently have " + 
                            formatAmount(player.getCurrency().getBankCoins()) + " coins in the bank.")
            ).queue();
            return;
        }
        
        // Withdraw the amount
        boolean success = player.getCurrency().withdrawFromBank(amount);
        
        if (!success) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Transaction Failed", 
                            "Failed to withdraw the coins. Please try again later.")
            ).queue();
            return;
        }
        
        // Save the changes
        playerRepository.save(player);
        
        // Send success message
        StringBuilder message = new StringBuilder();
        message.append("Successfully withdrew ").append(formatAmount(amount)).append(" coins from your bank account.\n\n");
        message.append("**New Balances**\n");
        message.append("💰 Wallet: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`\n");
        message.append("🏦 Bank: `").append(formatAmount(player.getCurrency().getBankCoins())).append(" coins`\n");
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Withdrawal Successful", message.toString())
        ).queue();
        
        logger.info("User {} withdrew {} coins from bank", event.getUser().getName(), amount);
    }
    
    /**
     * Handle info operation
     */
    private void handleInfo(SlashCommandInteractionEvent event, Player player) {
        StringBuilder description = new StringBuilder();
        
        description.append("**Bank Account Information**\n\n");
        description.append("🏦 **Current Balance**: `").append(formatAmount(player.getCurrency().getBankCoins())).append(" coins`\n");
        description.append("💰 **Wallet Balance**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`\n");
        description.append("💸 **Total Assets**: `").append(formatAmount(player.getCurrency().getTotalBalance())).append(" coins`\n\n");
        
        // Add some tips
        description.append("**Bank Benefits**\n");
        description.append("• Coins in your bank account are safe when you die\n");
        description.append("• No interest is earned on bank deposits currently\n");
        description.append("• Use `/bank deposit <amount>` to deposit coins\n");
        description.append("• Use `/bank withdraw <amount>` to withdraw coins\n");
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.customEmbed("Bank Account", description.toString(), new Color(0, 128, 255))
        ).queue();
    }
    
    /**
     * Format a currency amount with commas
     */
    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        String focusedOption = event.getFocusedOption().getName();
        
        if ("amount".equals(focusedOption)) {
            try {
                // Get the user's balances for smart suggestions
                long userId = event.getUser().getIdLong();
                LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(userId);
                
                // If player isn't linked or doesn't exist, just use default suggestions
                if (linkedPlayer == null) {
                    return getDefaultAmountSuggestions(subcommand);
                }
                
                Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
                if (player == null) {
                    return getDefaultAmountSuggestions(subcommand);
                }
                
                // Get wallet and bank balances
                long walletBalance = player.getCurrency().getCoins();
                long bankBalance = player.getCurrency().getBankCoins();
                
                String currentValue = event.getFocusedOption().getValue();
                boolean hasCustomValue = !currentValue.isEmpty();
                long customValue = 0;
                
                if (hasCustomValue) {
                    try {
                        customValue = Long.parseLong(currentValue);
                    } catch (NumberFormatException e) {
                        // Invalid number, ignore and use suggestions
                        hasCustomValue = false;
                    }
                }
                
                // Generate suggestions based on the subcommand (deposit or withdraw)
                if ("deposit".equals(subcommand)) {
                    return generateDepositSuggestions(walletBalance, hasCustomValue, customValue);
                } else if ("withdraw".equals(subcommand)) {
                    return generateWithdrawSuggestions(bankBalance, hasCustomValue, customValue);
                }
            } catch (Exception e) {
                logger.error("Error generating autocomplete suggestions", e);
            }
            
            // Fallback to default suggestions
            return getDefaultAmountSuggestions(subcommand);
        }
        
        return List.of(); // No suggestions for other options
    }
    
    /**
     * Generate smart suggestions for deposit amounts based on wallet balance
     */
    private List<Choice> generateDepositSuggestions(long walletBalance, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // If user entered a custom value and it's valid, add it first
        if (hasCustomValue && customValue > 0 && customValue <= walletBalance) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add all wallet (deposit all)
        if (walletBalance > 0) {
            suggestions.add(new Choice("All wallet coins (" + formatAmount(walletBalance) + ")", walletBalance));
        }
        
        // Add percentage-based options if balance is sufficient
        if (walletBalance >= 100) {
            suggestions.add(new Choice("Half wallet (" + formatAmount(walletBalance / 2) + ")", walletBalance / 2));
            
            if (walletBalance >= 1000) {
                suggestions.add(new Choice("10% wallet (" + formatAmount(walletBalance / 10) + ")", walletBalance / 10));
                suggestions.add(new Choice("25% wallet (" + formatAmount(walletBalance / 4) + ")", walletBalance / 4));
                suggestions.add(new Choice("75% wallet (" + formatAmount(walletBalance * 3 / 4) + ")", walletBalance * 3 / 4));
            }
        }
        
        // Add common fixed amounts
        addCommonAmounts(suggestions, walletBalance);
        
        return suggestions;
    }
    
    /**
     * Generate smart suggestions for withdraw amounts based on bank balance
     */
    private List<Choice> generateWithdrawSuggestions(long bankBalance, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // If user entered a custom value and it's valid, add it first
        if (hasCustomValue && customValue > 0 && customValue <= bankBalance) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add all bank (withdraw all)
        if (bankBalance > 0) {
            suggestions.add(new Choice("All bank coins (" + formatAmount(bankBalance) + ")", bankBalance));
        }
        
        // Add percentage-based options if balance is sufficient
        if (bankBalance >= 100) {
            suggestions.add(new Choice("Half bank (" + formatAmount(bankBalance / 2) + ")", bankBalance / 2));
            
            if (bankBalance >= 1000) {
                suggestions.add(new Choice("10% bank (" + formatAmount(bankBalance / 10) + ")", bankBalance / 10));
                suggestions.add(new Choice("25% bank (" + formatAmount(bankBalance / 4) + ")", bankBalance / 4));
                suggestions.add(new Choice("75% bank (" + formatAmount(bankBalance * 3 / 4) + ")", bankBalance * 3 / 4));
            }
        }
        
        // Add common fixed amounts
        addCommonAmounts(suggestions, bankBalance);
        
        return suggestions;
    }
    
    /**
     * Add common fixed amount suggestions up to the maximum balance
     */
    private void addCommonAmounts(List<Choice> suggestions, long maxBalance) {
        long[] commonAmounts = {100, 500, 1000, 5000, 10000, 50000, 100000, 500000, 1000000};
        
        for (long amount : commonAmounts) {
            if (amount <= maxBalance) {
                suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
            }
        }
    }
    
    /**
     * Get default amount suggestions when player data is not available
     */
    private List<Choice> getDefaultAmountSuggestions(String subcommand) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add common fixed amounts as defaults
        long[] defaultAmounts = {100, 500, 1000, 5000, 10000, 50000, 100000};
        for (long amount : defaultAmounts) {
            suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
        }
        
        return suggestions;
    }
}