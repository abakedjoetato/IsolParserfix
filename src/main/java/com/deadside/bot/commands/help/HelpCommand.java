package com.deadside.bot.commands.help;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedThemes;
import com.deadside.bot.utils.ResourceManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced help command with categories and dropdown menus
 * Provides detailed information about the bot's functionality
 */
public class HelpCommand extends ListenerAdapter implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(HelpCommand.class);
    
    // Command categories for organization
    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_ECONOMY = "economy";
    private static final String CATEGORY_STATS = "stats";
    private static final String CATEGORY_ADMIN = "admin";
    private static final String CATEGORY_EVENTS = "events";
    private static final String CATEGORY_FACTION = "faction";
    
    // Map of category IDs to their display names and descriptions
    private final Map<String, CategoryInfo> categories = new HashMap<>();
    
    // Map of category IDs to their commands
    private final Map<String, List<CommandInfo>> commandsByCategory = new HashMap<>();
    
    /**
     * Initialize the help command with categories and command information
     */
    public HelpCommand() {
        initializeCategories();
        populateCommands();
    }
    
    /**
     * Initialize category information
     */
    private void initializeCategories() {
        // Define categories with names and descriptions (no emojis)
        categories.put(CATEGORY_GENERAL, new CategoryInfo(
                "General",
                "Basic bot commands and utilities"
        ));
        
        categories.put(CATEGORY_ECONOMY, new CategoryInfo(
                "Economy & Bounties",
                "Currency, gambling, and bounty hunting features"
        ));
        
        categories.put(CATEGORY_STATS, new CategoryInfo(
                "Player Statistics",
                "Track kills, deaths, and player performance"
        ));
        
        categories.put(CATEGORY_ADMIN, new CategoryInfo(
                "Administration",
                "Server management and admin-only features"
        ));
        
        categories.put(CATEGORY_EVENTS, new CategoryInfo(
                "Game Events",
                "Tracking of in-game events like airdrops and missions"
        ));
        
        categories.put(CATEGORY_FACTION, new CategoryInfo(
                "Factions",
                "Create and manage player factions"
        ));
        
        // Initialize command lists for each category
        for (String categoryId : categories.keySet()) {
            commandsByCategory.put(categoryId, new ArrayList<>());
        }
    }
    
    /**
     * Populate commands for each category
     */
    private void populateCommands() {
        // General commands
        commandsByCategory.get(CATEGORY_GENERAL).add(new CommandInfo(
                "help",
                "Shows this help menu",
                "Displays information about all available commands organized by category",
                false
        ));
        
        commandsByCategory.get(CATEGORY_GENERAL).add(new CommandInfo(
                "ping",
                "Check bot response time",
                "Verify the bot is online and responsive, and see latency information",
                false
        ));
        
        commandsByCategory.get(CATEGORY_GENERAL).add(new CommandInfo(
                "status",
                "Check server status",
                "Shows current online player count and server information",
                false
        ));
        
        // Economy commands
        commandsByCategory.get(CATEGORY_ECONOMY).add(new CommandInfo(
                "balance",
                "Check your coin balance",
                "View your current coin balance and earnings statistics",
                false
        ));
        
        commandsByCategory.get(CATEGORY_ECONOMY).add(new CommandInfo(
                "bounty",
                "Place or check bounties",
                "Place bounties on other players or view active bounties",
                false
        ));
        
        commandsByCategory.get(CATEGORY_ECONOMY).add(new CommandInfo(
                "daily",
                "Collect daily coins",
                "Get your daily coin reward (resets every 24 hours)",
                false
        ));
        
        commandsByCategory.get(CATEGORY_ECONOMY).add(new CommandInfo(
                "gamble",
                "Gamble your coins",
                "Try your luck and bet coins for a chance to win more",
                false
        ));
        
        // Statistic commands
        commandsByCategory.get(CATEGORY_STATS).add(new CommandInfo(
                "profile",
                "View your player profile",
                "Shows your kill stats, death ratio, and other player metrics",
                false
        ));
        
        commandsByCategory.get(CATEGORY_STATS).add(new CommandInfo(
                "leaderboard",
                "View player rankings",
                "View top players by kills, bounties, or other metrics",
                false
        ));
        
        commandsByCategory.get(CATEGORY_STATS).add(new CommandInfo(
                "weapons",
                "View weapon statistics",
                "See your most used weapons and effectiveness with each",
                false
        ));
        
        // Admin commands
        commandsByCategory.get(CATEGORY_ADMIN).add(new CommandInfo(
                "config",
                "Configure bot settings",
                "Set up feed channels, permissions, and other bot features",
                true
        ));
        
        commandsByCategory.get(CATEGORY_ADMIN).add(new CommandInfo(
                "test",
                "Generate test events",
                "Create sample events to test feed formatting and channels",
                true
        ));
        
        commandsByCategory.get(CATEGORY_ADMIN).add(new CommandInfo(
                "reset",
                "Reset player or server data",
                "Clear statistics for a player or the entire server",
                true
        ));
        
        // Event commands
        commandsByCategory.get(CATEGORY_EVENTS).add(new CommandInfo(
                "events",
                "View recent server events",
                "List recent events like airdrops, helicopter crashes, etc.",
                false
        ));
        
        commandsByCategory.get(CATEGORY_EVENTS).add(new CommandInfo(
                "kills",
                "View recent kill feed",
                "Shows recent player kills and kill information",
                false
        ));
        
        // Faction commands
        commandsByCategory.get(CATEGORY_FACTION).add(new CommandInfo(
                "faction",
                "Manage your faction",
                "Create, join, or leave factions",
                false
        ));
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash("help", "Display help information for bot commands");
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            // Create category selection menu
            StringSelectMenu categoriesMenu = StringSelectMenu.create("help:category")
                    .setPlaceholder("Select a command category")
                    .addOptions(getCategoryOptions())
                    .build();
            
            // Create main help embed
            EmbedBuilder mainEmbed = new EmbedBuilder()
                    .setTitle("Deadside Bot Help")
                    .setDescription("Welcome to the Deadside Discord Bot help system. Use the dropdown menu below to browse command categories.")
                    .setColor(EmbedUtils.EMERALD_GREEN)
                    .addField("About", "This bot provides features for Deadside game servers including killfeed tracking, player statistics, an economy system, and more.", false)
                    .addField("Navigation", "Select a category from the dropdown menu to see available commands.", false)
                    .setFooter(EmbedUtils.STANDARD_FOOTER)
                    .setTimestamp(Instant.now())
                    .setThumbnail("attachment://" + ResourceManager.MAIN_LOGO);
            
            FileUpload icon = ResourceManager.getImageAsFileUpload(ResourceManager.MAIN_LOGO);
            
            // Reply with the embed and category selection menu
            event.replyEmbeds(mainEmbed.build())
                    .addComponents(ActionRow.of(categoriesMenu))
                    .addFiles(icon)
                    .queue();
            
        } catch (Exception e) {
            logger.error("Error executing help command", e);
            event.replyEmbeds(EmbedThemes.errorEmbed(
                    "Error",
                    "An error occurred while processing the help command.")
            ).setEphemeral(true).queue();
        }
    }
    
    @Override
    public List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        return new ArrayList<>(); // No autocomplete needed
    }
    
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        // Handle dropdown menu selections for help categories
        if (!event.getComponentId().startsWith("help:")) {
            return; // Not our component
        }
        
        if (event.getComponentId().equals("help:category")) {
            // Category selection
            String selectedCategory = event.getValues().get(0);
            displayCategoryCommands(event, selectedCategory);
        } else if (event.getComponentId().equals("help:back")) {
            // Back button selection
            showMainMenu(event);
        }
    }
    
    /**
     * Show the main help menu
     */
    private void showMainMenu(StringSelectInteractionEvent event) {
        // Create category selection menu
        StringSelectMenu categoriesMenu = StringSelectMenu.create("help:category")
                .setPlaceholder("Select a command category")
                .addOptions(getCategoryOptions())
                .build();
        
        // Create main help embed
        EmbedBuilder mainEmbed = new EmbedBuilder()
                .setTitle("Deadside Bot Help")
                .setDescription("Welcome to the Deadside Discord Bot help system. Use the dropdown menu below to browse command categories.")
                .setColor(EmbedUtils.EMERALD_GREEN)
                .addField("About", "This bot provides features for Deadside game servers including killfeed tracking, player statistics, an economy system, and more.", false)
                .addField("Navigation", "Select a category from the dropdown menu to see available commands.", false)
                .setFooter(EmbedUtils.STANDARD_FOOTER)
                .setTimestamp(Instant.now())
                .setThumbnail("attachment://" + ResourceManager.MAIN_LOGO);
        
        FileUpload icon = ResourceManager.getImageAsFileUpload(ResourceManager.MAIN_LOGO);
        
        // Edit the message with the main menu
        event.editMessageEmbeds(mainEmbed.build())
                .setComponents(ActionRow.of(categoriesMenu))
                .setFiles(icon)
                .queue();
    }
    
    /**
     * Display commands for a specific category
     */
    private void displayCategoryCommands(StringSelectInteractionEvent event, String categoryId) {
        if (!categories.containsKey(categoryId)) {
            event.reply("Invalid category selected.").setEphemeral(true).queue();
            return;
        }
        
        CategoryInfo category = categories.get(categoryId);
        List<CommandInfo> commands = commandsByCategory.get(categoryId);
        
        // Create back button
        StringSelectMenu backMenu = StringSelectMenu.create("help:back")
                .setPlaceholder("Return to categories")
                .addOption("Back to Categories", "back", "Return to the main help menu")
                .build();
        
        // Build embed for category
        EmbedBuilder categoryEmbed = new EmbedBuilder()
                .setTitle(category.name + " Commands")
                .setDescription(category.description)
                .setColor(EmbedUtils.EMERALD_GREEN)
                .setFooter(EmbedUtils.STANDARD_FOOTER)
                .setTimestamp(Instant.now());
        
        // Add icon based on category
        String iconName = getIconForCategory(categoryId);
        if (iconName != null) {
            categoryEmbed.setThumbnail("attachment://" + iconName);
        }
        
        // Add commands to the embed
        for (CommandInfo command : commands) {
            String commandDesc = command.briefDescription;
            if (command.requiresAdmin) {
                commandDesc += " (Admin Only)";
            }
            
            categoryEmbed.addField("/" + command.name, commandDesc, false);
        }
        
        // Get icon file
        FileUpload icon = null;
        if (iconName != null) {
            icon = ResourceManager.getImageAsFileUpload(iconName);
        }
        
        // Edit the message with the category information
        if (icon != null) {
            event.editMessageEmbeds(categoryEmbed.build())
                    .setComponents(ActionRow.of(backMenu))
                    .setFiles(icon)
                    .queue();
        } else {
            event.editMessageEmbeds(categoryEmbed.build())
                    .setComponents(ActionRow.of(backMenu))
                    .queue();
        }
    }
    
    /**
     * Get appropriate icon for a category
     */
    private String getIconForCategory(String categoryId) {
        switch (categoryId) {
            case CATEGORY_GENERAL:
                return ResourceManager.MAIN_LOGO;
            case CATEGORY_ECONOMY:
                return ResourceManager.TRADER_ICON;
            case CATEGORY_STATS:
                return ResourceManager.WEAPON_STATS_ICON;
            case CATEGORY_ADMIN:
                return ResourceManager.CONNECTIONS_ICON;
            case CATEGORY_EVENTS:
                return ResourceManager.MISSION_ICON;
            case CATEGORY_FACTION:
                return ResourceManager.FACTION_ICON;
            default:
                return ResourceManager.MAIN_LOGO;
        }
    }
    
    /**
     * Create SelectOptions for all categories
     */
    private List<SelectOption> getCategoryOptions() {
        List<SelectOption> options = new ArrayList<>();
        
        for (Map.Entry<String, CategoryInfo> entry : categories.entrySet()) {
            String id = entry.getKey();
            CategoryInfo info = entry.getValue();
            
            SelectOption option = SelectOption.of(info.name, id)
                    .withDescription(info.description);
            
            options.add(option);
        }
        
        return options;
    }
    
    /**
     * Static class to hold category information
     */
    private static class CategoryInfo {
        public final String name;
        public final String description;
        
        public CategoryInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
    
    /**
     * Static class to hold command information
     */
    private static class CommandInfo {
        public final String name;
        public final String briefDescription;
        public final String fullDescription;
        public final boolean requiresAdmin;
        
        public CommandInfo(String name, String briefDescription, String fullDescription, boolean requiresAdmin) {
            this.name = name;
            this.briefDescription = briefDescription;
            this.fullDescription = fullDescription;
            this.requiresAdmin = requiresAdmin;
        }
    }
}