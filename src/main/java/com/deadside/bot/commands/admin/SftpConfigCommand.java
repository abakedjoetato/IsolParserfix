package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.sftp.SftpManager;
import com.deadside.bot.utils.EmbedThemes;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for managing SFTP configuration for servers
 * Allows updating and testing SFTP connections
 */
public class SftpConfigCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(SftpConfigCommand.class);
    private final GameServerRepository serverRepository;
    private final SftpManager sftpManager;
    
    public SftpConfigCommand() {
        this.serverRepository = new GameServerRepository();
        this.sftpManager = new SftpManager();
    }
    
    @Override
    public String getName() {
        return "sftpconfig";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("sftpconfig", "Configure SFTP settings for a server")
            .addSubcommands(
                new SubcommandData("update", "Update SFTP configuration for a server")
                    .addOption(OptionType.STRING, "server", "Name of the server to update", true)
                    .addOption(OptionType.STRING, "host", "SFTP host address", true)
                    .addOption(OptionType.STRING, "username", "SFTP username", true)
                    .addOption(OptionType.STRING, "password", "SFTP password", true)
                    .addOption(OptionType.INTEGER, "port", "SFTP port (default: 22)", false),
                new SubcommandData("test", "Test SFTP connection for a server")
                    .addOption(OptionType.STRING, "server", "Name of the server to test", true)
            )
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
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
                case "update" -> updateSftpConfig(event);
                case "test" -> testSftpConnection(event);
                default -> event.getHook().sendMessage("Unknown subcommand: " + subCommand).setEphemeral(true).queue();
            }
        } catch (Exception e) {
            logger.error("Error executing sftpconfig command", e);
            event.getHook().sendMessage("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
    
    private void updateSftpConfig(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        String serverName = event.getOption("server", OptionMapping::getAsString);
        String host = event.getOption("host", OptionMapping::getAsString);
        
        // Handle option that might not be present
        OptionMapping portOption = event.getOption("port");
        int port = (portOption != null) ? portOption.getAsInt() : 22;
        
        String username = event.getOption("username", OptionMapping::getAsString);
        String password = event.getOption("password", OptionMapping::getAsString);
        
        // Get the server from the database
        GameServer server = serverRepository.findByNameAndGuildId(serverName, guild.getIdLong());
        if (server == null) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Server Not Found", 
                    "Server **" + serverName + "** not found.")
            ).queue();
            return;
        }
        
        // Update SFTP settings
        server.setUseSftpForLogs(true);
        server.setSftpHost(host);
        server.setSftpPort(port);
        server.setSftpUsername(username);
        server.setSftpPassword(password);
        
        // Ensure regular credentials match SFTP credentials for consistency and fallback
        server.setHost(host);
        server.setUsername(username);
        server.setPassword(password);
        
        // Make sure both sets of credentials are synchronized
        server.synchronizeCredentials();
        
        // Test the connection first
        boolean connectionSuccess = false;
        try {
            logger.info("Testing updated SFTP connection for server {} ({}:{})", serverName, host, port);
            connectionSuccess = sftpManager.testConnection(server);
        } catch (Exception e) {
            logger.error("Error testing SFTP connection", e);
        }
        
        // Save the server even if connection fails
        serverRepository.save(server);
        
        if (connectionSuccess) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.successEmbed("SFTP Configuration Updated", 
                    "Successfully updated SFTP configuration for server **" + serverName + "**\n" +
                    "Host: " + host + "\n" +
                    "Port: " + port + "\n" +
                    "Username: " + username + "\n" +
                    "Connection test: **Success**")
            ).queue();
        } else {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.warningEmbed("SFTP Configuration Updated (Warning)", 
                    "Updated SFTP configuration for server **" + serverName + "**\n" +
                    "Host: " + host + "\n" +
                    "Port: " + port + "\n" +
                    "Username: " + username + "\n" +
                    "Connection test: **Failed** - Configuration saved anyway. Please verify your credentials.")
            ).queue();
        }
    }
    
    private void testSftpConnection(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        
        String serverName = event.getOption("server", OptionMapping::getAsString);
        
        // Get the server from the database
        GameServer server = serverRepository.findByNameAndGuildId(serverName, guild.getIdLong());
        if (server == null) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Server Not Found", 
                    "Server **" + serverName + "** not found.")
            ).queue();
            return;
        }
        
        // If the server doesn't have SFTP configuration, show error
        if (!server.hasSftpConfig()) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("Missing SFTP Configuration", 
                    "Server **" + serverName + "** does not have SFTP configuration.\n" +
                    "Use `/sftpconfig update` to configure SFTP settings.")
            ).queue();
            return;
        }
        
        // Test the connection
        boolean connectionSuccess = false;
        try {
            logger.info("Testing SFTP connection for server {} ({}:{})", 
                serverName, 
                server.getSftpHost() != null ? server.getSftpHost() : server.getHost(), 
                server.getSftpPort() > 0 ? server.getSftpPort() : server.getPort());
                
            connectionSuccess = sftpManager.testConnection(server);
        } catch (Exception e) {
            logger.error("Error testing SFTP connection", e);
        }
        
        if (connectionSuccess) {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.successEmbed("SFTP Connection Test", 
                    "Successfully connected to SFTP server for **" + serverName + "**\n" +
                    "Host: " + (server.getSftpHost() != null ? server.getSftpHost() : server.getHost()) + "\n" +
                    "Port: " + (server.getSftpPort() > 0 ? server.getSftpPort() : server.getPort()) + "\n" +
                    "Username: " + (server.getSftpUsername() != null ? server.getSftpUsername() : server.getUsername()))
            ).queue();
        } else {
            event.getHook().sendMessageEmbeds(
                EmbedThemes.errorEmbed("SFTP Connection Test Failed", 
                    "Failed to connect to SFTP server for **" + serverName + "**\n" +
                    "Host: " + (server.getSftpHost() != null ? server.getSftpHost() : server.getHost()) + "\n" +
                    "Port: " + (server.getSftpPort() > 0 ? server.getSftpPort() : server.getPort()) + "\n" +
                    "Username: " + (server.getSftpUsername() != null ? server.getSftpUsername() : server.getUsername()) + "\n\n" +
                    "Please check your SFTP credentials and try again.")
            ).queue();
        }
    }
}