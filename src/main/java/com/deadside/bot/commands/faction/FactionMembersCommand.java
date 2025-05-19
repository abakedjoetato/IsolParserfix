package com.deadside.bot.commands.faction;

import com.deadside.bot.commands.Command;
import com.deadside.bot.db.models.Faction;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.FactionRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.faction.FactionStatsSync;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to manage faction members
 */
public class FactionMembersCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(FactionMembersCommand.class);
    private final FactionRepository factionRepository;
    private final PlayerRepository playerRepository;
    private final FactionStatsSync factionStatsSync;

    public FactionMembersCommand(FactionRepository factionRepository, PlayerRepository playerRepository) {
        this.factionRepository = factionRepository;
        this.playerRepository = playerRepository;
        this.factionStatsSync = new FactionStatsSync();
    }

    @Override
    public String getName() {
        return "factionmembers";
    }

    @Override
    public String getDescription() {
        return "Manage faction members";
    }

    @Override
    public List<SubcommandData> getSubcommands() {
        List<SubcommandData> subcommands = new ArrayList<>();

        // List members subcommand
        SubcommandData listCommand = new SubcommandData("list", "List members of a faction");
        listCommand.addOption(OptionType.STRING, "faction", "The name of the faction", true);
        subcommands.add(listCommand);

        // Join faction subcommand
        SubcommandData joinCommand = new SubcommandData("join", "Join a faction");
        joinCommand.addOption(OptionType.STRING, "faction", "The name of the faction to join", true);
        subcommands.add(joinCommand);

        // Leave faction subcommand
        SubcommandData leaveCommand = new SubcommandData("leave", "Leave your current faction");
        subcommands.add(leaveCommand);

        // Invite player subcommand (for faction leaders/officers)
        SubcommandData inviteCommand = new SubcommandData("invite", "Invite a player to your faction");
        inviteCommand.addOption(OptionType.USER, "player", "The player to invite", true);
        subcommands.add(inviteCommand);

        // Kick player subcommand (for faction leaders/officers)
        SubcommandData kickCommand = new SubcommandData("kick", "Remove a player from your faction");
        kickCommand.addOption(OptionType.USER, "player", "The player to remove", true);
        subcommands.add(kickCommand);

        return subcommands;
    }

    @Override
    public List<OptionData> getOptions() {
        return new ArrayList<>(); // Options are defined in subcommands
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Please specify a subcommand.").setEphemeral(true).queue();
            return;
        }

        try {
            switch (subcommand) {
                case "list":
                    listMembers(event);
                    break;
                case "join":
                    joinFaction(event);
                    break;
                case "leave":
                    leaveFaction(event);
                    break;
                case "invite":
                    invitePlayer(event);
                    break;
                case "kick":
                    kickPlayer(event);
                    break;
                default:
                    event.reply("Unknown subcommand: " + subcommand).setEphemeral(true).queue();
                    break;
            }
        } catch (Exception e) {
            logger.error("Error executing faction members command: {}", e.getMessage(), e);
            event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    /**
     * List all members of a faction
     */
    private void listMembers(SlashCommandInteractionEvent event) {
        String factionName = event.getOption("faction", "", OptionMapping::getAsString);
        if (factionName.isEmpty()) {
            event.reply("Please specify a faction name.").setEphemeral(true).queue();
            return;
        }

        Faction faction = factionRepository.findByName(factionName);
        if (faction == null) {
            event.reply("Faction not found: " + factionName).setEphemeral(true).queue();
            return;
        }

        List<Player> members = playerRepository.findByFactionId(faction.getId());
        if (members.isEmpty()) {
            event.reply("No members found in faction: " + factionName).setEphemeral(true).queue();
            return;
        }

        // Create embed with faction members information
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚔️ " + faction.getName() + " - Members (" + members.size() + ")")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());

        if (faction.getLogoUrl() != null && !faction.getLogoUrl().isEmpty()) {
            embed.setThumbnail(faction.getLogoUrl());
        }

        StringBuilder description = new StringBuilder();
        
        // List faction leader first
        members.stream()
                .filter(p -> p.isFactionLeader())
                .findFirst()
                .ifPresent(leader -> {
                    description.append("**👑 Leader:** ").append(leader.getName()).append("\n\n");
                });

        // List faction officers next
        List<Player> officers = members.stream()
                .filter(p -> p.isFactionOfficer() && !p.isFactionLeader())
                .collect(Collectors.toList());
        
        if (!officers.isEmpty()) {
            description.append("**🔰 Officers:**\n");
            for (Player officer : officers) {
                description.append("• ").append(officer.getName()).append("\n");
            }
            description.append("\n");
        }

        // List regular members
        List<Player> regularMembers = members.stream()
                .filter(p -> !p.isFactionOfficer() && !p.isFactionLeader())
                .collect(Collectors.toList());
        
        if (!regularMembers.isEmpty()) {
            description.append("**👥 Members:**\n");
            for (Player member : regularMembers) {
                description.append("• ").append(member.getName()).append("\n");
            }
        }

        embed.setDescription(description.toString());
        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Join a faction
     */
    private void joinFaction(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String factionName = event.getOption("faction", "", OptionMapping::getAsString);
        
        if (factionName.isEmpty()) {
            event.reply("Please specify a faction name.").setEphemeral(true).queue();
            return;
        }

        // Get player from database
        Player player = playerRepository.findByDiscordId(user.getId());
        if (player == null) {
            event.reply("You need to link your Deadside account first. Use the /link command.").setEphemeral(true).queue();
            return;
        }

        // Check if player is already in a faction
        if (player.getFactionId() != null) {
            Faction currentFaction = factionRepository.findById(player.getFactionId());
            if (currentFaction != null) {
                event.reply("You are already a member of " + currentFaction.getName() + ". Use /factionmembers leave first.").setEphemeral(true).queue();
                return;
            }
        }

        // Find the faction
        Faction faction = factionRepository.findByName(factionName);
        if (faction == null) {
            event.reply("Faction not found: " + factionName).setEphemeral(true).queue();
            return;
        }

        // Join the faction
        player.setFactionId(faction.getId());
        player.setFactionJoinDate(Instant.now());
        playerRepository.save(player);

        // Update faction stats
        factionStatsSync.updateFaction(faction.getId());

        event.reply("You have joined the faction: " + faction.getName()).queue();
    }

    /**
     * Leave current faction
     */
    private void leaveFaction(SlashCommandInteractionEvent event) {
        User user = event.getUser();

        // Get player from database
        Player player = playerRepository.findByDiscordId(user.getId());
        if (player == null) {
            event.reply("You need to link your Deadside account first. Use the /link command.").setEphemeral(true).queue();
            return;
        }

        // Check if player is in a faction
        if (player.getFactionId() == null) {
            event.reply("You are not a member of any faction.").setEphemeral(true).queue();
            return;
        }

        Faction faction = factionRepository.findById(player.getFactionId());
        if (faction == null) {
            // Orphaned faction ID, just clear it
            player.setFactionId(null);
            player.setFactionJoinDate(null);
            playerRepository.save(player);
            event.reply("You have left your faction.").queue();
            return;
        }

        // Check if player is faction leader
        if (player.isFactionLeader()) {
            long memberCount = playerRepository.countByFactionId(faction.getId());
            if (memberCount > 1) {
                event.reply("You cannot leave the faction as a leader while there are still members. Transfer leadership first or kick all members.").setEphemeral(true).queue();
                return;
            }

            // Leader is last member, delete the faction
            factionRepository.delete(faction);
            player.setFactionId(null);
            player.setFactionJoinDate(null);
            player.setFactionLeader(false);
            player.setFactionOfficer(false);
            playerRepository.save(player);
            
            event.reply("You have left and disbanded the faction: " + faction.getName()).queue();
            return;
        }

        // Regular member leaving
        player.setFactionId(null);
        player.setFactionJoinDate(null);
        player.setFactionOfficer(false);
        playerRepository.save(player);

        // Update faction stats
        factionStatsSync.updateFaction(faction.getId());

        event.reply("You have left the faction: " + faction.getName()).queue();
    }

    /**
     * Invite a player to your faction
     */
    private void invitePlayer(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        Member targetMember = event.getOption("player", null, OptionMapping::getAsMember);
        
        if (targetMember == null) {
            event.reply("Please specify a valid player to invite.").setEphemeral(true).queue();
            return;
        }
        
        // Check if target is the same as inviter
        if (targetMember.getUser().getId().equals(user.getId())) {
            event.reply("You cannot invite yourself to a faction.").setEphemeral(true).queue();
            return;
        }

        // Get inviter from database
        Player inviter = playerRepository.findByDiscordId(user.getId());
        if (inviter == null) {
            event.reply("You need to link your Deadside account first. Use the /link command.").setEphemeral(true).queue();
            return;
        }

        // Check if inviter is in a faction and has permission
        if (inviter.getFactionId() == null) {
            event.reply("You are not a member of any faction.").setEphemeral(true).queue();
            return;
        }

        if (!inviter.isFactionLeader() && !inviter.isFactionOfficer()) {
            event.reply("Only faction leaders and officers can invite players.").setEphemeral(true).queue();
            return;
        }

        // Get target player from database
        Player target = playerRepository.findByDiscordId(targetMember.getUser().getId());
        if (target == null) {
            event.reply(targetMember.getUser().getName() + " needs to link their Deadside account first using the /link command.").setEphemeral(true).queue();
            return;
        }

        // Check if target is already in a faction
        if (target.getFactionId() != null) {
            event.reply(target.getName() + " is already a member of a faction.").setEphemeral(true).queue();
            return;
        }

        // Get faction details
        Faction faction = factionRepository.findById(inviter.getFactionId());
        if (faction == null) {
            event.reply("Faction not found. This shouldn't happen - please contact an administrator.").setEphemeral(true).queue();
            return;
        }

        // Add target to faction
        target.setFactionId(faction.getId());
        target.setFactionJoinDate(Instant.now());
        playerRepository.save(target);

        // Update faction stats
        factionStatsSync.updateFaction(faction.getId());

        // Notify the target user
        targetMember.getUser().openPrivateChannel()
                .flatMap(channel -> channel.sendMessage("You have been invited to join the faction: " + faction.getName() + " by " + inviter.getName() + ". You are now a member of this faction."))
                .queue(null, error -> logger.warn("Could not send DM to user {}", targetMember.getUser().getId()));

        event.reply("You have invited " + target.getName() + " to your faction, and they are now a member.").queue();
    }

    /**
     * Kick a player from your faction
     */
    private void kickPlayer(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        Member targetMember = event.getOption("player", null, OptionMapping::getAsMember);
        
        if (targetMember == null) {
            event.reply("Please specify a valid player to kick.").setEphemeral(true).queue();
            return;
        }
        
        // Check if target is the same as kicker
        if (targetMember.getUser().getId().equals(user.getId())) {
            event.reply("You cannot kick yourself. Use /factionmembers leave instead.").setEphemeral(true).queue();
            return;
        }

        // Get kicker from database
        Player kicker = playerRepository.findByDiscordId(user.getId());
        if (kicker == null) {
            event.reply("You need to link your Deadside account first. Use the /link command.").setEphemeral(true).queue();
            return;
        }

        // Check if kicker is in a faction and has permission
        if (kicker.getFactionId() == null) {
            event.reply("You are not a member of any faction.").setEphemeral(true).queue();
            return;
        }

        if (!kicker.isFactionLeader() && !kicker.isFactionOfficer()) {
            event.reply("Only faction leaders and officers can kick players.").setEphemeral(true).queue();
            return;
        }

        // Get target player from database
        Player target = playerRepository.findByDiscordId(targetMember.getUser().getId());
        if (target == null) {
            event.reply(targetMember.getUser().getName() + " is not linked to a Deadside account.").setEphemeral(true).queue();
            return;
        }

        // Check if target is in the same faction
        if (target.getFactionId() == null || !target.getFactionId().equals(kicker.getFactionId())) {
            event.reply(target.getName() + " is not a member of your faction.").setEphemeral(true).queue();
            return;
        }

        // Check if target is a leader and kicker is not
        if (target.isFactionLeader() && !kicker.isFactionLeader()) {
            event.reply("You cannot kick the faction leader.").setEphemeral(true).queue();
            return;
        }

        // Get faction details
        Faction faction = factionRepository.findById(kicker.getFactionId());
        if (faction == null) {
            event.reply("Faction not found. This shouldn't happen - please contact an administrator.").setEphemeral(true).queue();
            return;
        }

        // Remove target from faction
        target.setFactionId(null);
        target.setFactionJoinDate(null);
        target.setFactionLeader(false);
        target.setFactionOfficer(false);
        playerRepository.save(target);

        // Update faction stats
        factionStatsSync.updateFaction(faction.getId());

        // Notify the target user
        targetMember.getUser().openPrivateChannel()
                .flatMap(channel -> channel.sendMessage("You have been removed from the faction: " + faction.getName() + " by " + kicker.getName() + "."))
                .queue(null, error -> logger.warn("Could not send DM to user {}", targetMember.getUser().getId()));

        event.reply("You have removed " + target.getName() + " from your faction.").queue();
    }
}