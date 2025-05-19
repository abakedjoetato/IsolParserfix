package com.deadside.bot.ui;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for creating selection menu-based UI elements
 */
public class SelectMenuBuilder {
    private final List<SelectOption> options = new ArrayList<>();
    private String id;
    private String placeholder;
    private int minValues = 1;
    private int maxValues = 1;
    private boolean disabled = false;
    
    /**
     * Create a new SelectMenuBuilder with the given ID
     */
    public SelectMenuBuilder(String id) {
        this.id = id;
    }
    
    /**
     * Set the placeholder text for the menu
     */
    public SelectMenuBuilder setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }
    
    /**
     * Set the minimum number of selections
     */
    public SelectMenuBuilder setMinValues(int minValues) {
        this.minValues = minValues;
        return this;
    }
    
    /**
     * Set the maximum number of selections
     */
    public SelectMenuBuilder setMaxValues(int maxValues) {
        this.maxValues = maxValues;
        return this;
    }
    
    /**
     * Set whether the menu is disabled
     */
    public SelectMenuBuilder setDisabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }
    
    /**
     * Add an option to the menu
     */
    public SelectMenuBuilder addOption(String value, String label) {
        options.add(SelectOption.of(label, value));
        return this;
    }
    
    /**
     * Add an option to the menu with description
     */
    public SelectMenuBuilder addOption(String value, String label, String description) {
        options.add(SelectOption.of(label, value).withDescription(description));
        return this;
    }
    
    /**
     * Add an option to the menu with emoji
     */
    public SelectMenuBuilder addOption(String value, String label, String emojiUnicode, String description) {
        options.add(SelectOption.of(label, value)
                .withDescription(description)
                .withEmoji(Emoji.fromUnicode(emojiUnicode)));
        return this;
    }
    
    /**
     * Add a default selected option to the menu
     */
    public SelectMenuBuilder addDefaultOption(String value, String label, String description) {
        options.add(SelectOption.of(label, value)
                .withDescription(description)
                .withDefault(true));
        return this;
    }
    
    /**
     * Build the select menu as an ActionRow
     */
    public ActionRow build() {
        if (options.isEmpty()) {
            throw new IllegalStateException("Select menu must have at least one option");
        }
        
        StringSelectMenu.Builder builder = StringSelectMenu.create(id)
                .addOptions(options)
                .setMinValues(minValues)
                .setMaxValues(Math.min(maxValues, options.size()));
        
        if (placeholder != null && !placeholder.isEmpty()) {
            builder.setPlaceholder(placeholder);
        }
        
        if (disabled) {
            builder.setDisabled(true);
        }
        
        return ActionRow.of(builder.build());
    }
    
    /**
     * Create a simple server selection menu
     */
    public static ActionRow createServerSelectMenu(String id, List<String> serverNames) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(id)
                .setPlaceholder("Select a server")
                .setMinValues(1)
                .setMaxValues(1);
        
        for (String serverName : serverNames) {
            menuBuilder.addOption(serverName, serverName);
        }
        
        return ActionRow.of(menuBuilder.build());
    }
    
    /**
     * Create a simple weapon type filter menu
     */
    public static ActionRow createWeaponFilterMenu(String id) {
        return new SelectMenuBuilder(id)
                .setPlaceholder("Filter by weapon type")
                .setMinValues(1)
                .setMaxValues(1)
                .addOption("all", "All Weapons", "🔫", "Show all weapon types")
                .addDefaultOption("all", "All Weapons", "Show all weapon types")
                .addOption("assault", "Assault Rifles", "🔫", "Show only assault rifles")
                .addOption("sniper", "Sniper Rifles", "🎯", "Show only sniper rifles")
                .addOption("shotgun", "Shotguns", "💥", "Show only shotguns")
                .addOption("smg", "SMGs", "⚡", "Show only submachine guns")
                .addOption("pistol", "Pistols", "🔫", "Show only pistols")
                .addOption("melee", "Melee Weapons", "🔪", "Show only melee weapons")
                .build();
    }
    
    /**
     * Create a time period selection menu
     */
    public static ActionRow createTimePeriodMenu(String id) {
        return new SelectMenuBuilder(id)
                .setPlaceholder("Select time period")
                .setMinValues(1)
                .setMaxValues(1)
                .addOption("day", "Last 24 Hours", "🕒", "Show data from the last 24 hours")
                .addOption("week", "Last 7 Days", "📅", "Show data from the last 7 days")
                .addDefaultOption("month", "Last 30 Days", "Show data from the last 30 days")
                .addOption("all", "All Time", "📊", "Show all historical data")
                .build();
    }
}