package com.deadside.bot.commands.server;

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
import java.util.Random;
import java.awt.Color;
import java.time.Instant;

/**
 * Command to show server status and information
 */
public class ServerStatusCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(ServerStatusCommand.class);
    
    // Sample server list for autocomplete
    private static final List<String> SERVER_LIST = Arrays.asList(
        "Emerald EU", "Emerald US", "Emerald Asia", "Deadside Official EU", 
        "Deadside Official US", "Deadside Official Asia"
    );
    
    // Random generator for simulating player counts
    private static final Random random = new Random();

    @Override
    public String getName() {
        return "serverstatus";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("serverstatus", "View status of Deadside servers")
                .addOptions(
                        new OptionData(OptionType.STRING, "server", "Select a specific server to check", false)
                                .setAutoComplete(true)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        try {
            String serverName = event.getOption("server", null, o -> o.getAsString());
            
            if (serverName == null) {
                // Show status of all servers
                List<EmbedBuilder> serverStatusEmbeds = new ArrayList<>();
                
                for (String server : SERVER_LIST) {
                    serverStatusEmbeds.add(createServerStatusEmbed(server));
                }
                
                // Send the first embed, which includes overall stats
                EmbedBuilder overallEmbed = new EmbedBuilder()
                        .setTitle("Deadside Server Status")
                        .setDescription("Current status for all monitored Deadside servers")
                        .setColor(EmbedUtils.EMERALD_GREEN)
                        .addField("Total Servers", String.valueOf(SERVER_LIST.size()), true)
                        .addField("Total Players", "172/384", true)
                        .addField("Most Popular", "Emerald EU (47/64)", true)
                        .setFooter(EmbedUtils.STANDARD_FOOTER)
                        .setTimestamp(Instant.now());
                
                event.getHook().sendMessageEmbeds(overallEmbed.build()).queue();
                
                // If there are only a few servers, send detailed embeds for each
                if (SERVER_LIST.size() <= 3) {
                    for (EmbedBuilder embed : serverStatusEmbeds) {
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }
                } else {
                    // Just send a message that they can check individual servers
                    event.getHook().sendMessage("Use `/serverstatus server:<name>` to view detailed information for a specific server.").queue();
                }
            } else {
                // Show status for the specified server
                EmbedBuilder embed = createServerStatusEmbed(serverName);
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }
            
            logger.info("Server status command executed for server: {}", serverName != null ? serverName : "all");
        } catch (Exception e) {
            logger.error("Error executing server status command", e);
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Error",
                    "An error occurred while retrieving server status: " + e.getMessage()
            )).queue();
        }
    }
    
    /**
     * Create a server status embed for a specific server
     */
    private EmbedBuilder createServerStatusEmbed(String serverName) {
        // This would typically fetch real data from a server status API or database
        // For now, using simulated data
        int maxPlayers = 64;
        int currentPlayers = random.nextInt(maxPlayers);
        boolean isOnline = true;
        String map = "Blackzone";
        String ip = "127.0.0.1";
        int port = 7000 + random.nextInt(1000);
        
        // Create a color based on player count (green when full, red when empty, gradient in between)
        float percentFull = (float) currentPlayers / maxPlayers;
        Color serverColor = new Color(
                (int) (255 * (1 - percentFull)),  // Red component
                (int) (200 * percentFull),        // Green component
                20                                // Blue component
        );
        
        return new EmbedBuilder()
                .setTitle("Server Status: " + serverName)
                .setDescription(isOnline ? "🟢 **ONLINE**" : "🔴 **OFFLINE**")
                .setColor(isOnline ? serverColor : Color.RED)
                .addField("Players", currentPlayers + "/" + maxPlayers, true)
                .addField("Map", map, true)
                .addField("IP:Port", ip + ":" + port, true)
                .addField("Uptime", "3d 7h 42m", true)
                .addField("Restarts", "Every 6 hours", true)
                .addField("Next Restart", "2h 18m", true)
                .setFooter(EmbedUtils.STANDARD_FOOTER)
                .setTimestamp(Instant.now());
    }

    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String option = event.getFocusedOption().getName();
        String value = event.getFocusedOption().getValue().toLowerCase();
        
        if (option.equals("server")) {
            List<Choice> choices = new ArrayList<>();
            
            for (String server : SERVER_LIST) {
                if (server.toLowerCase().contains(value)) {
                    choices.add(new Choice(server, server));
                }
            }
            
            return choices;
        }
        
        return List.of();
    }
}