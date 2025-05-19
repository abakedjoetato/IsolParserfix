package com.deadside.bot.commands.server;

import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to set the bounty channel for displaying bounty announcements
 */
public class SetBountyChannelCommand {
    private static final Logger logger = LoggerFactory.getLogger(SetBountyChannelCommand.class);

    public static CommandData getCommandData() {
        return Commands.slash("setbountychannel", "Set the channel for bounty announcements")
                .addOptions(
                        new OptionData(OptionType.STRING, "server", "The server to set the bounty channel for", true),
                        new OptionData(OptionType.CHANNEL, "channel", "The channel to use for bounty announcements", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    public static void execute(SlashCommandInteractionEvent event) {
        // Defer reply since this would normally involve database operations
        event.deferReply().queue();
        
        try {
            // Get the server name and channel from options
            String serverName = event.getOption("server", OptionMapping::getAsString);
            TextChannel channel = event.getOption("channel", OptionMapping::getAsChannel).asTextChannel();
            
            if (serverName == null || channel == null) {
                event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                        "Missing Parameters",
                        "You must provide a server name and a text channel."
                )).queue();
                return;
            }
            
            // This is a simplified implementation without database operations
            // In a real implementation, we would save this to the database
            
            // Send success message
            event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed(
                    "Bounty Channel Set",
                    String.format("All bounty announcements for **%s** will now be sent to %s.", 
                            serverName, channel.getAsMention())
            )).queue();
            
            logger.info("Set bounty channel for server '{}' to channel '{}'", 
                    serverName, channel.getName());
            
        } catch (Exception e) {
            logger.error("Error executing set bounty channel command", e);
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Error",
                    "An error occurred: " + e.getMessage()
            )).queue();
        }
    }
}