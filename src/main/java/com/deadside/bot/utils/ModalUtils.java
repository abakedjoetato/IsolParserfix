package com.deadside.bot.utils;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating and managing modal dialogs for complex input
 */
public class ModalUtils {
    private static final Logger logger = LoggerFactory.getLogger(ModalUtils.class);
    
    /**
     * Create a server configuration modal
     * @param member The member who will receive the modal
     * @param serverId The ID of the server being configured (or null for new server)
     * @return The modal to show
     */
    public static Modal createServerConfigModal(Member member, String serverId) {
        String modalId = serverId != null ? "server_config:" + serverId : "server_config:new";
        
        // Create text inputs for server configuration
        TextInput serverNameInput = TextInput.create("server_name", "Server Name", TextInputStyle.SHORT)
                .setPlaceholder("My Deadside Server")
                .setMinLength(3)
                .setMaxLength(100)
                .setRequired(true)
                .build();
                
        TextInput serverIpInput = TextInput.create("server_ip", "Server IP/Host", TextInputStyle.SHORT)
                .setPlaceholder("127.0.0.1 or hostname")
                .setMinLength(3)
                .setMaxLength(100)
                .setRequired(true)
                .build();
                
        TextInput serverPortInput = TextInput.create("server_port", "Server Port", TextInputStyle.SHORT)
                .setPlaceholder("22")
                .setMinLength(1)
                .setMaxLength(5)
                .setRequired(true)
                .build();
                
        TextInput serverUserInput = TextInput.create("server_user", "SFTP Username", TextInputStyle.SHORT)
                .setPlaceholder("username")
                .setMinLength(1)
                .setMaxLength(50)
                .setRequired(true)
                .build();
                
        TextInput serverPassInput = TextInput.create("server_pass", "SFTP Password", TextInputStyle.SHORT)
                .setPlaceholder("password")
                .setMinLength(1)
                .setMaxLength(100)
                .setRequired(true)
                .build();
                
        // Build the modal
        return Modal.create(modalId, "Server Configuration")
                .addActionRows(
                        ActionRow.of(serverNameInput),
                        ActionRow.of(serverIpInput),
                        ActionRow.of(serverPortInput),
                        ActionRow.of(serverUserInput),
                        ActionRow.of(serverPassInput)
                )
                .build();
    }
    
    /**
     * Create a faction creation modal
     * @param event The slash command event that triggered this modal
     * @return The modal to show
     */
    public static Modal createFactionModal(SlashCommandInteractionEvent event) {
        String modalId = "faction_create:" + event.getUser().getId();
        
        // Create text inputs for faction creation
        TextInput factionNameInput = TextInput.create("faction_name", "Faction Name", TextInputStyle.SHORT)
                .setPlaceholder("Alpha Squad")
                .setMinLength(3)
                .setMaxLength(32)
                .setRequired(true)
                .build();
                
        TextInput factionTagInput = TextInput.create("faction_tag", "Faction Tag", TextInputStyle.SHORT)
                .setPlaceholder("ALPHA")
                .setMinLength(2)
                .setMaxLength(8)
                .setRequired(true)
                .build();
                
        TextInput factionColorInput = TextInput.create("faction_color", "Faction Color (Hex)", TextInputStyle.SHORT)
                .setPlaceholder("#00FF00")
                .setMinLength(7)
                .setMaxLength(7)
                .setRequired(true)
                .setValue("#00AA00") // Default green color
                .build();
                
        TextInput factionDescInput = TextInput.create("faction_desc", "Faction Description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Tell us about your faction...")
                .setMinLength(10)
                .setMaxLength(1000)
                .setRequired(true)
                .build();
                
        // Build the modal
        return Modal.create(modalId, "Create Faction")
                .addActionRows(
                        ActionRow.of(factionNameInput),
                        ActionRow.of(factionTagInput),
                        ActionRow.of(factionColorInput),
                        ActionRow.of(factionDescInput)
                )
                .build();
    }
    
    /**
     * Create a player link modal
     * @param event The slash command event that triggered this modal
     * @return The modal to show
     */
    public static Modal createPlayerLinkModal(SlashCommandInteractionEvent event) {
        String modalId = "player_link:" + event.getUser().getId();
        
        // Create text inputs for player linking
        TextInput playerNameInput = TextInput.create("player_name", "In-Game Player Name", TextInputStyle.SHORT)
                .setPlaceholder("Your exact in-game name")
                .setMinLength(3)
                .setMaxLength(32)
                .setRequired(true)
                .build();
                
        TextInput linkTypeInput = TextInput.create("link_type", "Link Type", TextInputStyle.SHORT)
                .setPlaceholder("main or alt")
                .setMinLength(3)
                .setMaxLength(4)
                .setRequired(true)
                .setValue("main") // Default value
                .build();
                
        // Build the modal
        return Modal.create(modalId, "Link Player Account")
                .addActionRows(
                        ActionRow.of(playerNameInput),
                        ActionRow.of(linkTypeInput)
                )
                .build();
    }
    
    /**
     * Create a premium purchase modal
     * @param event The slash command event that triggered this modal
     * @return The modal to show
     */
    public static Modal createPremiumPurchaseModal(SlashCommandInteractionEvent event) {
        String modalId = "premium_purchase:" + event.getGuild().getId();
        return createPremiumPurchaseModalInternal(modalId);
    }
    
    /**
     * Create a premium purchase modal from a button interaction
     * @param event The button interaction event that triggered this modal
     * @return The modal to show
     */
    public static Modal createPremiumPurchaseModal(ButtonInteractionEvent event) {
        String modalId = "premium_purchase:" + event.getGuild().getId();
        return createPremiumPurchaseModalInternal(modalId);
    }
    
    /**
     * Internal helper method to create premium purchase modal
     * @param modalId The modal ID to use
     * @return The configured modal
     */
    private static Modal createPremiumPurchaseModalInternal(String modalId) {
        
        // Create text inputs for premium purchase info
        TextInput durationInput = TextInput.create("duration", "Duration (days)", TextInputStyle.SHORT)
                .setPlaceholder("30")
                .setMinLength(1)
                .setMaxLength(3)
                .setRequired(true)
                .setValue("30") // Default value
                .build();
                
        TextInput discountCodeInput = TextInput.create("discount_code", "Discount Code (optional)", TextInputStyle.SHORT)
                .setPlaceholder("Enter discount code if you have one")
                .setRequired(false)
                .build();
                
        // Build the modal
        return Modal.create(modalId, "Premium Purchase")
                .addActionRows(
                        ActionRow.of(durationInput),
                        ActionRow.of(discountCodeInput)
                )
                .build();
    }
}