package com.deadside.bot.commands.economy;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Command for playing roulette with emeralds (currency)
 */
public class RouletteCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(RouletteCommand.class);
    
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, RouletteGame> activeGames = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
    
    // Roulette wheel has 37 slots: 0-36 (0 is green, 1-36 are red/black alternating)
    private static final int NUM_WHEEL_SLOTS = 37;
    private static final int[] RED_NUMBERS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
    private static final Set<Integer> RED_NUMBERS_SET = new HashSet<>();
    private static final Color GREEN_COLOR = new Color(0, 128, 0);
    private static final Color RED_COLOR = new Color(204, 0, 0);
    private static final Color BLACK_COLOR = new Color(0, 0, 0);
    
    static {
        for (int num : RED_NUMBERS) {
            RED_NUMBERS_SET.add(num);
        }
    }
    
    /**
     * Represents a roulette game session
     */
    private static class RouletteGame {
        private final String userId;
        private final long bet;
        private String betType;
        private String betValue;
        private boolean isActive = true;
        private boolean isSpinning = false;
        private int winningNumber;
        private long winAmount;
        private ScheduledFuture<?> timeoutFuture;
        
        public RouletteGame(String userId, long bet) {
            this.userId = userId;
            this.bet = bet;
        }
        
        public void placeBet(String betType, String betValue) {
            this.betType = betType;
            this.betValue = betValue;
        }
        
        public void setSpinning(boolean spinning) {
            this.isSpinning = spinning;
        }
        
        public void setWinningNumber(int winningNumber) {
            this.winningNumber = winningNumber;
        }
        
        public void setWinAmount(long winAmount) {
            this.winAmount = winAmount;
        }
        
        public void end() {
            this.isActive = false;
            if (timeoutFuture != null && !timeoutFuture.isDone()) {
                timeoutFuture.cancel(true);
            }
        }
        
        // Getters
        public String getUserId() {
            return userId;
        }
        
        public long getBet() {
            return bet;
        }
        
        public String getBetType() {
            return betType;
        }
        
        public String getBetValue() {
            return betValue;
        }
        
        public boolean isActive() {
            return isActive;
        }
        
        public boolean isSpinning() {
            return isSpinning;
        }
        
        public int getWinningNumber() {
            return winningNumber;
        }
        
        public long getWinAmount() {
            return winAmount;
        }
        
        public void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
            this.timeoutFuture = timeoutFuture;
        }
    }
    
    @Override
    public String getName() {
        return "roulette";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option with autocomplete for bet amounts
        OptionData betOption = new OptionData(OptionType.INTEGER, "bet", "Amount of coins to bet", true)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "Play a game of roulette with coins")
                .addOptions(betOption);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long bet = event.getOption("bet", 0L, OptionMapping::getAsLong);
        String userId = event.getUser().getId();
        
        if (bet <= 0) {
            EmbedSender.replyEmbed(event, EmbedUtils.errorEmbed("Error", "Bet amount must be greater than 0"), true);
            return;
        }
        
        // Check if user already has an active game
        if (activeGames.containsKey(userId)) {
            EmbedSender.replyEmbed(event, EmbedUtils.errorEmbed("Error", "You already have an active roulette game"), true);
            return;
        }
        
        // Defer the reply for database operations which may take longer than 3 seconds
        event.deferReply().queue();
        
        // Get linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(Long.parseLong(userId));
        
        if (linkedPlayer == null) {
            EmbedSender.sendEmbed(event.getHook(), EmbedUtils.errorEmbed("Account Not Linked", 
                              "You don't have a linked Deadside account. Use `/link` to connect your Discord and Deadside accounts."));
            return;
        }
        
        // Get player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        
        if (player == null) {
            EmbedSender.sendEmbed(event.getHook(), EmbedUtils.errorEmbed("Player Not Found", 
                              "Unable to find your player data. This could be because you haven't been active yet."));
            return;
        }
        
        // Check if player has enough balance
        if (player.getCurrency().getCoins() < bet) {
            EmbedSender.sendEmbed(event.getHook(), EmbedUtils.errorEmbed("Insufficient Funds", 
                             "You don't have enough coins. You have " + 
                             String.format("%,d", player.getCurrency().getCoins()) + " coins."));
            return;
        }
        
        // Deduct bet amount
        player.getCurrency().removeCoins(bet);
        playerRepository.save(player);
        
        // Create new game
        RouletteGame game = new RouletteGame(userId, bet);
        activeGames.put(userId, game);
        
        // Set timeout for the game (5 minutes)
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            if (activeGames.containsKey(userId)) {
                RouletteGame expiredGame = activeGames.get(userId);
                if (expiredGame.isActive() && !expiredGame.isSpinning()) {
                    activeGames.remove(userId);
                    // Refund bet amount - get the player again in case data changed
                    try {
                        LinkedPlayer lp = linkedPlayerRepository.findByDiscordId(Long.parseLong(userId));
                        if (lp != null) {
                            Player p = playerRepository.findByPlayerId(lp.getMainPlayerId());
                            if (p != null) {
                                p.getCurrency().addCoins(bet);
                                playerRepository.save(p);
                                logger.info("Roulette game for user {} timed out and bet was refunded", userId);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error refunding bet for timed out roulette game", e);
                    }
                }
            }
        }, 5, TimeUnit.MINUTES);
        game.setTimeoutFuture(timeoutFuture);
        
        // Create embed with roulette table and betting options
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎲 Roulette")
                .setDescription("Select your bet type and place your bet!\n" +
                        "You have bet " + String.format("%,d", bet) + " coins.")
                .setColor(Color.GREEN)
                .setThumbnail("https://i.imgur.com/GUWr3LA.png") // Roulette wheel image
                .addField("Roulette Bets", 
                        "**Straight Up**: Bet on a single number (35:1)\n" +
                        "**Red/Black**: Bet on all red or black numbers (1:1)\n" +
                        "**Even/Odd**: Bet on even or odd numbers (1:1)\n" +
                        "**Low/High**: Bet on 1-18 or 19-36 (1:1)\n" +
                        "**Dozens**: Bet on 1-12, 13-24, or 25-36 (2:1)\n" +
                        "**Columns**: Bet on one of three columns (2:1)", false)
                .setFooter("Game will expire if no bet is placed within 5 minutes");
        
        // Create bet type selection menu
        StringSelectMenu betTypeMenu = StringSelectMenu.create("roulette:betType:" + userId)
                .setPlaceholder("Select bet type")
                .addOptions(
                        SelectOption.of("Straight Up (Single Number)", "straight")
                                .withDescription("Pays 35 to 1")
                                .withEmoji(Emoji.fromUnicode("🎯")),
                        SelectOption.of("Red or Black", "color")
                                .withDescription("Pays 1 to 1")
                                .withEmoji(Emoji.fromUnicode("🔴")),
                        SelectOption.of("Even or Odd", "evenodd")
                                .withDescription("Pays 1 to 1")
                                .withEmoji(Emoji.fromUnicode("🔢")),
                        SelectOption.of("Low (1-18) or High (19-36)", "lowhigh")
                                .withDescription("Pays 1 to 1")
                                .withEmoji(Emoji.fromUnicode("⬆️")),
                        SelectOption.of("Dozens (1-12, 13-24, 25-36)", "dozen")
                                .withDescription("Pays 2 to 1")
                                .withEmoji(Emoji.fromUnicode("🔢")),
                        SelectOption.of("Columns", "column")
                                .withDescription("Pays 2 to 1")
                                .withEmoji(Emoji.fromUnicode("📊"))
                )
                .build();
        
        event.getHook().sendMessageEmbeds(embed.build())
                .addActionRow(betTypeMenu)
                .addActionRow(Button.danger("roulette:cancel:" + userId, "Cancel and Refund"))
                .queue();
    }
    
    /**
     * Handle button interactions for roulette game
     * @param event Button interaction event
     * @param buttonData Button data
     */
    public void handleButtonInteraction(ButtonInteractionEvent event, String[] buttonData) {
        if (buttonData.length < 3) {
            return;
        }
        
        String action = buttonData[1];
        String userId = buttonData[2];
        
        // Defer reply for button interactions
        event.deferReply().queue();
        
        // Only the game owner can interact with the buttons
        if (!event.getUser().getId().equals(userId)) {
            event.getHook().sendMessage("This is not your game!").setEphemeral(true).queue();
            return;
        }
        
        RouletteGame game = activeGames.get(userId);
        if (game == null || !game.isActive()) {
            event.getHook().sendMessage("This game has expired or does not exist.").setEphemeral(true).queue();
            return;
        }
        
        switch (action) {
            case "cancel" -> handleCancelAction(event, game);
            case "spin" -> handleSpinAction(event, game);
            case "playAgain" -> handlePlayAgainAction(event, game);
            default -> event.getHook().sendMessage("Unknown action: " + action).setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle select menu interactions for roulette game
     * @param event Select menu interaction event
     * @param menuData Menu data
     */
    public void handleSelectMenuInteraction(StringSelectInteractionEvent event, String[] menuData) {
        if (menuData.length < 3) {
            return;
        }
        
        String menuAction = menuData[1];
        String userId = menuData[2];
        
        // Defer reply for menu handling
        event.deferReply().queue();
        
        // Only the game owner can interact with the menus
        if (!event.getUser().getId().equals(userId)) {
            event.getHook().sendMessage("This is not your game!").setEphemeral(true).queue();
            return;
        }
        
        RouletteGame game = activeGames.get(userId);
        if (game == null || !game.isActive()) {
            event.getHook().sendMessage("This game has expired or does not exist.").setEphemeral(true).queue();
            return;
        }
        
        String selectedValue = event.getValues().get(0);
        
        switch (menuAction) {
            case "betType" -> handleBetTypeSelection(event, game, selectedValue);
            case "betValue" -> handleBetValueSelection(event, game, selectedValue);
            default -> event.getHook().sendMessage("Unknown menu action: " + menuAction).setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle bet type selection
     * @param event Select menu interaction event
     * @param game Current roulette game
     * @param betType Selected bet type
     */
    private void handleBetTypeSelection(StringSelectInteractionEvent event, RouletteGame game, String betType) {
        game.placeBet(betType, null);
        
        StringSelectMenu valueMenu = null;
        String description = "";
        
        switch (betType) {
            case "straight" -> {
                // For straight up bets, we need a menu with all numbers 0-36
                StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("roulette:betValue:" + game.getUserId())
                        .setPlaceholder("Select a number (0-36)");
                
                // Add 0 as a special green number
                menuBuilder.addOption("0 (Green)", "0", "35:1 payout");
                
                // Add all other numbers 1-36 with their colors
                for (int i = 1; i <= 36; i++) {
                    String color = RED_NUMBERS_SET.contains(i) ? "Red" : "Black";
                    menuBuilder.addOption(i + " (" + color + ")", String.valueOf(i), "35:1 payout");
                }
                
                valueMenu = menuBuilder.build();
                description = "Select a single number to bet on. Pays 35 to 1 if the ball lands on your number.";
            }
            case "color" -> {
                valueMenu = StringSelectMenu.create("roulette:betValue:" + game.getUserId())
                        .setPlaceholder("Select Red or Black")
                        .addOptions(
                                SelectOption.of("Red", "red")
                                        .withDescription("Bet on all red numbers")
                                        .withEmoji(Emoji.fromUnicode("🔴")),
                                SelectOption.of("Black", "black")
                                        .withDescription("Bet on all black numbers")
                                        .withEmoji(Emoji.fromUnicode("⚫"))
                        )
                        .build();
                description = "Bet on all red or black numbers. Pays 1 to 1 if the ball lands on your selected color.";
            }
            case "evenodd" -> {
                valueMenu = StringSelectMenu.create("roulette:betValue:" + game.getUserId())
                        .setPlaceholder("Select Even or Odd")
                        .addOptions(
                                SelectOption.of("Even", "even")
                                        .withDescription("Bet on even numbers (2, 4, 6, etc.)")
                                        .withEmoji(Emoji.fromUnicode("2️⃣")),
                                SelectOption.of("Odd", "odd")
                                        .withDescription("Bet on odd numbers (1, 3, 5, etc.)")
                                        .withEmoji(Emoji.fromUnicode("1️⃣"))
                        )
                        .build();
                description = "Bet on whether the winning number will be even or odd. Pays 1 to 1.";
            }
            case "lowhigh" -> {
                valueMenu = StringSelectMenu.create("roulette:betValue:" + game.getUserId())
                        .setPlaceholder("Select Low or High")
                        .addOptions(
                                SelectOption.of("Low (1-18)", "low")
                                        .withDescription("Bet on numbers 1 through 18")
                                        .withEmoji(Emoji.fromUnicode("⬇️")),
                                SelectOption.of("High (19-36)", "high")
                                        .withDescription("Bet on numbers 19 through 36")
                                        .withEmoji(Emoji.fromUnicode("⬆️"))
                        )
                        .build();
                description = "Bet on whether the winning number will be low (1-18) or high (19-36). Pays 1 to 1.";
            }
            case "dozen" -> {
                valueMenu = StringSelectMenu.create("roulette:betValue:" + game.getUserId())
                        .setPlaceholder("Select a Dozen")
                        .addOptions(
                                SelectOption.of("First Dozen (1-12)", "1st12")
                                        .withDescription("Bet on numbers 1 through 12")
                                        .withEmoji(Emoji.fromUnicode("1️⃣")),
                                SelectOption.of("Second Dozen (13-24)", "2nd12")
                                        .withDescription("Bet on numbers 13 through 24")
                                        .withEmoji(Emoji.fromUnicode("2️⃣")),
                                SelectOption.of("Third Dozen (25-36)", "3rd12")
                                        .withDescription("Bet on numbers 25 through 36")
                                        .withEmoji(Emoji.fromUnicode("3️⃣"))
                        )
                        .build();
                description = "Bet on one of three dozens (1-12, 13-24, or 25-36). Pays 2 to 1.";
            }
            case "column" -> {
                valueMenu = StringSelectMenu.create("roulette:betValue:" + game.getUserId())
                        .setPlaceholder("Select a Column")
                        .addOptions(
                                SelectOption.of("First Column (1,4,7,...,34)", "1st")
                                        .withDescription("Bet on numbers 1,4,7,...,34")
                                        .withEmoji(Emoji.fromUnicode("1️⃣")),
                                SelectOption.of("Second Column (2,5,8,...,35)", "2nd")
                                        .withDescription("Bet on numbers 2,5,8,...,35")
                                        .withEmoji(Emoji.fromUnicode("2️⃣")),
                                SelectOption.of("Third Column (3,6,9,...,36)", "3rd")
                                        .withDescription("Bet on numbers 3,6,9,...,36")
                                        .withEmoji(Emoji.fromUnicode("3️⃣"))
                        )
                        .build();
                description = "Bet on one of three columns. Each column contains 12 numbers. Pays 2 to 1.";
            }
        }
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎲 Roulette - Select Your Bet")
                .setDescription("You've selected: **" + formatBetTypeName(betType) + "**\n" +
                        description + "\n\n" +
                        "Your bet: **" + String.format("%,d", game.getBet()) + " coins**")
                .setColor(Color.GREEN)
                .setThumbnail("https://i.imgur.com/GUWr3LA.png");
        
        ActionRow[] actionRows = valueMenu != null ? 
                new ActionRow[] { ActionRow.of(valueMenu), ActionRow.of(Button.danger("roulette:cancel:" + game.getUserId(), "Cancel and Refund")) } :
                new ActionRow[] { ActionRow.of(Button.danger("roulette:cancel:" + game.getUserId(), "Cancel and Refund")) };
        
        event.getHook().sendMessageEmbeds(embed.build())
                .setComponents(List.of(actionRows))
                .setEphemeral(false)
                .queue();
    }
    
    /**
     * Handle bet value selection
     * @param event Select menu interaction event
     * @param game Current roulette game
     * @param betValue Selected bet value
     */
    private void handleBetValueSelection(StringSelectInteractionEvent event, RouletteGame game, String betValue) {
        game.placeBet(game.getBetType(), betValue);
        
        String betDescription = formatBetDescription(game.getBetType(), betValue);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎲 Roulette - Ready to Spin")
                .setDescription("You've bet **" + String.format("%,d", game.getBet()) + " coins** on:\n" +
                        "**" + betDescription + "**\n\n" +
                        "Click the Spin button when you're ready!")
                .setColor(Color.GREEN)
                .setThumbnail("https://i.imgur.com/GUWr3LA.png");
        
        // Spin and Cancel buttons
        event.getHook().sendMessageEmbeds(embed.build())
                .addActionRow(
                        Button.success("roulette:spin:" + game.getUserId(), "Spin the Wheel")
                                .withEmoji(Emoji.fromUnicode("🎲")),
                        Button.danger("roulette:cancel:" + game.getUserId(), "Cancel and Refund")
                )
                .queue();
    }
    
    /**
     * Handle spin action
     * @param event Button interaction event
     * @param game Current roulette game
     */
    private void handleSpinAction(ButtonInteractionEvent event, RouletteGame game) {
        if (game.getBetType() == null || game.getBetValue() == null) {
            event.reply("You need to place a bet first!").setEphemeral(true).queue();
            return;
        }
        
        // Defer edit for the spinning operation
        event.deferEdit().queue();
        
        game.setSpinning(true);
        
        // First show an animated "spinning" message
        EmbedBuilder spinningEmbed = new EmbedBuilder()
                .setTitle("🎲 Roulette - Wheel is Spinning")
                .setDescription("The wheel is spinning...\n\n" +
                        "Your bet: **" + String.format("%,d", game.getBet()) + " coins** on **" + 
                        formatBetDescription(game.getBetType(), game.getBetValue()) + "**")
                .setColor(Color.YELLOW)
                .setImage("https://i.imgur.com/bEhNi0y.gif"); // Spinning wheel animation GIF
        
        event.getHook().sendMessageEmbeds(spinningEmbed.build())
                .setEphemeral(false)
                .queue();
        
        // Schedule the result to be shown after a delay (3 seconds)
        scheduler.schedule(() -> {
            // Determine the winning number (0-36)
            int winningNumber = random.nextInt(NUM_WHEEL_SLOTS);
            game.setWinningNumber(winningNumber);
            
            // Calculate if player won and how much
            boolean won = isWinningBet(game.getBetType(), game.getBetValue(), winningNumber);
            long winAmount = calculateWinAmount(game.getBetType(), game.getBet(), won);
            game.setWinAmount(winAmount);
            
            // Update player balance if they won
            if (won) {
                LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(Long.parseLong(game.getUserId()));
                if (linkedPlayer != null) {
                    Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
                    if (player != null) {
                        player.getCurrency().addCoins(winAmount);
                        playerRepository.save(player);
                    }
                }
            }
            
            // Determine the color of the winning number
            Color resultColor;
            String colorName;
            if (winningNumber == 0) {
                resultColor = GREEN_COLOR;
                colorName = "Green";
            } else if (RED_NUMBERS_SET.contains(winningNumber)) {
                resultColor = RED_COLOR;
                colorName = "Red";
            } else {
                resultColor = BLACK_COLOR;
                colorName = "Black";
            }
            
            // Build the result embed
            EmbedBuilder resultEmbed = new EmbedBuilder()
                    .setTitle("🎲 Roulette - Result")
                    .setColor(resultColor);
            
            if (won) {
                resultEmbed.setDescription("**The ball landed on " + winningNumber + " " + colorName + "**\n\n" +
                        "**🎉 YOU WON! 🎉**\n" +
                        "Your bet: **" + String.format("%,d", game.getBet()) + " coins** on **" + 
                        formatBetDescription(game.getBetType(), game.getBetValue()) + "**\n" +
                        "You won: **" + String.format("%,d", winAmount) + " coins**");
                
                // Show additional winning details based on bet type
                resultEmbed.addField("Winnings Breakdown", 
                        "Original bet: " + String.format("%,d", game.getBet()) + " coins\n" +
                        "Profit: " + String.format("%,d", (winAmount - game.getBet())) + " coins\n" +
                        "Total winnings: " + String.format("%,d", winAmount) + " coins", false);
            } else {
                resultEmbed.setDescription("**The ball landed on " + winningNumber + " " + colorName + "**\n\n" +
                        "**❌ YOU LOST ❌**\n" +
                        "Your bet: **" + String.format("%,d", game.getBet()) + " coins** on **" + 
                        formatBetDescription(game.getBetType(), game.getBetValue()) + "**\n" +
                        "You lost: **" + String.format("%,d", game.getBet()) + " coins**");
            }
            
            // Show the result image
            resultEmbed.setThumbnail(getResultImageUrl(winningNumber));
            
            // Play again button
            try {
                event.getHook().editOriginalEmbeds(resultEmbed.build())
                        .setActionRow(
                                Button.primary("roulette:playAgain:" + game.getUserId(), "Play Again")
                                        .withEmoji(Emoji.fromUnicode("🔄"))
                        )
                        .queue();
            } catch (Exception e) {
                logger.error("Error updating roulette result", e);
            }
            
            // Game is complete, remove from active games
            game.end();
            activeGames.remove(game.getUserId());
            
        }, 3, TimeUnit.SECONDS);
    }
    
    /**
     * Handle cancel action
     * @param event Button interaction event
     * @param game Current roulette game
     */
    private void handleCancelAction(ButtonInteractionEvent event, RouletteGame game) {
        // Defer reply for database operations
        event.deferReply().queue();
        
        // Refund the bet
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(Long.parseLong(game.getUserId()));
        if (linkedPlayer != null) {
            Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
            if (player != null) {
                player.getCurrency().addCoins(game.getBet());
                playerRepository.save(player);
            }
        }
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎲 Roulette - Cancelled")
                .setDescription("Your bet of **" + game.getBet() + " coins** has been refunded.")
                .setColor(Color.RED);
        
        event.getHook().sendMessageEmbeds(embed.build())
                .setEphemeral(true)
                .queue();
        
        // End the game
        game.end();
        activeGames.remove(game.getUserId());
    }
    
    /**
     * Handle play again action
     * @param event Button interaction event
     * @param game Current (completed) roulette game
     */
    private void handlePlayAgainAction(ButtonInteractionEvent event, RouletteGame game) {
        // Get user ID and previous bet amount
        String userId = game.getUserId();
        long bet = game.getBet();
        
        // Defer reply for database operations
        event.deferReply().queue();
        
        // Make sure they're not already in a game
        if (activeGames.containsKey(userId)) {
            event.getHook().sendMessage("You already have an active roulette game").setEphemeral(true).queue();
            return;
        }
        
        // Get linked player
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(Long.parseLong(userId));
        
        if (linkedPlayer == null) {
            event.getHook().sendMessage("You don't have a linked Deadside account. Use `/link` to connect your Discord and Deadside accounts.")
                 .setEphemeral(true).queue();
            return;
        }
        
        // Get player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        
        if (player == null) {
            event.getHook().sendMessage("Unable to find your player data. This could be because you haven't been active yet.")
                 .setEphemeral(true).queue();
            return;
        }
        
        // Check if player has enough balance
        if (player.getCurrency().getCoins() < bet) {
            event.getHook().sendMessage("You don't have enough coins for this bet. Your current balance is " + 
                   String.format("%,d", player.getCurrency().getCoins()) + " coins.")
                 .setEphemeral(true).queue();
            return;
        }
        
        // Deduct bet amount
        player.getCurrency().removeCoins(bet);
        playerRepository.save(player);
        
        // Create new game
        RouletteGame newGame = new RouletteGame(userId, bet);
        activeGames.put(userId, newGame);
        
        // Set timeout for the game (5 minutes)
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            if (activeGames.containsKey(userId)) {
                RouletteGame expiredGame = activeGames.get(userId);
                if (expiredGame.isActive() && !expiredGame.isSpinning()) {
                    activeGames.remove(userId);
                    // Refund bet amount - get the player again in case data changed
                    try {
                        LinkedPlayer lp = linkedPlayerRepository.findByDiscordId(Long.parseLong(userId));
                        if (lp != null) {
                            Player p = playerRepository.findByPlayerId(lp.getMainPlayerId());
                            if (p != null) {
                                p.getCurrency().addCoins(bet);
                                playerRepository.save(p);
                                logger.info("Roulette game for user {} timed out and bet was refunded", userId);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error refunding bet for timed out roulette game", e);
                    }
                }
            }
        }, 5, TimeUnit.MINUTES);
        newGame.setTimeoutFuture(timeoutFuture);
        
        // Create embed with roulette table and betting options
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎲 Roulette")
                .setDescription("Select your bet type and place your bet!\n" +
                        "You have bet " + bet + " coins.")
                .setColor(Color.GREEN)
                .setThumbnail("https://i.imgur.com/GUWr3LA.png") // Roulette wheel image
                .addField("Roulette Bets", 
                        "**Straight Up**: Bet on a single number (35:1)\n" +
                        "**Red/Black**: Bet on all red or black numbers (1:1)\n" +
                        "**Even/Odd**: Bet on even or odd numbers (1:1)\n" +
                        "**Low/High**: Bet on 1-18 or 19-36 (1:1)\n" +
                        "**Dozens**: Bet on 1-12, 13-24, or 25-36 (2:1)\n" +
                        "**Columns**: Bet on one of three columns (2:1)", false)
                .setFooter("Game will expire if no bet is placed within 5 minutes");
        
        // Create bet type selection menu
        StringSelectMenu betTypeMenu = StringSelectMenu.create("roulette:betType:" + userId)
                .setPlaceholder("Select bet type")
                .addOptions(
                        SelectOption.of("Straight Up (Single Number)", "straight")
                                .withDescription("Pays 35 to 1")
                                .withEmoji(Emoji.fromUnicode("🎯")),
                        SelectOption.of("Red or Black", "color")
                                .withDescription("Pays 1 to 1")
                                .withEmoji(Emoji.fromUnicode("🔴")),
                        SelectOption.of("Even or Odd", "evenodd")
                                .withDescription("Pays 1 to 1")
                                .withEmoji(Emoji.fromUnicode("🔢")),
                        SelectOption.of("Low (1-18) or High (19-36)", "lowhigh")
                                .withDescription("Pays 1 to 1")
                                .withEmoji(Emoji.fromUnicode("⬆️")),
                        SelectOption.of("Dozens (1-12, 13-24, 25-36)", "dozen")
                                .withDescription("Pays 2 to 1")
                                .withEmoji(Emoji.fromUnicode("🔢")),
                        SelectOption.of("Columns", "column")
                                .withDescription("Pays 2 to 1")
                                .withEmoji(Emoji.fromUnicode("📊"))
                )
                .build();
        
        event.getHook().sendMessageEmbeds(embed.build())
                .addActionRow(betTypeMenu)
                .addActionRow(Button.danger("roulette:cancel:" + userId, "Cancel and Refund"))
                .queue();
    }
    
    /**
     * Check if the bet is a winning bet
     * @param betType Type of bet
     * @param betValue Value of bet
     * @param winningNumber Winning number
     * @return True if bet is a winning bet
     */
    private boolean isWinningBet(String betType, String betValue, int winningNumber) {
        if (winningNumber == 0) {
            // Only straight bet on 0 wins when 0 comes up
            return betType.equals("straight") && betValue.equals("0");
        }
        
        switch (betType) {
            case "straight":
                return betValue.equals(String.valueOf(winningNumber));
                
            case "color":
                boolean isRed = RED_NUMBERS_SET.contains(winningNumber);
                return (betValue.equals("red") && isRed) || (betValue.equals("black") && !isRed);
                
            case "evenodd":
                boolean isEven = winningNumber % 2 == 0;
                return (betValue.equals("even") && isEven) || (betValue.equals("odd") && !isEven);
                
            case "lowhigh":
                boolean isLow = winningNumber >= 1 && winningNumber <= 18;
                return (betValue.equals("low") && isLow) || (betValue.equals("high") && !isLow);
                
            case "dozen":
                if (betValue.equals("1st12")) {
                    return winningNumber >= 1 && winningNumber <= 12;
                } else if (betValue.equals("2nd12")) {
                    return winningNumber >= 13 && winningNumber <= 24;
                } else if (betValue.equals("3rd12")) {
                    return winningNumber >= 25 && winningNumber <= 36;
                }
                return false;
                
            case "column":
                if (betValue.equals("1st")) {
                    return winningNumber % 3 == 1;
                } else if (betValue.equals("2nd")) {
                    return winningNumber % 3 == 2;
                } else if (betValue.equals("3rd")) {
                    return winningNumber % 3 == 0 && winningNumber != 0;
                }
                return false;
                
            default:
                return false;
        }
    }
    
    /**
     * Calculate win amount based on bet type and amount
     * @param betType Type of bet
     * @param betAmount Amount of bet
     * @param won Whether the bet was won
     * @return Win amount
     */
    private long calculateWinAmount(String betType, long betAmount, boolean won) {
        if (!won) {
            return 0;
        }
        
        // Different bet types have different payouts
        switch (betType) {
            case "straight":
                // Straight pays 35 to 1
                return betAmount * 36; // Original bet + 35x payout
                
            case "color":
            case "evenodd":
            case "lowhigh":
                // These all pay 1 to 1
                return betAmount * 2; // Original bet + 1x payout
                
            case "dozen":
            case "column":
                // These pay 2 to 1
                return betAmount * 3; // Original bet + 2x payout
                
            default:
                return 0;
        }
    }
    
    /**
     * Format bet type name for display
     * @param betType Type of bet
     * @return Formatted bet type name
     */
    private String formatBetTypeName(String betType) {
        return switch (betType) {
            case "straight" -> "Straight Up";
            case "color" -> "Red/Black";
            case "evenodd" -> "Even/Odd";
            case "lowhigh" -> "Low/High";
            case "dozen" -> "Dozen";
            case "column" -> "Column";
            default -> betType;
        };
    }
    
    /**
     * Format bet description for display
     * @param betType Type of bet
     * @param betValue Value of bet
     * @return Formatted bet description
     */
    private String formatBetDescription(String betType, String betValue) {
        return switch (betType) {
            case "straight" -> "Straight Up on " + betValue;
            case "color" -> betValue.substring(0, 1).toUpperCase() + betValue.substring(1);
            case "evenodd" -> betValue.substring(0, 1).toUpperCase() + betValue.substring(1);
            case "lowhigh" -> betValue.equals("low") ? "Low (1-18)" : "High (19-36)";
            case "dozen" -> switch (betValue) {
                case "1st12" -> "First Dozen (1-12)";
                case "2nd12" -> "Second Dozen (13-24)";
                case "3rd12" -> "Third Dozen (25-36)";
                default -> betValue;
            };
            case "column" -> switch (betValue) {
                case "1st" -> "First Column (1,4,7,...,34)";
                case "2nd" -> "Second Column (2,5,8,...,35)";
                case "3rd" -> "Third Column (3,6,9,...,36)";
                default -> betValue;
            };
            default -> betValue;
        };
    }
    
    /**
     * Get result image URL for the winning number
     * @param number Winning number
     * @return URL of the image
     */
    private String getResultImageUrl(int number) {
        // For simplicity, we'll use a static image but in production you might have
        // different images for different results or dynamically generate them
        if (number == 0) {
            return "https://i.imgur.com/fLBmGcx.png"; // Green 0
        } else if (RED_NUMBERS_SET.contains(number)) {
            return "https://i.imgur.com/RG5pKOK.png"; // Red number
        } else {
            return "https://i.imgur.com/s1FwnEQ.png"; // Black number
        }
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
                logger.error("Error generating autocomplete suggestions for roulette", e);
            }
            
            // Fallback to default suggestions
            return getDefaultBetSuggestions();
        }
        
        return List.of(); // No suggestions for other options
    }
    
    /**
     * Generate smart suggestions for bet amounts based on wallet balance
     */
    private List<Choice> generateBetSuggestions(long walletBalance, boolean hasCustomValue, long customValue) {
        List<Choice> suggestions = new ArrayList<>();
        
        // If user entered a custom value and it's valid, add it first
        if (hasCustomValue && customValue > 0 && customValue <= walletBalance) {
            suggestions.add(new Choice(formatAmount(customValue) + " coins", customValue));
        }
        
        // Add "All in" option if they have coins
        if (walletBalance > 0) {
            suggestions.add(new Choice("All in! (" + formatAmount(walletBalance) + ")", walletBalance));
        }
        
        // Add percentage-based options if balance is sufficient
        if (walletBalance >= 100) {
            suggestions.add(new Choice("Half (" + formatAmount(walletBalance / 2) + ")", walletBalance / 2));
            
            if (walletBalance >= 1000) {
                suggestions.add(new Choice("10% (" + formatAmount(walletBalance / 10) + ")", walletBalance / 10));
                suggestions.add(new Choice("25% (" + formatAmount(walletBalance / 4) + ")", walletBalance / 4));
            }
        }
        
        // Add common fixed bet amounts
        addCommonBetAmounts(suggestions, walletBalance);
        
        return suggestions;
    }
    
    /**
     * Add common fixed bet amount suggestions up to the maximum balance
     */
    private void addCommonBetAmounts(List<Choice> suggestions, long maxBalance) {
        long[] commonAmounts = {50, 100, 500, 1000, 5000, 10000, 50000, 100000};
        
        for (long amount : commonAmounts) {
            if (amount <= maxBalance) {
                suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
            }
        }
    }
    
    /**
     * Get default bet suggestions when player data is not available
     */
    private List<Choice> getDefaultBetSuggestions() {
        List<Choice> suggestions = new ArrayList<>();
        
        // Add common fixed amounts as defaults
        long[] defaultAmounts = {50, 100, 500, 1000, 5000, 10000};
        for (long amount : defaultAmounts) {
            suggestions.add(new Choice(formatAmount(amount) + " coins", amount));
        }
        
        return suggestions;
    }
    
    /**
     * Format a currency amount with commas
     */
    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }
}