package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.utils.AdvancedEmbeds;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.ResourceManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Command for testing various game event embed formats
 * Creates mock outputs for different types of game events
 */
public class TestCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(TestCommand.class);
    private static final Random random = new Random();
    
    // Sample data for mock events
    private static final String[] PLAYER_NAMES = {
        "EmeraldHunter", "DeadsideVeteran", "ApexSurvivor", "WastelandScout", 
        "RadiationRanger", "ScrapCollector", "BountyKiller", "ZoneExplorer", 
        "cuhslice", "Jolina"
    };
    
    private static final String[] WEAPONS = {
        "M4A1", "AK-74", "Mosin", "Glock-17", "MP5", "SR-25", "SVD", "KA-M", "Sawed-Off Shotgun"
    };
    
    private static final String[] LOCATIONS = {
        "Military Base", "Riverside Town", "Northern Mountains", "Railway Station", 
        "Abandoned Factory", "Central Town", "Western Forest", "Eastern Checkpoint"
    };
    
    private static final String[] FACTIONS = {
        "Emerald Raiders", "Deadside Nomads", "Zone Stalkers", "Wasteland Scavengers",
        "Last Hope", "Raven Company", "Black Market Traders", "Lone Wolves"
    };
    
    private static final String[] MISSION_TYPES = {
        "Elimination", "Supply Recovery", "Area Control", "VIP Extraction",
        "Reconnaissance", "Sabotage", "Resource Acquisition", "Bounty"
    };
    
    @Override
    public String getName() {
        return "test";
    }
    
    @Override
    public CommandData getCommandData() {
        OptionData eventTypeOption = new OptionData(OptionType.STRING, "event", "Type of event to test", true)
                .addChoice("Killfeed", "killfeed")
                .addChoice("Connection", "connection")
                .addChoice("Airdrop", "airdrop")
                .addChoice("Helicrash", "helicrash")
                .addChoice("Mission", "mission")
                .addChoice("Trader", "trader")
                .addChoice("Bounty", "bounty")
                .addChoice("Suicide", "suicide")
                .addChoice("Falling", "falling");
        
        return Commands.slash("test", "Generate test event messages")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addOptions(eventTypeOption);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Defer reply to avoid timeout
        event.deferReply().queue();
        
        // Extract event type from options
        String eventType = event.getOption("event", "killfeed", OptionMapping::getAsString);
        
        try {
            // Create appropriate mock event based on type
            switch (eventType.toLowerCase()) {
                case "killfeed":
                    sendKillfeedEvent(event);
                    break;
                case "connection":
                    sendConnectionEvent(event);
                    break;
                case "airdrop":
                    sendAirdropEvent(event);
                    break;
                case "helicrash":
                    sendHelicrashEvent(event);
                    break;
                case "mission":
                    sendMissionEvent(event);
                    break;
                case "trader":
                    sendTraderEvent(event);
                    break;
                case "bounty":
                    sendBountyEvent(event);
                    break;
                case "suicide":
                    sendSuicideEvent(event);
                    break;
                case "falling":
                    sendFallingDeathEvent(event);
                    break;
                default:
                    event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                            "Invalid Event Type",
                            "The specified event type is not supported.")
                    ).queue();
            }
            
            logger.info("Test command executed by user {} for event type: {}", 
                    event.getUser().getId(), eventType);
        } catch (Exception e) {
            logger.error("Error processing test event", e);
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed(
                    "Error",
                    "An error occurred while generating the test event: " + e.getMessage())
            ).queue();
        }
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        return new ArrayList<>(); // No additional autocomplete needed beyond fixed options
    }
    
    /**
     * Send a mock killfeed event with properly attached thumbnail image
     */
    private void sendKillfeedEvent(SlashCommandInteractionEvent event) {
        String killer = getRandomElement(PLAYER_NAMES);
        String victim = getRandomElement(PLAYER_NAMES, killer); // Ensure different player
        String weapon = getRandomElement(WEAPONS);
        int distance = random.nextInt(400) + 10; // 10-410m
        
        // First prepare the file to upload
        File iconFile = new File("attached_assets/Killfeed.png");
        if (!iconFile.exists()) {
            System.out.println("WARNING: Killfeed icon not found at: " + iconFile.getAbsolutePath());
            // Try fallback location
            iconFile = new File("src/main/resources/images/Killfeed.png");
        }
        
        // Create the embed with proper thumbnail
        MessageEmbed embed = AdvancedEmbeds.advancedKillfeedEmbed(killer, victim, weapon, distance, false, 0);
        
        if (!iconFile.exists()) {
            // Just send without attachment if icon not found
            event.getHook().sendMessageEmbeds(embed).queue();
            return;
        }
        
        // Manually create FileUpload and send with embed
        try {
            FileUpload iconUpload = FileUpload.fromData(iconFile, "Killfeed.png");
            event.getHook().sendMessageEmbeds(embed)
                  .addFiles(iconUpload)
                  .queue();
            System.out.println("Sent killfeed embed with attached thumbnail: " + iconFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error attaching thumbnail to killfeed: " + e.getMessage());
            event.getHook().sendMessageEmbeds(embed).queue();
        }
    }
    
    /**
     * Send a mock connection event (player join/leave) with properly displayed thumbnail
     */
    private void sendConnectionEvent(SlashCommandInteractionEvent event) {
        boolean isJoining = random.nextBoolean();
        String player = getRandomElement(PLAYER_NAMES);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(isJoining ? "Player Connected" : "Player Disconnected")
                .setDescription(player + (isJoining ? " has joined the server" : " has left the server"))
                .setColor(isJoining ? EmbedUtils.EMERALD_GREEN : EmbedUtils.DARK_GRAY)
                .setThumbnail("attachment://Connections.png") // Direct reference to ensure visibility
                .setFooter("Emerald EU Server", null)
                .setTimestamp(Instant.now());
        
        // Prepare the connection icon file
        File iconFile = new File("attached_assets/Connections.png");
        if (!iconFile.exists()) {
            // Fallback location
            iconFile = new File("src/main/resources/images/Connections.png");
        }
        
        try {
            if (iconFile.exists()) {
                // Send with attachment for proper thumbnail display
                FileUpload iconUpload = FileUpload.fromData(iconFile, "Connections.png");
                event.getHook().sendMessageEmbeds(embed.build())
                     .addFiles(iconUpload)
                     .queue();
                System.out.println("Sent connection embed with thumbnail: " + iconFile.getAbsolutePath());
            } else {
                // Fall back to regular send if icon not found
                com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed.build());
            }
        } catch (Exception e) {
            System.err.println("Error attaching thumbnail: " + e.getMessage());
            com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed.build());
        }
    }
    
    /**
     * Send a mock airdrop event
     */
    private void sendAirdropEvent(SlashCommandInteractionEvent event) {
        String[] statuses = {"Waiting", "Inbound", "Dropped"};
        String status = statuses[random.nextInt(statuses.length)];
        String location = getRandomElement(LOCATIONS);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Airdrop Event")
                .setDescription("An airdrop is " + (status.equals("Dropped") ? "now available" : "inbound") + "!")
                .setColor(EmbedUtils.EMERALD_GREEN)
                .setThumbnail(EmbedUtils.AIRDROP_ICON)
                .addField("Status", status, true)
                .addField("Location", location, true)
                .addField("Contents", "High-tier weapons and supplies", false)
                .setFooter("Emerald EU Server", null)
                .setTimestamp(Instant.now());
        
        com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed.build());
    }
    
    /**
     * Send a mock helicopter crash event
     */
    private void sendHelicrashEvent(SlashCommandInteractionEvent event) {
        String location = getRandomElement(LOCATIONS);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Helicopter Crash")
                .setDescription("A helicopter has crashed in the wilderness!")
                .setColor(new Color(150, 75, 0)) // Brown
                .setThumbnail(EmbedUtils.HELICRASH_ICON)
                .addField("Location", location, true)
                .addField("Loot Tier", "Military Grade", true)
                .addField("Status", "Active", false)
                .setFooter("Emerald EU Server", null)
                .setTimestamp(Instant.now());
        
        com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed.build());
    }
    
    /**
     * Send a mock mission event
     */
    private void sendMissionEvent(SlashCommandInteractionEvent event) {
        String missionType = getRandomElement(MISSION_TYPES);
        String location = getRandomElement(LOCATIONS);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Mission Available")
                .setDescription("A new mission is active in the zone!")
                .setColor(new Color(148, 0, 211)) // Purple
                .setThumbnail(EmbedUtils.MISSION_ICON)
                .addField("Mission Type", missionType, true)
                .addField("Location", location, true)
                .addField("Difficulty", "Hard", true)
                .addField("Rewards", "Premium weapons, gear, and currency", false)
                .setFooter("Emerald EU Server", null)
                .setTimestamp(Instant.now());
        
        com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed.build());
    }
    
    /**
     * Send a mock trader event
     */
    private void sendTraderEvent(SlashCommandInteractionEvent event) {
        String location = getRandomElement(LOCATIONS);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Trader Event")
                .setDescription("A special trader has appeared!")
                .setColor(new Color(0, 128, 0)) // Green
                .setThumbnail(EmbedUtils.TRADER_ICON)
                .addField("Location", location, true)
                .addField("Duration", "30 minutes", true)
                .addField("Special Offers", "Rare weapons and equipment available", false)
                .setFooter("Emerald EU Server", null)
                .setTimestamp(Instant.now());
        
        com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed.build());
    }
    
    /**
     * Send a mock bounty event
     */
    private void sendBountyEvent(SlashCommandInteractionEvent event) {
        String killer = getRandomElement(PLAYER_NAMES);
        String victim = getRandomElement(PLAYER_NAMES, killer);
        int bountyValue = (random.nextInt(20) + 5) * 100; // 500-2500
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Bounty Collected")
                .setDescription(killer + " collected the bounty on " + victim + "'s head")
                .setColor(new Color(212, 175, 55)) // Gold
                .setThumbnail(EmbedUtils.BOUNTY_ICON)
                .addField("Killer", killer, true)
                .addField("Target", victim, true)
                .addField("Bounty Value", bountyValue + " coins", true)
                .setFooter("Emerald EU Server", null)
                .setTimestamp(Instant.now());
        
        com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed.build());
    }
    
    /**
     * Send a mock suicide event
     */
    private void sendSuicideEvent(SlashCommandInteractionEvent event) {
        String player = getRandomElement(PLAYER_NAMES);
        // Use normalized causes without emojis
        String[] normalizedCauses = {"Menu Suicide", "Wrong Button", "Disconnected", "Exit Zone"};
        String cause = normalizedCauses[random.nextInt(normalizedCauses.length)];
        
        MessageEmbed embed = AdvancedEmbeds.advancedSuicideEmbed(player, cause);
        com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed);
    }
    
    /**
     * Send a mock falling death event
     */
    private void sendFallingDeathEvent(SlashCommandInteractionEvent event) {
        String player = getRandomElement(PLAYER_NAMES);
        int height = random.nextInt(30) + 10; // 10-40m
        
        MessageEmbed embed = AdvancedEmbeds.advancedFallingDeathEmbed(player, height);
        com.deadside.bot.utils.EmbedSender.sendEmbed(event.getHook(), embed);
    }
    
    /**
     * Get a random element from an array
     */
    private <T> T getRandomElement(T[] array) {
        return array[random.nextInt(array.length)];
    }
    
    /**
     * Get a random element from an array, excluding a specific element
     */
    private <T> T getRandomElement(T[] array, T exclude) {
        T result;
        do {
            result = getRandomElement(array);
        } while (result.equals(exclude));
        return result;
    }
}