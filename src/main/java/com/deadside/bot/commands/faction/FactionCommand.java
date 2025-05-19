package com.deadside.bot.commands.faction;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.Faction;
import com.deadside.bot.db.repositories.FactionRepository;
import com.deadside.bot.premium.FeatureGate;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Command for managing factions
 */
public class FactionCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(FactionCommand.class);
    private final FactionRepository factionRepository = new FactionRepository();
    
    // Patterns for validation
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\w\\s]{3,32}$");
    private static final Pattern TAG_PATTERN = Pattern.compile("^[\\w]{2,8}$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    
    @Override
    public String getName() {
        return "faction";
    }
    
    @Override
    public CommandData getCommandData() {
        // Create option with autocomplete for faction names
        OptionData factionNameOption = new OptionData(OptionType.STRING, "name", "Faction name or tag", true)
                .setAutoComplete(true);
        
        // Read-only version (optional parameter)
        OptionData factionNameOptionOptional = new OptionData(OptionType.STRING, "name", "Faction name or tag", false)
                .setAutoComplete(true);
        
        return Commands.slash(getName(), "Manage your faction")
                .addSubcommands(
                        new SubcommandData("create", "Create a new faction")
                                .addOption(OptionType.STRING, "name", "Faction name (3-32 characters, alphanumeric)", true)
                                .addOption(OptionType.STRING, "tag", "Faction tag (2-8 characters, alphanumeric)", true)
                                .addOption(OptionType.STRING, "color", "Faction color (hex code, e.g. #FF0000)", true)
                                .addOption(OptionType.STRING, "description", "Faction description", true),
                        new SubcommandData("info", "View faction information")
                                .addOptions(factionNameOptionOptional),
                        new SubcommandData("join", "Join a faction")
                                .addOptions(factionNameOption),
                        new SubcommandData("leave", "Leave your current faction"),
                        new SubcommandData("list", "List all factions"),
                        new SubcommandData("member", "Manage faction members")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "action", "Action to perform", true)
                                                .addChoice("add", "add")
                                                .addChoice("remove", "remove")
                                                .addChoice("promote", "promote")
                                                .addChoice("demote", "demote"),
                                        new OptionData(OptionType.USER, "user", "User to manage", true)
                                ),
                        new SubcommandData("update", "Update faction settings")
                                .addOption(OptionType.STRING, "name", "New faction name (3-32 characters, alphanumeric)", false)
                                .addOption(OptionType.STRING, "tag", "New faction tag (2-8 characters, alphanumeric)", false)
                                .addOption(OptionType.STRING, "color", "New faction color (hex code, e.g. #FF0000)", false)
                                .addOption(OptionType.STRING, "description", "New faction description", false),
                        new SubcommandData("disband", "Disband your faction")
                );
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            String subCommand = event.getSubcommandName();
            if (subCommand == null) {
                event.reply("Invalid command usage.").setEphemeral(true).queue();
                return;
            }
            
            // Check if guild has access to the factions feature (premium feature)
            if (!FeatureGate.checkCommandAccess(event, FeatureGate.Feature.FACTIONS)) {
                // The FeatureGate utility already sent a premium upsell message
                return;
            }
            
            // Quick setup and defer reply for most commands
            long guildId = event.getGuild().getIdLong();
            User user = event.getUser();
            
            event.deferReply().queue();
            
            switch (subCommand) {
                case "create" -> createFaction(event, guildId, user);
                case "info" -> showFactionInfo(event, guildId, user);
                case "join" -> joinFaction(event, guildId, user);
                case "leave" -> leaveFaction(event, guildId, user);
                case "list" -> listFactions(event, guildId);
                case "member" -> manageMember(event, guildId, user);
                case "update" -> updateFaction(event, guildId, user);
                case "disband" -> disbandFaction(event, guildId, user);
                default -> event.getHook().sendMessage("Unknown subcommand: " + subCommand).queue();
            }
        } catch (Exception e) {
            logger.error("Error executing faction command", e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessage("An error occurred: " + e.getMessage()).queue();
            } else {
                event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
            }
        }
    }
    
    /**
     * Create a new faction
     */
    private void createFaction(SlashCommandInteractionEvent event, long guildId, User user) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String tag = event.getOption("tag", OptionMapping::getAsString);
        String color = event.getOption("color", OptionMapping::getAsString);
        String description = event.getOption("description", OptionMapping::getAsString);
        
        // Validate inputs
        if (!NAME_PATTERN.matcher(name).matches()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Name", 
                            "Faction name must be 3-32 characters and contain only alphanumeric characters and spaces.")
            ).queue();
            return;
        }
        
        if (!TAG_PATTERN.matcher(tag).matches()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Tag", 
                            "Faction tag must be 2-8 characters and contain only alphanumeric characters.")
            ).queue();
            return;
        }
        
        if (!COLOR_PATTERN.matcher(color).matches()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Color", 
                            "Faction color must be a valid hex code (e.g. #FF0000).")
            ).queue();
            return;
        }
        
        // Check if user is already in a faction
        List<Faction> userFactions = factionRepository.findByMember(user.getIdLong());
        if (!userFactions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Already in a Faction", 
                            "You are already a member of the faction **" + userFactions.get(0).getName() + "**.\n" +
                            "You must leave your current faction before creating a new one.")
            ).queue();
            return;
        }
        
        // Check if faction name already exists
        Faction existingFaction = factionRepository.findByNameInGuild(guildId, name);
        if (existingFaction != null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Faction Already Exists", 
                            "A faction with the name **" + name + "** already exists in this server.")
            ).queue();
            return;
        }
        
        // Check if faction tag already exists
        existingFaction = factionRepository.findByTagInGuild(guildId, tag);
        if (existingFaction != null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Tag Already Exists", 
                            "A faction with the tag **" + tag + "** already exists in this server.")
            ).queue();
            return;
        }
        
        // Create and save the faction
        Faction faction = new Faction(name, tag, description, guildId, user.getIdLong(), color);
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Faction Created", 
                        "Successfully created faction **" + name + "** [" + tag + "]!\n\n" +
                        "You are now the owner of this faction. Use `/faction member add @user` to add members.")
        ).queue();
        
        logger.info("User {} created faction {} [{}] in guild {}", 
                user.getName(), name, tag, event.getGuild().getName());
    }
    
    /**
     * Show faction information
     */
    private void showFactionInfo(SlashCommandInteractionEvent event, long guildId, User user) {
        // If no faction specified, show user's faction
        String factionName = event.getOption("name", OptionMapping::getAsString);
        Faction faction;
        
        if (factionName == null) {
            // Show user's faction
            List<Faction> userFactions = factionRepository.findByMember(user.getIdLong());
            
            if (userFactions.isEmpty()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Not in a Faction", 
                                "You are not a member of any faction. Use `/faction join` to join a faction or `/faction create` to create your own.")
                ).queue();
                return;
            }
            
            faction = userFactions.get(0);
        } else {
            // Show specified faction
            faction = factionRepository.findByNameInGuild(guildId, factionName);
            
            if (faction == null) {
                // Try by tag
                faction = factionRepository.findByTagInGuild(guildId, factionName);
            }
            
            if (faction == null) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Faction Not Found", 
                                "No faction found with name or tag **" + factionName + "**.")
                ).queue();
                return;
            }
        }
        
        // Build faction info embed
        Color embedColor;
        try {
            embedColor = Color.decode(faction.getColor());
        } catch (NumberFormatException e) {
            embedColor = Color.GRAY;
        }
        
        StringBuilder description = new StringBuilder();
        description.append("**Tag**: ").append(faction.getTag()).append("\n");
        description.append("**Description**: ").append(faction.getDescription()).append("\n\n");
        
        description.append("**Level**: ").append(faction.getLevel()).append("\n");
        description.append("**Experience**: ").append(faction.getExperience()).append("/")
                .append(faction.getLevel() * 1000).append("\n");
        description.append("**Members**: ").append(faction.getTotalMemberCount()).append("/")
                .append(faction.getMaxMembers()).append("\n\n");
        
        description.append("**Owner**: <@").append(faction.getOwnerId()).append(">\n");
        
        if (!faction.getOfficerIds().isEmpty()) {
            description.append("**Officers**:\n");
            for (long officerId : faction.getOfficerIds()) {
                description.append("• <@").append(officerId).append(">\n");
            }
            description.append("\n");
        }
        
        if (!faction.getMemberIds().isEmpty()) {
            description.append("**Members**:\n");
            for (long memberId : faction.getMemberIds()) {
                description.append("• <@").append(memberId).append(">\n");
            }
        }
        
        description.append("\n**Created**: <t:").append(faction.getCreated() / 1000).append(":R>");
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.customEmbed("Faction: " + faction.getName(), description.toString(), embedColor)
        ).queue();
    }
    
    /**
     * Join a faction
     */
    private void joinFaction(SlashCommandInteractionEvent event, long guildId, User user) {
        String factionName = event.getOption("name", OptionMapping::getAsString);
        
        // Check if user is already in a faction
        List<Faction> userFactions = factionRepository.findByMember(user.getIdLong());
        if (!userFactions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Already in a Faction", 
                            "You are already a member of the faction **" + userFactions.get(0).getName() + "**.\n" +
                            "You must leave your current faction before joining another one.")
            ).queue();
            return;
        }
        
        // Find the faction
        Faction faction = factionRepository.findByNameInGuild(guildId, factionName);
        if (faction == null) {
            // Try by tag
            faction = factionRepository.findByTagInGuild(guildId, factionName);
        }
        
        if (faction == null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Faction Not Found", 
                            "No faction found with name or tag **" + factionName + "**.")
            ).queue();
            return;
        }
        
        // Check if faction is full
        if (faction.getTotalMemberCount() >= faction.getMaxMembers()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Faction is Full", 
                            "The faction **" + faction.getName() + "** is already at maximum capacity (" + 
                            faction.getMaxMembers() + " members).")
            ).queue();
            return;
        }
        
        // Add user to faction
        boolean added = faction.addMember(user.getIdLong());
        if (!added) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Could Not Join", 
                            "Failed to join faction **" + faction.getName() + "**. You might already be a member.")
            ).queue();
            return;
        }
        
        // Save faction
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Joined Faction", 
                        "You have successfully joined the faction **" + faction.getName() + "** [" + faction.getTag() + "]!")
        ).queue();
        
        logger.info("User {} joined faction {} [{}] in guild {}", 
                user.getName(), faction.getName(), faction.getTag(), event.getGuild().getName());
    }
    
    /**
     * Leave faction
     */
    private void leaveFaction(SlashCommandInteractionEvent event, long guildId, User user) {
        // Check if user is in a faction
        List<Faction> userFactions = factionRepository.findByMember(user.getIdLong());
        
        if (userFactions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not in a Faction", 
                            "You are not a member of any faction.")
            ).queue();
            return;
        }
        
        Faction faction = userFactions.get(0);
        
        // Check if user is the owner
        if (faction.isOwner(user.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Cannot Leave", 
                            "You are the owner of faction **" + faction.getName() + "**.\n" +
                            "You must transfer ownership using `/faction member promote @newowner` or disband the faction using `/faction disband`.")
            ).queue();
            return;
        }
        
        // Remove user from faction
        boolean removed = faction.removeMember(user.getIdLong());
        
        if (!removed) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Could Not Leave", 
                            "Failed to leave faction **" + faction.getName() + "**.")
            ).queue();
            return;
        }
        
        // Save faction
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Left Faction", 
                        "You have successfully left the faction **" + faction.getName() + "**.")
        ).queue();
        
        logger.info("User {} left faction {} [{}] in guild {}", 
                user.getName(), faction.getName(), faction.getTag(), event.getGuild().getName());
    }
    
    /**
     * List all factions
     */
    private void listFactions(SlashCommandInteractionEvent event, long guildId) {
        List<Faction> factions = factionRepository.findByGuild(guildId);
        
        if (factions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.infoEmbed("No Factions", 
                            "There are no factions in this server yet.\n" +
                            "Use `/faction create` to create your own faction!")
            ).queue();
            return;
        }
        
        StringBuilder description = new StringBuilder();
        description.append("**Total Factions**: ").append(factions.size()).append("\n\n");
        
        for (int i = 0; i < factions.size(); i++) {
            Faction faction = factions.get(i);
            description.append("`").append(i + 1).append(".` **")
                    .append(faction.getName()).append("** [").append(faction.getTag()).append("] - ")
                    .append("Level ").append(faction.getLevel()).append(", ")
                    .append(faction.getTotalMemberCount()).append(" members\n");
            
            // Add description snippet if space allows
            if (i < 5 && faction.getDescription().length() > 0) {
                String snippet = faction.getDescription();
                if (snippet.length() > 50) {
                    snippet = snippet.substring(0, 47) + "...";
                }
                description.append("   *\"").append(snippet).append("\"*\n");
            }
        }
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.infoEmbed("Factions", description.toString())
        ).queue();
    }
    
    /**
     * Manage faction members (add, remove, promote, demote)
     */
    private void manageMember(SlashCommandInteractionEvent event, long guildId, User user) {
        String action = event.getOption("action", OptionMapping::getAsString);
        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        
        // Check if user is in a faction and is owner or officer
        List<Faction> userFactions = factionRepository.findByMember(user.getIdLong());
        
        if (userFactions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not in a Faction", 
                            "You are not a member of any faction.")
            ).queue();
            return;
        }
        
        Faction faction = userFactions.get(0);
        
        // Check if user has permission (owner for promote/demote, owner or officer for add/remove)
        boolean isOwner = faction.isOwner(user.getIdLong());
        boolean isOfficer = faction.isOfficer(user.getIdLong());
        
        if (!isOwner && (action.equals("promote") || action.equals("demote"))) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("No Permission", 
                            "Only the faction owner can promote or demote members.")
            ).queue();
            return;
        }
        
        if (!isOwner && !isOfficer && (action.equals("add") || action.equals("remove"))) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("No Permission", 
                            "Only faction owners and officers can add or remove members.")
            ).queue();
            return;
        }
        
        // Perform the requested action
        switch (action) {
            case "add" -> addMember(event, faction, targetUser);
            case "remove" -> removeMember(event, faction, user, targetUser);
            case "promote" -> promoteMember(event, faction, targetUser);
            case "demote" -> demoteMember(event, faction, targetUser);
            default -> event.getHook().sendMessage("Unknown action: " + action).queue();
        }
    }
    
    /**
     * Add a member to the faction
     */
    private void addMember(SlashCommandInteractionEvent event, Faction faction, User targetUser) {
        // Check if target user is already in a faction
        List<Faction> targetFactions = factionRepository.findByMember(targetUser.getIdLong());
        
        if (!targetFactions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Already in a Faction", 
                            targetUser.getName() + " is already a member of the faction **" + 
                            targetFactions.get(0).getName() + "**.")
            ).queue();
            return;
        }
        
        // Check if faction is full
        if (faction.getTotalMemberCount() >= faction.getMaxMembers()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Faction is Full", 
                            "Your faction is already at maximum capacity (" + 
                            faction.getMaxMembers() + " members).")
            ).queue();
            return;
        }
        
        // Add user to faction
        boolean added = faction.addMember(targetUser.getIdLong());
        
        if (!added) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Could Not Add Member", 
                            "Failed to add " + targetUser.getName() + " to your faction.")
            ).queue();
            return;
        }
        
        // Save faction
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Member Added", 
                        "Successfully added " + targetUser.getAsMention() + " to your faction.")
        ).queue();
        
        logger.info("User {} added {} to faction {} [{}]", 
                event.getUser().getName(), targetUser.getName(), faction.getName(), faction.getTag());
    }
    
    /**
     * Remove a member from the faction
     */
    private void removeMember(SlashCommandInteractionEvent event, Faction faction, User user, User targetUser) {
        // Check if target user is in the faction
        if (!faction.isMember(targetUser.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not a Member", 
                            targetUser.getName() + " is not a member of your faction.")
            ).queue();
            return;
        }
        
        // Check if target is the owner
        if (faction.isOwner(targetUser.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Cannot Remove Owner", 
                            "You cannot remove the faction owner.")
            ).queue();
            return;
        }
        
        // Check if user is officer trying to remove another officer
        if (faction.isOfficer(targetUser.getIdLong()) && !faction.isOwner(user.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Cannot Remove Officer", 
                            "Only the faction owner can remove officers.")
            ).queue();
            return;
        }
        
        // Remove user from faction
        boolean removed = faction.removeMember(targetUser.getIdLong());
        
        if (!removed) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Could Not Remove Member", 
                            "Failed to remove " + targetUser.getName() + " from your faction.")
            ).queue();
            return;
        }
        
        // Save faction
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Member Removed", 
                        "Successfully removed " + targetUser.getAsMention() + " from your faction.")
        ).queue();
        
        logger.info("User {} removed {} from faction {} [{}]", 
                user.getName(), targetUser.getName(), faction.getName(), faction.getTag());
    }
    
    /**
     * Promote a member to officer
     */
    private void promoteMember(SlashCommandInteractionEvent event, Faction faction, User targetUser) {
        // Special case - if target is the owner, this is actually transferring ownership
        if (faction.isOwner(targetUser.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Already Owner", 
                            targetUser.getName() + " is already the owner of this faction.")
            ).queue();
            return;
        }
        
        // Check if target user is in the faction
        if (!faction.isMember(targetUser.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not a Member", 
                            targetUser.getName() + " is not a member of your faction.")
            ).queue();
            return;
        }
        
        // Check if target is already an officer
        if (faction.isOfficer(targetUser.getIdLong())) {
            // This is a special case, transferring ownership
            boolean transferred = faction.transferOwnership(targetUser.getIdLong());
            
            if (!transferred) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Could Not Transfer Ownership", 
                                "Failed to transfer ownership to " + targetUser.getName() + ".")
                ).queue();
                return;
            }
            
            // Save faction
            factionRepository.save(faction);
            
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.successEmbed("Ownership Transferred", 
                            "Successfully transferred faction ownership to " + targetUser.getAsMention() + ".\n" +
                            "You are now an officer of the faction.")
            ).queue();
            
            logger.info("User {} transferred ownership of faction {} [{}] to {}", 
                    event.getUser().getName(), faction.getName(), faction.getTag(), targetUser.getName());
            return;
        }
        
        // Regular promotion from member to officer
        boolean promoted = faction.promoteMember(targetUser.getIdLong());
        
        if (!promoted) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Could Not Promote", 
                            "Failed to promote " + targetUser.getName() + " to officer.")
            ).queue();
            return;
        }
        
        // Save faction
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Member Promoted", 
                        "Successfully promoted " + targetUser.getAsMention() + " to faction officer.")
        ).queue();
        
        logger.info("User {} promoted {} to officer in faction {} [{}]", 
                event.getUser().getName(), targetUser.getName(), faction.getName(), faction.getTag());
    }
    
    /**
     * Demote an officer to regular member
     */
    private void demoteMember(SlashCommandInteractionEvent event, Faction faction, User targetUser) {
        // Check if target is an officer
        if (!faction.isOfficer(targetUser.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not an Officer", 
                            targetUser.getName() + " is not an officer of your faction.")
            ).queue();
            return;
        }
        
        // Demote officer
        boolean demoted = faction.demoteOfficer(targetUser.getIdLong());
        
        if (!demoted) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Could Not Demote", 
                            "Failed to demote " + targetUser.getName() + " from officer.")
            ).queue();
            return;
        }
        
        // Save faction
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Officer Demoted", 
                        "Successfully demoted " + targetUser.getAsMention() + " to regular member.")
        ).queue();
        
        logger.info("User {} demoted {} from officer in faction {} [{}]", 
                event.getUser().getName(), targetUser.getName(), faction.getName(), faction.getTag());
    }
    
    /**
     * Update faction settings
     */
    private void updateFaction(SlashCommandInteractionEvent event, long guildId, User user) {
        // Check if user is in a faction and is owner
        List<Faction> userFactions = factionRepository.findByMember(user.getIdLong());
        
        if (userFactions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not in a Faction", 
                            "You are not a member of any faction.")
            ).queue();
            return;
        }
        
        Faction faction = userFactions.get(0);
        
        // Check if user is the owner
        if (!faction.isOwner(user.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("No Permission", 
                            "Only the faction owner can update faction settings.")
            ).queue();
            return;
        }
        
        // Get updated values
        String name = event.getOption("name", OptionMapping::getAsString);
        String tag = event.getOption("tag", OptionMapping::getAsString);
        String color = event.getOption("color", OptionMapping::getAsString);
        String description = event.getOption("description", OptionMapping::getAsString);
        
        boolean updated = false;
        StringBuilder updateMessage = new StringBuilder();
        
        // Update name if provided and valid
        if (name != null && !name.isEmpty()) {
            if (!NAME_PATTERN.matcher(name).matches()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Invalid Name", 
                                "Faction name must be 3-32 characters and contain only alphanumeric characters and spaces.")
                ).queue();
                return;
            }
            
            // Check if name is already taken by another faction
            Faction existingFaction = factionRepository.findByNameInGuild(guildId, name);
            if (existingFaction != null && !existingFaction.getId().equals(faction.getId())) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Name Already Taken", 
                                "A faction with the name **" + name + "** already exists in this server.")
                ).queue();
                return;
            }
            
            updateMessage.append("• Name: ").append(faction.getName()).append(" → ").append(name).append("\n");
            faction.setName(name);
            updated = true;
        }
        
        // Update tag if provided and valid
        if (tag != null && !tag.isEmpty()) {
            if (!TAG_PATTERN.matcher(tag).matches()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Invalid Tag", 
                                "Faction tag must be 2-8 characters and contain only alphanumeric characters.")
                ).queue();
                return;
            }
            
            // Check if tag is already taken by another faction
            Faction existingFaction = factionRepository.findByTagInGuild(guildId, tag);
            if (existingFaction != null && !existingFaction.getId().equals(faction.getId())) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Tag Already Taken", 
                                "A faction with the tag **" + tag + "** already exists in this server.")
                ).queue();
                return;
            }
            
            updateMessage.append("• Tag: ").append(faction.getTag()).append(" → ").append(tag).append("\n");
            faction.setTag(tag);
            updated = true;
        }
        
        // Update color if provided and valid
        if (color != null && !color.isEmpty()) {
            if (!COLOR_PATTERN.matcher(color).matches()) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Invalid Color", 
                                "Faction color must be a valid hex code (e.g. #FF0000).")
                ).queue();
                return;
            }
            
            updateMessage.append("• Color: ").append(faction.getColor()).append(" → ").append(color).append("\n");
            faction.setColor(color);
            updated = true;
        }
        
        // Update description if provided
        if (description != null && !description.isEmpty()) {
            updateMessage.append("• Description updated\n");
            faction.setDescription(description);
            updated = true;
        }
        
        // Check if anything was updated
        if (!updated) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.infoEmbed("No Changes", 
                            "No faction settings were updated. Provide at least one value to change.")
            ).queue();
            return;
        }
        
        // Save faction
        factionRepository.save(faction);
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.successEmbed("Faction Updated", 
                        "Successfully updated faction settings:\n\n" + updateMessage)
        ).queue();
        
        logger.info("User {} updated faction {} [{}] settings", 
                user.getName(), faction.getName(), faction.getTag());
    }
    
    /**
     * Disband a faction
     */
    private void disbandFaction(SlashCommandInteractionEvent event, long guildId, User user) {
        // Check if user is in a faction and is owner
        List<Faction> userFactions = factionRepository.findByMember(user.getIdLong());
        
        if (userFactions.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Not in a Faction", 
                            "You are not a member of any faction.")
            ).queue();
            return;
        }
        
        Faction faction = userFactions.get(0);
        
        // Check if user is the owner
        if (!faction.isOwner(user.getIdLong())) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("No Permission", 
                            "Only the faction owner can disband a faction.")
            ).queue();
            return;
        }
        
        // Disband faction
        String factionName = faction.getName();
        String factionTag = faction.getTag();
        boolean deleted = factionRepository.delete(faction);
        
        if (!deleted) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Could Not Disband", 
                            "Failed to disband your faction.")
            ).queue();
            return;
        }
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.warningEmbed("Faction Disbanded", 
                        "You have successfully disbanded the faction **" + factionName + "** [" + factionTag + "].\n" +
                        "All members have been removed from the faction.")
        ).queue();
        
        logger.info("User {} disbanded faction {} [{}] in guild {}", 
                user.getName(), factionName, factionTag, event.getGuild().getName());
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getGuild() == null) return List.of();
        
        String focusedOption = event.getFocusedOption().getName();
        String subcommand = event.getSubcommandName();
        
        // We only have autocomplete for faction names
        if ("name".equals(focusedOption)) {
            String currentInput = event.getFocusedOption().getValue().toLowerCase();
            long guildId = event.getGuild().getIdLong();
            
            // Get all factions in this guild
            List<Faction> factions = factionRepository.findAllByGuildId(guildId);
            
            return factions.stream()
                .filter(faction -> 
                    faction.getName().toLowerCase().contains(currentInput) || 
                    faction.getTag().toLowerCase().contains(currentInput))
                .map(faction -> new Choice(
                    faction.getName() + " [" + faction.getTag() + "]", // Display name with tag
                    faction.getName())                                 // Value is just the name
                )
                .limit(25) // Discord has a max of 25 choices
                .collect(Collectors.toList());
        }
        
        return List.of(); // Empty list for no suggestions
    }
}