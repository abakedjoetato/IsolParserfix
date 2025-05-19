package com.deadside.bot.premium;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * HTTP webhook controller for Tip4serv integration
 * Listens for payment notifications and processes them
 */
public class Tip4servWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(Tip4servWebhookController.class);
    private HttpServer server;
    private final PremiumManager premiumManager;
    private final int port;
    private final String webhookPath;
    private final String webhookSecret;
    
    /**
     * Create a new webhook controller
     * @param premiumManager The premium manager instance
     * @param port The port to listen on
     * @param webhookPath The path for the webhook endpoint
     * @param webhookSecret The secret key for validating webhooks
     */
    public Tip4servWebhookController(PremiumManager premiumManager, int port, String webhookPath, String webhookSecret) {
        this.premiumManager = premiumManager;
        this.port = port;
        this.webhookPath = webhookPath;
        this.webhookSecret = webhookSecret;
    }
    
    /**
     * Start the webhook server
     */
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(webhookPath, new WebhookHandler());
            server.setExecutor(Executors.newFixedThreadPool(5));
            server.start();
            logger.info("Tip4serv webhook server started on port {} with path {}", port, webhookPath);
        } catch (IOException e) {
            logger.error("Failed to start Tip4serv webhook server", e);
        }
    }
    
    /**
     * Stop the webhook server
     */
    public void stop() {
        if (server != null) {
            server.stop(1);
            logger.info("Tip4serv webhook server stopped");
        }
    }
    
    /**
     * Handler for webhook requests
     */
    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Only accept POST requests
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                
                // Validate webhook signature if provided
                String signature = exchange.getRequestHeaders().getFirst("X-Tip4serv-Signature");
                
                // Read request body
                InputStream inputStream = exchange.getRequestBody();
                byte[] requestBodyBytes = inputStream.readAllBytes();
                String requestBody = new String(requestBodyBytes, StandardCharsets.UTF_8);
                
                if (signature != null && !validateSignature(requestBody, signature)) {
                    logger.warn("Invalid webhook signature received");
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }
                
                // Process the webhook
                boolean success = premiumManager.processTip4servWebhook(requestBody);
                
                if (success) {
                    sendResponse(exchange, 200, "Webhook processed successfully");
                } else {
                    sendResponse(exchange, 500, "Error processing webhook");
                }
            } catch (Exception e) {
                logger.error("Error handling webhook request", e);
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
        
        /**
         * Send HTTP response
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(statusCode, response.length());
            
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response.getBytes());
            }
        }
        
        /**
         * Validate webhook signature
         * Uses a simple HMAC-SHA256 hash with the webhook secret
         */
        private boolean validateSignature(String payload, String signature) {
            try {
                // Skip validation if no webhook secret is configured
                if (webhookSecret == null || webhookSecret.isEmpty()) {
                    logger.warn("Webhook secret not configured, skipping signature validation");
                    return true;
                }
                
                // Create an HMAC-SHA256 hash
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                        webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                mac.init(secretKeySpec);
                byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
                
                // Convert bytes to hexadecimal string
                StringBuilder hexString = new StringBuilder();
                for (byte b : hmacBytes) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                
                // Compare generated signature with received signature
                return hexString.toString().equals(signature);
            } catch (Exception e) {
                logger.error("Error validating webhook signature", e);
                return false;
            }
        }
    }
}