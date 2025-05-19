package com.deadside.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

/**
 * Interface for all slash commands
 */
public interface ICommand {
    /**
     * Get the name of the command
     */
    String getName();
    
    /**
     * Get the command data for registration
     */
    CommandData getCommandData();
    
    /**
     * Execute the command
     */
    void execute(SlashCommandInteractionEvent event);
    
    /**
     * Handle autocomplete for this command.
     * Default implementation returns an empty list, meaning no autocomplete suggestions.
     * 
     * @param event The autocomplete event
     * @return List of choices for autocomplete, maximum 25 choices
     */
    default List<Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        return List.of(); // Default: no autocomplete
    }
}