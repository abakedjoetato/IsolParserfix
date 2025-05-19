package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.config.Config;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin commands for managing the economy system
 */
public class AdminEconomyCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(AdminEconomyCommand.class);
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final Config config = Config.getInstance();
    
    @Override
    public String getName() {
        return "eco";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create options with autocomplete
        OptionData giveAmountOption = new OptionData(OptionType.INTEGER, "amount", "The amount of coins to give", true)
                .setAutoComplete(true);
        OptionData takeAmountOption = new OptionData(OptionType.INTEGER, "amount", "The amount of coins to take", true)
                .setAutoComplete(true);
        OptionData setAmountOption = new OptionData(OptionType.INTEGER, "amount", "The amount of coins to set", true)
                .setAutoComplete(true);
        OptionData dailyAmountOption = new OptionData(OptionType.INTEGER, "amount", "The new daily reward amount", true)
                .setAutoComplete(true);
        OptionData workMaxAmountOption = new OptionData(OptionType.INTEGER, "amount", "The new maximum work reward amount", true)
                .setAutoComplete(true);
        OptionData workMinAmountOption = new OptionData(OptionType.INTEGER, "amount", "The new minimum work reward amount", true)
                .setAutoComplete(true);
                
        return Commands.slash(getName(), "Admin commands for managing the economy system")
                .addSubcommands(
                        new SubcommandData("give", "Give coins to a player")
                                .addOption(OptionType.USER, "user", "The user to give coins to", true)
                                .addOptions(giveAmountOption)
                                .addOption(OptionType.STRING, "reason", "The reason for giving coins", false),
                        new SubcommandData("take", "Take coins from a player")
                                .addOption(OptionType.USER, "user", "The user to take coins from", true)
                                .addOptions(takeAmountOption)
                                .addOption(OptionType.STRING, "reason", "The reason for taking coins", false),
                        new SubcommandData("set", "Set a player's coin balance")
                                .addOption(OptionType.USER, "user", "The user to set coins for", true)
                                .addOptions(setAmountOption)
                                .addOption(OptionType.STRING, "reason", "The reason for setting coins", false),
                        new SubcommandData("reset", "Reset a player's economy data")
                                .addOption(OptionType.USER, "user", "The user to reset economy data for", true)
                                .addOption(OptionType.BOOLEAN, "confirm", "Confirm the reset", true),
                        new SubcommandData("setdaily", "Set the daily reward amount for all users")
                                .addOptions(dailyAmountOption),
                        new SubcommandData("setworkmax", "Set the maximum work reward amount")
                                .addOptions(workMaxAmountOption),
                        new SubcommandData("setworkmin", "Set the minimum work reward amount")
                                .addOptions(workMinAmountOption)
                );
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is owner or admin
        if (!isAdminOrOwner(event.getUser().getIdLong())) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Unauthorized", "You don't have permission to use admin economy commands."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Error", "No subcommand provided."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Defer reply for all admin commands as they involve database operations
        event.deferReply().queue();
        
        switch (subcommand) {
            case "give" -> handleGiveCommand(event);
            case "take" -> handleTakeCommand(event);
            case "set" -> handleSetCommand(event);
            case "reset" -> handleResetCommand(event);
            case "setdaily" -> handleSetDailyCommand(event);
            case "setworkmax" -> handleSetWorkMaxCommand(event);
            case "setworkmin" -> handleSetWorkMinCommand(event);
            default -> event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "Unknown subcommand: " + subcommand))
                    .queue();
        }
    }
    
    /**
     * Handle give command - give coins to a player
     */
    private void handleGiveCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        String reason = event.getOption("reason", "No reason provided", OptionMapping::getAsString);
        
        if (targetUser == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "No user specified."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        if (amount <= 0) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "Amount must be greater than 0."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
        if (linkedPlayer == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Linked", 
                    targetUser.getAsMention() + " doesn't have a linked Deadside account."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        if (player == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Found", 
                    "Unable to find player data for " + targetUser.getAsMention() + "."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Add coins
        long oldBalance = player.getCurrency().getCoins();
        player.getCurrency().addCoins(amount);
        
        // Save player
        playerRepository.save(player);
        
        // Log transaction
        logger.info("Admin {} gave {} coins to {} ({}). Reason: {}", 
                event.getUser().getId(), amount, targetUser.getId(), player.getName(), reason);
        
        // Reply with success message through the hook since we deferred earlier
        event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed("Coins Added",
                String.format("Successfully gave **%,d coins** to %s.\n\n" +
                        "Old balance: **%,d coins**\n" +
                        "New balance: **%,d coins**\n\n" +
                        "Reason: %s",
                        amount, targetUser.getAsMention(), oldBalance, player.getCurrency().getCoins(), reason)))
                .queue();
    }
    
    /**
     * Handle take command - take coins from a player
     */
    private void handleTakeCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        String reason = event.getOption("reason", "No reason provided", OptionMapping::getAsString);
        
        if (targetUser == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "No user specified."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        if (amount <= 0) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "Amount must be greater than 0."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
        if (linkedPlayer == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Linked", 
                    targetUser.getAsMention() + " doesn't have a linked Deadside account."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        if (player == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Found", 
                    "Unable to find player data for " + targetUser.getAsMention() + "."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Check if player has enough coins
        long currentBalance = player.getCurrency().getCoins();
        if (currentBalance < amount) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Insufficient Funds", 
                    String.format("%s only has %,d coins, cannot take %,d.", 
                            targetUser.getAsMention(), currentBalance, amount)))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Remove coins
        player.getCurrency().removeCoins(amount);
        
        // Save player
        playerRepository.save(player);
        
        // Log transaction
        logger.info("Admin {} took {} coins from {} ({}). Reason: {}", 
                event.getUser().getId(), amount, targetUser.getId(), player.getName(), reason);
        
        // Reply with success message through the hook since we deferred earlier
        event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed("Coins Removed",
                String.format("Successfully took **%,d coins** from %s.\n\n" +
                        "Old balance: **%,d coins**\n" +
                        "New balance: **%,d coins**\n\n" +
                        "Reason: %s",
                        amount, targetUser.getAsMention(), currentBalance, player.getCurrency().getCoins(), reason)))
                .queue();
    }
    
    /**
     * Handle set command - set a player's coin balance
     */
    private void handleSetCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        String reason = event.getOption("reason", "No reason provided", OptionMapping::getAsString);
        
        if (targetUser == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "No user specified."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        if (amount < 0) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "Amount cannot be negative."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
        if (linkedPlayer == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Linked", 
                    targetUser.getAsMention() + " doesn't have a linked Deadside account."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        if (player == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Found", 
                    "Unable to find player data for " + targetUser.getAsMention() + "."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Remember old balance
        long oldBalance = player.getCurrency().getCoins();
        
        // Set coins
        player.getCurrency().setCoins(amount);
        
        // Save player
        playerRepository.save(player);
        
        // Log transaction
        logger.info("Admin {} set {} coins for {} ({}). Reason: {}", 
                event.getUser().getId(), amount, targetUser.getId(), player.getName(), reason);
        
        // Reply with success message through the hook since we deferred earlier
        event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed("Balance Set",
                String.format("Successfully set %s's balance to **%,d coins**.\n\n" +
                        "Old balance: **%,d coins**\n" +
                        "New balance: **%,d coins**\n\n" +
                        "Reason: %s",
                        targetUser.getAsMention(), amount, oldBalance, player.getCurrency().getCoins(), reason)))
                .queue();
    }
    
    /**
     * Handle reset command - reset a player's economy data
     */
    private void handleResetCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        boolean confirm = event.getOption("confirm", false, OptionMapping::getAsBoolean);
        
        if (targetUser == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "No user specified."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        if (!confirm) {
            event.getHook().sendMessageEmbeds(EmbedUtils.warningEmbed("Confirmation Required", 
                    "You must confirm this action by setting the confirm option to true. " +
                    "This will reset all economy data for " + targetUser.getAsMention() + "."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
        if (linkedPlayer == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Linked", 
                    targetUser.getAsMention() + " doesn't have a linked Deadside account."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Find player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        if (player == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Player Not Found", 
                    "Unable to find player data for " + targetUser.getAsMention() + "."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Reset economy data
        player.setCurrency(new com.deadside.bot.db.models.Currency());
        
        // Save player
        playerRepository.save(player);
        
        // Log action
        logger.info("Admin {} reset economy data for {} ({})", 
                event.getUser().getId(), targetUser.getId(), player.getName());
        
        // Reply with success message through the hook since we deferred earlier
        event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed("Economy Data Reset",
                "Successfully reset all economy data for " + targetUser.getAsMention() + "."))
                .queue();
    }
    
    /**
     * Handle setdaily command - set the daily reward amount for all users
     */
    private void handleSetDailyCommand(SlashCommandInteractionEvent event) {
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        
        if (amount <= 0) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "Daily reward amount must be greater than 0."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Set the daily reward amount in the global config
        config.setDailyRewardAmount(amount);
        
        // Log action
        logger.info("Admin {} set daily reward amount to {}", 
                event.getUser().getId(), amount);
        
        // Reply with success message through the hook since we deferred earlier
        event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed("Daily Reward Updated",
                String.format("Successfully set the daily reward amount to **%,d coins** for all users.", amount)))
                .queue();
    }
    
    /**
     * Handle setworkmax command - set the maximum work reward amount
     */
    private void handleSetWorkMaxCommand(SlashCommandInteractionEvent event) {
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        
        if (amount <= 0) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Error", "Maximum work reward amount must be greater than 0."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Get the current min work amount
        long minWorkAmount = config.getWorkMinAmount();
        if (amount < minWorkAmount) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Error", 
                    String.format("Maximum work reward amount (%,d) cannot be less than minimum work reward amount (%,d).", 
                            amount, minWorkAmount)))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Set the work reward amount in the global config
        config.setWorkMaxAmount(amount);
        
        // Log action
        logger.info("Admin {} set maximum work reward amount to {}", 
                event.getUser().getId(), amount);
        
        // Reply with success message
        event.replyEmbeds(EmbedUtils.successEmbed("Work Reward Updated",
                String.format("Successfully set the maximum work reward amount to **%,d coins**.", amount)))
                .queue();
    }
    
    /**
     * Handle setworkmin command - set the minimum work reward amount
     */
    private void handleSetWorkMinCommand(SlashCommandInteractionEvent event) {
        long amount = event.getOption("amount", 0L, OptionMapping::getAsLong);
        
        if (amount <= 0) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Error", "Minimum work reward amount must be greater than 0."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Get the current max work amount
        long maxWorkAmount = config.getWorkMaxAmount();
        if (amount > maxWorkAmount) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Error", 
                    String.format("Minimum work reward amount (%,d) cannot be greater than maximum work reward amount (%,d).", 
                            amount, maxWorkAmount)))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        
        // Set the work reward amount in the global config
        config.setWorkMinAmount(amount);
        
        // Log action
        logger.info("Admin {} set minimum work reward amount to {}", 
                event.getUser().getId(), amount);
        
        // Reply with success message
        event.replyEmbeds(EmbedUtils.successEmbed("Work Reward Updated",
                String.format("Successfully set the minimum work reward amount to **%,d coins**.", amount)))
                .queue();
    }
    
    /**
     * Check if a user is an admin or the bot owner
     */
    private boolean isAdminOrOwner(long userId) {
        String ownerIdStr = config.getBotOwnerId();
        long ownerId = 0;
        try {
            // Convert owner ID from String to long for comparison
            ownerId = Long.parseLong(ownerIdStr);
        } catch (NumberFormatException e) {
            logger.error("Invalid bot owner ID format: {}", ownerIdStr);
            // Fall back to string comparison if needed
            return String.valueOf(userId).equals(ownerIdStr) || config.getAdminUserIds().contains(userId);
        }
        return userId == ownerId || config.getAdminUserIds().contains(userId);
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (!isAdminOrOwner(event.getUser().getIdLong())) {
            return List.of(); // Non-admins don't get suggestions
        }
        
        String focusedOption = event.getFocusedOption().getName();
        String subcommand = event.getSubcommandName();
        
        if ("amount".equals(focusedOption)) {
            // Handle user input if any
            String currentValue = event.getFocusedOption().getValue();
            boolean hasCustomValue = !currentValue.isEmpty();
            long customValue = 0;
            
            if (hasCustomValue) {
                try {
                    customValue = Long.parseLong(currentValue);
                } catch (NumberFormatException e) {
                    hasCustomValue = false;
                }
            }
            
            // Get appropriate suggestions based on subcommand
            return switch (subcommand) {
                case "give" -> getGiveAmountSuggestions(event, hasCustomValue, customValue);
                case "take" -> getTakeAmountSuggestions(event, hasCustomValue, customValue);
                case "set" -> getSetAmountSuggestions(event, hasCustomValue, customValue);
                case "setdaily" -> getDailyAmountSuggestions(hasCustomValue, customValue);
                case "setworkmax" -> getWorkMaxAmountSuggestions(hasCustomValue, customValue);
                case "setworkmin" -> getWorkMinAmountSuggestions(hasCustomValue, customValue);
                default -> List.of();
            };
        }
        
        return List.of(); // No suggestions for other options
    }
    
    /**
     * Generate suggestions for give command amounts
     */
    private List<Choice> getGiveAmountSuggestions(CommandAutoCompleteInteractionEvent event, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add custom value if valid
        if (hasCustomValue && customValue > 0) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Try to get target user's balance for context-aware suggestions
        OptionMapping userOption = event.getOption("user");
        if (userOption != null) {
            User targetUser = userOption.getAsUser();
            LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
            
            if (linkedPlayer != null) {
                Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
                if (player != null) {
                    // Add balance-based suggestions
                    long balance = player.getCurrency().getCoins();
                    
                    // Add percentage-based suggestions
                    suggestions.add(new Choice("Match balance: " + formatAmount(balance), balance));
                    if (balance >= 1000) {
                        suggestions.add(new Choice("10% of balance: " + formatAmount(balance / 10), balance / 10));
                        suggestions.add(new Choice("Double balance: " + formatAmount(balance * 2), balance * 2));
                    }
                }
            }
        }
        
        // Add standard increments
        addCommonAdminAmounts(suggestions);
        
        return suggestions;
    }
    
    /**
     * Generate suggestions for take command amounts
     */
    private List<Choice> getTakeAmountSuggestions(CommandAutoCompleteInteractionEvent event, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add custom value if valid
        if (hasCustomValue && customValue > 0) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Try to get target user's balance for context-aware suggestions
        OptionMapping userOption = event.getOption("user");
        if (userOption != null) {
            User targetUser = userOption.getAsUser();
            LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
            
            if (linkedPlayer != null) {
                Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
                if (player != null) {
                    // Add balance-based suggestions
                    long balance = player.getCurrency().getCoins();
                    
                    if (balance > 0) {
                        // Add take all option
                        suggestions.add(new Choice("All coins: " + formatAmount(balance), balance));
                        
                        // Add percentage-based suggestions
                        if (balance >= 10) {
                            suggestions.add(new Choice("Half: " + formatAmount(balance / 2), balance / 2));
                        }
                        if (balance >= 100) {
                            suggestions.add(new Choice("10%: " + formatAmount(balance / 10), balance / 10));
                        }
                    }
                }
            }
        }
        
        // Add standard increments
        addCommonAdminAmounts(suggestions);
        
        return suggestions;
    }
    
    /**
     * Generate suggestions for set command amounts
     */
    private List<Choice> getSetAmountSuggestions(CommandAutoCompleteInteractionEvent event, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add custom value if valid
        if (hasCustomValue && customValue >= 0) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add zero for reset
        suggestions.add(new Choice("Zero (0 coins)", 0));
        
        // Try to get target user's balance for context-aware suggestions
        OptionMapping userOption = event.getOption("user");
        if (userOption != null) {
            User targetUser = userOption.getAsUser();
            LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
            
            if (linkedPlayer != null) {
                Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
                if (player != null) {
                    // Add balance-based suggestions
                    long balance = player.getCurrency().getCoins();
                    
                    // Add current balance option
                    suggestions.add(new Choice("Current balance: " + formatAmount(balance), balance));
                    
                    // Add double and triple balance options
                    suggestions.add(new Choice("Double: " + formatAmount(balance * 2), balance * 2));
                    suggestions.add(new Choice("Triple: " + formatAmount(balance * 3), balance * 3));
                }
            }
        }
        
        // Add standard admin amounts
        addCommonAdminAmounts(suggestions);
        
        // Add special economy-breaking amounts
        long[] specialAmounts = {100000, 1000000, 10000000};
        for (long amount : specialAmounts) {
            suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
        }
        
        return suggestions;
    }
    
    /**
     * Generate suggestions for daily reward amounts
     */
    private List<Choice> getDailyAmountSuggestions(boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add custom value if valid
        if (hasCustomValue && customValue > 0) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add current daily amount
        long currentDaily = config.getDailyAmount();
        suggestions.add(new Choice("Current: " + formatAmount(currentDaily), currentDaily));
        
        // Add percentage adjustments to current
        suggestions.add(new Choice("+50%: " + formatAmount((long)(currentDaily * 1.5)), (long)(currentDaily * 1.5)));
        suggestions.add(new Choice("+100%: " + formatAmount(currentDaily * 2), currentDaily * 2));
        suggestions.add(new Choice("-50%: " + formatAmount(currentDaily / 2), currentDaily / 2));
        
        // Add standard daily reward options
        long[] dailyOptions = {500, 1000, 2000, 5000, 10000};
        for (long amount : dailyOptions) {
            suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
        }
        
        return suggestions;
    }
    
    /**
     * Generate suggestions for work max reward amounts
     */
    private List<Choice> getWorkMaxAmountSuggestions(boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add custom value if valid
        if (hasCustomValue && customValue > 0) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add current work max amount
        long currentMax = config.getWorkMaxAmount();
        long currentMin = config.getWorkMinAmount();
        suggestions.add(new Choice("Current: " + formatAmount(currentMax), currentMax));
        
        // Ensure max is greater than min
        if (hasCustomValue && customValue <= currentMin) {
            suggestions.add(new Choice("Min+1: " + formatAmount(currentMin + 1) + " (minimum allowed)", currentMin + 1));
        }
        
        // Add percentage adjustments to current
        suggestions.add(new Choice("+50%: " + formatAmount((long)(currentMax * 1.5)), (long)(currentMax * 1.5)));
        suggestions.add(new Choice("+100%: " + formatAmount(currentMax * 2), currentMax * 2));
        
        // Add standard work reward options
        long[] workMaxOptions = {1000, 2000, 3000, 5000, 10000};
        for (long amount : workMaxOptions) {
            if (amount > currentMin) {
                suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
            }
        }
        
        return suggestions;
    }
    
    /**
     * Generate suggestions for work min reward amounts
     */
    private List<Choice> getWorkMinAmountSuggestions(boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add custom value if valid
        if (hasCustomValue && customValue > 0) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add current work min amount
        long currentMin = config.getWorkMinAmount();
        long currentMax = config.getWorkMaxAmount();
        suggestions.add(new Choice("Current: " + formatAmount(currentMin), currentMin));
        
        // Ensure min is less than max
        if (hasCustomValue && customValue >= currentMax) {
            suggestions.add(new Choice("Max-1: " + formatAmount(currentMax - 1) + " (maximum allowed)", currentMax - 1));
        }
        
        // Add percentage adjustments to current
        suggestions.add(new Choice("+50%: " + formatAmount((long)(currentMin * 1.5)), (long)(currentMin * 1.5)));
        suggestions.add(new Choice("-50%: " + formatAmount(currentMin / 2), currentMin / 2));
        
        // Add standard work reward options
        long[] workMinOptions = {100, 250, 500, 750, 1000};
        for (long amount : workMinOptions) {
            if (amount < currentMax) {
                suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
            }
        }
        
        return suggestions;
    }
    
    /**
     * Add common admin amount suggestions
     */
    private void addCommonAdminAmounts(List<Choice> suggestions) {
        long[] commonAmounts = {100, 500, 1000, 5000, 10000, 25000, 50000};
        for (long amount : commonAmounts) {
            suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
        }
    }
    
    /**
     * Format a currency amount with commas
     */
    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }
}