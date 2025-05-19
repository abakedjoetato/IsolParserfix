package com.deadside.bot.commands.faction;

import com.deadside.bot.commands.Command;
import com.deadside.bot.db.models.Faction;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.FactionRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to create a new faction
 */
public class FactionCreateCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(FactionCreateCommand.class);
    private final FactionRepository factionRepository;
    private final PlayerRepository playerRepository;

    public FactionCreateCommand(FactionRepository factionRepository, PlayerRepository playerRepository) {
        this.factionRepository = factionRepository;
        this.playerRepository = playerRepository;
    }

    @Override
    public String getName() {
        return "factioncreate";
    }

    @Override
    public String getDescription() {
        return "Create a new faction";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();
        
        options.add(new OptionData(OptionType.STRING, "name", "Name of the faction", true));
        options.add(new OptionData(OptionType.STRING, "description", "Description of the faction", false));
        options.add(new OptionData(OptionType.STRING, "logo", "URL to faction logo (must be a valid image URL)", false));
        options.add(new OptionData(OptionType.STRING, "tag", "Short tag for the faction (max 5 characters)", false));
        
        return options;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        User user = event.getUser();
        String factionName = event.getOption("name", "", OptionMapping::getAsString);
        String description = event.getOption("description", "", OptionMapping::getAsString);
        String logoUrl = event.getOption("logo", "", OptionMapping::getAsString);
        String tag = event.getOption("tag", "", OptionMapping::getAsString);

        // Validate faction name
        if (factionName.isEmpty() || factionName.length() < 3 || factionName.length() > 32) {
            event.reply("Faction name must be between 3 and 32 characters.").setEphemeral(true).queue();
            return;
        }

        // Validate tag
        if (!tag.isEmpty() && tag.length() > 5) {
            event.reply("Faction tag must be at most 5 characters.").setEphemeral(true).queue();
            return;
        }

        try {
            // Check if player exists and is linked
            Player player = playerRepository.findByDiscordId(user.getId());
            if (player == null) {
                event.reply("You need to link your Deadside account first. Use the /link command.").setEphemeral(true).queue();
                return;
            }

            // Check if player is already in a faction
            if (player.getFactionId() != null) {
                event.reply("You are already a member of a faction. Leave your current faction first.").setEphemeral(true).queue();
                return;
            }

            // Check if faction name is already taken
            if (factionRepository.findByName(factionName) != null) {
                event.reply("A faction with that name already exists.").setEphemeral(true).queue();
                return;
            }

            // Check if faction tag is already taken
            if (!tag.isEmpty() && factionRepository.findByTag(tag) != null) {
                event.reply("A faction with that tag already exists.").setEphemeral(true).queue();
                return;
            }

            // Create the faction
            Faction faction = new Faction();
            faction.setId(new org.bson.types.ObjectId());
            faction.setName(factionName);
            faction.setDescription(description.isEmpty() ? null : description);
            faction.setLogoUrl(logoUrl.isEmpty() ? null : logoUrl);
            faction.setTag(tag.isEmpty() ? null : tag);
            faction.setCreatedAt(System.currentTimeMillis());
            faction.setLevel(1);
            faction.setExperience(0);
            faction.setExperienceNextLevel(1000); // Initial XP needed for level 2
            faction.setCreatorId(player.getId());
            faction.setTerritoryControl(0);
            faction.setTotalKills(0);
            faction.setTotalDeaths(0);
            
            // Save the faction
            factionRepository.save(faction);
            
            // Add the creator as a member and leader
            player.setFactionId(faction.getId());
            player.setFactionJoinDate(Instant.now());
            player.setFactionLeader(true);
            player.setFactionOfficer(true); // Leaders are also officers
            playerRepository.save(player);
            
            // Send success message
            String successMessage = "Congratulations! You have successfully created the faction " + faction.getName() + 
                                    "\nYou are now its leader. Use /faction commands to manage your faction.";
                                    
            event.replyEmbeds(
                EmbedUtils.factionEmbed("Faction Created: " + faction.getName(),
                    successMessage, Color.GREEN)
            ).queue();
            
            logger.info("User {} created faction {}", user.getId(), factionName);
        } catch (Exception e) {
            logger.error("Error creating faction: {}", e.getMessage(), e);
            event.reply("An error occurred while creating the faction: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
}