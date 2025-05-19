import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * A simple Discord bot that connects to Discord and responds to basic commands
 * This is a temporary solution until we can get the full Deadside bot working
 */
public class SimpleBotLoader extends ListenerAdapter {
    
    public static void main(String[] args) {
        try {
            // Get Discord token from environment variable
            String token = System.getenv("DISCORD_TOKEN");
            if (token == null || token.isEmpty()) {
                System.err.println("ERROR: DISCORD_TOKEN environment variable not set!");
                System.exit(1);
            }
            
            System.out.println("Starting Deadside Discord bot (simple version)...");
            
            // Create and connect the bot to Discord
            JDA jda = JDABuilder.createDefault(token)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.playing("Deadside"))
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(new SimpleBotLoader())
                .build();
            
            // Wait for connection to be established
            jda.awaitReady();
            System.out.println("Bot successfully connected to Discord!");
            System.out.println("Connected to " + jda.getGuilds().size() + " servers");
            
        } catch (Exception e) {
            System.err.println("Error starting Discord bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages to avoid feedback loops
        if (event.getAuthor().isBot()) return;
        
        String content = event.getMessage().getContentRaw();
        
        // Simple commands
        if (content.equals("!ping")) {
            event.getChannel().sendMessage("Pong! Bot is online.").queue();
        }
        else if (content.equals("!help")) {
            event.getChannel().sendMessage("**Deadside Bot Commands:**\n" +
                "- !ping: Check if the bot is online\n" +
                "- !help: Show this help message").queue();
        }
        else if (content.equals("!info")) {
            event.getChannel().sendMessage("This is a simplified version of the Deadside Discord bot.\n" +
                "Full features coming soon!").queue();
        }
    }
}