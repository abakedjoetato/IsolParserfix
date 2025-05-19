package com.deadside.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive test class for Deadside log parsing
 */
public class TestRunner {
    // Regex patterns for different event types
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}:\\d{3})\\]\\[\\s*\\d+\\]");
    private static final Pattern PLAYER_JOIN_PATTERN = Pattern.compile("LogSFPS: \\[Login\\] Player (.+?) connected");
    private static final Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("LogSFPS: \\[Logout\\] Player (.+?) disconnected");
    private static final Pattern PLAYER_KILLED_PATTERN = Pattern.compile("LogSFPS: \\[Kill\\] (.+?) killed (.+?) with (.+?) at distance (\\d+)");
    private static final Pattern PLAYER_DIED_PATTERN = Pattern.compile("LogSFPS: \\[Death\\] (.+?) died from (.+?)");
    
    // Improved patterns for game events
    private static final Pattern AIRDROP_PATTERN = Pattern.compile("LogSFPS: AirDrop switched to (\\w+)");
    private static final Pattern MISSION_PATTERN = Pattern.compile("LogSFPS: Mission (.+?) switched to (\\w+)");
    private static final Pattern MISSION_RESPAWN_PATTERN = Pattern.compile("LogSFPS: Mission (.+?) will respawn in (\\d+)");
    private static final Pattern MISSION_FAIL_PATTERN = Pattern.compile("LogSFPS: \\[USFPSACMission::Fail\\] (.+)");
    private static final Pattern TRADER_PATTERN = Pattern.compile("LogSFPS: Trader (\\w+) is now (\\w+)");
    private static final Pattern HELICOPTER_PATTERN = Pattern.compile("LogSFPS: Helicopter at ([\\d\\.\\-]+), ([\\d\\.\\-]+), ([\\d\\.\\-]+)");
    private static final Pattern HELI_CRASH_PATTERN = Pattern.compile("LogSFPS: Helicopter crashed at ([\\d\\.\\-]+), ([\\d\\.\\-]+), ([\\d\\.\\-]+)");
    
    // Vehicle patterns
    private static final Pattern VEHICLE_SPAWN_PATTERN = Pattern.compile("LogSFPS: \\[ASFPSVehicleSpawnPoint\\] Spawned vehicle ([A-Za-z0-9_]+) at ([A-Za-z0-9_]+)");
    private static final Pattern VEHICLE_ADD_PATTERN = Pattern.compile("LogSFPS: \\[ASFPSGameMode::NewVehicle_Add\\] Add vehicle ([A-Za-z0-9_]+) Total (\\d+)");
    private static final Pattern VEHICLE_DEL_PATTERN = Pattern.compile("LogSFPS: \\[ASFPSGameMode::NewVehicle_Del\\] Del vehicle ([A-Za-z0-9_]+) Total (\\d+)");
    
    // Regex pattern for CSV death log
    private static final Pattern CSV_LINE_PATTERN = Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2};.*;.*;.*;.*;.*;\\d+;$");

    // Track all mission names and statuses for analysis
    private static final Set<String> missionNames = new HashSet<>();
    private static final Set<String> missionStatuses = new HashSet<>();

    public static void main(String[] args) {
        System.out.println("Running enhanced log parser tests...");
        
        // Test server log parsing with comprehensive event detection
        testServerLogParsing("attached_assets/Deadside.log");
        
        // Test CSV death log parsing
        testCsvLogParsing("attached_assets/2025.04.10-00.00.00.csv");
    }
    
    /**
     * Normalizes a player name for Discord display
     * - Removes special characters that might cause formatting issues
     * - Limits length for proper display
     */
    private static String normalizeNameForDiscord(String name) {
        if (name == null || name.isEmpty()) {
            return "Unknown Player";
        }
        
        // Remove discord formatting characters that might break messages
        String normalized = name.replaceAll("[*_~`|>]", "")
                               .trim();
        
        // Limit length for proper display
        if (normalized.length() > 32) {
            normalized = normalized.substring(0, 29) + "...";
        }
        
        return normalized;
    }
    
    /**
     * Format a message as it would appear in Discord
     */
    private static String formatForDiscord(String eventType, String message) {
        String emoji = "";
        
        // Add appropriate emoji based on event type
        switch (eventType) {
            case "JOIN":
                emoji = "➡️ ";
                break;
            case "LEAVE":
                emoji = "⬅️ ";
                break;
            case "KILL":
                emoji = "☠️ ";
                break;
            case "SUICIDE":
                emoji = "💀 ";
                break;
            case "AIRDROP":
                emoji = "🪂 ";
                break;
            case "MISSION":
                emoji = "🎯 ";
                break;
            case "VEHICLE":
                emoji = "🚗 ";
                break;
            case "TRADER":
                emoji = "💰 ";
                break;
            case "HELICOPTER":
                emoji = "🚁 ";
                break;
            default:
                emoji = "ℹ️ ";
        }
        
        return emoji + message;
    }
    
    private static void testServerLogParsing(String filePath) {
        System.out.println("======= DEADSIDE SERVER EVENT DETECTION REPORT =======");
        System.out.println("Analyzing server log: " + filePath);
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            
            int totalLines = 0;
            int matchedLines = 0;
            int airdropMatches = 0;
            int missionMatches = 0;
            int missionRespawnMatches = 0;
            int traderMatches = 0;
            int helicopterMatches = 0;
            int heliCrashMatches = 0;
            int joinMatches = 0;
            int leaveMatches = 0;
            int vehicleSpawnMatches = 0;
            int vehicleAddMatches = 0;
            int vehicleDelMatches = 0;
            
            // Lists to store unique event instances
            List<String> airdropEvents = new ArrayList<>();
            List<String> missionEvents = new ArrayList<>();
            List<String> traderEvents = new ArrayList<>();
            List<String> helicopterEvents = new ArrayList<>();
            List<String> vehicleEvents = new ArrayList<>();
            
            // Find specific event patterns in the log
            for (String line : lines) {
                totalLines++;
                boolean matched = false;
                
                // Extract timestamp if present
                Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(line);
                String timestamp = "";
                if (timestampMatcher.find()) {
                    timestamp = timestampMatcher.group(1);
                }
                
                // ===== AIRDROP EVENTS =====
                Matcher airdropMatcher = AIRDROP_PATTERN.matcher(line);
                if (airdropMatcher.find()) {
                    String status = airdropMatcher.group(1);
                    missionStatuses.add(status); // Track the status
                    
                    String message = "Airdrop status changed to " + status;
                    String formattedEvent = formatForDiscord("AIRDROP", message) + " [" + timestamp + "]";
                    airdropEvents.add(formattedEvent);
                    
                    airdropMatches++;
                    matched = true;
                }
                
                // ===== MISSION EVENTS =====
                Matcher missionMatcher = MISSION_PATTERN.matcher(line);
                if (missionMatcher.find()) {
                    String missionName = missionMatcher.group(1);
                    String status = missionMatcher.group(2);
                    
                    missionNames.add(missionName); // Track mission names
                    missionStatuses.add(status);   // Track status values
                    
                    // Format mission name for readability
                    String formattedMissionName = missionName.replace("_", " ")
                                                           .replace("SFPSACMission", "")
                                                           .replace("Mis", "Mission ")
                                                           .trim();
                    
                    String message = "Mission " + formattedMissionName + " is now " + status;
                    String formattedEvent = formatForDiscord("MISSION", message) + " [" + timestamp + "]";
                    missionEvents.add(formattedEvent);
                    
                    missionMatches++;
                    matched = true;
                }
                
                // ===== MISSION RESPAWN EVENTS =====
                Matcher respawnMatcher = MISSION_RESPAWN_PATTERN.matcher(line);
                if (respawnMatcher.find()) {
                    String missionName = respawnMatcher.group(1);
                    String respawnTime = respawnMatcher.group(2);
                    
                    missionNames.add(missionName); // Track mission names
                    
                    String message = "Mission " + missionName + " will respawn in " + respawnTime + " seconds";
                    String formattedEvent = formatForDiscord("MISSION", message) + " [" + timestamp + "]";
                    missionEvents.add(formattedEvent);
                    
                    missionRespawnMatches++;
                    matched = true;
                }
                
                // ===== TRADER EVENTS =====
                Matcher traderMatcher = TRADER_PATTERN.matcher(line);
                if (traderMatcher.find()) {
                    String traderId = traderMatcher.group(1);
                    String status = traderMatcher.group(2);
                    
                    String message = "Trader " + traderId + " is now " + status;
                    String formattedEvent = formatForDiscord("TRADER", message) + " [" + timestamp + "]";
                    traderEvents.add(formattedEvent);
                    
                    traderMatches++;
                    matched = true;
                }
                
                // ===== HELICOPTER EVENTS =====
                Matcher helicopterMatcher = HELICOPTER_PATTERN.matcher(line);
                if (helicopterMatcher.find()) {
                    String xCoord = helicopterMatcher.group(1);
                    String yCoord = helicopterMatcher.group(2);
                    String zCoord = helicopterMatcher.group(3);
                    
                    String message = "Helicopter spotted at coordinates X:" + xCoord + " Y:" + yCoord + " Z:" + zCoord;
                    String formattedEvent = formatForDiscord("HELICOPTER", message) + " [" + timestamp + "]";
                    helicopterEvents.add(formattedEvent);
                    
                    helicopterMatches++;
                    matched = true;
                }
                
                // ===== HELICOPTER CRASH EVENTS =====
                Matcher heliCrashMatcher = HELI_CRASH_PATTERN.matcher(line);
                if (heliCrashMatcher.find()) {
                    String xCoord = heliCrashMatcher.group(1);
                    String yCoord = heliCrashMatcher.group(2);
                    String zCoord = heliCrashMatcher.group(3);
                    
                    String message = "Helicopter crashed at coordinates X:" + xCoord + " Y:" + yCoord + " Z:" + zCoord;
                    String formattedEvent = formatForDiscord("HELICOPTER", message) + " [" + timestamp + "]";
                    helicopterEvents.add(formattedEvent);
                    
                    heliCrashMatches++;
                    matched = true;
                }
                
                // ===== VEHICLE SPAWN EVENTS =====
                Matcher vehicleSpawnMatcher = VEHICLE_SPAWN_PATTERN.matcher(line);
                if (vehicleSpawnMatcher.find()) {
                    String vehicleId = vehicleSpawnMatcher.group(1);
                    String spawnPoint = vehicleSpawnMatcher.group(2);
                    
                    String message = "Vehicle " + vehicleId + " spawned at " + spawnPoint;
                    String formattedEvent = formatForDiscord("VEHICLE", message) + " [" + timestamp + "]";
                    vehicleEvents.add(formattedEvent);
                    
                    vehicleSpawnMatches++;
                    matched = true;
                }
                
                // ===== VEHICLE ADD EVENTS =====
                Matcher vehicleAddMatcher = VEHICLE_ADD_PATTERN.matcher(line);
                if (vehicleAddMatcher.find()) {
                    String vehicleId = vehicleAddMatcher.group(1);
                    String total = vehicleAddMatcher.group(2);
                    
                    String message = "Vehicle " + vehicleId + " added (Total: " + total + ")";
                    String formattedEvent = formatForDiscord("VEHICLE", message) + " [" + timestamp + "]";
                    vehicleEvents.add(formattedEvent);
                    
                    vehicleAddMatches++;
                    matched = true;
                }
                
                // ===== VEHICLE DELETE EVENTS =====
                Matcher vehicleDelMatcher = VEHICLE_DEL_PATTERN.matcher(line);
                if (vehicleDelMatcher.find()) {
                    String vehicleId = vehicleDelMatcher.group(1);
                    String total = vehicleDelMatcher.group(2);
                    
                    String message = "Vehicle " + vehicleId + " removed (Total: " + total + ")";
                    String formattedEvent = formatForDiscord("VEHICLE", message) + " [" + timestamp + "]";
                    vehicleEvents.add(formattedEvent);
                    
                    vehicleDelMatches++;
                    matched = true;
                }
                
                // ===== PLAYER JOIN EVENTS =====
                Matcher joinMatcher = PLAYER_JOIN_PATTERN.matcher(line);
                if (joinMatcher.find()) {
                    String playerName = joinMatcher.group(1);
                    String normalizedName = normalizeNameForDiscord(playerName);
                    
                    String message = "Player " + normalizedName + " connected to the server";
                    System.out.println(formatForDiscord("JOIN", message) + " [" + timestamp + "]");
                    joinMatches++;
                    matched = true;
                }
                
                // ===== PLAYER LEAVE EVENTS =====
                Matcher leaveMatcher = PLAYER_LEAVE_PATTERN.matcher(line);
                if (leaveMatcher.find()) {
                    String playerName = leaveMatcher.group(1);
                    String normalizedName = normalizeNameForDiscord(playerName);
                    
                    String message = "Player " + normalizedName + " disconnected from the server";
                    System.out.println(formatForDiscord("LEAVE", message) + " [" + timestamp + "]");
                    leaveMatches++;
                    matched = true;
                }
                
                if (matched) {
                    matchedLines++;
                }
            }
            
            // Display events by category
            System.out.println("\n===== AIRDROP EVENTS (" + airdropMatches + ") =====");
            airdropEvents.forEach(System.out::println);
            
            System.out.println("\n===== MISSION EVENTS (" + (missionMatches + missionRespawnMatches) + ") =====");
            missionEvents.forEach(System.out::println);
            
            System.out.println("\n===== TRADER EVENTS (" + traderMatches + ") =====");
            traderEvents.forEach(System.out::println);
            
            System.out.println("\n===== HELICOPTER EVENTS (" + (helicopterMatches + heliCrashMatches) + ") =====");
            helicopterEvents.forEach(System.out::println);
            
            int totalVehicleMatches = vehicleSpawnMatches + vehicleAddMatches + vehicleDelMatches;
            System.out.println("\n===== VEHICLE EVENTS (" + totalVehicleMatches + ") =====");
            vehicleEvents.forEach(System.out::println);
            
            // Print out all mission statuses found
            System.out.println("\nAll Mission Statuses Found:");
            missionStatuses.forEach(status -> System.out.println("- " + status));
            
            // Print all unique mission names for analysis
            System.out.println("\nAll Mission Names Found:");
            missionNames.forEach(name -> {
                String formattedName = name.replace("_", " ")
                                         .replace("SFPSACMission", "")
                                         .replace("Mis", "Mission ")
                                         .trim();
                System.out.println("- " + name + " → " + formattedName);
            });
            
            System.out.println("\nServer Log Analysis Results:");
            System.out.println("Total lines: " + totalLines);
            System.out.println("Matched event lines: " + matchedLines);
            System.out.println("Airdrop events: " + airdropMatches);
            System.out.println("Mission events: " + missionMatches);
            System.out.println("Total vehicle events: " + (vehicleSpawnMatches + vehicleAddMatches + vehicleDelMatches));
            System.out.println("Player join events: " + joinMatches);
            System.out.println("Player leave events: " + leaveMatches);
            System.out.println("Unique mission names: " + missionNames.size());
            System.out.println("Unique mission statuses: " + missionStatuses.size());
            
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
        }
    }
    
    private static void testCsvLogParsing(String filePath) {
        System.out.println("\n======= Testing CSV Death Log Parsing =======");
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            
            int totalLines = lines.size();
            int matchedLines = 0;
            int playerKills = 0;
            int suicides = 0;
            
            for (String line : lines) {
                // Check if the line matches our expected CSV format
                Matcher csvMatcher = CSV_LINE_PATTERN.matcher(line);
                if (csvMatcher.matches()) {
                    matchedLines++;
                    
                    // Parse the CSV fields
                    String[] parts = line.split(";");
                    String timestamp = parts[0];
                    String victim = normalizeNameForDiscord(parts[1]);
                    String victimId = parts[2];
                    String killer = normalizeNameForDiscord(parts[3]);
                    String killerId = parts[4];
                    String weapon = parts[5];
                    int distance = Integer.parseInt(parts[6]);
                    
                    // Determine if it's a suicide or a kill
                    boolean isSuicide = victim.equals(killer) || 
                                       weapon.equals("suicide_by_relocation") || 
                                       weapon.equals("falling") ||
                                       weapon.equals("drowning") ||
                                       weapon.equals("bleeding") ||
                                       weapon.equals("starvation");
                    
                    if (isSuicide) {
                        String message = victim + " died from " + formatDeathCause(weapon);
                        System.out.println(formatForDiscord("SUICIDE", message) + " [" + timestamp + "]");
                        suicides++;
                    } else {
                        String weaponInfo = formatWeapon(weapon);
                        String distanceInfo = distance > 0 ? " from " + distance + "m" : "";
                        
                        String message = killer + " killed " + victim + " with " + weaponInfo + distanceInfo;
                        System.out.println(formatForDiscord("KILL", message) + " [" + timestamp + "]");
                        playerKills++;
                    }
                } else {
                    System.out.println("NON-MATCHING LINE: " + line);
                }
            }
            
            System.out.println("\nCSV Death Log Analysis Results:");
            System.out.println("Total lines: " + totalLines);
            System.out.println("Matched CSV lines: " + matchedLines);
            System.out.println("Player kills: " + playerKills);
            System.out.println("Suicides: " + suicides);
            
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }
    }
    
    /**
     * Format death cause to be more readable
     */
    private static String formatDeathCause(String cause) {
        switch (cause) {
            case "suicide_by_relocation":
                return "suicide (relocation)";
            case "falling":
                return "falling damage";
            case "drowning":
                return "drowning";
            case "bleeding":
                return "bleeding out";
            case "starvation":
                return "starvation";
            default:
                return cause;
        }
    }
    
    /**
     * Format weapon name to be more readable
     */
    private static String formatWeapon(String weapon) {
        // Convert weapon codes to friendly names if needed
        return weapon;
    }
}