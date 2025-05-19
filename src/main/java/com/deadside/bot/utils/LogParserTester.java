package com.deadside.bot.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to test log parsing patterns against sample log files
 */
public class LogParserTester {
    
    // Regex patterns for different event types (same as in DeadsideLogParser)
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}:\\d{3})\\]\\[\\s*\\d+\\]");
    private static final Pattern PLAYER_JOIN_PATTERN = Pattern.compile("LogSFPS: \\[Login\\] Player (.+?) connected");
    private static final Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("LogSFPS: \\[Logout\\] Player (.+?) disconnected");
    private static final Pattern PLAYER_KILLED_PATTERN = Pattern.compile("LogSFPS: \\[Kill\\] (.+?) killed (.+?) with (.+?) at distance (\\d+)");
    private static final Pattern PLAYER_DIED_PATTERN = Pattern.compile("LogSFPS: \\[Death\\] (.+?) died from (.+?)");
    private static final Pattern AIRDROP_PATTERN = Pattern.compile("LogSFPS: AirDrop switched to (\\w+)");
    private static final Pattern MISSION_PATTERN = Pattern.compile("LogSFPS: Mission (.+?) switched to (\\w+)");
    
    // Regex pattern for CSV death log
    private static final Pattern CSV_LINE_PATTERN = Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2};.*;.*;.*;.*;.*;\\d+;$");
    
    public static void main(String[] args) {
        runTests();
    }
    
    public static void runTests() {
        // Test server log parsing
        testServerLogParsing("attached_assets/Deadside.log");
        
        // Test CSV death log parsing
        testCsvLogParsing("attached_assets/2025.04.10-00.00.00.csv");
    }
    
    private static void testServerLogParsing(String filePath) {
        System.out.println("======= Testing Server Log Parsing =======");
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            
            int totalMatches = 0;
            int joinMatches = 0;
            int leaveMatches = 0;
            int killMatches = 0;
            int deathMatches = 0;
            int airdropMatches = 0;
            int missionMatches = 0;
            
            List<String> matchedLines = new ArrayList<>();
            
            for (String line : lines) {
                boolean matched = false;
                
                // Extract timestamp if present
                String timestamp = "";
                Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(line);
                if (timestampMatcher.find()) {
                    timestamp = timestampMatcher.group(1);
                }
                
                // Player join events
                Matcher joinMatcher = PLAYER_JOIN_PATTERN.matcher(line);
                if (joinMatcher.find()) {
                    String playerName = joinMatcher.group(1).trim();
                    System.out.println("JOIN: " + playerName + " [" + timestamp + "]");
                    joinMatches++;
                    matched = true;
                }
                
                // Player leave events
                Matcher leaveMatcher = PLAYER_LEAVE_PATTERN.matcher(line);
                if (leaveMatcher.find()) {
                    String playerName = leaveMatcher.group(1).trim();
                    System.out.println("LEAVE: " + playerName + " [" + timestamp + "]");
                    leaveMatches++;
                    matched = true;
                }
                
                // Player killed events
                Matcher killedMatcher = PLAYER_KILLED_PATTERN.matcher(line);
                if (killedMatcher.find()) {
                    String killer = killedMatcher.group(1).trim();
                    String victim = killedMatcher.group(2).trim();
                    String weapon = killedMatcher.group(3).trim();
                    String distance = killedMatcher.group(4).trim();
                    System.out.println("KILL: " + killer + " killed " + victim + " with " + weapon + " (" + distance + "m) [" + timestamp + "]");
                    killMatches++;
                    matched = true;
                }
                
                // Player died events
                Matcher diedMatcher = PLAYER_DIED_PATTERN.matcher(line);
                if (diedMatcher.find()) {
                    String player = diedMatcher.group(1).trim();
                    String cause = diedMatcher.group(2).trim();
                    System.out.println("DEATH: " + player + " died from " + cause + " [" + timestamp + "]");
                    deathMatches++;
                    matched = true;
                }
                
                // Airdrop events
                Matcher airdropMatcher = AIRDROP_PATTERN.matcher(line);
                if (airdropMatcher.find()) {
                    String status = airdropMatcher.group(1).trim();
                    System.out.println("AIRDROP: Status changed to " + status + " [" + timestamp + "]");
                    airdropMatches++;
                    matched = true;
                }
                
                // Mission events
                Matcher missionMatcher = MISSION_PATTERN.matcher(line);
                if (missionMatcher.find()) {
                    String missionName = missionMatcher.group(1).trim();
                    String status = missionMatcher.group(2).trim();
                    System.out.println("MISSION: " + missionName + " is now " + status + " [" + timestamp + "]");
                    missionMatches++;
                    matched = true;
                }
                
                if (matched) {
                    totalMatches++;
                    matchedLines.add(line);
                }
            }
            
            // Report results
            System.out.println("\nServer Log Parsing Results:");
            System.out.println("Total lines processed: " + lines.size());
            System.out.println("Total matches: " + totalMatches);
            System.out.println("Join events: " + joinMatches);
            System.out.println("Leave events: " + leaveMatches);
            System.out.println("Kill events: " + killMatches);
            System.out.println("Death events: " + deathMatches);
            System.out.println("Airdrop events: " + airdropMatches);
            System.out.println("Mission events: " + missionMatches);
            
            // Check for mission patterns specifically
            System.out.println("\nMission Events Analysis:");
            lines.stream()
                .filter(line -> line.contains("Mission") && line.contains("switched to"))
                .forEach(line -> {
                    boolean matched = MISSION_PATTERN.matcher(line).find();
                    System.out.println((matched ? "✓ " : "✗ ") + line);
                });
            
        } catch (IOException e) {
            System.err.println("Error reading server log file: " + e.getMessage());
        }
    }
    
    private static void testCsvLogParsing(String filePath) {
        System.out.println("\n======= Testing CSV Death Log Parsing =======");
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            
            int totalMatches = 0;
            int playerKills = 0;
            int suicides = 0;
            int deaths = 0;
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                // Simple validation that this looks like a death log line
                if (!CSV_LINE_PATTERN.matcher(line).matches()) {
                    System.out.println("NON-MATCHING LINE: " + line);
                    continue;
                }
                
                try {
                    // The CSV format is: timestamp;victim;victimId;killer;killerId;weapon;distance;
                    String[] parts = line.split(";");
                    if (parts.length < 7) {
                        System.out.println("INVALID FORMAT: " + line);
                        continue;
                    }
                    
                    // Parse death log entry
                    String timestamp = parts[0];
                    String victim = parts[1];
                    String victimId = parts[2];
                    String killer = parts[3];
                    String killerId = parts[4];
                    String weapon = parts[5];
                    int distance = Integer.parseInt(parts[6]);
                    
                    // Determine if this is a suicide, player kill, or natural death
                    boolean isSuicide = victim.equals(killer) || weapon.contains("suicide") || 
                                        weapon.equals("falling") || weapon.equals("bleeding") || 
                                        weapon.equals("drowning") || weapon.equals("starvation");
                    
                    if (isSuicide) {
                        System.out.println("SUICIDE: " + victim + " died from " + weapon + " [" + timestamp + "]");
                        suicides++;
                    } else {
                        System.out.println("KILL: " + killer + " killed " + victim + " with " + weapon + " (" + distance + "m) [" + timestamp + "]");
                        playerKills++;
                    }
                    
                    totalMatches++;
                } catch (Exception e) {
                    System.err.println("Error parsing CSV line: " + line + " - " + e.getMessage());
                }
            }
            
            // Report results
            System.out.println("\nCSV Death Log Parsing Results:");
            System.out.println("Total lines processed: " + lines.size());
            System.out.println("Total matches: " + totalMatches);
            System.out.println("Player kills: " + playerKills);
            System.out.println("Suicides: " + suicides);
            
        } catch (IOException e) {
            System.err.println("Error reading CSV death log file: " + e.getMessage());
        }
    }
}