package com.yourname.antispam.listeners;

import com.yourname.antispam.AntiSpamPlugin;
import com.yourname.antispam.utils.StringSimilarity;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for whisper/private message commands.
 * Monitors commands like /w, /msg, /tell, /whisper for anti-spam checks.
 */
public class WhisperLimiter implements Listener {
    private final AntiSpamPlugin plugin;
    
    // Track last message timestamp per user
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    
    // Track last message content per user
    private final Map<UUID, String> lastMessageContent = new ConcurrentHashMap<>();
    
    // Track message intervals for pattern detection (stores last 3 intervals)
    private final Map<UUID, java.util.LinkedList<Long>> messageIntervals = new ConcurrentHashMap<>();
    
    // Track players who have been flagged for bot-like behavior
    private final Map<UUID, Long> botFlaggedPlayers = new ConcurrentHashMap<>();
    
    // Expected interval for flagged players (tolerance)
    private final Map<UUID, Long> expectedInterval = new ConcurrentHashMap<>();
    
    public WhisperLimiter(AntiSpamPlugin plugin) {
        this.plugin = plugin;
        
        // Start cleanup task for offline players (every 10 minutes)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOfflinePlayers, 12000L, 12000L);
    }
    
    /**
     * Update message intervals for a player.
     * Keeps track of the last 3 intervals to detect patterns.
     * 
     * @param playerUUID Player's UUID
     * @param interval Time interval in milliseconds
     */
    private void updateMessageIntervals(UUID playerUUID, long interval) {
        messageIntervals.compute(playerUUID, (uuid, intervals) -> {
            if (intervals == null) {
                intervals = new java.util.LinkedList<>();
            }
            intervals.add(interval);
            // Keep only last 3 intervals
            while (intervals.size() > 3) {
                intervals.removeFirst();
            }
            return intervals;
        });
    }
    
    /**
     * Check if a player's message intervals show a suspicious pattern.
     * Detects if the last 3 intervals are nearly identical (within 1-2ms tolerance).
     * Once detected, the player is flagged and all subsequent messages with similar intervals are blocked.
     * 
     * @param playerUUID Player's UUID
     * @param currentInterval Current interval in milliseconds
     * @return true if a suspicious pattern is detected
     */
    private boolean isIntervalPatternDetected(UUID playerUUID, long currentInterval) {
        long tolerance = 2L; // 2ms tolerance
        
        // Check if player is already flagged
        Long flagTime = botFlaggedPlayers.get(playerUUID);
        if (flagTime != null) {
            // Player is flagged, check if current interval matches the expected pattern
            Long expected = expectedInterval.get(playerUUID);
            if (expected != null && Math.abs(currentInterval - expected) <= tolerance) {
                plugin.getLogger().info("Flagged player " + playerUUID + " continues whisper bot pattern: " + 
                    currentInterval + "ms (expected ~" + expected + "ms)");
                return true;
            } else {
                // Interval changed significantly, unflag the player
                plugin.getLogger().info("Player " + playerUUID + " changed whisper interval pattern, unflagging");
                botFlaggedPlayers.remove(playerUUID);
                expectedInterval.remove(playerUUID);
            }
        }
        
        // Not flagged yet, check for initial pattern (3 consecutive similar intervals)
        java.util.LinkedList<Long> intervals = messageIntervals.get(playerUUID);
        
        // Need at least 2 previous intervals to compare
        if (intervals == null || intervals.size() < 2) {
            return false;
        }
        
        // Get the last 2 intervals
        Long interval1 = intervals.get(intervals.size() - 2);
        Long interval2 = intervals.get(intervals.size() - 1);
        
        if (interval1 == null || interval2 == null) {
            return false;
        }
        
        // Check if all 3 intervals are nearly identical (within 2ms tolerance)
        boolean match1 = Math.abs(interval1 - interval2) <= tolerance;
        boolean match2 = Math.abs(interval2 - currentInterval) <= tolerance;
        boolean match3 = Math.abs(interval1 - currentInterval) <= tolerance;
        
        // All three intervals must be similar
        if (match1 && match2 && match3) {
            // Flag this player and record the expected interval
            long avgInterval = (interval1 + interval2 + currentInterval) / 3;
            botFlaggedPlayers.put(playerUUID, System.currentTimeMillis());
            expectedInterval.put(playerUUID, avgInterval);
            
            plugin.getLogger().info("Initial whisper bot pattern detected for player " + playerUUID + ": " + 
                interval1 + "ms, " + interval2 + "ms, " + currentInterval + "ms (avg=" + avgInterval + "ms, tolerance=" + tolerance + "ms)");
            plugin.getLogger().info("Player flagged - will block all subsequent whispers with similar intervals");
            return true;
        }
        
        return false;
    }
    
    /**
     * Clean up data for offline players.
     */
    private void cleanupOfflinePlayers() {
        java.util.Set<UUID> onlinePlayerUUIDs = new java.util.HashSet<>();
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            onlinePlayerUUIDs.add(player.getUniqueId());
        }
        
        // Remove offline players from maps
        lastMessageTime.keySet().retainAll(onlinePlayerUUIDs);
        lastMessageContent.keySet().retainAll(onlinePlayerUUIDs);
        messageIntervals.keySet().retainAll(onlinePlayerUUIDs);
        botFlaggedPlayers.keySet().retainAll(onlinePlayerUUIDs);
        expectedInterval.keySet().retainAll(onlinePlayerUUIDs);
        
        plugin.getLogger().info("WhisperLimiter: Cleaned up offline player data");
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Check if whisper check is enabled
        if (!plugin.isWhisperCheckEnabled()) {
            return;
        }
        
        String message = event.getMessage().toLowerCase();
        
        // Check if this is a whisper command
        if (!isWhisperCommand(message)) {
            return;
        }
        
        // Extract the message content from the command
        String whisperContent = extractWhisperContent(event.getMessage());
        if (whisperContent == null || whisperContent.isEmpty()) {
            return;
        }
        
        UUID id = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        
        // Whitelist check: bypass all checks if whitelisted
        if (plugin.getWhitelistManager().isWhitelisted(id)) {
            Long lastTime = lastMessageTime.put(id, now);
            lastMessageContent.put(id, whisperContent);
            // Update intervals for whitelisted players too
            if (lastTime != null) {
                updateMessageIntervals(id, now - lastTime);
            }
            return;
        }
        
        // Mute check: highest priority
        if (plugin.getMuteManager().isMuted(id)) {
            event.setCancelled(true);
            long remaining = plugin.getMuteManager().getRemainingMuteTime(id);
            String timeStr = plugin.getMuteManager().formatTime(remaining);
            event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "你已被禁言，剩余时间：" + timeStr);
            plugin.getStatsManager().recordViolation(id, "muted", whisperContent);
            return;
        }
        
        // Profanity filter: check for blocked words first (highest priority)
        if (plugin.isProfanityFilterEnabled()) {
            if (plugin.getProfanityFilter().containsProfanity(whisperContent)) {
                event.setCancelled(true);
                String blockedWord = plugin.getProfanityFilter().findBlockedWord(whisperContent);
                event.getPlayer().sendMessage(ChatColor.RED + "有违规文字：" + ChatColor.YELLOW + blockedWord);
                plugin.getLogger().info("AntiSpam: Blocked whisper from " + event.getPlayer().getName() + 
                    " (profanity detected: " + blockedWord + ")");
                plugin.getStatsManager().recordViolation(id, "profanity", whisperContent);
                return;
            }
        }
        
        // Fast-send detection
        long currentDelay = plugin.getAntiSpamDelayMs();
        Long last = lastMessageTime.get(id);
        long interval = 0;
        
        if (last != null) {
            interval = now - last;
            
            if (interval < currentDelay) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "请勿刷屏");
                plugin.getLogger().info("AntiSpam: Blocked whisper from " + event.getPlayer().getName() + 
                    " (sent too fast, gap=" + interval + "ms)");
                plugin.getStatsManager().recordViolation(id, "spam", whisperContent);
                return;
            }
            
            // Interval pattern detection: check if intervals are suspiciously consistent
            if (plugin.isIntervalCheckEnabled()) {
                if (isIntervalPatternDetected(id, interval)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "检测到异常发送模式（疑似机器人）");
                    plugin.getLogger().info("AntiSpam: Blocked whisper from " + event.getPlayer().getName() + 
                        " (interval pattern detected, suspected bot behavior)");
                    plugin.getStatsManager().recordViolation(id, "bot-pattern", whisperContent);
                    // Update interval but don't update lastMessageTime
                    updateMessageIntervals(id, interval);
                    return;
                }
            }
        }
        
        // Similarity detection
        if (plugin.isSimilarityCheckEnabled()) {
            String lastMsg = lastMessageContent.get(id);
            int minLength = plugin.getMinLengthForCheck();
            
            if (lastMsg != null && whisperContent.length() >= minLength && lastMsg.length() >= minLength) {
                double similarity = StringSimilarity.similarity(whisperContent, lastMsg);
                double threshold = plugin.getSimilarityThreshold();
                
                if (similarity >= threshold) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "消息过于相似");
                    plugin.getLogger().info("AntiSpam: Blocked whisper from " + event.getPlayer().getName() + 
                        " (similar message, similarity=" + String.format("%.2f", similarity * 100) + "%)");
                    plugin.getStatsManager().recordViolation(id, "similarity", whisperContent);
                    return;
                }
            }
        }
        
        // Update last message time and content for successful messages
        lastMessageTime.put(id, now);
        lastMessageContent.put(id, whisperContent);
        
        // Update message intervals
        if (interval > 0) {
            updateMessageIntervals(id, interval);
        }
        
        // Clear bot flag if player sends a successful message (pattern broken)
        if (botFlaggedPlayers.containsKey(id)) {
            plugin.getLogger().info("Player " + id + " sent successful whisper, clearing bot flag");
            botFlaggedPlayers.remove(id);
            expectedInterval.remove(id);
        }
    }
    
    /**
     * Check if the command is a whisper command.
     */
    private boolean isWhisperCommand(String command) {
        // Remove leading slash
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        
        // Check common whisper command aliases
        String[] whisperCommands = {"w ", "msg ", "tell ", "whisper ", "m ", "t ", "pm "};
        for (String cmd : whisperCommands) {
            if (command.startsWith(cmd)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extract the message content from a whisper command.
     * Format: /w <player> <message>
     */
    private String extractWhisperContent(String command) {
        // Remove leading slash
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        
        // Split by spaces
        String[] parts = command.split(" ", 3);
        
        // Need at least: command, player, message
        if (parts.length < 3) {
            return null;
        }
        
        // Return the message part
        return parts[2];
    }
}
