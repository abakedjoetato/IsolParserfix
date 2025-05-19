package com.deadside.bot.ui;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for creating button-based UI menus
 */
public class ButtonMenuBuilder {
    private final List<Button> buttons = new ArrayList<>();
    private int maxButtonsPerRow = 5; // Discord limit is 5 buttons per row
    
    /**
     * Add a primary button (blurple color)
     */
    public ButtonMenuBuilder addPrimaryButton(String id, String label) {
        buttons.add(Button.primary(id, label));
        return this;
    }
    
    /**
     * Add a primary button with emoji
     */
    public ButtonMenuBuilder addPrimaryButton(String id, String label, String emojiUnicode) {
        buttons.add(Button.primary(id, label).withEmoji(Emoji.fromUnicode(emojiUnicode)));
        return this;
    }
    
    /**
     * Add a secondary button (gray color)
     */
    public ButtonMenuBuilder addSecondaryButton(String id, String label) {
        buttons.add(Button.secondary(id, label));
        return this;
    }
    
    /**
     * Add a secondary button with emoji
     */
    public ButtonMenuBuilder addSecondaryButton(String id, String label, String emojiUnicode) {
        buttons.add(Button.secondary(id, label).withEmoji(Emoji.fromUnicode(emojiUnicode)));
        return this;
    }
    
    /**
     * Add a success button (green color)
     */
    public ButtonMenuBuilder addSuccessButton(String id, String label) {
        buttons.add(Button.success(id, label));
        return this;
    }
    
    /**
     * Add a success button with emoji
     */
    public ButtonMenuBuilder addSuccessButton(String id, String label, String emojiUnicode) {
        buttons.add(Button.success(id, label).withEmoji(Emoji.fromUnicode(emojiUnicode)));
        return this;
    }
    
    /**
     * Add a danger button (red color)
     */
    public ButtonMenuBuilder addDangerButton(String id, String label) {
        buttons.add(Button.danger(id, label));
        return this;
    }
    
    /**
     * Add a danger button with emoji
     */
    public ButtonMenuBuilder addDangerButton(String id, String label, String emojiUnicode) {
        buttons.add(Button.danger(id, label).withEmoji(Emoji.fromUnicode(emojiUnicode)));
        return this;
    }
    
    /**
     * Add a link button
     */
    public ButtonMenuBuilder addLinkButton(String url, String label) {
        buttons.add(Button.link(url, label));
        return this;
    }
    
    /**
     * Add a link button with emoji
     */
    public ButtonMenuBuilder addLinkButton(String url, String label, String emojiUnicode) {
        buttons.add(Button.link(url, label).withEmoji(Emoji.fromUnicode(emojiUnicode)));
        return this;
    }
    
    /**
     * Add a disabled button
     */
    public ButtonMenuBuilder addDisabledButton(Button button) {
        buttons.add(button.asDisabled());
        return this;
    }
    
    /**
     * Set the maximum number of buttons per row
     */
    public ButtonMenuBuilder setMaxButtonsPerRow(int maxButtonsPerRow) {
        if (maxButtonsPerRow < 1 || maxButtonsPerRow > 5) {
            throw new IllegalArgumentException("maxButtonsPerRow must be between 1 and 5");
        }
        this.maxButtonsPerRow = maxButtonsPerRow;
        return this;
    }
    
    /**
     * Build the button menu as a list of ActionRows
     */
    public List<ActionRow> build() {
        List<ActionRow> rows = new ArrayList<>();
        
        for (int i = 0; i < buttons.size(); i += maxButtonsPerRow) {
            List<Button> rowButtons = buttons.subList(
                    i, 
                    Math.min(i + maxButtonsPerRow, buttons.size())
            );
            rows.add(ActionRow.of(rowButtons));
        }
        
        return rows;
    }
    
    /**
     * Create a simple pagination menu
     */
    public static List<ActionRow> createPaginationMenu(String baseId, int currentPage, int totalPages) {
        List<Button> paginationButtons = new ArrayList<>();
        
        // First page button
        paginationButtons.add(Button.secondary(
                baseId + ":first", "First")
                .withEmoji(Emoji.fromUnicode("⏮️"))
                .withDisabled(currentPage <= 1)
        );
        
        // Previous page button
        paginationButtons.add(Button.primary(
                baseId + ":prev", "Prev")
                .withEmoji(Emoji.fromUnicode("◀️"))
                .withDisabled(currentPage <= 1)
        );
        
        // Page indicator (non-interactive)
        paginationButtons.add(Button.secondary(
                baseId + ":page", "Page " + currentPage + "/" + totalPages)
                .asDisabled()
        );
        
        // Next page button
        paginationButtons.add(Button.primary(
                baseId + ":next", "Next")
                .withEmoji(Emoji.fromUnicode("▶️"))
                .withDisabled(currentPage >= totalPages)
        );
        
        // Last page button
        paginationButtons.add(Button.secondary(
                baseId + ":last", "Last")
                .withEmoji(Emoji.fromUnicode("⏭️"))
                .withDisabled(currentPage >= totalPages)
        );
        
        return Arrays.asList(ActionRow.of(paginationButtons));
    }
    
    /**
     * Create a confirmation menu with Yes/No buttons
     */
    public static List<ActionRow> createConfirmationMenu(String baseId) {
        return Arrays.asList(ActionRow.of(
                Button.success(baseId + ":yes", "Confirm").withEmoji(Emoji.fromUnicode("✅")),
                Button.danger(baseId + ":no", "Cancel").withEmoji(Emoji.fromUnicode("❌"))
        ));
    }
}