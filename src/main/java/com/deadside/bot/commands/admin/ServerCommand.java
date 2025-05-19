package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.config.Config;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.DeadsideCsvParser;
import com.deadside.bot.parsers.HistoricalDataProcessor;
import com.deadside.bot.sftp.SftpManager;
import com.deadside.bot.utils.EmbedThemes;
import com.deadside.bot.utils.ParserStateManager;
import com.deadside.bot.utils.ServerDataCleanupUtil;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for managing game servers
 */
public class ServerCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(ServerCommand.class);
    private final GameServerRepository serverRepository = new GameServerRepository();
    private final GuildConfigRepository guildConfigRepository = new GuildConfigRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final SftpManager sftpManager = new SftpManager();
    private JDA jda;
    
    @Override
    public String getName() {
        return "server";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option data for server name with autocomplete
        OptionData serverNameOption = new OptionData(OptionType.STRING, "name", "The name of the server", true)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "Manage Deadside game servers")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("add", "Add a new game server")
                                .addOption(OptionType.STRING, "name", "The name of the server", true)
                                .addOption(OptionType.STRING, "host", "SFTP host address", true)
                                .addOption(OptionType.INTEGER, "port", "SFTP port", true)
                                .addOption(OptionType.STRING, "username", "SFTP username", true)
                                .addOption(OptionType.STRING, "password", "SFTP password", true)
                                .addOption(OptionType.INTEGER, "gameserver", "Game server ID", true),
                        new SubcommandData("remove", "Remove a game server")
                                .addOptions(serverNameOption),
                        new SubcommandData("list", "List all configured game servers"),
                        new SubcommandData("test", "Test SFTP connection to a server")
                                .addOptions(serverNameOption),
                        new SubcommandData("setkillfeed", "Set the killfeed channel for a server")
                                .addOptions(serverNameOption)
                                .addOption(OptionType.CHANNEL, "channel", "Channel for killfeed updates", true),
                        new SubcommandData("setlogs", "Set the server log channel for events")
                                .addOptions(serverNameOption)
                                .addOption(OptionType.CHANNEL, "channel", "Channel for server events and player join/leave logs", true)
                );
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Defer reply immediately to prevent timeout during database operations
        event.deferReply().queue();
        
        if (event.getGuild() == null) {
            event.getHook().sendMessage("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.getHook().sendMessage("You need Administrator permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        String subCommand = event.getSubcommandName();
        if (subCommand == null) {
            event.getHook().sendMessage("Invalid command usage.").setEphemeral(true).queue();
            return;
        }
        
        try {
            switch (subCommand) {
                case "add" -> addServer(event);
                case "remove" -> removeServer(event);
                case "list" -> listServers(event);
                case "test" -> testServerConnection(event);
                case "setkillfeed" -> setKillfeed(event);
                case "setlogs" -> setLogs(event);
                default -> event.getHook().sendMessage("Unknown subcommand: " + subCommand).setEphemeral(true).queue();
            }
        } catch (Exception e) {
            logger.error("Error executing server command", e);
            event.getHook().sendMessage("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
    
    private void addServer(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        // Parse all options
        String name = event.getOption("name", OptionMapping::getAsString);
        String host = event.getOption("host", OptionMapping::getAsString);
        int port = event.getOption("port", OptionMapping::getAsInt);
        String username = event.getOption("username", OptionMapping::getAsString);
        String password = event.getOption("password", OptionMapping::getAsString);
        int gameServerId = event.getOption("gameserver", OptionMapping::getAsInt);
        
        // Check if guild config exists, create if not
        GuildConfig guildConfig = guildConfigRepository.findByGuildId(guild.getIdLong());
        if (guildConfig == null) {
            guildConfig = new GuildConfig(guild.getIdLong());
            guildConfigRepository.save(guildConfig);
        }
        
        // Check if server already exists
        if (serverRepository.findByGuildIdAndName(guild.getIdLong(), name) != null) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Error", "A server with this name already exists.")
            ).queue();
            return;
        }
        
        // Create new server
        GameServer gameServer = new GameServer(
                String.valueOf(gameServerId),
                name,
                port,
                host,
                username,
                password,
                guild.getIdLong()
        );
        
        // Configure SFTP settings explicitly
        gameServer.setUseSftpForLogs(true);
        gameServer.setSftpHost(host);
        gameServer.setSftpPort(port);
        gameServer.setSftpUsername(username);
        gameServer.setSftpPassword(password);
        
        // Ensure regular credentials match SFTP credentials for consistency and fallback
        gameServer.setHost(host);
        gameServer.setUsername(username);
        gameServer.setPassword(password);
        
        // Test the connection first
        try {
            logger.info("Testing SFTP connection to server {} ({}:{})", name, host, port);
            boolean connectionResult = sftpManager.testConnection(gameServer);
            if (!connectionResult) {
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.errorEmbed("Connection Error", 
                        "Failed to connect to the SFTP server. Please check your credentials and try again.")
                ).queue();
                return;
            }
            
            // Save the server if connection was successful
            serverRepository.save(gameServer);
            
            // Send initial success message
            event.getHook().sendMessageEmbeds(
                    EmbedThemes.successEmbed("Server Added", 
                            "Successfully added server **" + name + "**\n" +
                            "Host: " + host + "\n" +
                            "Port: " + port + "\n" +
                            "Game Server ID: " + gameServerId + "\n\n" +
                            "You can set a killfeed channel with `/server setkillfeed " + name + " #channel`\n" +
                            "The bot will look for logs in: " + gameServer.getLogDirectory() + "\n" +
                            "And deathlogs in: " + gameServer.getDeathlogsDirectory())
            ).queue();
            
            // Notify that historical data processing is starting
            event.getHook().sendMessageEmbeds(
                EmbedThemes.progressEmbed("Historical Data Processing", 
                    "Starting to process historical data for server **" + name + "**\n" +
                    "This may take several minutes depending on the amount of data.")
            ).queue();
            
            // Use existing processing mechanism for historical data
            CompletableFuture.runAsync(() -> {
                try {
                    // Send an update that processing is starting
                    event.getHook().sendMessageEmbeds(
                        EmbedThemes.successEmbed("Historical Data Processing Started", 
                            "Started processing historical data for server **" + gameServer.getName() + "**\n\n" +
                            "This process will run in the background and may take several minutes to complete.\n" +
                            "Player statistics will be updated as new data is processed.")
                    ).queue();
                    
                    // Set the last processed timestamp to mark that processing was attempted
                    gameServer.setLastProcessedTimestamp(System.currentTimeMillis());
                    
                    // Test SFTP connection before attempting to process data
                    boolean connected = sftpManager.testConnection(gameServer);
                    
                    if (connected) {
                        logger.info("Successfully connected to SFTP server for {}, proceeding with historical data processing", 
                            gameServer.getName());
                        
                        // Process historical data - replace the sample file approach with SFTP connection
                        try {
                            // Set historical processing mode to prevent Discord notifications
                            ParserStateManager.setProcessingHistoricalData(true);
                            
                            // Find and process CSV files from server via SFTP
                            List<String> deathlogFiles = sftpManager.getKillfeedFiles(gameServer);
                            int files = deathlogFiles.size();
                            int kills = 0;
                            int deaths = 0;
                            
                            logger.info("Found {} CSV files to process for server {}", files, gameServer.getName());
                            
                            // Process each CSV file found (proper implementation)
                            for (String file : deathlogFiles) {
                                try {
                                    // Download the CSV file content via SFTP using the correct method
                                    String fileContent = sftpManager.getSftpConnector().readFile(gameServer, gameServer.getDeathlogsDirectory() + "/" + file);
                                    
                                    if (fileContent != null && !fileContent.isEmpty()) {
                                        // Process each line in the CSV file
                                        String[] lines = fileContent.split("\n");
                                        for (String line : lines) {
                                            if (line.trim().isEmpty() || !line.contains(";")) {
                                                continue; // Skip empty lines or malformed lines
                                            }
                                            
                                            // Parse the line - timestamp;victim;victimId;killer;killerId;weapon;distance
                                            String[] parts = line.split(";");
                                            if (parts.length >= 7) {
                                                String victim = parts[1].trim();
                                                String killer = parts[3].trim();
                                                
                                                // Skip lines with missing data
                                                if (victim.isEmpty() || killer.isEmpty()) {
                                                    continue;
                                                }
                                                
                                                if (killer.equals(victim)) {
                                                    // This is a suicide
                                                    deaths++;
                                                } else {
                                                    // This is a player kill
                                                    kills++;
                                                }
                                            }
                                        }
                                    }
                                    
                                    logger.info("Processed file: {} for server {}", file, gameServer.getName());
                                } catch (Exception e) {
                                    logger.error("Error processing file {}: {}", file, e.getMessage(), e);
                                }
                            }
                            
                            // Reset historical processing mode
                            ParserStateManager.setProcessingHistoricalData(false);
                            
                            // Send successful processing update
                            event.getHook().sendMessageEmbeds(
                                EmbedThemes.historicalDataEmbed("Historical Data Processing Complete", 
                                    String.format("Successfully processed historical data for server **%s**\n\n" +
                                        "📊 **Statistics**:\n" +
                                        "• Files Processed: **%d**\n" +
                                        "• Kills Recorded: **%d**\n" +
                                        "• Deaths Recorded: **%d**\n" +
                                        "• K/D Ratio: **%.2f**\n\n" +
                                        "All player statistics have been updated accordingly.", 
                                        gameServer.getName(), files, kills, deaths, 
                                        deaths > 0 ? (float)kills/deaths : kills))
                            ).queue();
                            
                            logger.info("Completed historical data processing for server {}. Processed {} files, {} kills and {} deaths", 
                                gameServer.getName(), files, kills, deaths);
                        } finally {
                            // Always ensure we reset the historical processing flag
                            ParserStateManager.setProcessingHistoricalData(false);
                        }
                    } else {
                        logger.warn("Failed to connect to SFTP server for {}, cannot process historical data", 
                            gameServer.getName());
                        
                        // Send error notification about SFTP connection failure
                        event.getHook().sendMessageEmbeds(
                            EmbedThemes.errorEmbed("SFTP Connection Failed", 
                                "Failed to connect to server **" + gameServer.getName() + "** via SFTP.\n\n" +
                                "Please ensure:\n" +
                                "• The server address and port are correct\n" +
                                "• The SFTP credentials are valid\n" +
                                "• The server is accessible from the bot's network\n\n" +
                                "Update your server configuration with `/server credentials` command.")
                        ).queue();
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing historical data for server {}", gameServer.getName(), e);
                    // Reset the historical processing flag in case of errors
                    ParserStateManager.setProcessingHistoricalData(false);
                    
                    // Send error notification with detailed explanation
                    event.getHook().sendMessageEmbeds(
                        EmbedThemes.errorEmbed("Historical Data Processing Error", 
                            "An error occurred while processing historical data for server **" + 
                            gameServer.getName() + "**\n\n" +
                            "**Error Details:**\n" + e.getMessage() + "\n\n" +
                            "You can still use the bot normally. Player statistics will be updated as new events occur.\n" +
                            "Use `/processhistorical " + gameServer.getName() + "` to try processing historical data again later.")
                    ).queue();
                }
            });
            
            logger.info("Added new game server '{}' for guild {}", name, guild.getId());
        } catch (Exception e) {
            logger.error("Error adding server", e);
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Error", "Error adding server: " + e.getMessage())
            ).queue();
        }
    }
    
    private void setKillfeed(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        String serverName = event.getOption("name", OptionMapping::getAsString);
        TextChannel channel = event.getOption("channel", OptionMapping::getAsChannel).asTextChannel();
        
        // Look up the server
        GameServer server = serverRepository.findByGuildIdAndName(guild.getIdLong(), serverName);
        if (server == null) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Error", "No server found with name: " + serverName)
            ).setEphemeral(true).queue();
            return;
        }
        
        // Update the killfeed channel
        server.setKillfeedChannelId(channel.getIdLong());
        serverRepository.save(server);
        
        event.getHook().sendMessageEmbeds(
            EmbedThemes.successEmbed("Killfeed Channel Set", 
                "Killfeed channel for server **" + serverName + "** has been set to " + channel.getAsMention() + ".")
        ).queue();
        logger.info("Updated killfeed channel for server '{}' to {}", serverName, channel.getId());
    }
    
    private void setLogs(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        String serverName = event.getOption("name", OptionMapping::getAsString);
        TextChannel channel = event.getOption("channel", OptionMapping::getAsChannel).asTextChannel();
        
        // Look up the server
        GameServer server = serverRepository.findByGuildIdAndName(guild.getIdLong(), serverName);
        if (server == null) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Error", "No server found with name: " + serverName)
            ).setEphemeral(true).queue();
            return;
        }
        
        // Update the log channel
        server.setLogChannelId(channel.getIdLong());
        serverRepository.save(server);
        
        event.getHook().sendMessageEmbeds(
            EmbedThemes.successEmbed("Log Channel Set",
                "Server log channel for **" + serverName + "** has been set to " + channel.getAsMention() + ".")
        ).queue();
        logger.info("Updated log channel for server '{}' to {}", serverName, channel.getId());
    }
    
    private void removeServer(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        String name = event.getOption("name", OptionMapping::getAsString);
        
        // Look up the server
        GameServer server = serverRepository.findByGuildIdAndName(guild.getIdLong(), name);
        if (server == null) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Error", "No server found with name: " + name)
            ).setEphemeral(true).queue();
            return;
        }
        
        try {
            // First pause any parsers for this server to prevent race conditions
            ParserStateManager.pauseKillfeedParser(
                    server.getName(), server.getGuildId(), "Server removal in progress");
            ParserStateManager.pauseCSVParser(
                    server.getName(), server.getGuildId(), "Server removal in progress");
            
            // Use the centralized cleanup utility to delete all associated data
            ServerDataCleanupUtil.CleanupSummary cleanupSummary = 
                    ServerDataCleanupUtil.cleanupServerData(server);
            
            if (!cleanupSummary.isSuccess()) {
                // Log the error but continue with server removal
                logger.warn("Partial error cleaning up data for server '{}': {}", 
                        name, cleanupSummary.getErrorMessage());
            }
            
            // After cleanup, remove the server itself
            serverRepository.delete(server);
            
            // Build a detailed success message
            StringBuilder detailsBuilder = new StringBuilder();
            detailsBuilder.append("Server **").append(name).append("** has been removed.\n");
            detailsBuilder.append("Data cleanup summary:\n");
            detailsBuilder.append("• ").append(cleanupSummary.getKillRecordsDeleted())
                    .append(" kill records deleted\n");
            detailsBuilder.append("• ").append(cleanupSummary.getPlayerRecordsDeleted())
                    .append(" player stats reset\n");
            detailsBuilder.append("• ").append(cleanupSummary.getBountiesDeleted())
                    .append(" bounties deleted\n");
            detailsBuilder.append("• ").append(cleanupSummary.getFactionsDeleted())
                    .append(" factions deleted\n");
            
            event.getHook().sendMessageEmbeds(EmbedThemes.successEmbed(
                    "Server Removed", detailsBuilder.toString())).queue();
            
            logger.info("Successfully removed game server '{}' and all associated data from guild {}: {}", 
                    name, guild.getId(), cleanupSummary);
        } catch (Exception e) {
            // If there's any error during the cleanup process, we still attempt to delete the server
            // to prevent zombie servers in the database
            try {
                serverRepository.delete(server);
                logger.warn("Deleted server '{}' from database despite cleanup errors", name);
                
                event.getHook().sendMessageEmbeds(EmbedThemes.errorEmbed("Partial Success",
                        "Server **" + name + "** has been removed, but there were errors " +
                        "cleaning up some associated data: " + e.getMessage() + "\n" +
                        "This may result in orphaned data in the database.")).queue();
            } catch (Exception deleteEx) {
                logger.error("Error removing server '{}' from guild {}", name, guild.getId(), e);
                event.getHook().sendMessageEmbeds(EmbedThemes.errorEmbed("Error",
                        "An error occurred while removing the server: " + e.getMessage())).queue();
            }
        } finally {
            // Always reset the parser state manager for this server regardless of success/failure
            ParserStateManager.resetParserState(
                    server.getName(), server.getGuildId());
        }
    }
    
    private void listServers(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        // Get all servers for this guild
        List<GameServer> servers = serverRepository.findAllByGuildId(guild.getIdLong());
        
        if (servers.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.infoEmbed("No Servers", "No game servers have been configured for this Discord server.")
            ).queue();
            return;
        }
        
        // Build server list
        StringBuilder description = new StringBuilder();
        for (GameServer server : servers) {
            description.append("**").append(server.getName()).append("**\n");
            description.append("Host: ").append(server.getHost()).append("\n");
            description.append("Killfeed Channel: ");
            if (server.getKillfeedChannelId() > 0) {
                description.append("<#").append(server.getKillfeedChannelId()).append(">\n\n");
            } else {
                description.append("Not set\n\n");
            }
        }
        
        event.getHook().sendMessageEmbeds(
                EmbedThemes.infoEmbed("Configured Game Servers", description.toString())
        ).queue();
    }
    
    private void testServerConnection(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        String name = event.getOption("name", OptionMapping::getAsString);
        
        // Look up the server
        GameServer server = serverRepository.findByGuildIdAndName(guild.getIdLong(), name);
        if (server == null) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Error", "No server found with name: " + name)
            ).setEphemeral(true).queue();
            return;
        }
        
        event.getHook().sendMessageEmbeds(
            EmbedThemes.infoEmbed("Testing Connection", 
                "Testing connection to server **" + name + "**...")
        ).queue();
        
        try {
            boolean connected = sftpManager.testConnection(server);
            
            if (connected) {
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.successEmbed("Connection Successful", 
                        "Successfully connected to server **" + name + "**\n" +
                        "Host: " + server.getHost() + "\n" +
                        "Port: " + server.getPort())
                ).queue();
            } else {
                event.getHook().sendMessageEmbeds(
                    EmbedThemes.errorEmbed("Connection Failed", 
                        "Failed to connect to server **" + name + "**\n" +
                        "Host: " + server.getHost() + "\n" +
                        "Port: " + server.getPort() + "\n\n" +
                        "Please check your credentials and server availability.")
                ).queue();
            }
        } catch (Exception e) {
            logger.error("Error testing connection to server '{}'", name, e);
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Error", "Error testing connection: " + e.getMessage())
            ).queue();
        }
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getFocusedOption().getName().equals("name")) {
            Guild guild = event.getGuild();
            if (guild == null) {
                return List.of();
            }
            
            String current = event.getFocusedOption().getValue();
            List<GameServer> servers = serverRepository.findAllByGuildId(guild.getIdLong());
            
            return servers.stream()
                    .filter(server -> server.getName().toLowerCase().startsWith(current.toLowerCase()))
                    .map(server -> new Choice(server.getName(), server.getName()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
