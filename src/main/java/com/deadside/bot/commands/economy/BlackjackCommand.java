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
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Command for playing blackjack
 */
public class BlackjackCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(BlackjackCommand.class);
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final Random random = new Random();
    
    // Game sessions
    private final Map<Long, BlackjackGame> activeGames = new HashMap<>();
    
    // Card symbols
    private static final String[] SUITS = {"♠️", "♥️", "♦️", "♣️"};
    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    
    // Cooldown tracking
    private final Map<Long, Long> lastUsage = new HashMap<>();
    private static final long COOLDOWN_SECONDS = 3; // 3 second cooldown
    
    @Override
    public String getName() {
        return "blackjack";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option with autocomplete for bet amounts
        OptionData betOption = new OptionData(OptionType.INTEGER, "bet", "Amount to bet (min: 50, max: 2000)", true)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "Play a game of blackjack against the dealer")
                .addOptions(betOption);
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
            
            // Defer reply to give us time to process
            event.deferReply().queue();
            
            // Set cooldown
            setCooldown(userId);
            
            // Get bet amount
            int betAmount = event.getOption("bet", 0, OptionMapping::getAsInt);
            
            // Validate bet amount
            if (betAmount < 50 || betAmount > 2000) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Invalid Bet", 
                                "Bet amount must be between 50 and 2000 coins.")
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
            
            // Check if player already has an active game
            if (activeGames.containsKey(userId)) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.warningEmbed("Game In Progress", 
                                "You already have a blackjack game in progress. Finish that game first.")
                ).queue();
                return;
            }
            
            // Start a new blackjack game
            startBlackjackGame(event, player, betAmount);
            
        } catch (Exception e) {
            logger.error("Error executing blackjack command", e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "An error occurred while starting the blackjack game.")
                ).queue();
            } else {
                event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
            }
        }
    }
    
    /**
     * Start a new blackjack game
     */
    private void startBlackjackGame(SlashCommandInteractionEvent event, Player player, int betAmount) {
        // Take the bet
        player.getCurrency().removeCoins(betAmount);
        playerRepository.save(player);
        
        // Create a new game
        BlackjackGame game = new BlackjackGame(betAmount, player);
        
        // Deal initial cards
        game.dealInitialCards();
        
        // Store the game
        activeGames.put(event.getUser().getIdLong(), game);
        
        // Create buttons
        Button hitButton = Button.success("blackjack:hit:" + event.getUser().getId(), "Hit")
                .withEmoji(Emoji.fromUnicode("🎯"));
        Button standButton = Button.primary("blackjack:stand:" + event.getUser().getId(), "Stand")
                .withEmoji(Emoji.fromUnicode("🛑"));
        Button doubleDownButton = Button.secondary("blackjack:double:" + event.getUser().getId(), "Double Down")
                .withEmoji(Emoji.fromUnicode("💰"));
        
        // Disable double down if player doesn't have enough coins
        if (player.getCurrency().getCoins() < betAmount) {
            doubleDownButton = doubleDownButton.asDisabled();
        }
        
        // Check for blackjack
        if (game.isPlayerBlackjack()) {
            // Player has blackjack - show dealer's cards and end game
            game.revealDealerCards();
            
            // Create message
            String message = createGameDisplay(game);
            
            // Add buttons for new game
            Button playAgainButton = Button.success("blackjack:playAgain:" + betAmount + ":" + event.getUser().getId(), "Play Again");
            Button doubleButton = Button.secondary("blackjack:newDouble:" + betAmount + ":" + event.getUser().getId(), "Double Bet");
            Button halfButton = Button.secondary("blackjack:newHalf:" + betAmount + ":" + event.getUser().getId(), "Half Bet");
            
            // Send response
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.customEmbed("Blackjack - Blackjack! You Win!", message, Color.GREEN)
            ).addActionRow(halfButton, playAgainButton, doubleButton).queue();
            
            // End game and give payout (blackjack pays 3:2)
            int payout = (int) (betAmount * 2.5);
            player.getCurrency().addCoins(payout);
            playerRepository.save(player);
            
            // Remove the game
            activeGames.remove(event.getUser().getIdLong());
            
            logger.info("User {} got blackjack and won {} coins", event.getUser().getName(), payout - betAmount);
            
            return;
        }
        
        // Create message
        String message = createGameDisplay(game);
        
        // Send response with buttons
        event.getHook().sendMessageEmbeds(
                EmbedUtils.customEmbed("Blackjack - Your Turn", message, Color.BLUE)
        ).addActionRow(hitButton, standButton, doubleDownButton).queue();
        
        logger.info("User {} started a blackjack game with {} coin bet", event.getUser().getName(), betAmount);
        
        // Set a timer to auto-end the game after 5 minutes of inactivity
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                BlackjackGame expiredGame = activeGames.get(event.getUser().getIdLong());
                if (expiredGame != null && expiredGame.getStartTime() == game.getStartTime()) {
                    // Game still exists and hasn't been updated - expire it
                    activeGames.remove(event.getUser().getIdLong());
                    logger.info("Blackjack game for user {} expired due to inactivity", event.getUser().getName());
                }
            }
        }, 5 * 60 * 1000); // 5 minutes
    }
    
    /**
     * Handle button interactions for blackjack
     */
    public void handleButtonInteraction(ButtonInteractionEvent event, String[] buttonData) {
        if (buttonData.length < 3) {
            return;
        }
        
        String action = buttonData[1];
        String userId = buttonData[2];
        
        // Defer reply for button interactions
        event.deferReply().queue();
        
        // Verify that the user who clicked is the one who started the game
        if (!userId.equals(event.getUser().getId())) {
            event.getHook().sendMessage("This is not your game!").setEphemeral(true).queue();
            return;
        }
        
        // Get the user's game
        BlackjackGame game = activeGames.get(event.getUser().getIdLong());
        
        // If there's no active game but this is a command to start a new one
        if (game == null && (action.equals("playAgain") || action.equals("newDouble") || action.equals("newHalf"))) {
            // This is a button to start a new game
            handleNewGameButton(event, buttonData);
            return;
        }
        
        // Check if the game exists
        if (game == null) {
            event.getHook().sendMessage("You don't have an active blackjack game.").setEphemeral(true).queue();
            return;
        }
        
        // Handle the button based on action
        switch (action) {
            case "hit" -> handleHitAction(event, game);
            case "stand" -> handleStandAction(event, game);
            case "double" -> handleDoubleDownAction(event, game);
            default -> {
                event.getHook().sendMessage("Unknown action: " + action).setEphemeral(true).queue();
                logger.warn("Unknown blackjack button action: {}", action);
            }
        }
    }
    
    /**
     * Handle "Hit" button action
     */
    private void handleHitAction(ButtonInteractionEvent event, BlackjackGame game) {
        // Acknowledge the button click
        event.deferEdit().queue();
        
        // Deal a card to the player
        game.dealCardToPlayer();
        
        // Check if player busted
        if (game.isPlayerBusted()) {
            // Player busted - end game
            String message = createGameDisplay(game);
            
            // Add buttons for new game
            Button playAgainButton = Button.success("blackjack:playAgain:" + game.getBetAmount() + ":" + event.getUser().getId(), "Play Again");
            Button doubleButton = Button.secondary("blackjack:newDouble:" + game.getBetAmount() + ":" + event.getUser().getId(), "Double Bet");
            Button halfButton = Button.secondary("blackjack:newHalf:" + game.getBetAmount() + ":" + event.getUser().getId(), "Half Bet");
            
            // Check if player can afford these bets
            if (game.getPlayer().getCurrency().getCoins() < game.getBetAmount()) {
                playAgainButton = playAgainButton.asDisabled();
            }
            if (game.getPlayer().getCurrency().getCoins() < game.getBetAmount() * 2) {
                doubleButton = doubleButton.asDisabled();
            }
            
            // Update message
            event.getHook().editOriginalEmbeds(
                    EmbedUtils.customEmbed("Blackjack - Busted! You Lose!", message, Color.RED)
            ).setActionRow(halfButton, playAgainButton, doubleButton).queue();
            
            // Remove the game
            activeGames.remove(event.getUser().getIdLong());
            
            logger.info("User {} busted in blackjack and lost {} coins", event.getUser().getName(), game.getBetAmount());
            
            return;
        }
        
        // Check if player has 21 - automatic stand
        if (game.getPlayerValue() == 21) {
            handleStandAction(event, game);
            return;
        }
        
        // Player still in the game - update buttons
        Button hitButton = Button.success("blackjack:hit:" + event.getUser().getId(), "Hit")
                .withEmoji(Emoji.fromUnicode("🎯"));
        Button standButton = Button.primary("blackjack:stand:" + event.getUser().getId(), "Stand")
                .withEmoji(Emoji.fromUnicode("🛑"));
        
        String message = createGameDisplay(game);
        
        // Update message
        event.getHook().editOriginalEmbeds(
                EmbedUtils.customEmbed("Blackjack - Your Turn", message, Color.BLUE)
        ).setActionRow(hitButton, standButton).queue();
    }
    
    /**
     * Handle "Stand" button action
     */
    private void handleStandAction(ButtonInteractionEvent event, BlackjackGame game) {
        // Acknowledge the button click
        event.deferEdit().queue();
        
        // Dealer plays
        game.revealDealerCards();
        game.dealerPlay();
        
        // Create message
        String message = createGameDisplay(game);
        
        // Determine winner
        BlackjackGame.GameResult result = game.determineWinner();
        String title;
        Color color;
        
        // Calculate payout based on result
        int payout = 0;
        switch (result) {
            case PLAYER_WINS:
                title = "Blackjack - You Win!";
                color = Color.GREEN;
                payout = game.getBetAmount() * 2; // Win pays 1:1
                break;
            case DEALER_WINS:
                title = "Blackjack - Dealer Wins!";
                color = Color.RED;
                payout = 0; // No payout on loss
                break;
            case PUSH:
                title = "Blackjack - Push!";
                color = Color.YELLOW;
                payout = game.getBetAmount(); // Push returns bet
                break;
            default:
                title = "Blackjack - Game Over";
                color = Color.GRAY;
                payout = 0;
        }
        
        // Add payout to player's balance
        if (payout > 0) {
            game.getPlayer().getCurrency().addCoins(payout);
            playerRepository.save(game.getPlayer());
        }
        
        // Add result details to the message
        message += "\n\n**Result**: " + title.substring(11) + "\n";
        
        if (result == BlackjackGame.GameResult.PLAYER_WINS) {
            message += "**Winnings**: +" + formatAmount(game.getBetAmount()) + " coins\n";
        } else if (result == BlackjackGame.GameResult.PUSH) {
            message += "**Bet Returned**: " + formatAmount(game.getBetAmount()) + " coins\n";
        } else {
            message += "**Lost**: -" + formatAmount(game.getBetAmount()) + " coins\n";
        }
        
        message += "**Balance**: " + formatAmount(game.getPlayer().getCurrency().getCoins()) + " coins";
        
        // Add buttons for new game
        Button playAgainButton = Button.success("blackjack:playAgain:" + game.getBetAmount() + ":" + event.getUser().getId(), "Play Again");
        Button doubleButton = Button.secondary("blackjack:newDouble:" + game.getBetAmount() + ":" + event.getUser().getId(), "Double Bet");
        Button halfButton = Button.secondary("blackjack:newHalf:" + game.getBetAmount() + ":" + event.getUser().getId(), "Half Bet");
        
        // Check if player can afford these bets
        if (game.getPlayer().getCurrency().getCoins() < game.getBetAmount()) {
            playAgainButton = playAgainButton.asDisabled();
        }
        if (game.getPlayer().getCurrency().getCoins() < game.getBetAmount() * 2) {
            doubleButton = doubleButton.asDisabled();
        }
        
        // Update message
        event.getHook().editOriginalEmbeds(
                EmbedUtils.customEmbed(title, message, color)
        ).setActionRow(halfButton, playAgainButton, doubleButton).queue();
        
        // Remove the game
        activeGames.remove(event.getUser().getIdLong());
        
        // Log the result
        logger.info("User {} finished blackjack game with result: {}", event.getUser().getName(), result);
    }
    
    /**
     * Handle "Double Down" button action
     */
    private void handleDoubleDownAction(ButtonInteractionEvent event, BlackjackGame game) {
        // Check if player has enough coins to double down
        if (game.getPlayer().getCurrency().getCoins() < game.getBetAmount()) {
            event.reply("You don't have enough coins to double down.").setEphemeral(true).queue();
            return;
        }
        
        // Acknowledge the button click
        event.deferEdit().queue();
        
        // Double the bet
        game.getPlayer().getCurrency().removeCoins(game.getBetAmount());
        playerRepository.save(game.getPlayer());
        game.doubleBet();
        
        // Deal one card to player
        game.dealCardToPlayer();
        
        // Dealer plays (automatic stand after double down)
        game.revealDealerCards();
        game.dealerPlay();
        
        // Create message
        String message = createGameDisplay(game);
        
        // Determine winner
        BlackjackGame.GameResult result = game.determineWinner();
        String title;
        Color color;
        
        // Calculate payout based on result
        int payout = 0;
        switch (result) {
            case PLAYER_WINS:
                title = "Blackjack - Double Down Win!";
                color = Color.GREEN;
                payout = game.getBetAmount() * 2; // Win pays 1:1 on doubled bet
                break;
            case DEALER_WINS:
                title = "Blackjack - Double Down Loss!";
                color = Color.RED;
                payout = 0; // No payout on loss
                break;
            case PUSH:
                title = "Blackjack - Push on Double Down!";
                color = Color.YELLOW;
                payout = game.getBetAmount(); // Push returns bet
                break;
            default:
                title = "Blackjack - Game Over";
                color = Color.GRAY;
                payout = 0;
        }
        
        // Add payout to player's balance
        if (payout > 0) {
            game.getPlayer().getCurrency().addCoins(payout);
            playerRepository.save(game.getPlayer());
        }
        
        // Add result details to the message
        message += "\n\n**Double Down Result**: " + title.substring(11) + "\n";
        message += "**Total Bet**: " + formatAmount(game.getBetAmount()) + " coins\n";
        
        if (result == BlackjackGame.GameResult.PLAYER_WINS) {
            message += "**Winnings**: +" + formatAmount(game.getBetAmount()) + " coins\n";
        } else if (result == BlackjackGame.GameResult.PUSH) {
            message += "**Bet Returned**: " + formatAmount(game.getBetAmount()) + " coins\n";
        } else {
            message += "**Lost**: -" + formatAmount(game.getBetAmount()) + " coins\n";
        }
        
        message += "**Balance**: " + formatAmount(game.getPlayer().getCurrency().getCoins()) + " coins";
        
        // Add buttons for new game
        Button playAgainButton = Button.success("blackjack:playAgain:" + (game.getBetAmount() / 2) + ":" + event.getUser().getId(), "Play Again");
        Button doubleButton = Button.secondary("blackjack:newDouble:" + (game.getBetAmount() / 2) + ":" + event.getUser().getId(), "Double Bet");
        Button halfButton = Button.secondary("blackjack:newHalf:" + (game.getBetAmount() / 2) + ":" + event.getUser().getId(), "Half Bet");
        
        // Check if player can afford these bets
        if (game.getPlayer().getCurrency().getCoins() < (game.getBetAmount() / 2)) {
            playAgainButton = playAgainButton.asDisabled();
        }
        if (game.getPlayer().getCurrency().getCoins() < game.getBetAmount()) {
            doubleButton = doubleButton.asDisabled();
        }
        
        // Update message
        event.getHook().editOriginalEmbeds(
                EmbedUtils.customEmbed(title, message, color)
        ).setActionRow(halfButton, playAgainButton, doubleButton).queue();
        
        // Remove the game
        activeGames.remove(event.getUser().getIdLong());
        
        // Log the result
        logger.info("User {} double down in blackjack with result: {}", event.getUser().getName(), result);
    }
    
    /**
     * Handle buttons for starting a new game
     */
    private void handleNewGameButton(ButtonInteractionEvent event, String[] buttonData) {
        if (buttonData.length < 4) {
            return;
        }
        
        String action = buttonData[1];
        String betStr = buttonData[2];
        int betAmount = Integer.parseInt(betStr);
        
        // Calculate new bet based on action
        if (action.equals("newDouble")) {
            betAmount *= 2;
        } else if (action.equals("newHalf")) {
            betAmount = Math.max(50, betAmount / 2);
        }
        
        // Get linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(event.getUser().getIdLong());
        
        if (linkedPlayer == null) {
            event.reply("You don't have a linked Deadside account. Use `/link` to connect your Discord and Deadside accounts.")
                 .setEphemeral(true).queue();
            return;
        }
        
        // Get player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        
        if (player == null) {
            event.reply("Unable to find your player data. This could be because you haven't been active yet.")
                 .setEphemeral(true).queue();
            return;
        }
        
        // Check cooldown
        if (isOnCooldown(event.getUser().getIdLong())) {
            long timeLeft = getRemainingCooldown(event.getUser().getIdLong());
            event.reply("You need to wait " + timeLeft + " more seconds before playing again.").setEphemeral(true).queue();
            return;
        }
        
        // Set cooldown
        setCooldown(event.getUser().getIdLong());
        
        // Check if player has enough balance
        if (player.getCurrency().getCoins() < betAmount) {
            event.reply("You don't have enough coins for this bet. Your current balance is " + 
                       formatAmount(player.getCurrency().getCoins()) + " coins.")
                 .setEphemeral(true).queue();
            return;
        }
        
        // Acknowledge the button click
        event.deferEdit().queue();
        
        // Start a new game
        startNewBlackjackGame(event, player, betAmount);
    }
    
    /**
     * Start a new blackjack game from button interaction
     */
    private void startNewBlackjackGame(ButtonInteractionEvent event, Player player, int betAmount) {
        // Take the bet
        player.getCurrency().removeCoins(betAmount);
        playerRepository.save(player);
        
        // Create a new game
        BlackjackGame game = new BlackjackGame(betAmount, player);
        
        // Deal initial cards
        game.dealInitialCards();
        
        // Store the game
        activeGames.put(event.getUser().getIdLong(), game);
        
        // Create buttons
        Button hitButton = Button.success("blackjack:hit:" + event.getUser().getId(), "Hit")
                .withEmoji(Emoji.fromUnicode("🎯"));
        Button standButton = Button.primary("blackjack:stand:" + event.getUser().getId(), "Stand")
                .withEmoji(Emoji.fromUnicode("🛑"));
        Button doubleDownButton = Button.secondary("blackjack:double:" + event.getUser().getId(), "Double Down")
                .withEmoji(Emoji.fromUnicode("💰"));
        
        // Disable double down if player doesn't have enough coins
        if (player.getCurrency().getCoins() < betAmount) {
            doubleDownButton = doubleDownButton.asDisabled();
        }
        
        // Check for blackjack
        if (game.isPlayerBlackjack()) {
            // Player has blackjack - show dealer's cards and end game
            game.revealDealerCards();
            
            // Create message
            String message = createGameDisplay(game);
            
            // Add buttons for new game
            Button playAgainButton = Button.success("blackjack:playAgain:" + betAmount + ":" + event.getUser().getId(), "Play Again");
            Button doubleButton = Button.secondary("blackjack:newDouble:" + betAmount + ":" + event.getUser().getId(), "Double Bet");
            Button halfButton = Button.secondary("blackjack:newHalf:" + betAmount + ":" + event.getUser().getId(), "Half Bet");
            
            // Update message
            event.getHook().editOriginalEmbeds(
                    EmbedUtils.customEmbed("Blackjack - Blackjack! You Win!", message, Color.GREEN)
            ).setActionRow(halfButton, playAgainButton, doubleButton).queue();
            
            // End game and give payout (blackjack pays 3:2)
            int payout = (int) (betAmount * 2.5);
            player.getCurrency().addCoins(payout);
            playerRepository.save(player);
            
            // Remove the game
            activeGames.remove(event.getUser().getIdLong());
            
            logger.info("User {} got blackjack and won {} coins", event.getUser().getName(), payout - betAmount);
            
            return;
        }
        
        // Create message
        String message = createGameDisplay(game);
        
        // Update message with buttons
        event.getHook().editOriginalEmbeds(
                EmbedUtils.customEmbed("Blackjack - Your Turn", message, Color.BLUE)
        ).setActionRow(hitButton, standButton, doubleDownButton).queue();
        
        logger.info("User {} started a new blackjack game with {} coin bet", event.getUser().getName(), betAmount);
        
        // Set a timer to auto-end the game after 5 minutes of inactivity
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                BlackjackGame expiredGame = activeGames.get(event.getUser().getIdLong());
                if (expiredGame != null && expiredGame.getStartTime() == game.getStartTime()) {
                    // Game still exists and hasn't been updated - expire it
                    activeGames.remove(event.getUser().getIdLong());
                    logger.info("Blackjack game for user {} expired due to inactivity", event.getUser().getName());
                }
            }
        }, 5 * 60 * 1000); // 5 minutes
    }
    
    /**
     * Create a display string for the current game state
     */
    private String createGameDisplay(BlackjackGame game) {
        StringBuilder display = new StringBuilder();
        
        // Add bet information
        display.append("**Bet**: `").append(formatAmount(game.getBetAmount())).append(" coins`\n\n");
        
        // Add dealer's cards
        display.append("**Dealer's Hand**:\n");
        
        List<BlackjackCard> dealerCards = game.getDealerCards();
        List<String> dealerCardDisplay = new ArrayList<>();
        
        for (int i = 0; i < dealerCards.size(); i++) {
            BlackjackCard card = dealerCards.get(i);
            if (i == 0 && !game.isDealerRevealed()) {
                dealerCardDisplay.add("🂠"); // Face down card
            } else {
                dealerCardDisplay.add(card.toString());
            }
        }
        
        display.append(String.join(" ", dealerCardDisplay)).append("\n");
        
        if (game.isDealerRevealed()) {
            display.append("**Value**: ").append(game.getDealerValue());
            if (game.isDealerBusted()) {
                display.append(" (Busted!)");
            } else if (game.isDealerBlackjack()) {
                display.append(" (Blackjack!)");
            }
            display.append("\n\n");
        } else {
            display.append("**Value**: ?\n\n");
        }
        
        // Add player's cards
        display.append("**Your Hand**:\n");
        
        List<BlackjackCard> playerCards = game.getPlayerCards();
        List<String> playerCardDisplay = new ArrayList<>();
        
        for (BlackjackCard card : playerCards) {
            playerCardDisplay.add(card.toString());
        }
        
        display.append(String.join(" ", playerCardDisplay)).append("\n");
        
        display.append("**Value**: ").append(game.getPlayerValue());
        if (game.isPlayerBusted()) {
            display.append(" (Busted!)");
        } else if (game.isPlayerBlackjack()) {
            display.append(" (Blackjack!)");
        }
        display.append("\n\n");
        
        // Add balance
        display.append("**Balance**: `").append(formatAmount(game.getPlayer().getCurrency().getCoins())).append(" coins`");
        
        return display.toString();
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
        
        if ("bet".equals(focusedOption)) {
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
                logger.error("Error generating autocomplete suggestions for blackjack", e);
            }
            
            // Fallback to default suggestions
            return getDefaultBetSuggestions();
        }
        
        return List.of(); // No suggestions for other options
    }
    
    /**
     * Generate smart suggestions for bet amounts based on wallet balance and blackjack constraints
     */
    private List<Choice> generateBetSuggestions(long walletBalance, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // Blackjack min bet is 50, max is 2000
        long minBet = 50;
        long maxBet = Math.min(walletBalance, 2000);
        
        // If user entered a custom value that's valid for blackjack, add it first
        if (hasCustomValue && customValue >= minBet && customValue <= maxBet) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add max bet option if they have enough coins
        if (maxBet >= minBet) {
            if (maxBet == 2000) {
                suggestions.add(new Choice("Maximum bet (2,000)", 2000));
            } else {
                suggestions.add(new Choice("All in! (" + formatAmount(maxBet) + ")", maxBet));
            }
        }
        
        // Add percentage-based options if balance is sufficient
        if (walletBalance >= 200) {
            suggestions.add(new Choice("Half balance (" + formatAmount(walletBalance / 2) + ")", 
                    Math.min(walletBalance / 2, 2000)));
            
            if (walletBalance >= 1000) {
                suggestions.add(new Choice("25% balance (" + formatAmount(walletBalance / 4) + ")", 
                        Math.min(walletBalance / 4, 2000)));
            }
        }
        
        // Add common blackjack betting options
        long[] blackjackBets = {50, 100, 200, 500, 1000, 1500, 2000};
        for (long bet : blackjackBets) {
            if (bet <= maxBet && bet >= minBet) {
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
        
        // Default blackjack bets
        long[] defaultBets = {50, 100, 200, 500, 1000, 1500, 2000};
        for (long bet : defaultBets) {
            suggestions.add(new Choice(formatAmount(bet) + " coins", bet));
        }
        
        return suggestions;
    }
    
    /**
     * Class representing a blackjack game
     */
    public static class BlackjackGame {
        private final int initialBetAmount;
        private int betAmount;
        private final Player player;
        private final List<BlackjackCard> playerCards = new ArrayList<>();
        private final List<BlackjackCard> dealerCards = new ArrayList<>();
        private boolean dealerRevealed = false;
        private final long startTime;
        private final List<BlackjackCard> deck = new ArrayList<>();
        
        public BlackjackGame(int betAmount, Player player) {
            this.initialBetAmount = betAmount;
            this.betAmount = betAmount;
            this.player = player;
            this.startTime = System.currentTimeMillis();
            
            // Create and shuffle deck
            createDeck();
        }
        
        /**
         * Create and shuffle a deck of cards
         */
        private void createDeck() {
            // Create a deck (standard 52-card deck)
            for (String suit : SUITS) {
                for (int i = 0; i < RANKS.length; i++) {
                    // Card value: Ace=1, 2=2, ..., 10=10, J/Q/K=10
                    int value = i == 0 ? 11 : Math.min(10, i + 1); // Ace is 11 initially
                    deck.add(new BlackjackCard(RANKS[i], suit, value));
                }
            }
            
            // Shuffle the deck
            Collections.shuffle(deck);
        }
        
        /**
         * Deal initial cards (2 to player, 2 to dealer)
         */
        public void dealInitialCards() {
            playerCards.add(drawCard());
            dealerCards.add(drawCard());
            playerCards.add(drawCard());
            dealerCards.add(drawCard());
        }
        
        /**
         * Draw a card from the deck
         */
        private BlackjackCard drawCard() {
            if (deck.isEmpty()) {
                // Reshuffle if needed
                createDeck();
            }
            return deck.remove(deck.size() - 1);
        }
        
        /**
         * Deal a card to the player
         */
        public void dealCardToPlayer() {
            playerCards.add(drawCard());
        }
        
        /**
         * Deal a card to the dealer
         */
        public void dealCardToDealer() {
            dealerCards.add(drawCard());
        }
        
        /**
         * Reveal dealer's cards
         */
        public void revealDealerCards() {
            dealerRevealed = true;
        }
        
        /**
         * Execute dealer's play (hit until 17 or higher)
         */
        public void dealerPlay() {
            // Dealer must stand on soft 17 or higher
            while (getDealerValue() < 17) {
                dealCardToDealer();
            }
        }
        
        /**
         * Get player's hand value
         */
        public int getPlayerValue() {
            return calculateHandValue(playerCards);
        }
        
        /**
         * Get dealer's hand value
         */
        public int getDealerValue() {
            return calculateHandValue(dealerCards);
        }
        
        /**
         * Calculate hand value
         */
        private int calculateHandValue(List<BlackjackCard> cards) {
            int value = 0;
            int aces = 0;
            
            for (BlackjackCard card : cards) {
                value += card.getValue();
                if (card.getValue() == 11) {
                    aces++;
                }
            }
            
            // Adjust for aces if busted
            while (value > 21 && aces > 0) {
                value -= 10;
                aces--;
            }
            
            return value;
        }
        
        /**
         * Check if player has blackjack
         */
        public boolean isPlayerBlackjack() {
            return playerCards.size() == 2 && getPlayerValue() == 21;
        }
        
        /**
         * Check if dealer has blackjack
         */
        public boolean isDealerBlackjack() {
            return dealerCards.size() == 2 && getDealerValue() == 21;
        }
        
        /**
         * Check if player is busted
         */
        public boolean isPlayerBusted() {
            return getPlayerValue() > 21;
        }
        
        /**
         * Check if dealer is busted
         */
        public boolean isDealerBusted() {
            return getDealerValue() > 21;
        }
        
        /**
         * Double the bet and return if successful
         */
        public void doubleBet() {
            betAmount *= 2;
        }
        
        /**
         * Determine the winner
         */
        public GameResult determineWinner() {
            int playerValue = getPlayerValue();
            int dealerValue = getDealerValue();
            
            // Player busts
            if (playerValue > 21) {
                return GameResult.DEALER_WINS;
            }
            
            // Dealer busts
            if (dealerValue > 21) {
                return GameResult.PLAYER_WINS;
            }
            
            // Compare values
            if (playerValue > dealerValue) {
                return GameResult.PLAYER_WINS;
            } else if (dealerValue > playerValue) {
                return GameResult.DEALER_WINS;
            } else {
                return GameResult.PUSH;
            }
        }
        
        // Getters
        
        public List<BlackjackCard> getPlayerCards() {
            return playerCards;
        }
        
        public List<BlackjackCard> getDealerCards() {
            return dealerCards;
        }
        
        public boolean isDealerRevealed() {
            return dealerRevealed;
        }
        
        public int getBetAmount() {
            return betAmount;
        }
        
        public int getInitialBetAmount() {
            return initialBetAmount;
        }
        
        public Player getPlayer() {
            return player;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        /**
         * Enum for game results
         */
        public enum GameResult {
            PLAYER_WINS,
            DEALER_WINS,
            PUSH
        }
    }
    
    /**
     * Class representing a playing card in blackjack
     */
    private static class BlackjackCard {
        private final String rank;
        private final String suit;
        private final int value;
        
        public BlackjackCard(String rank, String suit, int value) {
            this.rank = rank;
            this.suit = suit;
            this.value = value;
        }
        
        public String getRank() {
            return rank;
        }
        
        public String getSuit() {
            return suit;
        }
        
        public int getValue() {
            return value;
        }
        
        @Override
        public String toString() {
            return "`" + rank + suit + "`";
        }
    }
}