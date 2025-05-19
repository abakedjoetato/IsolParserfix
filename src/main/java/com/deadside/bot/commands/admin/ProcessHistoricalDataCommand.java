package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.KillfeedParser;
import com.deadside.bot.sftp.SftpConnector;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

/**
 * Command to process all historical data for a server
 * This will reparse all CSV files from the beginning
 */
public class ProcessHistoricalDataCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(ProcessHistoricalDataCommand.class);
    private final GameServerRepository serverRepository;
    
    public ProcessHistoricalDataCommand() {
        this.serverRepository = new GameServerRepository();
    }
    
    @Override
    public String getName() {
        return "processhistorical";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "Process all historical data for a server (admin only)")
                .addOption(OptionType.STRING, "server", "Server name", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        try {
            String serverName = event.getOption("server", "", OptionMapping::getAsString);
            if (serverName.isEmpty()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "Server name is required")
                ).queue();
                return;
            }
            
            GameServer server = serverRepository.findByGuildIdAndName(event.getGuild().getIdLong(), serverName);
            if (server == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "Server not found: " + serverName)
                ).queue();
                return;
            }
            
            // Process the request in a separate thread to avoid blocking
            new Thread(() -> {
                try {
                    // Let the user know we're starting
                    event.getHook().sendMessageEmbeds(
                            new EmbedBuilder()
                                    .setTitle("Historical Data Processing Started")
                                    .setDescription("Processing historical data for server: " + serverName)
                                    .setColor(Color.YELLOW)
                                    .build()
                    ).queue();
                    
                    // Create parsers with actual JDA instance
                    JDA jda = event.getJDA();
                    KillfeedParser killfeedParser = new KillfeedParser(jda);
                    DeadsideCsvParser csvParser = new DeadsideCsvParser(jda, new SftpConnector(), new PlayerRepository(), new GameServerRepository());
                    
                    // Process historical killfeed data
                    int kills = killfeedParser.processServer(server, true);
                    
                    // Process historical death logs
                    int deaths = csvParser.processDeathLogs(server, true);
                    
                    // Update the server to save the progress
                    serverRepository.save(server);
                    
                    // Let the user know we're done
                    event.getHook().sendMessageEmbeds(
                            new EmbedBuilder()
                                    .setTitle("Historical Data Processing Complete")
                                    .setDescription("Processed **" + kills + " kills** and **" + deaths + " deaths** for server: " + serverName)
                                    .setColor(Color.GREEN)
                                    .build()
                    ).queue();
                    
                    logger.info("Historical data processing complete for server: {}. Processed {} kills and {} deaths", 
                            serverName, kills, deaths);
                } catch (Exception e) {
                    logger.error("Error processing historical data for server: {}", serverName, e);
                    event.getHook().sendMessageEmbeds(
                            EmbedUtils.errorEmbed("Error", "Error processing historical data: " + e.getMessage())
                    ).queue();
                }
            }).start();
            
        } catch (Exception e) {
            logger.error("Error executing processhistorical command", e);
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Error", "Error: " + e.getMessage())
            ).queue();
        }
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        return List.of();
    }
}