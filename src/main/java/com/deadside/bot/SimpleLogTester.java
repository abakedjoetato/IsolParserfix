package com.deadside.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple utility to quickly find all event types in Deadside logs
 */
public class SimpleLogTester {
    
    public static void main(String[] args) {
        System.out.println("======= DEADSIDE LOG EVENT ANALYZER =======");
        
        // Analyze server log
        analyzeServerLog("attached_assets/Deadside.log");
        
        // Analyze death log
        analyzeDeathLog("attached_assets/2025.04.10-00.00.00.csv");
    }
    
    private static void analyzeServerLog(String path) {
        System.out.println("\n----- Analyzing Server Log -----");
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            
            // Define patterns for different event types
            Map<String, Pattern> patterns = new HashMap<>();
            
            // Player events
            patterns.put("PLAYER_JOIN", Pattern.compile("LogSFPS: \\[Login\\] Player (.+?) connected"));
            patterns.put("PLAYER_LEAVE", Pattern.compile("LogSFPS: \\[Logout\\] Player (.+?) disconnected"));
            
            // Mission events
            patterns.put("MISSION_STATUS", Pattern.compile("LogSFPS: Mission (.+?) switched to (\\w+)"));
            patterns.put("MISSION_RESPAWN", Pattern.compile("LogSFPS: Mission (.+?) will respawn in (\\d+)"));
            
            // Airdrop events
            patterns.put("AIRDROP", Pattern.compile("LogSFPS: AirDrop switched to (\\w+)"));
            
            // Vehicle events
            patterns.put("VEHICLE_SPAWN", Pattern.compile("LogSFPS: \\[ASFPSVehicleSpawnPoint\\] Spawned vehicle (.+?) at (.+)"));
            patterns.put("VEHICLE_ADD", Pattern.compile("LogSFPS: \\[ASFPSGameMode::NewVehicle_Add\\] Add vehicle (.+?) Total (\\d+)"));
            patterns.put("VEHICLE_REMOVE", Pattern.compile("LogSFPS: \\[ASFPSGameMode::NewVehicle_Del\\] Del vehicle (.+?) Total (\\d+)"));
            
            // Find example matches
            Map<String, List<String>> examples = new HashMap<>();
            Map<String, Integer> counts = new HashMap<>();
            
            // Initialize collections
            for (String key : patterns.keySet()) {
                examples.put(key, new ArrayList<>());
                counts.put(key, 0);
            }
            
            // Track mission states
            List<String> missionStates = new ArrayList<>();
            
            // Process each line
            for (String line : lines) {
                for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
                    String eventType = entry.getKey();
                    Pattern pattern = entry.getValue();
                    
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        // Count match
                        counts.put(eventType, counts.get(eventType) + 1);
                        
                        // Store example if we don't have too many
                        List<String> eventExamples = examples.get(eventType);
                        if (eventExamples.size() < 3) {
                            eventExamples.add(line);
                        }
                        
                        // If this is a mission status change, track the state
                        if (eventType.equals("MISSION_STATUS") && matcher.groupCount() >= 2) {
                            String state = matcher.group(2);
                            if (!missionStates.contains(state)) {
                                missionStates.add(state);
                            }
                        }
                    }
                }
            }
            
            // Display results
            System.out.println("\nDetected Event Types:");
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String eventType = entry.getKey();
                int count = entry.getValue();
                
                System.out.println("\n" + eventType + " - Found " + count + " occurrences");
                if (count > 0) {
                    List<String> eventExamples = examples.get(eventType);
                    for (int i = 0; i < Math.min(eventExamples.size(), 3); i++) {
                        System.out.println("  Example: " + eventExamples.get(i));
                    }
                }
            }
            
            // Show mission states
            System.out.println("\nMission States Found:");
            for (String state : missionStates) {
                System.out.println("  - " + state);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
        }
    }
    
    private static void analyzeDeathLog(String path) {
        System.out.println("\n----- Analyzing Death Log -----");
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            
            int totalEntries = 0;
            int playerKills = 0;
            int suicides = 0;
            
            // Track weapons
            List<String> weapons = new ArrayList<>();
            
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                totalEntries++;
                
                String[] parts = line.split(";");
                if (parts.length < 7) continue;
                
                String victim = parts[1];
                String killer = parts[3];
                String weapon = parts[5];
                
                // Add weapon to list if not seen before
                if (!weapons.contains(weapon)) {
                    weapons.add(weapon);
                }
                
                // Check if suicide
                boolean isSuicide = victim.equals(killer) || 
                                   weapon.equals("suicide_by_relocation") || 
                                   weapon.equals("falling") ||
                                   weapon.equals("drowning") ||
                                   weapon.equals("bleeding") ||
                                   weapon.equals("starvation");
                
                if (isSuicide) {
                    suicides++;
                } else {
                    playerKills++;
                }
            }
            
            // Display results
            System.out.println("\nDeath Log Analysis:");
            System.out.println("  Total entries: " + totalEntries);
            System.out.println("  Player kills: " + playerKills);
            System.out.println("  Suicides: " + suicides);
            
            System.out.println("\nWeapons Found:");
            for (String weapon : weapons) {
                System.out.println("  - " + weapon);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading death log: " + e.getMessage());
        }
    }
}