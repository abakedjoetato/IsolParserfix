package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

/**
 * Command to set different log channels for different types of events
 */
public class SetLogChannelsCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(SetLogChannelsCommand.class);
    private final GuildConfigRepository guildConfigRepository;
    
    public SetLogChannelsCommand() {
        this.guildConfigRepository = new GuildConfigRepository();
    }
    
    @Override
    public String getName() {
        return "setlog";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), "Set the log channels for different event types")
                .addSubcommands(
                        new SubcommandData("primary", "Set the primary/default channel for all notifications (including kills/deaths)")
                                .addOption(OptionType.CHANNEL, "channel", "The default text channel for all notifications", true),
                        new SubcommandData("longshots", "Set the channel for longshot kill notifications")
                                .addOption(OptionType.CHANNEL, "channel", "The text channel to send longshot kill notifications to", true),
                        new SubcommandData("events", "Set the channel for game events (airdrops, missions, etc.)")
                                .addOption(OptionType.CHANNEL, "channel", "The text channel to send event notifications to", true),
                        new SubcommandData("connections", "Set the channel for player connections/disconnections")
                                .addOption(OptionType.CHANNEL, "channel", "The text channel to send connection notifications to", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        try {
            // Get the subcommand that was used
            String subcommand = event.getSubcommandName();
            if (subcommand == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "Invalid subcommand")
                ).queue();
                return;
            }
            
            // Get the specified channel
            TextChannel channel = event.getOption("channel", null, option -> option.getAsChannel().asTextChannel());
            if (channel == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "Invalid channel specified. Must be a text channel.")
                ).queue();
                return;
            }
            
            // Get or create guild configuration
            long guildId = event.getGuild().getIdLong();
            GuildConfig config = guildConfigRepository.findByGuildId(guildId);
            if (config == null) {
                config = new GuildConfig(guildId);
            }
            
            // Update the appropriate channel based on the subcommand
            boolean updated = true;
            String channelType = "";
            
            switch (subcommand) {
                case "primary":
                    config.setPrimaryLogChannelId(channel.getIdLong());
                    channelType = "primary";
                    break;
                    
                // Kills and deaths are now handled by the primary channel
                    
                case "longshots":
                    config.setLongshotLogChannelId(channel.getIdLong());
                    channelType = "longshot kill";
                    break;
                    
                case "events":
                    config.setEventLogChannelId(channel.getIdLong());
                    channelType = "event";
                    break;
                    
                case "connections":
                    config.setConnectionLogChannelId(channel.getIdLong());
                    channelType = "player connection";
                    break;
                default:
                    updated = false;
                    break;
            }
            
            if (updated) {
                // Save the updated configuration
                guildConfigRepository.save(config);
                
                // Send success message
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.successEmbed(
                                "Channel Set",
                                channelType + " messages will now be sent to " + channel.getAsMention()
                        )
                ).queue();
                
                logger.info("Set {} log channel for guild {} to channel {}", 
                        channelType, event.getGuild().getName(), channel.getName());
            } else {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "Invalid subcommand: " + subcommand)
                ).queue();
            }
            
        } catch (Exception e) {
            logger.error("Error setting log channel", e);
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Error", "Failed to set log channel: " + e.getMessage())
            ).queue();
        }
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        return List.of();
    }
}