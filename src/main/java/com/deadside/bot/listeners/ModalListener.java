package com.deadside.bot.listeners;

import com.deadside.bot.db.models.Faction;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.LinkedPlayer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.FactionRepository;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.LinkedPlayerRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.premium.PremiumManager;
import com.deadside.bot.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;

/**
 * Listener for modal dialog interactions
 */
public class ModalListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ModalListener.class);
    private final GameServerRepository gameServerRepository = new GameServerRepository();
    private final FactionRepository factionRepository = new FactionRepository();
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final LinkedPlayerRepository linkedPlayerRepository = new LinkedPlayerRepository();
    private final PremiumManager premiumManager = new PremiumManager();
    
    // Patterns for validation
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\w\\s]{3,32}$");
    private static final Pattern TAG_PATTERN = Pattern.compile("^[\\w]{2,8}$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");
    
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        
        try {
            if (modalId.startsWith("server_config:")) {
                handleServerConfigModal(event, modalId.substring("server_config:".length()));
            } else if (modalId.startsWith("faction_create:")) {
                handleFactionCreateModal(event);
            } else if (modalId.startsWith("player_link:")) {
                handlePlayerLinkModal(event);
            } else if (modalId.startsWith("premium_purchase:")) {
                handlePremiumPurchaseModal(event);
            } else {
                logger.warn("Unknown modal ID: {}", modalId);
            }
        } catch (Exception e) {
            logger.error("Error handling modal interaction", e);
            event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle server configuration modal
     */
    private void handleServerConfigModal(ModalInteractionEvent event, String serverId) {
        event.deferReply(true).queue(); // Ephemeral response
        
        // Extract values from the modal
        String serverName = event.getValue("server_name").getAsString();
        String serverIp = event.getValue("server_ip").getAsString();
        String serverPortStr = event.getValue("server_port").getAsString();
        String serverUser = event.getValue("server_user").getAsString();
        String serverPass = event.getValue("server_pass").getAsString();
        
        // Validate port
        if (!PORT_PATTERN.matcher(serverPortStr).matches()) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Port", 
                            "Server port must be a number between 1 and 65535.")
            ).queue();
            return;
        }
        
        int serverPort;
        try {
            serverPort = Integer.parseInt(serverPortStr);
            if (serverPort < 1 || serverPort > 65535) {
                throw new NumberFormatException("Port out of range");
            }
        } catch (NumberFormatException e) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Port", 
                            "Server port must be a number between 1 and 65535.")
            ).queue();
            return;
        }
        
        // Get guild ID
        long guildId = event.getGuild().getIdLong();
        
        // Check if this is a new server or updating an existing one
        GameServer gameServer;
        boolean isNew = "new".equals(serverId);
        
        if (isNew) {
            // Create a new server
            gameServer = new GameServer(
                    serverName, 
                    serverIp, 
                    serverPort, 
                    serverUser, 
                    serverPass, 
                    guildId
            );
            
            gameServerRepository.save(gameServer);
            
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.successEmbed("Server Added", 
                            "Successfully added Deadside server **" + serverName + "**!\n\n" +
                            "The bot will now start monitoring this server for events and player statistics.")
            ).queue();
            
            logger.info("User {} added new server '{}' for guild {}", 
                    event.getUser().getName(), serverName, event.getGuild().getName());
        } else {
            // Update existing server
            try {
                // Use the serverId directly as a string
                gameServer = gameServerRepository.findById(serverId);
                
                if (gameServer == null) {
                    event.getHook().sendMessageEmbeds(
                            EmbedUtils.errorEmbed("Server Not Found", 
                                    "Could not find server with ID " + serverId + ". Please try adding a new server instead.")
                    ).queue();
                    return;
                }
                
                // Update server details
                gameServer.setName(serverName);
                gameServer.setHost(serverIp);
                gameServer.setPort(serverPort);
                gameServer.setUsername(serverUser);
                gameServer.setPassword(serverPass);
                
                gameServerRepository.save(gameServer);
                
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.successEmbed("Server Updated", 
                                "Successfully updated Deadside server **" + serverName + "**!")
                ).queue();
                
                logger.info("User {} updated server '{}' for guild {}", 
                        event.getUser().getName(), serverName, event.getGuild().getName());
            } catch (NumberFormatException e) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Invalid Server ID", 
                                "The server ID is invalid. Please try adding a new server instead.")
                ).queue();
            }
        }
    }
    
    /**
     * Handle faction creation modal
     */
    private void handleFactionCreateModal(ModalInteractionEvent event) {
        event.deferReply().queue(); // Public response
        
        // Extract values from the modal
        String name = event.getValue("faction_name").getAsString();
        String tag = event.getValue("faction_tag").getAsString();
        String color = event.getValue("faction_color").getAsString();
        String description = event.getValue("faction_desc").getAsString();
        
        // Get guild and user
        Guild guild = event.getGuild();
        User user = event.getUser();
        long guildId = guild.getIdLong();
        long userId = user.getIdLong();
        
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
        List<Faction> userFactions = factionRepository.findByMember(userId);
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
        Faction faction = new Faction(name, tag, description, guildId, userId, color);
        factionRepository.save(faction);
        
        // Parse color for embed
        Color embedColor;
        try {
            embedColor = Color.decode(color);
        } catch (NumberFormatException e) {
            embedColor = EmbedUtils.EMERALD_GREEN;
        }
        
        event.getHook().sendMessageEmbeds(
                EmbedUtils.customEmbed(
                        "🛡️ Faction Created: " + name + " [" + tag + "]",
                        "**Description**: " + description + "\n\n" +
                        "You are now the owner of this faction. Use `/faction member add @user` to add members.",
                        embedColor
                )
        ).queue();
        
        logger.info("User {} created faction {} [{}] in guild {}", 
                user.getName(), name, tag, guild.getName());
    }
    
    /**
     * Handle player linking modal
     */
    private void handlePlayerLinkModal(ModalInteractionEvent event) {
        event.deferReply().queue(); // Public response
        
        // Extract values from the modal
        String playerName = event.getValue("player_name").getAsString();
        String linkType = event.getValue("link_type").getAsString().toLowerCase();
        
        // Validate linkType
        boolean isMain = "main".equals(linkType);
        boolean isAlt = "alt".equals(linkType);
        
        if (!isMain && !isAlt) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Link Type", 
                            "Link type must be either 'main' or 'alt'.")
            ).queue();
            return;
        }
        
        // Get user ID
        long userId = event.getUser().getIdLong();
        
        // Get guild ID for isolation
        Guild guild = event.getGuild();
        if (guild == null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Error", 
                            "This command can only be used in a server.")).queue();
            return;
        }
        
        long guildId = guild.getIdLong();
        String serverId = guild.getName(); // Default to guild name as server ID
        
        // Find player by name with proper isolation
        Player player = playerRepository.findByNameExact(playerName, guildId, serverId);
        if (player == null) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Player Not Found", 
                            "No player found with the name **" + playerName + "**.\n\n" +
                            "Make sure you've entered your exact in-game name and that you've played on the server before.")
            ).queue();
            return;
        }
        ObjectId playerId = player.getId();
        
        // Check if player is already linked
        LinkedPlayer linkedPlayer = linkedPlayerRepository.findByPlayerId(playerId);
        if (linkedPlayer != null && linkedPlayer.getDiscordId() != userId) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Already Linked", 
                            "The player **" + playerName + "** is already linked to another Discord user.")
            ).queue();
            return;
        }
        
        // Get existing linked player or create new one
        LinkedPlayer userLinkedPlayer = linkedPlayerRepository.findByDiscordId(userId);
        
        if (isMain) {
            // Linking as main account
            if (userLinkedPlayer == null) {
                // No existing link, create new
                userLinkedPlayer = new LinkedPlayer(userId, playerId);
                linkedPlayerRepository.save(userLinkedPlayer);
                
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.successEmbed("Account Linked", 
                                "Successfully linked your Discord account to the Deadside player **" + playerName + "** as your main account!")
                ).queue();
            } else {
                // Update existing link
                userLinkedPlayer.setMainPlayerId(playerId);
                linkedPlayerRepository.save(userLinkedPlayer);
                
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.successEmbed("Main Account Updated", 
                                "Successfully updated your main Deadside account to **" + playerName + "**!")
                ).queue();
            }
        } else {
            // Linking as alt account
            if (userLinkedPlayer == null) {
                // Must have a main account first
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("No Main Account", 
                                "You must link a main account before adding alt accounts.\n" +
                                "Please use `link_type: main` first.")
                ).queue();
                return;
            }
            
            // Add alt account
            if (userLinkedPlayer.getAltPlayerIds().contains(playerId.toString())) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Already Linked", 
                                "The player **" + playerName + "** is already linked as one of your alt accounts.")
                ).queue();
                return;
            }
            
            userLinkedPlayer.addAltPlayerId(playerId.toString());
            linkedPlayerRepository.save(userLinkedPlayer);
            
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.successEmbed("Alt Account Added", 
                            "Successfully added **" + playerName + "** as an alt account!")
            ).queue();
        }
        
        logger.info("User {} linked Deadside player '{}' as {}", 
                event.getUser().getName(), playerName, linkType);
    }
    
    /**
     * Handle premium purchase modal
     */
    private void handlePremiumPurchaseModal(ModalInteractionEvent event) {
        event.deferReply(true).queue(); // Ephemeral response
        
        // Extract values from the modal
        String durationStr = event.getValue("duration").getAsString();
        String discountCode = event.getValues().stream()
                .filter(value -> value.getId().equals("discount_code"))
                .findFirst()
                .map(value -> value.getAsString())
                .orElse(null);
        
        // Validate duration
        int duration;
        try {
            duration = Integer.parseInt(durationStr);
            if (duration < 1 || duration > 365) {
                event.getHook().sendMessageEmbeds(
                        EmbedUtils.errorEmbed("Invalid Duration", 
                                "Duration must be between 1 and 365 days.")
                ).queue();
                return;
            }
        } catch (NumberFormatException e) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.errorEmbed("Invalid Duration", 
                            "Duration must be a number of days.")
            ).queue();
            return;
        }
        
        // Get guild ID
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();
        
        // Check if guild already has premium
        if (premiumManager.hasPremium(guildId)) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.infoEmbed("Already Premium", 
                            "This server already has an active premium subscription.\n\n" +
                            premiumManager.getPremiumStatusDetails(guildId) + "\n\n" +
                            "If you wish to extend the subscription, please wait until it expires or contact support.")
            ).queue();
            return;
        }
        
        // Generate payment link - in a real bot, this would connect to Tip4serv
        // For now we'll simulate the process
        String paymentLink = "https://tip4serv.com/payment?guild=" + guildId + 
                "&user=" + userId + "&duration=" + duration;
        
        if (discountCode != null && !discountCode.isEmpty()) {
            paymentLink += "&discount=" + discountCode;
        }
        
        // Calculate price (example)
        double basePrice = 4.99;
        double totalPrice = basePrice;
        
        // Apply discount if valid code
        boolean discountApplied = false;
        if (discountCode != null && !discountCode.isEmpty()) {
            if ("DEADSIDE20".equalsIgnoreCase(discountCode)) {
                totalPrice = totalPrice * 0.8; // 20% off
                discountApplied = true;
            }
        }
        
        // Calculate total based on duration
        if (duration > 1) {
            // Apply bulk discount
            if (duration >= 90) {
                totalPrice = totalPrice * 0.7 * duration; // 30% off for 90+ days
            } else if (duration >= 30) {
                totalPrice = totalPrice * 0.8 * duration; // 20% off for 30+ days
            } else {
                totalPrice = totalPrice * 0.9 * duration; // 10% off for multiple days
            }
        }
        
        // Create a payment summary
        StringBuilder description = new StringBuilder();
        description.append("**Duration**: ").append(duration).append(" days\n");
        description.append("**Base Price**: $").append(String.format("%.2f", basePrice)).append("/day\n");
        
        if (discountApplied) {
            description.append("**Discount Code**: ").append(discountCode).append(" (20% off)\n");
        }
        
        if (duration > 1) {
            description.append("**Bulk Discount**: ");
            if (duration >= 90) {
                description.append("30% off (90+ days)\n");
            } else if (duration >= 30) {
                description.append("20% off (30+ days)\n");
            } else {
                description.append("10% off (multiple days)\n");
            }
        }
        
        description.append("**Total**: $").append(String.format("%.2f", totalPrice)).append("\n\n");
        description.append("Click the button below to complete your purchase.");
        
        // Send payment information with link
        event.getHook().sendMessageEmbeds(
                EmbedUtils.premiumEmbed("Premium Purchase Summary", description.toString())
        ).addActionRow(
                net.dv8tion.jda.api.interactions.components.buttons.Button.link(paymentLink, "Complete Purchase")
        ).queue();
        
        logger.info("User {} initiated premium purchase for guild {} ({})", 
                event.getUser().getName(), event.getGuild().getName(), guildId);
    }
}