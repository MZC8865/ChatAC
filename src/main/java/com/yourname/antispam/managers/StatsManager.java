package com.yourname.antispam.managers;

import com.yourname.antispam.AntiSpamPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manager for tracking player statistics (message count, violation count).
 */
public class StatsManager {
    private final AntiSpamPlugin plugin;
    private final ConcurrentHashMap<UUID, PlayerStats> playerStats;
    private final File statsFile;
    private FileConfiguration statsConfig;
    private final Object lock = new Object();
    
    public StatsManager(AntiSpamPlugin plugin) {
        this.plugin = plugin;
        this.playerStats = new ConcurrentHashMap<>();
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        loadStats();
        
        // Start auto-save task (every 5 minutes)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveStats, 6000L, 6000L);
    }
    
    /**
     * Record a message sent by a player.
     * 
     * @param playerUUID Player's UUID
     */
    public void recordMessage(UUID playerUUID) {
        playerStats.computeIfAbsent(playerUUID, k -> new PlayerStats()).incrementMessages();
    }
    
    /**
     * Record a violation by a player.
     * 
     * @param playerUUID Player's UUID
     * @param violationType Type of violation (e.g., "spam", "profanity", "similarity")
     */
    public void recordViolation(UUID playerUUID, String violationType) {
        recordViolation(playerUUID, violationType, null);
    }
    
    /**
     * Record a violation by a player with message content.
     * 
     * @param playerUUID Player's UUID
     * @param violationType Type of violation
     * @param message The message that violated the rules
     */
    public void recordViolation(UUID playerUUID, String violationType, String message) {
        PlayerStats stats = playerStats.computeIfAbsent(playerUUID, k -> new PlayerStats());
        stats.incrementViolations();
        stats.addViolationRecord(new ViolationRecord(
            System.currentTimeMillis(),
            violationType,
            message != null ? message : ""
        ));
    }
    
    /**
     * Get statistics for a player.
     * 
     * @param playerUUID Player's UUID
     * @return PlayerStats object, or null if no stats exist
     */
    public PlayerStats getStats(UUID playerUUID) {
        return playerStats.get(playerUUID);
    }
    
    /**
     * Reset statistics for a player.
     * 
     * @param playerUUID Player's UUID
     */
    public void resetStats(UUID playerUUID) {
        playerStats.remove(playerUUID);
        saveStats();
    }
    
    /**
     * Clean up stats for offline players (called periodically).
     * Removes stats for players who haven't been online recently.
     */
    public void cleanupOfflinePlayerStats() {
        // This will be called by the cleanup task
        // For now, we keep all stats. In the future, we could add a last-seen timestamp
        // and remove stats for players who haven't been online for X days
    }
    
    /**
     * Load stats from file.
     */
    private void loadStats() {
        synchronized (lock) {
            if (!statsFile.exists()) {
                try {
                    statsFile.getParentFile().mkdirs();
                    statsFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to create stats.yml: " + e.getMessage());
                    return;
                }
            }
            
            statsConfig = YamlConfiguration.loadConfiguration(statsFile);
            
            // Load stats from config
            if (statsConfig.contains("stats")) {
                for (String uuidStr : statsConfig.getConfigurationSection("stats").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        int messages = statsConfig.getInt("stats." + uuidStr + ".messages", 0);
                        int violations = statsConfig.getInt("stats." + uuidStr + ".violations", 0);
                        
                        PlayerStats stats = new PlayerStats();
                        stats.setMessages(messages);
                        stats.setViolations(violations);
                        
                        // Load violation records
                        if (statsConfig.contains("stats." + uuidStr + ".records")) {
                            List<?> recordsList = statsConfig.getList("stats." + uuidStr + ".records");
                            if (recordsList != null) {
                                for (Object obj : recordsList) {
                                    if (obj instanceof String) {
                                        String recordStr = (String) obj;
                                        ViolationRecord record = ViolationRecord.fromString(recordStr);
                                        if (record != null) {
                                            stats.addViolationRecord(record);
                                        }
                                    }
                                }
                            }
                        }
                        
                        playerStats.put(uuid, stats);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in stats.yml: " + uuidStr);
                    }
                }
            }
            
            plugin.getLogger().info("Loaded stats for " + playerStats.size() + " players");
        }
    }
    
    /**
     * Save stats to file.
     */
    public void saveStats() {
        synchronized (lock) {
            statsConfig = new YamlConfiguration();
            
            // Save all player stats
            for (var entry : playerStats.entrySet()) {
                String uuidStr = entry.getKey().toString();
                PlayerStats stats = entry.getValue();
                statsConfig.set("stats." + uuidStr + ".messages", stats.getMessages());
                statsConfig.set("stats." + uuidStr + ".violations", stats.getViolations());
                
                // Save violation records (only keep last 100 records per player)
                List<String> recordStrings = stats.getViolationRecords().stream()
                    .map(ViolationRecord::toString)
                    .collect(Collectors.toList());
                statsConfig.set("stats." + uuidStr + ".records", recordStrings);
            }
            
            try {
                statsConfig.save(statsFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save stats.yml: " + e.getMessage());
            }
        }
    }
    
    /**
     * Inner class to hold player statistics.
     */
    public static class PlayerStats {
        private int messages;
        private int violations;
        private final List<ViolationRecord> violationRecords;
        private static final int MAX_RECORDS = 100; // Keep last 100 violations
        
        public PlayerStats() {
            this.messages = 0;
            this.violations = 0;
            this.violationRecords = new ArrayList<>();
        }
        
        public void incrementMessages() {
            messages++;
        }
        
        public void incrementViolations() {
            violations++;
        }
        
        public void addViolationRecord(ViolationRecord record) {
            violationRecords.add(record);
            // Keep only last MAX_RECORDS records
            if (violationRecords.size() > MAX_RECORDS) {
                violationRecords.remove(0);
            }
        }
        
        public int getMessages() {
            return messages;
        }
        
        public int getViolations() {
            return violations;
        }
        
        public List<ViolationRecord> getViolationRecords() {
            return new ArrayList<>(violationRecords);
        }
        
        /**
         * Get violation records within a time range.
         * 
         * @param minutes Number of minutes to look back
         * @return List of violation records within the time range
         */
        public List<ViolationRecord> getRecentViolations(int minutes) {
            long cutoffTime = System.currentTimeMillis() - (minutes * 60 * 1000L);
            return violationRecords.stream()
                .filter(record -> record.getTimestamp() >= cutoffTime)
                .collect(Collectors.toList());
        }
        
        public void setMessages(int messages) {
            this.messages = messages;
        }
        
        public void setViolations(int violations) {
            this.violations = violations;
        }
    }
    
    /**
     * Inner class to hold violation record details.
     */
    public static class ViolationRecord {
        private final long timestamp;
        private final String type;
        private final String message;
        
        public ViolationRecord(long timestamp, String type, String message) {
            this.timestamp = timestamp;
            this.type = type;
            this.message = message;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getType() {
            return type;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return timestamp + "|" + type + "|" + message;
        }
        
        public static ViolationRecord fromString(String str) {
            try {
                String[] parts = str.split("\\|", 3);
                if (parts.length >= 2) {
                    long timestamp = Long.parseLong(parts[0]);
                    String type = parts[1];
                    String message = parts.length > 2 ? parts[2] : "";
                    return new ViolationRecord(timestamp, type, message);
                }
            } catch (Exception e) {
                // Invalid format, skip
            }
            return null;
        }
    }
}
