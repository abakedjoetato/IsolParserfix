package com.deadside.bot.parsers;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.models.KillRecord;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.db.repositories.KillRecordRepository;
import com.deadside.bot.db.repositories.PlayerRepository;
import com.deadside.bot.parsers.fixes.CsvParsingFix;
import com.deadside.bot.sftp.SftpManager;
import com.deadside.bot.utils.EmbedUtils;
import com.deadside.bot.utils.AdvancedEmbeds;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Deadside killfeed CSV files
 */
public class KillfeedParser {
    private static final Logger logger = LoggerFactory.getLogger(KillfeedParser.class);
    private final SftpManager sftpManager;
    private final KillRecordRepository killRecordRepository;
    private final PlayerRepository playerRepository;
    private final JDA jda;
    
    // Old CSV format: "1970/01/01-00:00:00","PlayerName1","killed","PlayerName2","with","WeaponName","from","100m"
    // New CSV format: timestamp;killer;killerID;victim;victimID;weapon;distance;platform1;platform2
    // Example: 2025.05.15-00.11.07;Fatalben0;0002548521ba4271a497e39d5bfe5611;Rogue731;00022ac42542497589f654e6ac2c0a6f;MR5;20;XSX;XSX;
    private static final Pattern CSV_PATTERN = Pattern.compile(
            "(\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2});([^;]+);([^;]+);([^;]+);([^;]+);([^;]+);(\\d+);([^;]+);([^;]+);"
    );
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss");
    
    public KillfeedParser(JDA jda) {
        this.jda = jda;
        this.sftpManager = new SftpManager();
        this.killRecordRepository = new KillRecordRepository();
        this.playerRepository = new PlayerRepository();
    }
    
    /**
     * Process killfeed for a server
     * @param server The game server to process
     * @param processHistorical If true, scan all historical files; if false, just process newest
     * @return Number of new kill records processed
     */
    public int processServer(GameServer server, boolean processHistorical) {
        try {
            TextChannel killfeedChannel = getTextChannel(server, "kill");
            if (killfeedChannel == null) {
                logger.warn("Killfeed channel not found for server: {}", server.getName());
                return 0;
            }
            
            // Get CSV files
            List<String> files = sftpManager.getKillfeedFiles(server);
            if (files.isEmpty()) {
                logger.warn("No killfeed files found for server: {}", server.getName());
                return 0;
            }
            
            // Sort files by name (should be date-based)
            Collections.sort(files);
            
            String lastProcessedFile = server.getLastProcessedKillfeedFile();
            long lastProcessedLine = server.getLastProcessedKillfeedLine();
            
            // Determine which files to process based on processHistorical flag
            List<String> filesToProcess = new ArrayList<>();
            
            if (processHistorical) {
                // When processing historical data, process ALL files
                logger.info("Processing ALL historical killfeed data for server: {}", server.getName());
                filesToProcess.addAll(files);
                // Reset the last processed line counter since we're starting from scratch
                lastProcessedLine = -1;
            } else {
                // Normal operation - only process from the last point or newest file
                if (lastProcessedFile.isEmpty()) {
                    // If no file has been processed yet, start with the newest file
                    lastProcessedFile = files.get(files.size() - 1);
                    filesToProcess.add(lastProcessedFile);
                    lastProcessedLine = -1;
                } else {
                    // Check if we need to move to a newer file
                    int fileIndex = files.indexOf(lastProcessedFile);
                    if (fileIndex < 0) {
                        // File no longer exists, start with the newest file
                        lastProcessedFile = files.get(files.size() - 1);
                        filesToProcess.add(lastProcessedFile);
                        lastProcessedLine = -1;
                    } else {
                        // Process current file and any newer files
                        filesToProcess.addAll(files.subList(fileIndex, files.size()));
                    }
                }
            }
            
            List<KillRecord> newRecords = new ArrayList<>();
            int processedKills = 0;
            
            // Process each file in the list
            for (String currentFile : filesToProcess) {
                String fileContent = sftpManager.readKillfeedFile(server, currentFile);
                if (fileContent.isEmpty()) {
                    logger.warn("Empty or unreadable killfeed file: {} for server: {}", 
                            currentFile, server.getName());
                    continue;
                }
                
                // Only apply the line counter to the first file if not in historical mode
                // For new files or historical processing, always start from the beginning
                long startLine = -1;
                if (currentFile.equals(lastProcessedFile) && !processHistorical) {
                    startLine = lastProcessedLine;
                }
                
                String[] lines = fileContent.split("\n");
                
                // Process each line after the last processed line
                for (int i = 0; i < lines.length; i++) {
                    if (i <= startLine) continue;
                    
                    String line = lines[i].trim();
                    if (line.isEmpty()) continue;
                    
                    KillRecord killRecord = parseKillRecord(line, server);
                    if (killRecord != null) {
                        newRecords.add(killRecord);
                        processedKills++;
                        
                        // Update player stats
                        updatePlayerStats(killRecord);
                        
                        // Send to Discord channel (only if not historical processing)
                        if (!processHistorical) {
                            sendKillfeedMessage(killfeedChannel, killRecord);
                        }
                    }
                    
                    // Update the last line we processed for this file
                    lastProcessedLine = i;
                }
                
                // Update the last processed file
                lastProcessedFile = currentFile;
            }
            
            // Save all new records to database
            if (!newRecords.isEmpty()) {
                killRecordRepository.saveAll(newRecords);
            }
            
            // Update server progress
            server.updateKillfeedProgress(lastProcessedFile, lastProcessedLine);
            
            logger.info("Processed {} new kills for server: {}", processedKills, server.getName());
            return processedKills;
        } catch (Exception e) {
            logger.error("Error processing killfeed for server: {}", server.getName(), e);
            return 0;
        }
    }
    
    /**
     * Process killfeed for a server (default method for backward compatibility)
     * @param server The game server to process
     * @return Number of new kill records processed
     */
    public int processServer(GameServer server) {
        return processServer(server, false);
    }
    
    /**
     * Parse a CSV line into a KillRecord
     */
    private KillRecord parseKillRecord(String line, GameServer server) {
        Matcher matcher = CSV_PATTERN.matcher(line);
        if (!matcher.matches()) {
            logger.warn("Killfeed line does not match expected format: {}", line);
            return null;
        }
        
        try {
            String timestamp = matcher.group(1);
            String killer = matcher.group(2);
            String killerId = matcher.group(3);
            String victim = matcher.group(4);
            String victimId = matcher.group(5);
            String weapon = matcher.group(6);
            String distanceStr = matcher.group(7);
            String killerPlatform = matcher.group(8);
            String victimPlatform = matcher.group(9);
            
            // Handle suicide cases - identify if this is a suicide/falling death
            boolean isSuicide = killer.equals(victim);
            boolean isFalling = weapon.equalsIgnoreCase("falling") || 
                               weapon.toLowerCase().contains("fall damage");
            boolean isMenuSuicide = isSuicide && (weapon.toLowerCase().contains("suicide") || 
                                   weapon.toLowerCase().contains("menu"));
            
            long distance = Long.parseLong(distanceStr);
            long timeMs = DATE_FORMAT.parse(timestamp).getTime();
            
            KillRecord record = new KillRecord(
                    server.getGuildId(),
                    server.getName(),
                    killer,
                    victim,
                    weapon,
                    distance,
                    timeMs,
                    line
            );
            
            // Add death type flags
            record.setSuicide(isSuicide);
            record.setFalling(isFalling);
            record.setMenuSuicide(isMenuSuicide);
            
            return record;
        } catch (ParseException e) {
            logger.error("Error parsing killfeed timestamp in line: {}", line, e);
            return null;
        } catch (NumberFormatException e) {
            logger.error("Error parsing killfeed distance in line: {}", line, e);
            return null;
        } catch (Exception e) {
            logger.error("Error parsing killfeed line: {}", line, e);
            return null;
        }
    }
    
    /**
     * Update player statistics from a kill record
     * Enhanced to handle suicides separately from regular deaths
     */
    private void updatePlayerStats(KillRecord record) {
        try {
            // For suicides, only update the victim's stats
            if (record.isSuicide()) {
                // Find or create victim player (who committed suicide)
                Player victim = playerRepository.findByName(record.getVictim());
                if (victim == null) {
                    // Create new player with a generated ID based on name
                    victim = new Player(record.getVictim().toLowerCase().replace(" ", "_") + "_id", record.getVictim());
                    playerRepository.save(victim);
                }
                
                // Add suicide count instead of a regular death
                victim.addSuicide();
                
                // Save the updated player stats
                playerRepository.save(victim);
                
                // For suicides, we don't update weapon or kill stats
                return;
            }
            
            // Normal kill processing for non-suicide events
            // Find or create killer player
            Player killer = playerRepository.findByName(record.getKiller());
            if (killer == null) {
                // Create new player with a generated ID based on name
                killer = new Player(record.getKiller().toLowerCase().replace(" ", "_") + "_id", record.getKiller());
                playerRepository.save(killer);
            }
            
            // Find or create victim player
            Player victim = playerRepository.findByName(record.getVictim());
            if (victim == null) {
                // Create new player with a generated ID based on name
                victim = new Player(record.getVictim().toLowerCase().replace(" ", "_") + "_id", record.getVictim());
                playerRepository.save(victim);
            }
            
            // Update killer stats
            killer.addKill();
            playerRepository.save(killer);
            
            // Update victim stats
            victim.addDeath();
            playerRepository.save(victim);
            
            // Track weapon stats
            if (killer.getMostUsedWeapon().equals(record.getWeapon())) {
                killer.setMostUsedWeaponKills(killer.getMostUsedWeaponKills() + 1);
            } else if (killer.getMostUsedWeaponKills() == 0) {
                killer.setMostUsedWeapon(record.getWeapon());
                killer.setMostUsedWeaponKills(1);
            }
            
            // Track victim stats
            if (killer.getMostKilledPlayer().equals(record.getVictim())) {
                killer.setMostKilledPlayerCount(killer.getMostKilledPlayerCount() + 1);
            } else if (killer.getMostKilledPlayerCount() == 0) {
                killer.setMostKilledPlayer(record.getVictim());
                killer.setMostKilledPlayerCount(1);
            }
            
            // Track killer stats for victim
            if (victim.getKilledByMost().equals(record.getKiller())) {
                victim.setKilledByMostCount(victim.getKilledByMostCount() + 1);
            } else if (victim.getKilledByMostCount() == 0) {
                victim.setKilledByMost(record.getKiller());
                victim.setKilledByMostCount(1);
            }
        } catch (Exception e) {
            logger.error("Error updating player stats for kill record: {} -> {}", 
                    record.getKiller(), record.getVictim(), e);
        }
    }
    
    /**
     * Send a killfeed message to Discord
     * Enhanced to handle different death types (kills, suicides, falling deaths)
     */
    private void sendKillfeedMessage(TextChannel channel, KillRecord record) {
        if (channel == null) return;
        
        // Different embed for each death type
        if (record.isSuicide()) {
            if (record.isFalling()) {
                // Falling death
                channel.sendMessageEmbeds(AdvancedEmbeds.advancedFallingDeathEmbed(
                    record.getVictim(), 
                    (int)record.getDistance()  // Use distance as approximate height
                )).queue();
            } else {
                // Other suicide - normalize menu suicide messages
                String cause = record.getWeapon();
                
                // Handle menu suicide with varied messages
                if (record.isMenuSuicide() || 
                    cause.toLowerCase().contains("suicide_by_relocation") ||
                    cause.toLowerCase().contains("menu")) {
                    
                    // Normalize menu suicide messages with random variants
                    String[] menuSuicideCauses = {
                        "Menu Suicide", 
                        "Wrong Button", 
                        "Menu Navigation",
                        "Respawn Request",
                        "Voluntary Departure",
                        "Redeployment",
                        "Rage Quit",
                        "Strategic Reposition"
                    };
                    
                    int randomIndex = new Random().nextInt(menuSuicideCauses.length);
                    cause = menuSuicideCauses[randomIndex];
                }
                
                // Clean up other causes
                cause = cause.replace("_", " ").trim();
                
                channel.sendMessageEmbeds(AdvancedEmbeds.advancedSuicideEmbed(
                    record.getVictim(), 
                    cause
                )).queue();
            }
        } else {
            // Regular kill
            channel.sendMessageEmbeds(EmbedUtils.killfeedEmbed(
                record.getKiller(), 
                record.getVictim(), 
                record.getWeapon(), 
                (int)record.getDistance()
            )).queue();
        }
    }
    
    /**
     * Get the appropriate Text Channel to send messages to based on event type
     * @param server The game server
     * @param eventType The type of event (kill, death, connection, airdrop, etc.)
     * @return The text channel, or null if not found/configured
     */
    private TextChannel getTextChannel(GameServer server, String eventType) {
        if (jda == null || server.getGuildId() == 0) {
            return null;
        }
        
        // Default to killfeed channel from server config
        long channelId = server.getKillfeedChannelId();
        
        // Try to find guild configuration for multi-channel support
        if ("kill".equals(eventType)) {
            // Use default killfeed channel for kills
            channelId = server.getKillfeedChannelId();
        } else {
            // Check if guild has specialized channels configured
            GuildConfigRepository guildConfigRepo = new GuildConfigRepository();
            GuildConfig guildConfig = guildConfigRepo.findByGuildId(server.getGuildId());
            
            if (guildConfig != null) {
                // If guild has specialized channels, use the appropriate one for the event type
                long eventChannelId = guildConfig.getLogChannelForEventType(eventType);
                if (eventChannelId != 0) {
                    channelId = eventChannelId;
                }
            }
        }
        
        // If no channel configured, return null
        if (channelId == 0) {
            return null;
        }
        
        // Get the channel from JDA
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            logger.warn("Could not find text channel with ID {} for server {} and event type {}",
                    channelId, server.getName(), eventType);
        }
        
        return channel;
    }
}
