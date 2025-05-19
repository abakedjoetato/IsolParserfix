package com.deadside.bot.commands.stats;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command to show weapon statistics in Deadside
 */
public class WeaponStatsCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(WeaponStatsCommand.class);
    
    // List of weapon types for autocomplete
    private static final List<String> WEAPON_TYPES = Arrays.asList(
        "assault_rifle", "smg", "pistol", "sniper", "shotgun", "melee", "throwable"
    );
    
    // List of specific weapons for autocomplete
    private static final List<String> WEAPONS = Arrays.asList(
        "AK-47", "M4A1", "M16A4", "AK-74", "AKS-74U", "VSS", "SVD", "M24", 
        "Mosin", "RPK", "Hunting Rifle", "MP5", "MP7", "UMP-45", "Vector", 
        "Glock 17", "M1911", "Colt Python", "Desert Eagle", "Makarov", 
        "Remington 870", "Saiga-12", "Double Barrel", "Combat Knife", "Axe",
        "Hammer", "Baseball Bat", "Crowbar", "Machete", "Frag Grenade", "Smoke Grenade"
    );

    @Override
    public String getName() {
        return "weaponstats";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("weaponstats", "View weapon statistics")
                .addOptions(
                        new OptionData(OptionType.STRING, "weapon", "The name of the weapon", true)
                                .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "type", "The type of weapon to filter by", false)
                                .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "server", "The server to check stats for (default: all servers)", false)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        try {
            String weapon = event.getOption("weapon", "", o -> o.getAsString());
            String type = event.getOption("type", "", o -> o.getAsString());
            String server = event.getOption("server", "All servers", o -> o.getAsString());
            
            // Create a placeholder response with weapon stats
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Weapon Statistics: " + weapon)
                    .setDescription("Statistics for " + weapon + " across " + server + "\n\n" +
                                    "*More detailed weapon stats will be implemented soon.*")
                    .setColor(EmbedUtils.EMERALD_GREEN)
                    .setThumbnail(EmbedUtils.WEAPON_STATS_ICON)
                    .addField("Kills", "217", true)
                    .addField("Headshots", "73 (33.6%)", true)
                    .addField("Average Kill Distance", "124m", true)
                    .addField("Longest Kill", "543m", true)
                    .addField("Damage Per Shot", "37-42", true)
                    .addField("Popularity Rank", "#3", true)
                    .setFooter(EmbedUtils.STANDARD_FOOTER)
                    .setTimestamp(java.time.Instant.now());
            
            // Send the embed
            event.getHook().sendMessageEmbeds(embed.build()).queue();
            
            logger.info("Sent weapon stats for {} of type {} in server {}", weapon, type, server);
        } catch (Exception e) {
            logger.error("Error executing weapon stats command", e);
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Error",
                    "An error occurred while retrieving weapon statistics: " + e.getMessage()
            )).queue();
        }
    }

    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String option = event.getFocusedOption().getName();
        String value = event.getFocusedOption().getValue().toLowerCase();
        
        if (option.equals("weapon")) {
            List<Choice> choices = new ArrayList<>();
            int count = 0;
            
            for (String weapon : WEAPONS) {
                if (weapon.toLowerCase().contains(value)) {
                    choices.add(new Choice(weapon, weapon));
                    count++;
                    if (count >= 25) break; // Discord limits choices to 25
                }
            }
            
            return choices;
        } else if (option.equals("type")) {
            List<Choice> choices = new ArrayList<>();
            
            for (String type : WEAPON_TYPES) {
                if (type.contains(value)) {
                    choices.add(new Choice(type, type));
                }
            }
            
            return choices;
        }
        
        return List.of();
    }
}