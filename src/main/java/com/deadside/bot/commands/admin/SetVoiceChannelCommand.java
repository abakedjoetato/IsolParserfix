package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Command to set a voice channel for player count updates.
 */
public class SetVoiceChannelCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(SetVoiceChannelCommand.class);
    private final GuildConfigRepository guildConfigRepository;

    public SetVoiceChannelCommand() {
        this.guildConfigRepository = new GuildConfigRepository();
    }
    
    @Override
    public String getName() {
        return "setvoicechannel";
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        // This command doesn't need autocomplete
        return Collections.emptyList();
    }

    /**
     * Get the command data for this command.
     *
     * @return The command data
     */
    public CommandData getCommandData() {
        return Commands.slash("setvoicechannel", "Set a voice channel to display player count")
                .addOption(OptionType.CHANNEL, "channel", "The voice channel to use for player count display", true)
                .addOption(OptionType.STRING, "servername", "The name of the server to track player count for", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .setGuildOnly(true);
    }

    /**
     * Execute the command.
     *
     * @param event The slash command event
     */
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        try {
            // Get the options
            OptionMapping channelOption = event.getOption("channel");
            OptionMapping serverNameOption = event.getOption("servername");
            
            if (channelOption == null || serverNameOption == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "Missing required options")
                ).queue();
                return;
            }
            
            // Make sure the channel is a voice channel
            if (!(channelOption.getAsChannel() instanceof VoiceChannel)) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "The selected channel must be a voice channel")
                ).queue();
                return;
            }
            
            VoiceChannel voiceChannel = channelOption.getAsChannel().asVoiceChannel();
            String serverName = serverNameOption.getAsString();
            
            // Get the guild configuration
            Guild guild = event.getGuild();
            if (guild == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Error", "This command can only be used in a guild")
                ).queue();
                return;
            }
            
            GuildConfig config = guildConfigRepository.findByGuildId(guild.getIdLong());
            if (config == null) {
                config = new GuildConfig(guild.getIdLong());
            }
            
            // Update the configuration
            config.setPlayerCountVoiceChannelId(voiceChannel.getIdLong());
            config.setPlayerCountServerName(serverName);
            guildConfigRepository.save(config);
            
            // Update the voice channel name initially
            voiceChannel.getManager().setName("Players: Updating...").queue();
            
            // Send success message
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.successEmbed(
                            "Voice Channel Set",
                            "The voice channel " + voiceChannel.getAsMention() + 
                            " will now display player count for server: " + serverName
                    )
            ).queue();
            
            logger.info("Set player count voice channel for guild {} to channel {} tracking server {}",
                    guild.getName(), voiceChannel.getName(), serverName);
            
        } catch (Exception e) {
            logger.error("Error setting voice channel", e);
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Error", "An error occurred: " + e.getMessage())
            ).queue();
        }
    }
}