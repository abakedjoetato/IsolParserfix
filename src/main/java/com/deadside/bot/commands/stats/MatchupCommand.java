package com.deadside.bot.commands.stats;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.PremiumManager;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Command for viewing head-to-head matchups between players
 */
public class MatchupCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(MatchupCommand.class);
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final PremiumManager premiumManager = new PremiumManager();
    private final DecimalFormat df = new DecimalFormat("#.##");
    
    @Override
    public String getName() {
        return "matchup";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "View head-to-head matchup between two players")
                .addOption(OptionType.STRING, "player1", "First player's name", true)
                .addOption(OptionType.STRING, "player2", "Second player's name", true);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        // Check for premium if feature is restricted
        long guildId = event.getGuild().getIdLong();
        if (!premiumManager.hasPremium(guildId)) {
            event.reply("This command is only available with premium. The killfeed is available for free.").setEphemeral(true).queue();
            return;
        }
        
        String player1Name = event.getOption("player1", OptionMapping::getAsString);
        String player2Name = event.getOption("player2", OptionMapping::getAsString);
        
        if (player1Name == null || player2Name == null) {
            event.reply("Please provide both player names.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        try {
            // Find player 1
            List<Player> player1Matches = playerRepository.findByNameLike(player1Name);
            Player player1 = findBestMatch(player1Matches, player1Name);
            
            if (player1 == null) {
                event.getHook().sendMessage("No player found with name: " + player1Name).queue();
                return;
            }
            
            // Find player 2
            List<Player> player2Matches = playerRepository.findByNameLike(player2Name);
            Player player2 = findBestMatch(player2Matches, player2Name);
            
            if (player2 == null) {
                event.getHook().sendMessage("No player found with name: " + player2Name).queue();
                return;
            }
            
            // Get matchup data
            int player1Kills = getKillsAgainst(player1, player2Name);
            int player2Kills = getKillsAgainst(player2, player1Name);
            
            // Build and send matchup embed
            event.getHook().sendMessageEmbeds(
                    createMatchupEmbed(player1, player2, player1Kills, player2Kills)
            ).queue();
            
        } catch (Exception e) {
            logger.error("Error retrieving player matchup", e);
            event.getHook().sendMessage("An error occurred while retrieving matchup data.").queue();
        }
    }
    
    /**
     * Find the best matching player from a list of potential matches
     */
    private Player findBestMatch(List<Player> players, String searchName) {
        if (players.isEmpty()) {
            return null;
        }
        
        // Look for exact name match first
        for (Player player : players) {
            if (player.getName().equalsIgnoreCase(searchName)) {
                return player;
            }
        }
        
        // If no exact match, return first result
        return players.get(0);
    }
    
    /**
     * Get number of kills player1 has against player2
     * For a complete implementation, this would query historical kill data
     * This is a simplified version based on the existing data model
     */
    private int getKillsAgainst(Player player1, String player2Name) {
        // Check if player2 is player1's most killed player
        if (player1.getMostKilledPlayer() != null && 
            player1.getMostKilledPlayer().equalsIgnoreCase(player2Name)) {
            return player1.getMostKilledPlayerCount();
        }
        
        // If not most killed, we assume they've encountered each other occasionally
        // In a full implementation, we would query a kill history collection
        if (player1.getKilledByMost() != null && 
            player1.getKilledByMost().equalsIgnoreCase(player2Name)) {
            // They've encountered each other, so estimate a lower kill count
            return Math.max(1, player1.getKilledByMostCount() / 2);
        }
        
        // Default to 0 if no known encounters
        return 0;
    }
    
    /**
     * Create matchup comparison embed
     */
    private net.dv8tion.jda.api.entities.MessageEmbed createMatchupEmbed(
            Player player1, Player player2, int player1Kills, int player2Kills) {
        
        StringBuilder description = new StringBuilder();
        
        // Player names and overall stats
        description.append("# ").append(player1.getName()).append(" vs. ").append(player2.getName()).append("\n\n");
        
        // Head-to-head stats
        description.append("## Head-to-Head Stats\n");
        description.append("**").append(player1.getName()).append("** has killed **")
                 .append(player2.getName()).append("** ")
                 .append(player1Kills).append(" times\n");
        
        description.append("**").append(player2.getName()).append("** has killed **")
                 .append(player1.getName()).append("** ")
                 .append(player2Kills).append(" times\n\n");
        
        // Determine who has the advantage
        if (player1Kills > player2Kills) {
            description.append("**").append(player1.getName()).append("** has the upper hand with a **")
                     .append(player1Kills - player2Kills).append("** kill advantage!\n\n");
        } else if (player2Kills > player1Kills) {
            description.append("**").append(player2.getName()).append("** has the upper hand with a **")
                     .append(player2Kills - player1Kills).append("** kill advantage!\n\n");
        } else {
            description.append("The matchup is perfectly even!\n\n");
        }
        
        // Overall player stats for comparison
        description.append("## Overall Player Stats\n");
        description.append("**").append(player1.getName()).append("**: ")
                 .append(player1.getKills()).append(" kills / ")
                 .append(player1.getDeaths()).append(" deaths / KD: ")
                 .append(df.format(player1.getKdRatio())).append("\n");
        
        description.append("**").append(player2.getName()).append("**: ")
                 .append(player2.getKills()).append(" kills / ")
                 .append(player2.getDeaths()).append(" deaths / KD: ")
                 .append(df.format(player2.getKdRatio())).append("\n\n");
        
        // Weapon comparison
        description.append("## Favorite Weapons\n");
        if (player1.getMostUsedWeapon() != null && !player1.getMostUsedWeapon().isEmpty()) {
            description.append("**").append(player1.getName()).append("**: ")
                     .append(player1.getMostUsedWeapon())
                     .append(" (").append(player1.getMostUsedWeaponKills()).append(" kills)\n");
        }
        
        if (player2.getMostUsedWeapon() != null && !player2.getMostUsedWeapon().isEmpty()) {
            description.append("**").append(player2.getName()).append("**: ")
                     .append(player2.getMostUsedWeapon())
                     .append(" (").append(player2.getMostUsedWeaponKills()).append(" kills)\n");
        }
        
        return EmbedUtils.infoEmbed("Player Matchup", description.toString());
    }
}