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
 * Utility to extract and display all event types from Deadside server logs
 */
public class DeadsideLogEventTester {

    public static void main(String[] args) {
        System.out.println("======= DEADSIDE EVENT DETECTION TEST =======");
        
        // Test server log pattern matching
        System.out.println("\nAnalyzing Deadside server log file...");
        analyzeServerLog("attached_assets/Deadside.log");
        
        // Test death log pattern matching
        System.out.println("\nAnalyzing death log CSV file...");
        analyzeDeathLog("attached_assets/2025.04.10-00.00.00.csv");
    }
    
    private static void analyzeServerLog(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            
            // Define regex patterns for different event types
            Map<String, Pattern> eventPatterns = new HashMap<>();
            // Player events
            eventPatterns.put("PLAYER_JOIN", Pattern.compile("LogSFPS: \\[Login\\] Player (.+?) connected"));
            eventPatterns.put("PLAYER_LEAVE", Pattern.compile("LogSFPS: \\[Logout\\] Player (.+?) disconnected"));
            
            // Mission events
            eventPatterns.put("MISSION_SWITCHED", Pattern.compile("LogSFPS: Mission (.+?) switched to (\\w+)"));
            eventPatterns.put("MISSION_RESPAWN", Pattern.compile("LogSFPS: Mission (.+?) will respawn in (\\d+)"));
            eventPatterns.put("MISSION_FAIL", Pattern.compile("LogSFPS: \\[USFPSACMission::Fail\\] (.+)"));
            
            // Airdrop events
            eventPatterns.put("AIRDROP", Pattern.compile("LogSFPS: AirDrop switched to (\\w+)"));
            
            // Vehicle events
            eventPatterns.put("VEHICLE_SPAWN", Pattern.compile("LogSFPS: \\[ASFPSVehicleSpawnPoint\\] Spawned vehicle (.+?) at (.+)"));
            eventPatterns.put("VEHICLE_ADD", Pattern.compile("LogSFPS: \\[ASFPSGameMode::NewVehicle_Add\\] Add vehicle (.+?) Total (\\d+)"));
            eventPatterns.put("VEHICLE_DEL", Pattern.compile("LogSFPS: \\[ASFPSGameMode::NewVehicle_Del\\] Del vehicle (.+?) Total (\\d+)"));
            
            // Helicopter events
            eventPatterns.put("HELICOPTER", Pattern.compile("LogSFPS: Helicopter at ([\\d\\.\\-]+), ([\\d\\.\\-]+), ([\\d\\.\\-]+)"));
            eventPatterns.put("HELICOPTER_CRASH", Pattern.compile("LogSFPS: Helicopter crashed at ([\\d\\.\\-]+), ([\\d\\.\\-]+), ([\\d\\.\\-]+)"));
            
            // Create counters for each event type
            Map<String, Integer> eventCounts = new HashMap<>();
            eventPatterns.keySet().forEach(key -> eventCounts.put(key, 0));
            
            // Store examples of each event type
            Map<String, List<String>> eventExamples = new HashMap<>();
            eventPatterns.keySet().forEach(key -> eventExamples.put(key, new ArrayList<>()));
            
            // Track all mission states
            List<String> missionStates = new ArrayList<>();
            
            // Analyze each line
            for (String line : lines) {
                // Check each pattern
                for (Map.Entry<String, Pattern> entry : eventPatterns.entrySet()) {
                    String eventType = entry.getKey();
                    Pattern pattern = entry.getValue();
                    
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        // Increment counter
                        eventCounts.put(eventType, eventCounts.get(eventType) + 1);
                        
                        // Store the actual matched line as an example (max 3 examples per type)
                        List<String> examples = eventExamples.get(eventType);
                        if (examples.size() < 3) {
                            examples.add(line);
                        }
                        
                        // Track mission states if this is a mission switch event
                        if (eventType.equals("MISSION_SWITCHED") && matcher.groupCount() >= 2) {
                            String state = matcher.group(2);
                            if (!missionStates.contains(state)) {
                                missionStates.add(state);
                            }
                        }
                    }
                }
            }
            
            // Print summary
            System.out.println("\n===== FOUND EVENT TYPES =====");
            for (Map.Entry<String, Integer> entry : eventCounts.entrySet()) {
                String eventType = entry.getKey();
                int count = entry.getValue();
                
                if (count > 0) {
                    System.out.println("\n" + eventType + " - Found " + count + " occurrences");
                    List<String> examples = eventExamples.get(eventType);
                    for (int i = 0; i < examples.size(); i++) {
                        System.out.println("  Example " + (i+1) + ": " + examples.get(i));
                    }
                }
            }
            
            // Print all mission states
            System.out.println("\n===== MISSION STATES =====");
            for (String state : missionStates) {
                System.out.println("  - " + state);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading server log: " + e.getMessage());
        }
    }
    
    private static void analyzeDeathLog(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            
            // CSV format: timestamp;victim;victimId;killer;killerId;weapon;distance;
            int totalLines = lines.size();
            int playerKills = 0;
            int suicides = 0;
            
            // Track all weapons found
            List<String> weaponsFound = new ArrayList<>();
            
            // Analyze each line
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(";");
                if (parts.length < 7) continue;
                
                String victim = parts[1];
                String killer = parts[3];
                String weapon = parts[5];
                
                // Record weapons
                if (!weaponsFound.contains(weapon)) {
                    weaponsFound.add(weapon);
                }
                
                // Determine if this is a suicide or a kill
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
            
            // Print summary
            System.out.println("\n===== DEATH LOG SUMMARY =====");
            System.out.println("Total lines: " + totalLines);
            System.out.println("Player kills: " + playerKills);
            System.out.println("Suicides: " + suicides);
            
            // Print all weapons found
            System.out.println("\n===== WEAPONS FOUND =====");
            for (String weapon : weaponsFound) {
                System.out.println("  - " + weapon);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading death log: " + e.getMessage());
        }
    }
}