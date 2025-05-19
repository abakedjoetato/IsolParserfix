package com.deadside.bot.commands.player;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for linking Discord users to in-game players
 */
public class LinkCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(LinkCommand.class);
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    
    @Override
    public String getName() {
        return "link";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option with autocomplete for player names
        OptionData playerNameOption = new OptionData(OptionType.STRING, "player_name", "Your in-game player name", true)
                .setAutoComplete(true);
        
        // Create option for alt player removal with autocomplete (only show linked alts)
        OptionData removeAltOption = new OptionData(OptionType.STRING, "player_name", "The alt in-game player name to remove", true)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "Link your Discord account to your in-game player")
                .addSubcommands(
                        new SubcommandData("main", "Link your Discord account to your main in-game player")
                                .addOptions(playerNameOption),
                        new SubcommandData("add", "Add an alt account to your linked profile")
                                .addOptions(playerNameOption),
                        new SubcommandData("remove", "Remove an alt account from your linked profile")
                                .addOptions(removeAltOption),
                        new SubcommandData("list", "List all players linked to your Discord account"),
                        new SubcommandData("info", "Show information about another user's linked players")
                                .addOption(OptionType.USER, "user", "The Discord user to look up", true)
                );
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getUser() == null) {
            event.reply("Could not identify user.").setEphemeral(true).queue();
            return;
        }
        
        String subCommand = event.getSubcommandName();
        if (subCommand == null) {
            event.reply("Invalid command usage.").setEphemeral(true).queue();
            return;
        }
        
        try {
            switch (subCommand) {
                case "main" -> linkMainPlayer(event);
                case "add" -> addAltPlayer(event);
                case "remove" -> removeAltPlayer(event);
                case "list" -> listLinkedPlayers(event);
                case "info" -> showLinkedPlayerInfo(event);
                default -> event.reply("Unknown subcommand: " + subCommand).setEphemeral(true).queue();
            }
        } catch (Exception e) {
            logger.error("Error executing link command", e);
            event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
    
    private void linkMainPlayer(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String playerName = event.getOption("player_name", OptionMapping::getAsString);
        
        // Acknowledge the command immediately
        event.deferReply().queue();
        
        // Check if the user is already linked
        LinkedPlayer existingLink = linkedPlayerRepository.findByDiscordId(user.getIdLong());
        if (existingLink != null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Already Linked", 
                            "You are already linked to player: **" + existingLink.getMainPlayerName() + "**\n" +
                            "Use `/link add` to add alt accounts.")
            ).queue();
            return;
        }
        
        // Search for the player in the database
        List<Player> matchingPlayers = playerRepository.findByNameLike(playerName);
        
        if (matchingPlayers.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Player Not Found", 
                            "Could not find a player with name: **" + playerName + "**\n" +
                            "Make sure you've entered your exact in-game name and that you've played on one of our tracked servers.")
            ).queue();
            return;
        }
        
        // If we found multiple matches, use the best match (exact match or first result)
        Player bestMatch = null;
        for (Player player : matchingPlayers) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                bestMatch = player;
                break;
            }
        }
        
        // If no exact match, just use the first result
        if (bestMatch == null) {
            bestMatch = matchingPlayers.get(0);
        }
        
        // Check if player is already linked to another Discord user
        LinkedPlayer existingPlayerLink = linkedPlayerRepository.findByPlayerId(bestMatch.getPlayerId());
        if (existingPlayerLink != null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Player Already Linked", 
                            "Player **" + bestMatch.getName() + "** is already linked to another Discord user.\n" +
                            "If this is your player, please contact an administrator.")
            ).queue();
            return;
        }
        
        // Create the link
        LinkedPlayer newLink = new LinkedPlayer(user.getIdLong(), bestMatch.getName(), bestMatch.getPlayerId());
        linkedPlayerRepository.save(newLink);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Link Created", 
                        "Successfully linked your Discord account to player: **" + bestMatch.getName() + "**\n" +
                        "All your stats and achievements will now be tracked under this profile.")
        ).queue();
        
        logger.info("Created new player link: Discord User [{}] to Player [{}]", user.getId(), bestMatch.getName());
    }
    
    private void addAltPlayer(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String playerName = event.getOption("player_name", OptionMapping::getAsString);
        
        // Acknowledge the command immediately
        event.deferReply().queue();
        
        // Check if the user is linked
        LinkedPlayer existingLink = linkedPlayerRepository.findByDiscordId(user.getIdLong());
        if (existingLink == null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not Linked", 
                            "You need to link your main account first using `/link main`.")
            ).queue();
            return;
        }
        
        // Search for the player in the database
        List<Player> matchingPlayers = playerRepository.findByNameLike(playerName);
        
        if (matchingPlayers.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Player Not Found", 
                            "Could not find a player with name: **" + playerName + "**\n" +
                            "Make sure you've entered your exact in-game name and that you've played on one of our tracked servers.")
            ).queue();
            return;
        }
        
        // If we found multiple matches, use the best match (exact match or first result)
        Player bestMatch = null;
        for (Player player : matchingPlayers) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                bestMatch = player;
                break;
            }
        }
        
        // If no exact match, just use the first result
        if (bestMatch == null) {
            bestMatch = matchingPlayers.get(0);
        }
        
        // Check if player is already linked to another Discord user
        LinkedPlayer existingPlayerLink = linkedPlayerRepository.findByPlayerId(bestMatch.getPlayerId());
        if (existingPlayerLink != null && existingPlayerLink.getDiscordId() != user.getIdLong()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Player Already Linked", 
                            "Player **" + bestMatch.getName() + "** is already linked to another Discord user.\n" +
                            "If this is your player, please contact an administrator.")
            ).queue();
            return;
        }
        
        // Check if player is already linked to this user
        if (existingLink.hasPlayerId(bestMatch.getPlayerId())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Already Linked", 
                            "Player **" + bestMatch.getName() + "** is already linked to your account.")
            ).queue();
            return;
        }
        
        // Add the alt
        existingLink.addAltPlayerId(bestMatch.getPlayerId());
        linkedPlayerRepository.save(existingLink);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Alt Added", 
                        "Successfully added **" + bestMatch.getName() + "** as an alt account linked to your profile.")
        ).queue();
        
        logger.info("Added alt player to link: Discord User [{}], Alt Player [{}]", user.getId(), bestMatch.getName());
    }
    
    private void removeAltPlayer(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String playerName = event.getOption("player_name", OptionMapping::getAsString);
        
        // Acknowledge the command immediately
        event.deferReply().queue();
        
        // Check if the user is linked
        LinkedPlayer existingLink = linkedPlayerRepository.findByDiscordId(user.getIdLong());
        if (existingLink == null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not Linked", 
                            "You are not linked to any player.")
            ).queue();
            return;
        }
        
        // Search for the player
        List<Player> matchingPlayers = playerRepository.findByNameLike(playerName);
        
        if (matchingPlayers.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Player Not Found", 
                            "Could not find a player with name: **" + playerName + "**")
            ).queue();
            return;
        }
        
        // Find best match
        Player bestMatch = null;
        for (Player player : matchingPlayers) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                bestMatch = player;
                break;
            }
        }
        
        if (bestMatch == null) {
            bestMatch = matchingPlayers.get(0);
        }
        
        // Check if this is the main player
        if (existingLink.getMainPlayerId().equals(bestMatch.getPlayerId())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Cannot Remove Main", 
                            "You cannot remove your main player. Use `/link main` with a different player to change your main account.")
            ).queue();
            return;
        }
        
        // Check if player is linked as an alt
        if (!existingLink.getAltPlayerIds().contains(bestMatch.getPlayerId())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not an Alt", 
                            "Player **" + bestMatch.getName() + "** is not linked as an alt account to your profile.")
            ).queue();
            return;
        }
        
        // Remove the alt
        existingLink.removeAltPlayerId(bestMatch.getPlayerId());
        linkedPlayerRepository.save(existingLink);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Alt Removed", 
                        "Successfully removed **" + bestMatch.getName() + "** from your linked profile.")
        ).queue();
        
        logger.info("Removed alt player from link: Discord User [{}], Alt Player [{}]", user.getId(), bestMatch.getName());
    }
    
    private void listLinkedPlayers(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        
        // Check if the user is linked
        LinkedPlayer existingLink = linkedPlayerRepository.findByDiscordId(user.getIdLong());
        if (existingLink == null) {
            event.reply("You are not linked to any in-game players. Use `/link main` to link your account.").setEphemeral(true).queue();
            return;
        }
        
        // Build the list of linked players
        StringBuilder description = new StringBuilder();
        description.append("**Main**: ").append(existingLink.getMainPlayerName()).append("\n\n");
        
        if (!existingLink.getAltPlayerIds().isEmpty()) {
            description.append("**Alts**:\n");
            for (String altId : existingLink.getAltPlayerIds()) {
                Player altPlayer = playerRepository.findByPlayerId(altId);
                String altName = altPlayer != null ? altPlayer.getName() : "Unknown Player";
                description.append("• ").append(altName).append("\n");
            }
        } else {
            description.append("No alt accounts linked.");
        }
        
        event.replyEmbeds(
                EmbedUtils.infoEmbed("Your Linked Players", description.toString())
        ).queue();
    }
    
    private void showLinkedPlayerInfo(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        
        // Check if the target user is linked
        LinkedPlayer existingLink = linkedPlayerRepository.findByDiscordId(targetUser.getIdLong());
        if (existingLink == null) {
            event.reply("User " + targetUser.getAsMention() + " is not linked to any in-game players.").queue();
            return;
        }
        
        // Build the list of linked players
        StringBuilder description = new StringBuilder();
        description.append("**Main**: ").append(existingLink.getMainPlayerName()).append("\n\n");
        
        if (!existingLink.getAltPlayerIds().isEmpty()) {
            description.append("**Alts**:\n");
            for (String altId : existingLink.getAltPlayerIds()) {
                Player altPlayer = playerRepository.findByPlayerId(altId);
                String altName = altPlayer != null ? altPlayer.getName() : "Unknown Player";
                description.append("• ").append(altName).append("\n");
            }
        } else {
            description.append("No alt accounts linked.");
        }
        
        event.replyEmbeds(
                EmbedUtils.infoEmbed(
                        targetUser.getName() + "'s Linked Players",
                        description.toString()
                )
        ).queue();
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        String focusedOption = event.getFocusedOption().getName();
        
        // We only need to autocomplete player_name field
        if ("player_name".equals(focusedOption)) {
            String currentInput = event.getFocusedOption().getValue().toLowerCase();
            
            if ("remove".equals(subcommand)) {
                // For remove, only show alt accounts linked to the user
                LinkedPlayer linkedPlayer = linkedPlayerRepository.findByDiscordId(event.getUser().getIdLong());
                
                if (linkedPlayer != null && !linkedPlayer.getAltPlayerIds().isEmpty()) {
                    return linkedPlayer.getAltPlayerIds().stream()
                        .map(playerId -> playerRepository.findByPlayerId(playerId))
                        .filter(player -> player != null && player.getName().toLowerCase().contains(currentInput))
                        .map(player -> new Choice(player.getName(), player.getName()))
                        .limit(25)
                        .collect(Collectors.toList());
                }
                
                return List.of(); // No alts to remove
            } else {
                // For main and add, show all existing players matching the input
                List<Player> matchingPlayers = playerRepository.findByNameLike(currentInput);
                
                return matchingPlayers.stream()
                    .map(player -> new Choice(player.getName(), player.getName()))
                    .limit(25) // Discord has a max of 25 choices
                    .collect(Collectors.toList());
            }
        }
        
        return List.of(); // No suggestions for other options
    }
}