package com.deadside.bot.commands.admin;

import com.deadside.bot.commands.ICommand;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.premium.PremiumManager;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.EmbedSender;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

/**
 * Command for managing premium features and subscriptions
 */
public class PremiumCommand implements ICommand {
    private static final Logger logger = LoggerFactory.getLogger(PremiumCommand.class);
    private final PremiumManager premiumManager;
    private final GameServerRepository serverRepository;
    private static final Color PREMIUM_COLOR = new Color(26, 188, 156); // Emerald green color for premium
    
    public PremiumCommand() {
        this.premiumManager = new PremiumManager();
        this.serverRepository = new GameServerRepository();
    }
    
    @Override
    public String getName() {
        return "premium";
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        
        if (guild == null || member == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Error", "This command can only be used in a server."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Check if user has admin permissions
        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied", 
                    "You need Administrator permission to manage premium features."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Error", "Invalid subcommand."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Defer reply for database operations
        event.deferReply().queue();
        
        switch (subcommand) {
            case "status":
                handleStatusSubcommand(event, guild);
                break;
                
            case "enable":
                handleEnableSubcommand(event, guild);
                break;
                
            case "disable":
                handleDisableSubcommand(event, guild);
                break;
                
            case "verify":
                handleVerifySubcommand(event, guild, member);
                break;
                
            case "assign":
                handleAssignSubcommand(event, guild);
                break;
                
            case "unassign":
                handleUnassignSubcommand(event, guild);
                break;
                
            case "list":
                handleListSubcommand(event, guild);
                break;
                
            default:
                event.replyEmbeds(EmbedUtils.errorEmbed("Error", "Unknown subcommand."))
                    .setEphemeral(true)
                    .queue();
                break;
        }
    }
    
    /**
     * Handle the /premium status subcommand
     */
    private void handleStatusSubcommand(SlashCommandInteractionEvent event, Guild guild) {
        // Get the server-specific premium status if a server is specified
        String serverName = event.getOption("server", OptionMapping::getAsString);
        
        if (serverName != null) {
            // Show premium status for a specific server
            handleServerSpecificStatus(event, guild, serverName);
        } else {
            // Show overall premium status for the guild
            handleGuildStatus(event, guild);
        }
    }
    
    /**
     * Handle displaying premium status for a specific server
     */
    private void handleServerSpecificStatus(SlashCommandInteractionEvent event, Guild guild, String serverName) {
        // Check if the server exists
        GameServer server = serverRepository.findByGuildIdAndName(guild.getIdLong(), serverName);
        if (server == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", 
                    "Server '" + serverName + "' does not exist."))
                .queue();
            return;
        }
        
        // Check if this specific server has premium
        boolean hasPremium = server.isPremium();
        String expirationInfo = "";
        
        if (hasPremium) {
            if (server.getPremiumUntil() == 0) {
                expirationInfo = "No expiration date (permanent premium)";
            } else {
                long remaining = server.getPremiumUntil() - System.currentTimeMillis();
                if (remaining <= 0) {
                    hasPremium = false;
                    expirationInfo = "Premium has expired";
                } else {
                    long daysRemaining = remaining / (24L * 60L * 60L * 1000L);
                    expirationInfo = String.format("Expires in %d days", daysRemaining);
                }
            }
        }
        
        if (hasPremium) {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                    "✨ Server Premium Status",
                    "Server **" + serverName + "** has **PREMIUM** features enabled!\n\n" + 
                    "Status: " + expirationInfo + "\n\n" +
                    "Premium features include:\n" +
                    "• Advanced statistics and leaderboards\n" +
                    "• Faction system\n" +
                    "• Economy features\n" +
                    "• Real-time event notifications\n" +
                    "• Server monitoring and detailed logs",
                    PREMIUM_COLOR
                ))
                .queue();
        } else {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                    "Server Premium Status",
                    "Server **" + serverName + "** is using the **FREE** tier.\n\n" + 
                    "Status: " + expirationInfo + "\n\n" +
                    "Only the basic killfeed feature is available. Upgrade to premium to unlock:\n" +
                    "• Advanced statistics and leaderboards\n" +
                    "• Faction system\n" +
                    "• Economy features\n" +
                    "• Real-time event notifications\n" +
                    "• Server monitoring and detailed logs\n\n" +
                    "Use `/premium assign " + serverName + "` to activate premium for this server.",
                    new Color(189, 195, 199) // Light gray for free tier
                ))
                .queue();
        }
    }
    
    /**
     * Handle displaying overall premium status for the guild
     */
    private void handleGuildStatus(SlashCommandInteractionEvent event, Guild guild) {
        // Count how many servers have premium
        int premiumServers = premiumManager.countPremiumServers(guild.getIdLong());
        int totalServers = serverRepository.findAllByGuildId(guild.getIdLong()).size();
        int availableSlots = premiumManager.getAvailablePremiumSlots(guild.getIdLong());
        
        if (premiumServers > 0) {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                    "✨ Premium Status",
                    "This Discord server has **" + premiumServers + " out of " + totalServers + 
                    "** game servers with premium enabled!\n\n" +
                    "You are using " + premiumServers + " out of " + availableSlots + " available premium slots.\n\n" +
                    "Premium features include:\n" +
                    "• Advanced statistics and leaderboards\n" +
                    "• Faction system\n" +
                    "• Economy features\n" +
                    "• Real-time event notifications\n" +
                    "• Server monitoring and detailed logs\n\n" +
                    "Use `/premium list` to see which servers have premium.",
                    PREMIUM_COLOR
                ))
                .queue();
        } else {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                    "Premium Status",
                    "This Discord server has **NO** game servers with premium enabled.\n\n" +
                    "You have " + availableSlots + " available premium slots.\n\n" +
                    "Only the basic killfeed feature is available. Upgrade to premium to unlock:\n" +
                    "• Advanced statistics and leaderboards\n" +
                    "• Faction system\n" +
                    "• Economy features\n" +
                    "• Real-time event notifications\n" +
                    "• Server monitoring and detailed logs\n\n" +
                    "Use `/premium assign <server>` to activate premium for a specific server.\n" +
                    "Use `/premium verify` to check payment status.",
                    new Color(189, 195, 199) // Light gray for free tier
                ))
                .queue();
        }
    }
    
    /**
     * Handle the /premium enable subcommand
     */
    private void handleEnableSubcommand(SlashCommandInteractionEvent event, Guild guild) {
        // Only bot owner can manually enable premium
        if (!isOwner(event.getUser().getIdLong())) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied", 
                    "Only the bot owner can manually enable premium. Please use `/premium verify` to activate your premium subscription."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        OptionMapping durationOption = event.getOption("days");
        int days = durationOption != null ? durationOption.getAsInt() : 0;
        
        // Use the non-deprecated method
        premiumManager.enableGuildPremium(guild.getIdLong(), days);
        
        // Explicitly clear the cache to ensure new status is recognized
        com.deadside.bot.premium.FeatureGate.clearCache(guild.getIdLong());
        
        String durationText = days > 0 ? "for " + days + " days" : "with no expiration";
        event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                "✨ Premium Enabled",
                "Premium features have been enabled for this server " + durationText + "!\n\n" +
                "All premium features are now available.",
                PREMIUM_COLOR
            ))
            .queue();
            
        logger.info("Premium manually enabled for guild ID: {} by user ID: {} for {} days", 
                guild.getId(), event.getUser().getId(), days);
    }
    
    /**
     * Handle the /premium disable subcommand
     */
    private void handleDisableSubcommand(SlashCommandInteractionEvent event, Guild guild) {
        // Only bot owner can manually disable premium
        if (!isOwner(event.getUser().getIdLong())) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied", 
                    "Only the bot owner can manually disable premium."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Use the non-deprecated method
        premiumManager.disableGuildPremium(guild.getIdLong());
        
        // Explicitly clear the cache to ensure new status is recognized
        com.deadside.bot.premium.FeatureGate.clearCache(guild.getIdLong());
        
        event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                "Premium Disabled",
                "Premium features have been disabled for this server.\n\n" +
                "Only the basic killfeed feature is now available.",
                new Color(189, 195, 199) // Light gray
            ))
            .queue();
            
        logger.info("Premium manually disabled for guild ID: {} by user ID: {}", 
                guild.getId(), event.getUser().getId());
    }
    
    /**
     * Handle the /premium verify subcommand
     */
    private void handleVerifySubcommand(SlashCommandInteractionEvent event, Guild guild, Member member) {
        event.deferReply().queue(); // This might take a while, so defer the reply
        
        boolean verified = premiumManager.verifyTip4servPayment(guild.getIdLong(), member.getIdLong());
        
        if (verified) {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                    "✨ Payment Verified",
                    "Your payment has been verified and premium features are now enabled for this server!\n\n" +
                    "All premium features are now available.",
                    PREMIUM_COLOR
                ))
                .queue();
                
            logger.info("Premium payment verified for guild ID: {} by user ID: {}", 
                    guild.getId(), member.getId());
        } else {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed(
                    "Payment Verification",
                    "No active payment was found for this server.\n\n" +
                    "If you recently purchased premium, it may take a few minutes to process. " +
                    "If the problem persists, please check you used the correct Discord account during checkout " +
                    "or contact support with your purchase confirmation.",
                    new Color(189, 195, 199) // Light gray
                ))
                .queue();
                
            logger.info("Premium payment verification failed for guild ID: {} by user ID: {}", 
                    guild.getId(), member.getId());
        }
    }
    
    /**
     * Handle the /premium assign subcommand
     * Allows assigning premium to a specific server
     */
    private void handleAssignSubcommand(SlashCommandInteractionEvent event, Guild guild) {
        String serverName = event.getOption("server", OptionMapping::getAsString);
        if (serverName == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "You must specify a server name."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Check if the server exists
        GameServer server = serverRepository.findByGuildIdAndName(guild.getIdLong(), serverName);
        if (server == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", 
                    "Server '" + serverName + "' does not exist. Use `/server add` to create it first."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // The server already has premium
        if (server.isPremium()) {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed("Already Premium", 
                    "The server '" + serverName + "' already has premium features enabled.",
                    PREMIUM_COLOR))
                .queue();
            return;
        }
        
        // Check if the guild has premium slots available
        int availableSlots = premiumManager.getAvailablePremiumSlots(guild.getIdLong());
        int usedSlots = premiumManager.countPremiumServers(guild.getIdLong());
        int remainingSlots = availableSlots - usedSlots;
        
        if (remainingSlots <= 0 && !premiumManager.hasGuildPremium(guild.getIdLong())) {
            // No slots available, suggest purchasing more
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed("No Premium Slots Available", 
                    "You don't have any available premium slots. You currently have " + usedSlots + 
                    " premium servers out of " + availableSlots + " slots.\n\n" +
                    "You can:\n" +
                    "• Purchase more premium slots\n" +
                    "• Free up a slot by using `/premium unassign` on another server\n\n" +
                    "Use `/premium list` to see all your servers and their premium status.",
                    new Color(189, 195, 199)))
                .queue();
            return;
        }
        
        // Assign premium to this server
        boolean success = premiumManager.enableServerPremium(guild.getIdLong(), serverName, 30); // Default to 30 days
        
        // Clear the cache to ensure premium status is recognized immediately
        com.deadside.bot.premium.FeatureGate.clearCache(guild.getIdLong());
        
        if (success) {
            event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed("Premium Assigned", 
                    "✨ Successfully assigned premium to server '" + serverName + "'.\n\n" +
                    "This server now has access to all premium features!\n\n" +
                    "You now have " + (usedSlots + 1) + " premium servers out of " + availableSlots + " available slots."))
                .queue();
            
            logger.info("Premium assigned to server '{}' in guild {}", serverName, guild.getId());
        } else {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", 
                    "Failed to assign premium to server '" + serverName + "'. Please try again later."))
                .setEphemeral(true)
                .queue();
        }
    }
    
    /**
     * Handle the /premium unassign subcommand
     * Allows removing premium from a specific server, freeing up a premium slot
     */
    private void handleUnassignSubcommand(SlashCommandInteractionEvent event, Guild guild) {
        String serverName = event.getOption("server", OptionMapping::getAsString);
        if (serverName == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", "You must specify a server name."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // Check if the server exists
        GameServer server = serverRepository.findByGuildIdAndName(guild.getIdLong(), serverName);
        if (server == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", 
                    "Server '" + serverName + "' does not exist."))
                .setEphemeral(true)
                .queue();
            return;
        }
        
        // The server doesn't have premium
        if (!server.isPremium()) {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed("Not Premium", 
                    "The server '" + serverName + "' doesn't have premium features enabled.",
                    new Color(189, 195, 199)))
                .queue();
            return;
        }
        
        // Check if it's a guild-wide premium
        if (premiumManager.hasGuildPremium(guild.getIdLong())) {
            event.getHook().sendMessageEmbeds(EmbedUtils.customEmbed("Guild Premium", 
                    "This guild has guild-wide premium, which affects all servers. " +
                    "You need to disable guild premium before managing individual servers.",
                    PREMIUM_COLOR))
                .queue();
            return;
        }
        
        // Get slot info
        int availableSlots = premiumManager.getAvailablePremiumSlots(guild.getIdLong());
        int usedSlots = premiumManager.countPremiumServers(guild.getIdLong());
        
        // Remove premium from this server
        boolean success = premiumManager.disableServerPremium(guild.getIdLong(), serverName);
        
        // Clear the cache to ensure premium status changes are recognized immediately
        com.deadside.bot.premium.FeatureGate.clearCache(guild.getIdLong());
        
        if (success) {
            event.getHook().sendMessageEmbeds(EmbedUtils.successEmbed("Premium Unassigned", 
                    "Premium has been removed from server '" + serverName + "'.\n\n" +
                    "This server now has only basic (killfeed) features.\n\n" +
                    "You now have " + (usedSlots - 1) + " premium servers out of " + availableSlots + " available slots.\n\n" +
                    "You can assign this free slot to another server with `/premium assign`."))
                .queue();
            
            logger.info("Premium unassigned from server '{}' in guild {}", serverName, guild.getId());
        } else {
            event.getHook().sendMessageEmbeds(EmbedUtils.errorEmbed("Error", 
                    "Failed to unassign premium from server '" + serverName + "'. Please try again later."))
                .setEphemeral(true)
                .queue();
        }
    }
    
    /**
     * Handle the /premium list subcommand
     * Lists all servers and their premium status
     */
    private void handleListSubcommand(SlashCommandInteractionEvent event, Guild guild) {
        List<GameServer> servers = serverRepository.findAllByGuildId(guild.getIdLong());
        
        if (servers.isEmpty()) {
            event.replyEmbeds(EmbedUtils.customEmbed("No Servers", 
                    "You don't have any game servers configured yet. Use `/server add` to add your first server.",
                    new Color(189, 195, 199)))
                .queue();
            return;
        }
        
        // Build the server list
        StringBuilder message = new StringBuilder();
        
        // For our server-specific approach, we only use individual server premium status
        int availableSlots = premiumManager.getAvailablePremiumSlots(guild.getIdLong());
        int usedSlots = 0;
        
        // Count premium servers (active only, not expired)
        for (GameServer server : servers) {
            if (server.isPremium()) {
                // Check if premium is expired for this server
                if (server.getPremiumUntil() > 0 && server.getPremiumUntil() < System.currentTimeMillis()) {
                    // Premium has expired, don't count it
                    continue;
                }
                usedSlots++;
            }
        }
        
        // We're using a server-specific approach, so we don't check for guild-wide premium
        message.append("**Premium Status:**\n");
        message.append("• Premium Slots: **").append(availableSlots).append("**\n");
        message.append("• Slots Used: **").append(usedSlots).append("**\n");
        message.append("• Slots Available: **").append(availableSlots - usedSlots).append("**\n\n");
        
        if (availableSlots == 0) {
            message.append("**You don't have any premium slots yet.**\n");
            message.append("Purchase premium to unlock additional features for your servers.\n\n");
        } else if (availableSlots > 0 && usedSlots == 0) {
            message.append("**You have unused premium slots!**\n");
            message.append("Assign premium to your servers with `/premium assign`.\n\n");
        }
        
        message.append("**Your Game Servers:**\n");
        
        for (GameServer server : servers) {
            String status;
            if (server.isPremium()) {
                status = "✨ **PREMIUM**";
                if (server.getPremiumUntil() > 0) {
                    long daysRemaining = (server.getPremiumUntil() - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L);
                    status += " (expires in " + daysRemaining + " days)";
                }
            } else {
                status = "⚠️ **BASIC** (killfeed only)";
            }
            
            message.append("• **").append(server.getName()).append("** - ").append(status).append("\n");
        }
        
        // Add instructions based on slot availability
        message.append("\n**Commands:**\n");
        
        if (availableSlots > usedSlots) {
            message.append("• Use `/premium assign [server]` to assign premium to a server\n");
        }
        
        if (usedSlots > 0) {
            message.append("• Use `/premium unassign [server]` to remove premium from a server\n");
        }
        
        message.append("• Use `/server add` to add a new game server\n");
        message.append("• Purchase more premium slots to enable premium features on more servers");
        
        event.replyEmbeds(EmbedUtils.customEmbed("Server Premium Status", message.toString(), 
                PREMIUM_COLOR))
            .queue();
    }
    
    @Override
    public List<net.dv8tion.jda.api.interactions.commands.Command.Choice> handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getGuild() == null) {
            return List.of();
        }

        // Only provide autocomplete for server options
        if (event.getFocusedOption().getName().equals("server")) {
            String current = event.getFocusedOption().getValue().toLowerCase();
            List<GameServer> servers = serverRepository.findAllByGuildId(event.getGuild().getIdLong());
            
            return servers.stream()
                .filter(server -> server.getName().toLowerCase().contains(current))
                .map(server -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(server.getName(), server.getName()))
                .limit(25) // Discord maximum
                .toList();
        }
        
        return List.of();
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash("premium", "Manage premium features and subscription")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
            .addSubcommands(
                new SubcommandData("status", "Check the premium status of this server"),
                new SubcommandData("list", "List all servers and their premium status"),
                new SubcommandData("assign", "Assign premium to a specific game server")
                    .addOption(OptionType.STRING, "server", "The name of the game server to assign premium to", true, true),
                new SubcommandData("unassign", "Remove premium from a specific game server")
                    .addOption(OptionType.STRING, "server", "The name of the game server to remove premium from", true, true),
                new SubcommandData("verify", "Verify a premium payment for this server"),
                new SubcommandData("enable", "Enable premium features for this server (Bot Owner Only)")
                    .addOption(OptionType.INTEGER, "days", "Duration in days (0 for unlimited)", false),
                new SubcommandData("disable", "Disable premium features for this server (Bot Owner Only)")
            );
    }
    
    /**
     * Check if a user is the bot owner
     */
    private boolean isOwner(long userId) {
        // Hardcoded owner ID as specified in requirements
        return userId == 462961235382763520L;
    }
}