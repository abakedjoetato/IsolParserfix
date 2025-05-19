package com.deadside.bot.utils;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for sending embeds with file attachments for thumbnails
 * Enhanced with Deadside themed styling enforcement
 */
public class EmbedSender {
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("attachment://([\\w\\d.]+)");
    
    // Standard Deadside footer
    private static final String STANDARD_FOOTER = "Powered By Discord.gg/EmeraldServers";
    
    /**
     * Extract attachment filenames from an embed
     * Enhanced to better support thumbnail extraction for proper display on the right side
     * 
     * @param embed The embed to extract from
     * @return List of filenames referenced in the embed
     */
    public static List<String> extractAttachmentFilenames(MessageEmbed embed) {
        List<String> filenames = new ArrayList<>();
        
        // Check thumbnail - critical for right-side display
        if (embed.getThumbnail() != null && embed.getThumbnail().getUrl() != null) {
            String url = embed.getThumbnail().getUrl();
            // Debug the thumbnail URL to help diagnose issues
            System.out.println("Thumbnail URL found: " + url);
            if (url.startsWith("attachment://")) {
                String filename = url.substring("attachment://".length());
                System.out.println("Extracted thumbnail filename: " + filename);
                filenames.add(filename);
            }
        }
        
        // Check image
        if (embed.getImage() != null && embed.getImage().getUrl() != null) {
            String url = embed.getImage().getUrl();
            if (url.startsWith("attachment://")) {
                String filename = url.substring("attachment://".length());
                filenames.add(filename);
            }
        }
        
        // Check author icon
        if (embed.getAuthor() != null && embed.getAuthor().getIconUrl() != null) {
            String url = embed.getAuthor().getIconUrl();
            if (url.startsWith("attachment://")) {
                String filename = url.substring("attachment://".length());
                filenames.add(filename);
            }
        }
        
        // Check footer icon
        if (embed.getFooter() != null && embed.getFooter().getIconUrl() != null) {
            String url = embed.getFooter().getIconUrl();
            if (url.startsWith("attachment://")) {
                String filename = url.substring("attachment://".length());
                filenames.add(filename);
            }
        }
        
        return filenames;
    }
    
    private static void extractFilename(String url, List<String> filenames) {
        Matcher matcher = ATTACHMENT_PATTERN.matcher(url);
        if (matcher.find()) {
            filenames.add(matcher.group(1));
        }
    }
    
    /**
     * Send an embed to a channel with needed file attachments
     * Ensures Deadside themed styling is applied to the embed
     * Enhanced to properly handle thumbnails on the right side
     * 
     * @param channel The channel to send to
     * @param embed The embed to send
     * @return CompletableFuture for the sent message
     */
    public static CompletableFuture<?> sendEmbed(MessageChannel channel, MessageEmbed embed) {
        // Apply Deadside themed styling
        MessageEmbed styledEmbed = ensureDeadsideStyling(embed);
        
        // Extract all attachment filenames from the embed's URLs
        List<String> filenames = extractAttachmentFilenames(styledEmbed);
        System.out.println("Found " + filenames.size() + " attachments in embed: " + 
                           (filenames.isEmpty() ? "none" : String.join(", ", filenames)));
        
        if (filenames.isEmpty()) {
            // No attachments needed, just send the embed
            return channel.sendMessageEmbeds(styledEmbed).submit();
        } else {
            // Create file uploads for each attachment
            List<FileUpload> uploads = new ArrayList<>();
            
            for (String filename : filenames) {
                FileUpload upload = ResourceManager.getImageAsFileUpload(filename);
                if (upload != null) {
                    uploads.add(upload);
                } else {
                    System.err.println("Failed to create FileUpload for: " + filename);
                }
            }
            
            if (uploads.isEmpty()) {
                // If we couldn't create any file uploads, send without attachments
                System.err.println("No valid file uploads could be created, sending embed without attachments");
                return channel.sendMessageEmbeds(styledEmbed).submit();
            } else {
                // Send the embed with all valid file uploads
                return channel.sendMessageEmbeds(styledEmbed)
                        .addFiles(uploads.toArray(new FileUpload[0]))
                        .submit();
            }
        }
    }
    
    /**
     * Ensure an embed follows Deadside themed styling
     * Adds the required Deadside styling elements to any embed that's missing them
     * 
     * @param embed The original embed
     * @return A styled embed with Deadside theming
     */
    public static MessageEmbed ensureDeadsideStyling(MessageEmbed embed) {
        net.dv8tion.jda.api.EmbedBuilder builder = new net.dv8tion.jda.api.EmbedBuilder(embed);
        
        // Ensure footer contains standard text
        if (embed.getFooter() == null || !embed.getFooter().getText().contains("EmeraldServers")) {
            builder.setFooter(STANDARD_FOOTER);
        }
        
        // Ensure timestamp is set
        if (embed.getTimestamp() == null) {
            builder.setTimestamp(java.time.Instant.now());
        }
        
        // Ensure color is set (default to Deadside emerald green if not)
        if (embed.getColorRaw() == 0) {
            builder.setColor(EmbedUtils.EMERALD_GREEN);
        }
        
        // Ensure thumbnail is set if missing
        if (embed.getThumbnail() == null) {
            builder.setThumbnail(ResourceManager.getAttachmentString(ResourceManager.MAIN_LOGO));
        }
        
        return builder.build();
    }
    
    /**
     * Send an embed as a reply to an interaction with needed file attachments
     * Ensures Deadside themed styling is applied to the embed
     * Enhanced to properly handle thumbnails on the right side
     * 
     * @param interaction The interaction to reply to
     * @param embed The embed to send
     * @param ephemeral Whether the reply should be ephemeral
     */
    public static void replyEmbed(IReplyCallback interaction, MessageEmbed embed, boolean ephemeral) {
        // Apply Deadside themed styling
        MessageEmbed styledEmbed = ensureDeadsideStyling(embed);
        
        // Extract all attachment filenames from the embed's URLs
        List<String> filenames = extractAttachmentFilenames(styledEmbed);
        System.out.println("Found " + filenames.size() + " attachments in reply embed: " + 
                           (filenames.isEmpty() ? "none" : String.join(", ", filenames)));
        
        if (filenames.isEmpty()) {
            // No attachments needed, just send the embed
            interaction.replyEmbeds(styledEmbed).setEphemeral(ephemeral).queue();
        } else {
            // Create file uploads for each attachment
            List<FileUpload> uploads = new ArrayList<>();
            
            for (String filename : filenames) {
                FileUpload upload = ResourceManager.getImageAsFileUpload(filename);
                if (upload != null) {
                    uploads.add(upload);
                } else {
                    System.err.println("Failed to create FileUpload for: " + filename);
                }
            }
            
            if (uploads.isEmpty()) {
                // If we couldn't create any file uploads, send without attachments
                System.err.println("No valid file uploads could be created, sending embed without attachments");
                interaction.replyEmbeds(styledEmbed).setEphemeral(ephemeral).queue();
            } else {
                // Send the embed with all valid file uploads
                interaction.replyEmbeds(styledEmbed)
                        .addFiles(uploads.toArray(new FileUpload[0]))
                        .setEphemeral(ephemeral)
                        .queue();
            }
        }
    }
    
    /**
     * Send an embed through an interaction hook with needed file attachments
     * Ensures Deadside themed styling is applied to the embed
     * Enhanced to properly handle thumbnails on the right side
     * 
     * @param hook The interaction hook to send through
     * @param embed The embed to send
     */
    public static void sendEmbed(InteractionHook hook, MessageEmbed embed) {
        // Apply Deadside themed styling
        MessageEmbed styledEmbed = ensureDeadsideStyling(embed);
        
        // Extract all attachment filenames from the embed's URLs
        List<String> filenames = extractAttachmentFilenames(styledEmbed);
        System.out.println("Found " + filenames.size() + " attachments in hook embed: " + 
                           (filenames.isEmpty() ? "none" : String.join(", ", filenames)));
        
        if (filenames.isEmpty()) {
            // No attachments needed, just send the embed
            hook.sendMessageEmbeds(styledEmbed).queue();
        } else {
            // Create file uploads for each attachment
            List<FileUpload> uploads = new ArrayList<>();
            
            for (String filename : filenames) {
                FileUpload upload = ResourceManager.getImageAsFileUpload(filename);
                if (upload != null) {
                    uploads.add(upload);
                } else {
                    System.err.println("Failed to create FileUpload for: " + filename);
                }
            }
            
            if (uploads.isEmpty()) {
                // If we couldn't create any file uploads, send without attachments
                System.err.println("No valid file uploads could be created, sending embed without attachments");
                hook.sendMessageEmbeds(styledEmbed).queue();
            } else {
                // Send the embed with all valid file uploads
                hook.sendMessageEmbeds(styledEmbed)
                        .addFiles(uploads.toArray(new FileUpload[0]))
                        .queue();
            }
        }
    }
}