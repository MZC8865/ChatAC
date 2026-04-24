package com.yourname.antispam.managers;

import com.yourname.antispam.AntiSpamPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for handling whitelist (players who bypass anti-spam checks).
 */
public class WhitelistManager {
    private final AntiSpamPlugin plugin;
    private final Set<UUID> whitelistedPlayers;
    private final File whitelistFile;
    private FileConfiguration whitelistConfig;
    private final Object lock = new Object();
    
    public WhitelistManager(AntiSpamPlugin plugin) {
        this.plugin = plugin;
        this.whitelistedPlayers = ConcurrentHashMap.newKeySet();
        this.whitelistFile = new File(plugin.getDataFolder(), "whitelist.yml");
        loadWhitelist();
    }
    
    /**
     * Add a player to the whitelist.
     * 
     * @param playerUUID Player's UUID
     * @param playerName Player's name (for logging)
     * @return true if added, false if already whitelisted
     */
    public boolean addPlayer(UUID playerUUID, String playerName) {
        boolean added = whitelistedPlayers.add(playerUUID);
        if (added) {
            saveWhitelist();
            plugin.getLogger().info("Added " + playerName + " to whitelist");
        }
        return added;
    }
    
    /**
     * Remove a player from the whitelist.
     * 
     * @param playerUUID Player's UUID
     * @param playerName Player's name (for logging)
     * @return true if removed, false if not whitelisted
     */
    public boolean removePlayer(UUID playerUUID, String playerName) {
        boolean removed = whitelistedPlayers.remove(playerUUID);
        if (removed) {
            saveWhitelist();
            plugin.getLogger().info("Removed " + playerName + " from whitelist");
        }
        return removed;
    }
    
    /**
     * Check if a player is whitelisted.
     * 
     * @param playerUUID Player's UUID
     * @return true if the player is whitelisted
     */
    public boolean isWhitelisted(UUID playerUUID) {
        return whitelistedPlayers.contains(playerUUID);
    }
    
    /**
     * Get all whitelisted player UUIDs.
     * 
     * @return Set of whitelisted UUIDs
     */
    public Set<UUID> getWhitelistedPlayers() {
        return new HashSet<>(whitelistedPlayers);
    }
    
    /**
     * Get the number of whitelisted players.
     * 
     * @return Number of whitelisted players
     */
    public int getWhitelistCount() {
        return whitelistedPlayers.size();
    }
    
    /**
     * Load whitelist from file.
     */
    private void loadWhitelist() {
        synchronized (lock) {
            if (!whitelistFile.exists()) {
                try {
                    whitelistFile.getParentFile().mkdirs();
                    whitelistFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to create whitelist.yml: " + e.getMessage());
                    return;
                }
            }
            
            whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
            
            // Load whitelist from config
            if (whitelistConfig.contains("whitelist")) {
                for (String uuidStr : whitelistConfig.getStringList("whitelist")) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        whitelistedPlayers.add(uuid);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in whitelist.yml: " + uuidStr);
                    }
                }
            }
            
            plugin.getLogger().info("Loaded " + whitelistedPlayers.size() + " whitelisted players");
        }
    }
    
    /**
     * Save whitelist to file.
     */
    private void saveWhitelist() {
        synchronized (lock) {
            whitelistConfig = new YamlConfiguration();
            
            // Convert UUIDs to strings
            java.util.List<String> uuidStrings = new java.util.ArrayList<>();
            for (UUID uuid : whitelistedPlayers) {
                uuidStrings.add(uuid.toString());
            }
            
            whitelistConfig.set("whitelist", uuidStrings);
            
            try {
                whitelistConfig.save(whitelistFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save whitelist.yml: " + e.getMessage());
            }
        }
    }
}
