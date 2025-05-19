package com.deadside.bot.premium;

import com.deadside.bot.config.Config;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.utils.HttpUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration with Tip4Serv for premium payment verification
 */
public class Tip4ServIntegration {
    private static final Logger logger = LoggerFactory.getLogger(Tip4ServIntegration.class);
    private static final String TIP4SERV_API_URL = "https://api.tip4serv.com/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    
    private final String apiKey;
    private final HttpClient httpClient;
    
    /**
     * Constructor with API key
     */
    public Tip4ServIntegration() {
        this.apiKey = Config.getInstance().getTip4servApiKey();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .build();
        
        logger.debug("Tip4Serv integration initialized");
    }
    
    /**
     * Verify a payment using the Tip4Serv API
     * @param transactionId The transaction ID to verify
     * @param discordUserId The Discord user ID associated with the payment
     * @return true if the payment is valid, false otherwise
     */
    public boolean verifyPayment(String transactionId, String discordUserId) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                logger.error("Tip4Serv API key is not configured");
                return false;
            }
            
            Map<String, String> params = new HashMap<>();
            params.put("transaction_id", transactionId);
            params.put("discord_id", discordUserId);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TIP4SERV_API_URL + "/verify-payment"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(new JSONObject(params).toString()))
                    .timeout(TIMEOUT)
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.error("Failed to verify payment: HTTP {} - {}", response.statusCode(), response.body());
                return false;
            }
            
            JSONObject responseBody = new JSONObject(response.body());
            boolean isValid = responseBody.getBoolean("valid");
            
            if (isValid) {
                logger.info("Payment verified for transaction {} by user {}", transactionId, discordUserId);
            } else {
                logger.warn("Invalid payment for transaction {} by user {}", transactionId, discordUserId);
            }
            
            return isValid;
        } catch (IOException | InterruptedException e) {
            logger.error("Error verifying payment: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error verifying payment: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if a server has an active premium subscription
     * @param serverId The server ID to check
     * @return true if the server has an active premium subscription, false otherwise
     */
    public boolean checkServerPremium(String serverId) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                logger.error("Tip4Serv API key is not configured");
                return false;
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TIP4SERV_API_URL + "/subscriptions/server/" + serverId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(TIMEOUT)
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.error("Failed to check server premium: HTTP {} - {}", response.statusCode(), response.body());
                return false;
            }
            
            JSONObject responseBody = new JSONObject(response.body());
            boolean isActive = responseBody.getBoolean("active");
            
            if (isActive) {
                logger.debug("Server {} has an active premium subscription", serverId);
            } else {
                logger.debug("Server {} does not have an active premium subscription", serverId);
            }
            
            return isActive;
        } catch (IOException | InterruptedException e) {
            logger.error("Error checking server premium: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error checking server premium: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if a user has an active premium subscription
     * @param discordUserId The Discord user ID to check
     * @return true if the user has an active premium subscription, false otherwise
     */
    public boolean checkUserPremium(String discordUserId) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                logger.error("Tip4Serv API key is not configured");
                return false;
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TIP4SERV_API_URL + "/subscriptions/user/" + discordUserId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(TIMEOUT)
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.error("Failed to check user premium: HTTP {} - {}", response.statusCode(), response.body());
                return false;
            }
            
            JSONObject responseBody = new JSONObject(response.body());
            boolean isActive = responseBody.getBoolean("active");
            
            if (isActive) {
                logger.debug("User {} has an active premium subscription", discordUserId);
            } else {
                logger.debug("User {} does not have an active premium subscription", discordUserId);
            }
            
            return isActive;
        } catch (IOException | InterruptedException e) {
            logger.error("Error checking user premium: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error checking user premium: {}", e.getMessage(), e);
            return false;
        }
    }
}