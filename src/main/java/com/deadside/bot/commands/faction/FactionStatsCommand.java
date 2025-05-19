package com.deadside.bot.commands.faction;

import com.deadside.bot.commands.Command;
import com.deadside.bot.db.models.Faction;
import com.deadside.bot.db.repositories.FactionRepository;
import com.deadside.bot.faction.FactionStatsSync;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command to display faction statistics
 */
public class FactionStatsCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(FactionStatsCommand.class);
    private final FactionRepository factionRepository;
    private final FactionStatsSync factionStatsSync;

    public FactionStatsCommand(FactionRepository factionRepository) {
        this.factionRepository = factionRepository;
        this.factionStatsSync = new FactionStatsSync();
    }

    @Override
    public String getName() {
        return "faction";
    }

    @Override
    public String getDescription() {
        return "Get information about factions";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();
        
        // Add option for faction name
        options.add(new OptionData(OptionType.STRING, "name", "The name of the faction to view stats for", false));
        
        // Add option for faction ranking type
        OptionData typeOption = new OptionData(OptionType.STRING, "type", "The type of faction stats to view", false);
        typeOption.addChoice("all", "all");
        typeOption.addChoice("xp", "xp");
        typeOption.addChoice("kills", "kills");
        typeOption.addChoice("deaths", "deaths");
        typeOption.addChoice("kd", "kd");
        options.add(typeOption);
        
        return options;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        String factionName = event.getOption("name", null, OptionMapping::getAsString);
        String type = event.getOption("type", "all", OptionMapping::getAsString);
        
        try {
            if (factionName != null) {
                // Display stats for a specific faction
                showFactionStats(event, factionName);
            } else {
                // Display faction rankings
                showFactionRankings(event, type);
            }
        } catch (Exception e) {
            logger.error("Error executing faction command: {}", e.getMessage(), e);
            event.reply("An error occurred while retrieving faction statistics.").setEphemeral(true).queue();
        }
    }
    
    /**
     * Display detailed stats for a specific faction
     */
    private void showFactionStats(SlashCommandInteractionEvent event, String factionName) {
        // First, let's make sure faction data is up-to-date
        factionStatsSync.updateAllFactions();
        
        Faction faction = factionRepository.findByName(factionName);
        if (faction == null) {
            event.reply("Faction not found: " + factionName).setEphemeral(true).queue();
            return;
        }
        
        EmbedBuilder embed = EmbedUtils.createDefaultEmbedBuilder()
                .setTitle("⚔️ " + faction.getName() + " - Faction Stats")
                .setColor(Color.BLUE)
                .addField("Level", String.valueOf(faction.getLevel()), true)
                .addField("Experience", String.valueOf(faction.getExperience()), true)
                .addField("Next Level", String.valueOf(faction.getExperienceNextLevel()), true)
                .addField("Members", String.valueOf(faction.getMemberCount()), true)
                .addField("Total Kills", String.valueOf(faction.getTotalKills()), true)
                .addField("Total Deaths", String.valueOf(faction.getTotalDeaths()), true)
                .addField("K/D Ratio", String.format("%.2f", faction.getKdRatio()), true)
                .addField("Territory Control", String.valueOf(faction.getTerritoryControl()) + "%", true)
                .addField("Faction Founded", new java.util.Date(faction.getCreatedAt()).toString(), false);
                
        if (faction.getDescription() != null && !faction.getDescription().isEmpty()) {
            embed.setDescription(faction.getDescription());
        }
        
        if (faction.getLogoUrl() != null && !faction.getLogoUrl().isEmpty()) {
            embed.setThumbnail(faction.getLogoUrl());
        }
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    /**
     * Display faction rankings based on the specified type
     */
    private void showFactionRankings(SlashCommandInteractionEvent event, String type) {
        // First, let's make sure faction data is up-to-date
        factionStatsSync.updateAllFactions();
        
        // Get factions using isolation-aware approach
        long guildId = event.getGuild().getIdLong();
        com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
        List<Faction> factions;
        try {
            factions = factionRepository.findAllByGuildId(guildId);
        } finally {
            com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
        }
        if (factions.isEmpty()) {
            event.reply("No factions found.").setEphemeral(true).queue();
            return;
        }
        
        // Sort factions based on the requested ranking type
        switch (type.toLowerCase()) {
            case "xp":
                Collections.sort(factions, (f1, f2) -> Integer.compare(f2.getExperience(), f1.getExperience()));
                break;
            case "kills":
                Collections.sort(factions, (f1, f2) -> Integer.compare(f2.getTotalKills(), f1.getTotalKills()));
                break;
            case "deaths":
                Collections.sort(factions, (f1, f2) -> Integer.compare(f2.getTotalDeaths(), f1.getTotalDeaths()));
                break;
            case "kd":
                Collections.sort(factions, (f1, f2) -> Double.compare(f2.getKdRatio(), f1.getKdRatio()));
                break;
            default:
                // Default to XP ranking
                Collections.sort(factions, (f1, f2) -> Integer.compare(f2.getExperience(), f1.getExperience()));
                break;
        }
        
        // Create the rankings embed
        EmbedBuilder embed = EmbedUtils.createDefaultEmbedBuilder()
                .setTitle("Faction Rankings - " + capitalizeFirstLetter(type))
                .setColor(new Color(255, 215, 0)); // Gold color
        
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < Math.min(10, factions.size()); i++) {
            Faction faction = factions.get(i);
            
            // Format the ranking entry based on type
            String value;
            switch (type.toLowerCase()) {
                case "xp":
                    value = String.format("%,d XP (Lvl %d)", faction.getExperience(), faction.getLevel());
                    break;
                case "kills":
                    value = String.format("%,d kills", faction.getTotalKills());
                    break;
                case "deaths":
                    value = String.format("%,d deaths", faction.getTotalDeaths());
                    break;
                case "kd":
                    value = String.format("%.2f K/D", faction.getKdRatio());
                    break;
                default:
                    value = String.format("%,d XP (Lvl %d)", faction.getExperience(), faction.getLevel());
                    break;
            }
            
            // Add to ranking list
            description.append(String.format("**%d.** %s - %s\n", (i + 1), faction.getName(), value));
        }
        
        embed.setDescription(description.toString());
        event.replyEmbeds(embed.build()).queue();
    }
    
    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
}