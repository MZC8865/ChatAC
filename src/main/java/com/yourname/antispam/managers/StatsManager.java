package com.yourname.antispam.managers;

import com.yourname.antispam.AntiSpamPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        playerStats.computeIfAbsent(playerUUID, k -> new PlayerStats()).incrementViolations();
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
        
        public PlayerStats() {
            this.messages = 0;
            this.violations = 0;
        }
        
        public void incrementMessages() {
            messages++;
        }
        
        public void incrementViolations() {
            violations++;
        }
        
        public int getMessages() {
            return messages;
        }
        
        public int getViolations() {
            return violations;
        }
        
        public void setMessages(int messages) {
            this.messages = messages;
        }
        
        public void setViolations(int violations) {
            this.violations = violations;
        }
    }
}
