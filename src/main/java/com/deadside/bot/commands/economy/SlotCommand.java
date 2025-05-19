package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Command for playing the slot machine
 */
public class SlotCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(SlotCommand.class);
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final Random random = new Random();
    
    // Slot machine symbols and their weights (probability)
    private static final SlotSymbol[] SYMBOLS = {
            new SlotSymbol("🍒", 30, 2),   // Cherries: high chance, low payout
            new SlotSymbol("🍊", 25, 3),   // Orange: medium-high chance, low payout
            new SlotSymbol("🍋", 20, 4),   // Lemon: medium chance, medium payout
            new SlotSymbol("🍇", 12, 6),   // Grapes: medium-low chance, medium payout
            new SlotSymbol("🍉", 8, 10),   // Watermelon: low chance, high payout
            new SlotSymbol("💎", 5, 15)    // Diamond: very low chance, very high payout
    };
    
    // Total weight for random selection
    private static final int TOTAL_WEIGHT = computeTotalWeight();
    
    // Cooldown tracking
    private final Map<Long, Long> lastUsage = new HashMap<>();
    private static final long COOLDOWN_SECONDS = 5; // 5 second cooldown
    
    @Override
    public String getName() {
        return "slot";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option with autocomplete for bet amounts
        OptionData betAmountOption = new OptionData(OptionType.INTEGER, "amount", "Amount to bet (min: 10, max: 1000)", true)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "Play the slot machine and try your luck")
                .addOptions(betAmountOption);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            // Check cooldown
            long userId = event.getUser().getIdLong();
            if (isOnCooldown(userId)) {
                long timeLeft = getRemainingCooldown(userId);
                event.reply("You need to wait " + timeLeft + " more seconds before playing again.").setEphemeral(true).queue();
                return;
            }
            
            // Set cooldown
            setCooldown(userId);
            
            // Defer reply to give us time to process
            event.deferReply().queue();
            
            // Get bet amount
            int betAmount = event.getOption("amount", 0, OptionMapping::getAsInt);
            
            // Validate bet amount
            if (betAmount < 10 || betAmount > 1000) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Invalid Bet", 
                                "Bet amount must be between 10 and 1000 coins.")
                ).queue();
                return;
            }
            
            // Get linked player information
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
            
            // Check if player has enough coins
            if (player.getCurrency().getCoins() < betAmount) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Insufficient Funds", 
                                "You don't have enough coins to place this bet. Your current balance is " + 
                                formatAmount(player.getCurrency().getCoins()) + " coins.")
                ).queue();
                return;
            }
            
            // Play the slot machine
            playSlots(event, player, betAmount);
            
        } catch (Exception e) {
            logger.error("Error executing slot command", e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "An error occurred while playing the slot machine.")
                ).queue();
            } else {
                event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
            }
        }
    }
    
    /**
     * Play the slot machine with animations
     */
    private void playSlots(SlashCommandInteractionEvent event, Player player, int betAmount) {
        // First, take the bet
        player.getCurrency().removeCoins(betAmount);
        
        // Animation phases
        final String[] spinningSymbols = {"🎰", "💫", "✨", "🎲", "🎯"};
        
        // Initial message with spinning animation
        String initialDisplay = "[ " + spinningSymbols[0] + " | " + spinningSymbols[0] + " | " + spinningSymbols[0] + " ]";
        
        // Create the initial response message
        StringBuilder initialMessage = new StringBuilder();
        initialMessage.append("**Bet**: `").append(formatAmount(betAmount)).append(" coins`\n\n");
        initialMessage.append("# ").append(initialDisplay).append("\n\n");
        initialMessage.append("*Spinning the reels...*\n\n");
        initialMessage.append("**Balance**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`");
        
        // Send the initial spinning message
        event.getHook().sendMessageEmbeds(
                EmbedUtils.customEmbed("Slot Machine - Spinning", initialMessage.toString(), Color.BLUE)
        ).queue(message -> {
            // Start the spinning animation
            animateSlotMachine(message, player, betAmount);
        });
        
        // Log the bet
        logger.info("User {} bet {} coins on slots", event.getUser().getName(), betAmount);
    }
    
    /**
     * Access method for animateSlotMachine (used by ButtonListener)
     */
    public void accessAnimateSlotMachine(net.dv8tion.jda.api.entities.Message message, Player player, int betAmount) {
        animateSlotMachine(message, player, betAmount);
    }
    
    /**
     * Animate the slot machine spinning
     */
    private void animateSlotMachine(net.dv8tion.jda.api.entities.Message message, Player player, int betAmount) {
        // Animation frames for spinning
        final String[][] spinFrames = {
            {"🎰", "🎰", "🎰"},
            {"💫", "🎰", "🎰"},
            {"💫", "💫", "🎰"},
            {"💫", "💫", "💫"},
            {"✨", "💫", "💫"},
            {"✨", "✨", "💫"},
            {"✨", "✨", "✨"},
            {"🎲", "✨", "✨"},
            {"🎲", "🎲", "✨"},
            {"🎲", "🎲", "🎲"}
        };
        
        // Final result (computed now but revealed later)
        SlotSymbol[] results = new SlotSymbol[3];
        for (int i = 0; i < 3; i++) {
            results[i] = spinReel();
        }
        
        // Calculate win amount
        int winAmount = calculateWinAmount(results, betAmount);
        boolean isWin = winAmount > 0;
        
        // If win, add to player's balance
        if (isWin) {
            player.getCurrency().addCoins(winAmount);
        }
        
        // Save player data
        playerRepository.save(player);
        
        // Create a thread to update the message multiple times for animation
        new Thread(() -> {
            try {
                // Show spinning frames
                for (int frame = 0; frame < spinFrames.length; frame++) {
                    // Create spinning display for current frame
                    String spinningDisplay = "[ " + spinFrames[frame][0] + " | " + 
                                             spinFrames[frame][1] + " | " + 
                                             spinFrames[frame][2] + " ]";
                    
                    // Update message with spinning animation
                    StringBuilder spinMessage = new StringBuilder();
                    spinMessage.append("**Bet**: `").append(formatAmount(betAmount)).append(" coins`\n\n");
                    spinMessage.append("# ").append(spinningDisplay).append("\n\n");
                    spinMessage.append("*Spinning the reels... " + (frame+1) + "/" + spinFrames.length + "*\n\n");
                    spinMessage.append("**Balance**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`");
                    
                    // Update the message
                    message.editMessageEmbeds(
                        EmbedUtils.customEmbed("Slot Machine - Spinning", spinMessage.toString(), Color.BLUE)
                    ).queue();
                    
                    // Slight delay between frames (speed up toward the end)
                    Thread.sleep(Math.max(200, 500 - (frame * 50)));
                }
                
                // Final reveal - first reel
                String partialReveal1 = "[ " + results[0].symbol + " | " + "💫" + " | " + "💫" + " ]";
                StringBuilder revealMessage1 = new StringBuilder();
                revealMessage1.append("**Bet**: `").append(formatAmount(betAmount)).append(" coins`\n\n");
                revealMessage1.append("# ").append(partialReveal1).append("\n\n");
                revealMessage1.append("*Revealing results...*\n\n");
                revealMessage1.append("**Balance**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`");
                
                message.editMessageEmbeds(
                    EmbedUtils.customEmbed("Slot Machine - Revealing", revealMessage1.toString(), Color.YELLOW)
                ).queue();
                Thread.sleep(700);
                
                // Final reveal - second reel
                String partialReveal2 = "[ " + results[0].symbol + " | " + results[1].symbol + " | " + "💫" + " ]";
                StringBuilder revealMessage2 = new StringBuilder();
                revealMessage2.append("**Bet**: `").append(formatAmount(betAmount)).append(" coins`\n\n");
                revealMessage2.append("# ").append(partialReveal2).append("\n\n");
                revealMessage2.append("*Almost there...*\n\n");
                revealMessage2.append("**Balance**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`");
                
                message.editMessageEmbeds(
                    EmbedUtils.customEmbed("Slot Machine - Revealing", revealMessage2.toString(), Color.YELLOW)
                ).queue();
                Thread.sleep(1000);
                
                // Final result display
                String finalDisplay = "[ " + results[0].symbol + " | " + results[1].symbol + " | " + results[2].symbol + " ]";
                
                // Create the final response message
                StringBuilder finalMessage = new StringBuilder();
                finalMessage.append("**Bet**: `").append(formatAmount(betAmount)).append(" coins`\n\n");
                finalMessage.append("# ").append(finalDisplay).append("\n\n");
                
                if (isWin) {
                    finalMessage.append("**🎉 YOU WON!** `").append(formatAmount(winAmount)).append(" coins`\n\n");
                    if (winAmount >= betAmount * 10) {
                        finalMessage.append("🔥 **MASSIVE WIN!** 🔥\n\n");
                    } else if (winAmount >= betAmount * 5) {
                        finalMessage.append("⭐ **BIG WIN!** ⭐\n\n");
                    }
                } else {
                    finalMessage.append("**😢 YOU LOST!** Better luck next time!\n\n");
                }
                
                finalMessage.append("**Balance**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`");
                
                // Send the final response with appropriate color based on result
                Color color = isWin ? Color.GREEN : Color.RED;
                String title = isWin ? "Slot Machine - Winner!" : "Slot Machine - Try Again";
                
                // Add buttons for replay
                net.dv8tion.jda.api.interactions.components.buttons.Button playAgainButton = 
                    net.dv8tion.jda.api.interactions.components.buttons.Button.primary("slot:playAgain:" + betAmount, "Play Again");
                net.dv8tion.jda.api.interactions.components.buttons.Button doubleButton = 
                    net.dv8tion.jda.api.interactions.components.buttons.Button.success("slot:double:" + betAmount, "Double Bet");
                net.dv8tion.jda.api.interactions.components.buttons.Button halfButton = 
                    net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("slot:half:" + betAmount, "Half Bet");
                
                // Disable buttons if not enough balance
                if (player.getCurrency().getCoins() < betAmount) {
                    playAgainButton = playAgainButton.asDisabled();
                }
                if (player.getCurrency().getCoins() < betAmount * 2) {
                    doubleButton = doubleButton.asDisabled();
                }
                
                message.editMessageEmbeds(
                    EmbedUtils.customEmbed(title, finalMessage.toString(), color)
                ).setActionRow(halfButton, playAgainButton, doubleButton).queue();
                
                // Log the result
                if (isWin) {
                    logger.info("User {} won {} coins from slots with a {} coin bet", 
                            message.getInteraction().getUser().getName(), winAmount, betAmount);
                } else {
                    logger.info("User {} lost {} coins on slots", 
                            message.getInteraction().getUser().getName(), betAmount);
                }
                
            } catch (InterruptedException e) {
                logger.error("Slot machine animation interrupted", e);
                
                // In case of error, show final result immediately
                String finalDisplay = "[ " + results[0].symbol + " | " + results[1].symbol + " | " + results[2].symbol + " ]";
                
                // Create the error message
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("**Animation Error**\n\n");
                errorMessage.append("# ").append(finalDisplay).append("\n\n");
                
                if (isWin) {
                    errorMessage.append("**You won** `").append(formatAmount(winAmount)).append(" coins`\n\n");
                } else {
                    errorMessage.append("**You lost** `").append(formatAmount(betAmount)).append(" coins`\n\n");
                }
                
                errorMessage.append("**Balance**: `").append(formatAmount(player.getCurrency().getCoins())).append(" coins`");
                
                message.editMessageEmbeds(
                    EmbedUtils.customEmbed("Slot Machine - Result", errorMessage.toString(), Color.GRAY)
                ).queue();
            }
        }).start();
    }
    
    /**
     * Spin a single reel and get a random symbol based on weight
     */
    private SlotSymbol spinReel() {
        int value = random.nextInt(TOTAL_WEIGHT);
        int weightSum = 0;
        
        for (SlotSymbol symbol : SYMBOLS) {
            weightSum += symbol.weight;
            if (value < weightSum) {
                return symbol;
            }
        }
        
        // Fallback to first symbol (should never happen)
        return SYMBOLS[0];
    }
    
    /**
     * Calculate win amount based on slot results
     */
    private int calculateWinAmount(SlotSymbol[] results, int betAmount) {
        // Check for three of a kind
        if (results[0].symbol.equals(results[1].symbol) && results[1].symbol.equals(results[2].symbol)) {
            // Return bet * multiplier
            return betAmount * results[0].multiplier;
        }
        
        // Check for three diamonds (special case)
        if (results[0].symbol.equals("💎") && results[1].symbol.equals("💎") && results[2].symbol.equals("💎")) {
            // Jackpot! Return bet * 50
            return betAmount * 50;
        }
        
        // Check for two of a kind (only for high-value symbols)
        if ((results[0].symbol.equals(results[1].symbol) && results[0].multiplier >= 6) ||
            (results[1].symbol.equals(results[2].symbol) && results[1].multiplier >= 6) ||
            (results[0].symbol.equals(results[2].symbol) && results[0].multiplier >= 6)) {
            
            // Return bet * (multiplier / 2)
            SlotSymbol matchedSymbol;
            if (results[0].symbol.equals(results[1].symbol)) {
                matchedSymbol = results[0];
            } else if (results[1].symbol.equals(results[2].symbol)) {
                matchedSymbol = results[1];
            } else {
                matchedSymbol = results[0];
            }
            
            return betAmount * (matchedSymbol.multiplier / 2);
        }
        
        // No win
        return 0;
    }
    
    /**
     * Compute the total weight of all symbols
     */
    private static int computeTotalWeight() {
        int total = 0;
        for (SlotSymbol symbol : SYMBOLS) {
            total += symbol.weight;
        }
        return total;
    }
    
    /**
     * Check if user is on cooldown
     */
    private boolean isOnCooldown(long userId) {
        if (!lastUsage.containsKey(userId)) {
            return false;
        }
        
        long lastUsed = lastUsage.get(userId);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastUsed) < (COOLDOWN_SECONDS * 1000);
    }
    
    /**
     * Set cooldown for user
     */
    private void setCooldown(long userId) {
        lastUsage.put(userId, System.currentTimeMillis());
    }
    
    /**
     * Get remaining cooldown time in seconds
     */
    private long getRemainingCooldown(long userId) {
        if (!lastUsage.containsKey(userId)) {
            return 0;
        }
        
        long lastUsed = lastUsage.get(userId);
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastUsed;
        long remaining = (COOLDOWN_SECONDS * 1000) - elapsed;
        
        return TimeUnit.MILLISECONDS.toSeconds(remaining) + 1; // +1 to round up
    }
    
    /**
     * Format a currency amount with commas
     */
    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String focusedOption = event.getFocusedOption().getName();
        
        if ("amount".equals(focusedOption)) {
            try {
                // Get the user's wallet balance for smart suggestions
                long userId = event.getUser().getIdLong();
                LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(userId);
                
                // If player isn't linked or doesn't exist, just use default suggestions
                if (linkedPlayer == null) {
                    return getDefaultBetSuggestions();
                }
                
                Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
                if (player == null) {
                    return getDefaultBetSuggestions();
                }
                
                // Get wallet balance
                long walletBalance = player.getCurrency().getCoins();
                
                // Handle user's input
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
                
                return generateBetSuggestions(walletBalance, hasCustomValue, customValue);
                
            } catch (Exception e) {
                logger.error("Error generating autocomplete suggestions for slots", e);
            }
            
            // Fallback to default suggestions
            return getDefaultBetSuggestions();
        }
        
        return List.of(); // No suggestions for other options
    }
    
    /**
     * Generate smart suggestions for bet amounts based on wallet balance and slot constraints
     */
    private List<Choice> generateBetSuggestions(long walletBalance, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Maximum bet for slots is 1000
        long maxBet = Math.min(walletBalance, 1000);
        
        // If user entered a custom value that's valid for slots, add it first
        if (hasCustomValue && customValue >= 10 && customValue <= maxBet) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add max bet option if they have enough coins
        if (maxBet >= 10) {
            if (maxBet == 1000) {
                suggestions.add(new Choice("Maximum bet (1,000)", 1000));
            } else {
                suggestions.add(new Choice("All in! (" + formatAmount(maxBet) + ")", maxBet));
            }
        }
        
        // Add common slot betting options
        long[] slotBets = {10, 20, 50, 100, 200, 500, 1000};
        for (long bet : slotBets) {
            if (bet <= maxBet && bet <= 1000) {
                suggestions.add(new Choice(formatAmount(bet) + " coins", bet));
            }
        }
        
        return suggestions;
    }
    
    /**
     * Get default bet suggestions
     */
    private List<Choice> getDefaultBetSuggestions() {
        List<Choice> suggestions = new ArrayList<>();
        
        // Default slot bets
        long[] defaultBets = {10, 20, 50, 100, 200, 500, 1000};
        for (long bet : defaultBets) {
            suggestions.add(new Choice(formatAmount(bet) + " coins", bet));
        }
        
        return suggestions;
    }
    
    /**
     * Inner class representing a slot machine symbol
     */
    private static class SlotSymbol {
        public final String symbol;   // Emoji symbol
        public final int weight;      // Weight for probability calculation
        public final int multiplier;  // Payout multiplier
        
        public SlotSymbol(String symbol, int weight, int multiplier) {
            this.symbol = symbol;
            this.weight = weight;
            this.multiplier = multiplier;
        }
    }
}