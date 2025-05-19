package com.deadside.bot.utils;

import java.awt.Color;

/**
 * Utility class for maintaining accessibility standards in UI elements
 */
public class AccessibilityUtils {
    
    // WCAG 2.0 minimum contrast ratio (4.5:1)
    private static final double MIN_CONTRAST_RATIO = 4.5;
    
    /**
     * Make sure a color has sufficient contrast for text
     * @param color The original color
     * @return An accessibility-enhanced version of the color
     */
    public static Color getAccessibleColor(Color color) {
        // Simply return the original color for now - in real impl, would check contrast ratios
        return color;
    }
    
    /**
     * Check if a color meets accessibility standards
     * @param color The color to check
     * @return True if the color meets accessibility standards
     */
    public static boolean meetsAccessibilityStandards(Color color) {
        // This would normally calculate contrast ratio against white or black
        // For now just check if it's not extremely low contrast
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return luminance > 0.1 && luminance < 0.9;
    }
    
    /**
     * Lightens a color if it's too dark for visibility
     * @param color The original color
     * @return A lighter version if needed
     */
    public static Color lightenIfTooDeep(Color color) {
        // Check if color is very dark
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        
        if (luminance < 0.2) {
            // Create a lighter version
            return new Color(
                Math.min(255, (int)(color.getRed() * 1.5)),
                Math.min(255, (int)(color.getGreen() * 1.5)),
                Math.min(255, (int)(color.getBlue() * 1.5))
            );
        }
        
        return color;
    }
    
    /**
     * Darkens a color if it's too light for visibility
     * @param color The original color
     * @return A darker version if needed
     */
    public static Color darkenIfTooLight(Color color) {
        // Check if color is very light
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        
        if (luminance > 0.8) {
            // Create a darker version
            return new Color(
                (int)(color.getRed() * 0.7),
                (int)(color.getGreen() * 0.7),
                (int)(color.getBlue() * 0.7)
            );
        }
        
        return color;
    }
}