package com.yourname.antispam;

import com.yourname.antispam.commands.AntiSpamCommand;
import com.yourname.antispam.listeners.ChatLimiter;
import com.yourname.antispam.managers.MuteManager;
import com.yourname.antispam.managers.WhitelistManager;
import com.yourname.antispam.managers.StatsManager;
import com.yourname.antispam.utils.ProfanityFilter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Basic Paper/Spigot plugin scaffold for anti-spam.
 */
public class AntiSpamPlugin extends JavaPlugin {
    // Configurable anti-spam delay in milliseconds (default 20ms)
    private volatile long antiSpamDelayMs = 20L;
    // Similarity check configuration
    private volatile boolean similarityCheckEnabled = true;
    private volatile double similarityThreshold = 0.8;
    private volatile int minLengthForCheck = 3;
    // Profanity filter configuration
    private volatile boolean profanityFilterEnabled = true;
    private ProfanityFilter profanityFilter;
    // Whisper check configuration
    private volatile boolean whisperCheckEnabled = true;
    // Interval pattern detection configuration
    private volatile boolean intervalCheckEnabled = true;
    // Auto-mute configuration
    private volatile boolean autoMuteEnabled = true;
    private volatile int autoMuteViolationsPerMinute = 5;
    private volatile long autoMuteDuration = 300L; // seconds
    // Mute manager
    private MuteManager muteManager;
    // Whitelist manager
    private WhitelistManager whitelistManager;
    // Stats manager
    private StatsManager statsManager;
    // Cleanup task ID
    private int cleanupTaskId = -1;
    
    @Override
    public void onEnable() {
        getLogger().info("AntiSpam enabled");
        // Diagnostic: log a snippet of plugin.yml from the JAR to verify packaging
        try (InputStream in = getResource("plugin.yml")) {
            if (in != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                int maxLines = 5;
                int count = 0;
                while ((line = br.readLine()) != null && count < maxLines) {
                    getLogger().info("[plugin.yml preview] " + line);
                    count++;
                }
            } else {
                getLogger().warning("plugin.yml not found inside JAR.");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to read plugin.yml from JAR: " + e.getMessage());
        }
        // Register listeners
        getServer().getPluginManager().registerEvents(new ChatLimiter(this), this);
        getServer().getPluginManager().registerEvents(new com.yourname.antispam.listeners.WhisperLimiter(this), this);
        getLogger().info("AntiSpam: Registered chat and whisper listeners");
        
        // Load initial config
        saveDefaultConfig();
        loadFromConfig();
        
        // Initialize mute manager
        this.muteManager = new MuteManager(this);
        getLogger().info("AntiSpam: Mute manager initialized");
        
        // Initialize whitelist manager
        this.whitelistManager = new WhitelistManager(this);
        getLogger().info("AntiSpam: Whitelist manager initialized");
        
        // Initialize stats manager
        this.statsManager = new StatsManager(this);
        getLogger().info("AntiSpam: Stats manager initialized");
        
        // Start cleanup task (every 30 minutes)
        startCleanupTask();
        getLogger().info("AntiSpam: Cleanup task started");
        
        // Register command directly with CommandMap for Paper plugins
        try {
            AntiSpamCommand cmdExecutor = new AntiSpamCommand(this);
            
            // Unregister existing "chat" command if it exists to avoid conflicts
            org.bukkit.command.Command existingCmd = getServer().getCommandMap().getCommand("chat");
            if (existingCmd != null) {
                existingCmd.unregister(getServer().getCommandMap());
                getLogger().info("AntiSpam: Unregistered existing 'chat' command");
            }
            
            Command cmd = new Command("chat") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return cmdExecutor.onCommand(sender, this, label, args);
                }
                
                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                    List<String> completions = cmdExecutor.onTabComplete(sender, this, alias, args);
                    return completions != null ? completions : new java.util.ArrayList<>();
                }
            };
            cmd.setDescription("Chat management commands");
            cmd.setUsage("/chat antispam <subcommand>");
            cmd.setPermission("antispam.admin");
            
            getServer().getCommandMap().register("antispam", cmd);
            getLogger().info("AntiSpam: command '/chat' registered with tab completion");
        } catch (Exception e) {
            getLogger().warning("Failed to register command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Cancel cleanup task
        if (cleanupTaskId != -1) {
            getServer().getScheduler().cancelTask(cleanupTaskId);
        }
        
        // Save stats before shutdown
        if (statsManager != null) {
            statsManager.saveStats();
        }
        
        getLogger().info("AntiSpam disabled");
    }

    // Getter/setter for configurable anti-spam delay
    public synchronized long getAntiSpamDelayMs() {
        return antiSpamDelayMs;
    }

    public synchronized void setAntiSpamDelayMs(long ms) {
        if (ms < 0) ms = 0;
        this.antiSpamDelayMs = ms;
        getLogger().info("AntiSpam delay set to " + ms + " ms");
    }

    // Getters/setters for similarity check configuration
    public synchronized boolean isSimilarityCheckEnabled() {
        return similarityCheckEnabled;
    }

    public synchronized void setSimilarityCheckEnabled(boolean enabled) {
        this.similarityCheckEnabled = enabled;
        getLogger().info("Similarity check " + (enabled ? "enabled" : "disabled"));
    }

    public synchronized double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public synchronized void setSimilarityThreshold(double threshold) {
        if (threshold < 0.0) threshold = 0.0;
        if (threshold > 1.0) threshold = 1.0;
        this.similarityThreshold = threshold;
        getLogger().info("Similarity threshold set to " + threshold);
    }

    public synchronized int getMinLengthForCheck() {
        return minLengthForCheck;
    }

    public synchronized void setMinLengthForCheck(int length) {
        if (length < 0) length = 0;
        this.minLengthForCheck = length;
        getLogger().info("Min length for similarity check set to " + length);
    }

    // Getters/setters for profanity filter configuration
    public synchronized boolean isProfanityFilterEnabled() {
        return profanityFilterEnabled;
    }

    public synchronized void setProfanityFilterEnabled(boolean enabled) {
        this.profanityFilterEnabled = enabled;
        getLogger().info("Profanity filter " + (enabled ? "enabled" : "disabled"));
    }

    public synchronized ProfanityFilter getProfanityFilter() {
        return profanityFilter;
    }

    // Getters/setters for whisper check configuration
    public synchronized boolean isWhisperCheckEnabled() {
        return whisperCheckEnabled;
    }

    public synchronized void setWhisperCheckEnabled(boolean enabled) {
        this.whisperCheckEnabled = enabled;
        getLogger().info("Whisper check " + (enabled ? "enabled" : "disabled"));
    }

    // Getters/setters for interval pattern detection
    public synchronized boolean isIntervalCheckEnabled() {
        return intervalCheckEnabled;
    }

    public synchronized void setIntervalCheckEnabled(boolean enabled) {
        this.intervalCheckEnabled = enabled;
        getLogger().info("Interval pattern check " + (enabled ? "enabled" : "disabled"));
    }

    // Getters/setters for auto-mute configuration
    public synchronized boolean isAutoMuteEnabled() {
        return autoMuteEnabled;
    }

    public synchronized void setAutoMuteEnabled(boolean enabled) {
        this.autoMuteEnabled = enabled;
        getLogger().info("Auto-mute " + (enabled ? "enabled" : "disabled"));
    }

    public synchronized int getAutoMuteViolationsPerMinute() {
        return autoMuteViolationsPerMinute;
    }

    public synchronized void setAutoMuteViolationsPerMinute(int violations) {
        if (violations < 1) violations = 1;
        this.autoMuteViolationsPerMinute = violations;
        getLogger().info("Auto-mute violations threshold set to " + violations + " per minute");
    }

    public synchronized long getAutoMuteDuration() {
        return autoMuteDuration;
    }

    public synchronized void setAutoMuteDuration(long seconds) {
        if (seconds < 1) seconds = 1;
        this.autoMuteDuration = seconds;
        getLogger().info("Auto-mute duration set to " + seconds + " seconds");
    }

    // Getter for mute manager
    public MuteManager getMuteManager() {
        return muteManager;
    }
    
    // Getter for whitelist manager
    public WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }
    
    // Getter for stats manager
    public StatsManager getStatsManager() {
        return statsManager;
    }

    // Reload config from disk and apply values
    public synchronized void reloadFromConfig() {
        reloadConfig();
        loadFromConfig();
        getLogger().info("AntiSpam config reloaded");
    }

    // Load config values into fields
    public synchronized void loadFromConfig() {
        this.antiSpamDelayMs = getConfig().getLong("anti-spam.delay-ms", 20L);
        this.similarityCheckEnabled = getConfig().getBoolean("anti-spam.similarity-check", true);
        this.similarityThreshold = getConfig().getDouble("anti-spam.similarity-threshold", 0.8);
        this.minLengthForCheck = getConfig().getInt("anti-spam.min-length-for-check", 3);
        
        // Load profanity filter settings
        this.profanityFilterEnabled = getConfig().getBoolean("anti-spam.profanity-filter", true);
        List<String> blockedWords = getConfig().getStringList("anti-spam.blocked-words");
        this.profanityFilter = new ProfanityFilter(blockedWords);
        
        // Load whisper check setting
        this.whisperCheckEnabled = getConfig().getBoolean("anti-spam.whisper-check", true);
        
        // Load interval check setting
        this.intervalCheckEnabled = getConfig().getBoolean("anti-spam.interval-check", true);
        
        // Load auto-mute settings
        this.autoMuteEnabled = getConfig().getBoolean("anti-spam.auto-mute.enabled", true);
        this.autoMuteViolationsPerMinute = getConfig().getInt("anti-spam.auto-mute.violations-per-minute", 5);
        this.autoMuteDuration = getConfig().getLong("anti-spam.auto-mute.mute-duration", 300L);
        
        getLogger().info("AntiSpam config loaded: delayMs=" + antiSpamDelayMs + 
            ", similarityCheck=" + similarityCheckEnabled + 
            ", threshold=" + similarityThreshold + 
            ", minLength=" + minLengthForCheck +
            ", profanityFilter=" + profanityFilterEnabled +
            ", blockedWords=" + profanityFilter.getBlockedWordCount() +
            ", whisperCheck=" + whisperCheckEnabled +
            ", intervalCheck=" + intervalCheckEnabled +
            ", autoMute=" + autoMuteEnabled + 
            " (threshold=" + autoMuteViolationsPerMinute + "/min, duration=" + autoMuteDuration + "s)");
    }
    
    // Save a config value to disk
    public synchronized void saveConfigValue(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
        getLogger().info("Saved config: " + path + " = " + value);
    }
    
    /**
     * Start the cleanup task to remove offline player data periodically.
     */
    private void startCleanupTask() {
        // Run every 30 minutes (36000 ticks)
        cleanupTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            getLogger().info("Running cleanup task...");
            
            // Cleanup will be handled by listeners themselves
            // This is just a placeholder for future enhancements
            
            getLogger().info("Cleanup task completed");
        }, 36000L, 36000L);
    }
}
