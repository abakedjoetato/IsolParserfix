package com.deadside.bot.commands.stats;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.FeatureGate;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.entities.User;
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

import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for viewing player statistics
 */
public class StatsCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(StatsCommand.class);
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final DecimalFormat df = new DecimalFormat("#.##");
    
    @Override
    public String getName() {
        return "stats";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option with autocomplete for player names
        OptionData playerOption = new OptionData(OptionType.STRING, "player", "The in-game player name to lookup", false)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "View Deadside player statistics")
                .addOptions(playerOption)
                .addOption(OptionType.USER, "user", "The Discord user to lookup", false);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        // Check if guild has access to the advanced statistics feature
        if (!FeatureGate.checkCommandAccess(event, FeatureGate.Feature.BASIC_STATS)) {
            // The FeatureGate utility already sent a premium upsell message
            return;
        }
        
        // Parse options
        String playerName = event.getOption("player", OptionMapping::getAsString);
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        
        // If neither option is provided, default to the command user
        if (playerName == null && targetUser == null) {
            targetUser = event.getUser();
        }
        
        event.deferReply().queue();
        
        try {
            if (playerName != null) {
                // Look up by player name
                displayPlayerStats(event, playerName);
            } else if (targetUser != null) {
                // Look up by Discord user
                displayUserStats(event, targetUser);
            }
        } catch (Exception e) {
            logger.error("Error retrieving player stats", e);
            event.getHook().sendMessage("An error occurred while retrieving statistics.").queue();
        }
    }
    
    private void displayPlayerStats(SlashCommandInteractionEvent event, String playerName) {
        long guildId = event.getGuild().getIdLong();
        
        // Find player by name (using our available methods)
        List<Player> matchingPlayers = playerRepository.findByNameLike(playerName);
        Player player = null;
        
        // Look for exact match first
        for (Player p : matchingPlayers) {
            if (p.getName().equalsIgnoreCase(playerName)) {
                player = p;
                break;
            }
        }
        
        // If no exact match, use first one if available
        if (player == null && !matchingPlayers.isEmpty()) {
            player = matchingPlayers.get(0);
        }
        
        if (player == null) {
            event.getHook().sendMessage("No player found with name: " + playerName).queue();
            return;
        }
        
        // Build and send stats embed with proper Deadside styling
        EmbedSender.sendEmbed(event.getHook(), createStatsEmbed(player));
    }
    
    private void displayUserStats(SlashCommandInteractionEvent event, User user) {
        long userId = user.getIdLong();
        
        // Use our LinkedPlayerRepository to find linked players
        LinkedPlayerRepository linkedPlayerRepo = new LinkedPlayerRepository();
        LinkedPlayer linkedPlayer = linkedPlayerRepo.findByDiscordId(userId);
        
        if (linkedPlayer == null) {
            event.getHook().sendMessage(user.getName() + " hasn't linked any Deadside accounts yet. " +
                    "Use `/link main` to connect your game account.").queue();
            return;
        }
        
        // Get the main player
        Player player = playerRepository.findByPlayerId(linkedPlayer.getMainPlayerId());
        
        if (player == null) {
            event.getHook().sendMessage("Could not find linked player data for " + user.getName() + ". " +
                    "Please try relinking your account.").queue();
            return;
        }
        
        // Build and send stats embed with proper Deadside styling
        EmbedSender.sendEmbed(event.getHook(), createStatsEmbed(player));
    }
    
    private net.dv8tion.jda.api.entities.MessageEmbed createStatsEmbed(Player player) {
        // Calculate KD ratio
        double kd = player.getDeaths() > 0 
                ? (double) player.getKills() / player.getDeaths() 
                : player.getKills();
        
        StringBuilder description = new StringBuilder();
        description.append("**Player**: ").append(player.getName()).append("\n\n");
        description.append("**Kills**: ").append(player.getKills()).append("\n");
        description.append("**Deaths**: ").append(player.getDeaths()).append("\n");
        description.append("**K/D Ratio**: ").append(df.format(kd)).append("\n\n");
        
        if (player.getMostUsedWeapon() != null && !player.getMostUsedWeapon().isEmpty()) {
            description.append("**Favorite Weapon**: ").append(player.getMostUsedWeapon()).append("\n");
        }
        
        if (player.getMostKilledPlayer() != null && !player.getMostKilledPlayer().isEmpty()) {
            description.append("**Most Killed**: ").append(player.getMostKilledPlayer()).append("\n");
        }
        
        if (player.getKilledByMost() != null && !player.getKilledByMost().isEmpty()) {
            description.append("**Killed By Most**: ").append(player.getKilledByMost()).append("\n");
        }
        
        description.append("\n**Last Seen**: <t:").append(player.getLastUpdated() / 1000).append(":R>");
        
        return EmbedUtils.infoEmbed("Player Statistics", description.toString());
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getGuild() == null) return List.of();
        
        String focusedOption = event.getFocusedOption().getName();
        
        // We only have autocomplete for player names
        if ("player".equals(focusedOption)) {
            String currentInput = event.getFocusedOption().getValue().toLowerCase();
            
            // Search for players with names matching the current input
            List<Player> matchingPlayers = playerRepository.findByNameLike(currentInput);
            
            // Convert to autocomplete choices
            return matchingPlayers.stream()
                .map(player -> new Choice(player.getName(), player.getName()))
                .limit(25)  // Discord limits to 25 choices max
                .collect(Collectors.toList());
        }
        
        return List.of(); // Empty list for no suggestions
    }
}
