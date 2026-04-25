package com.yourname.antispam.listeners;

import com.yourname.antispam.AntiSpamPlugin;
import com.yourname.antispam.utils.StringSimilarity;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very lightweight anti-spam: limits how often a player can chat.
 * If a player sends a message within the configured delay (default 20ms) after their last message,
 * the new message is blocked.
 * Also checks for message similarity to prevent repetitive spam.
 */
public class ChatLimiter implements Listener {
    private final AntiSpamPlugin plugin;

    // Track last message timestamp per user for fast-send detection
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    
    // Track last message content per user for similarity detection
    private final Map<UUID, String> lastMessageContent = new ConcurrentHashMap<>();
    
    // Track message intervals for pattern detection (stores last 3 intervals)
    private final Map<UUID, java.util.LinkedList<Long>> messageIntervals = new ConcurrentHashMap<>();
    
    // Track players who have been flagged for bot-like behavior
    private final Map<UUID, Long> botFlaggedPlayers = new ConcurrentHashMap<>();
    
    // Expected interval for flagged players (tolerance)
    private final Map<UUID, Long> expectedInterval = new ConcurrentHashMap<>();

    public ChatLimiter(AntiSpamPlugin plugin) {
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
     * Detects if the last 2 intervals are nearly identical (within 5ms tolerance).
     * Once detected, the player is flagged and all subsequent messages with similar intervals are blocked.
     * 
     * @param playerUUID Player's UUID
     * @param currentInterval Current interval in milliseconds
     * @return true if a suspicious pattern is detected
     */
    private boolean isIntervalPatternDetected(UUID playerUUID, long currentInterval) {
        long tolerance = 5L; // 5ms tolerance
        
        // Check if player is already flagged
        Long flagTime = botFlaggedPlayers.get(playerUUID);
        if (flagTime != null) {
            // Player is flagged, check if current interval matches the expected pattern
            Long expected = expectedInterval.get(playerUUID);
            if (expected != null && Math.abs(currentInterval - expected) <= tolerance) {
                plugin.getLogger().info("Flagged player " + playerUUID + " continues bot pattern: " + 
                    currentInterval + "ms (expected ~" + expected + "ms)");
                return true;
            } else {
                // Interval changed significantly, unflag the player
                plugin.getLogger().info("Player " + playerUUID + " changed interval pattern, unflagging");
                botFlaggedPlayers.remove(playerUUID);
                expectedInterval.remove(playerUUID);
            }
        }
        
        // Not flagged yet, check for initial pattern (2 consecutive similar intervals)
        java.util.LinkedList<Long> intervals = messageIntervals.get(playerUUID);
        
        // Need at least 1 previous interval to compare
        if (intervals == null || intervals.size() < 1) {
            return false;
        }
        
        // Get the last interval
        Long interval1 = intervals.get(intervals.size() - 1);
        
        if (interval1 == null) {
            return false;
        }
        
        // Check if the 2 intervals are nearly identical (within 5ms tolerance)
        boolean match = Math.abs(interval1 - currentInterval) <= tolerance;
        
        // Both intervals must be similar
        if (match) {
            // Flag this player and record the expected interval
            long avgInterval = (interval1 + currentInterval) / 2;
            botFlaggedPlayers.put(playerUUID, System.currentTimeMillis());
            expectedInterval.put(playerUUID, avgInterval);
            
            plugin.getLogger().info("Initial bot pattern detected for player " + playerUUID + ": " + 
                interval1 + "ms, " + currentInterval + "ms (avg=" + avgInterval + "ms, tolerance=" + tolerance + "ms)");
            plugin.getLogger().info("Player flagged - will block all subsequent messages with similar intervals");
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
        
        plugin.getLogger().info("ChatLimiter: Cleaned up offline player data");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        // If another plugin already canceled it, skip
        if (event.isCancelled()) return;

        UUID id = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        String message = event.getMessage();
        
        // Whitelist check: bypass all checks if whitelisted
        if (plugin.getWhitelistManager().isWhitelisted(id)) {
            // Still record the message for stats
            plugin.getStatsManager().recordMessage(id);
            // Update records for whitelisted players too
            Long lastTime = lastMessageTime.put(id, now);
            lastMessageContent.put(id, message);
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
            event.getPlayer().sendMessage(ChatColor.RED + "你已被禁言，剩余时间：" + timeStr);
            plugin.getStatsManager().recordViolation(id, "muted", message);
            return;
        }

        // Profanity filter: check for blocked words first (highest priority)
        if (plugin.isProfanityFilterEnabled()) {
            if (plugin.getProfanityFilter().containsProfanity(message)) {
                event.setCancelled(true);
                String blockedWord = plugin.getProfanityFilter().findBlockedWord(message);
                event.getPlayer().sendMessage(ChatColor.RED + "有违规文字：" + ChatColor.YELLOW + blockedWord);
                plugin.getLogger().info("AntiSpam: Blocked chat from " + event.getPlayer().getName() + 
                    " (profanity detected: " + blockedWord + ")");
                plugin.getStatsManager().recordViolation(id, "profanity", message);
                return;
            }
        }

        // Fast-send detection: block if messages are sent too quickly
        long currentDelay = plugin.getAntiSpamDelayMs();
        Long last = lastMessageTime.get(id);
        long interval = 0;
        
        if (last != null) {
            interval = now - last;
            
            if (interval < currentDelay) {
                // Block the message immediately
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "请勿刷屏（请等待" + ((currentDelay - interval) / 1000.0) + "秒）");
                plugin.getLogger().info("AntiSpam: Blocked chat from " + event.getPlayer().getName() + 
                    " (sent too fast, gap=" + interval + "ms, required=" + currentDelay + "ms)");
                plugin.getStatsManager().recordViolation(id, "spam", message);
                // Do NOT update lastMessageTime so the cooldown continues from the last successful message
                return;
            }
            
            // Interval pattern detection: check if intervals are suspiciously consistent
            if (plugin.isIntervalCheckEnabled()) {
                if (isIntervalPatternDetected(id, interval)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "检测到异常发送模式（疑似机器人）");
                    plugin.getLogger().info("AntiSpam: Blocked chat from " + event.getPlayer().getName() + 
                        " (interval pattern detected, suspected bot behavior)");
                    plugin.getStatsManager().recordViolation(id, "bot-pattern", message);
                    // Update lastMessageTime to keep tracking intervals, but don't update messageIntervals
                    lastMessageTime.put(id, now);
                    return;
                }
            }
        }
        
        // Similarity detection: check if message is too similar to the last one
        if (plugin.isSimilarityCheckEnabled()) {
            String lastMsg = lastMessageContent.get(id);
            int minLength = plugin.getMinLengthForCheck();
            
            // Only check similarity if both messages meet minimum length requirement
            if (lastMsg != null && message.length() >= minLength && lastMsg.length() >= minLength) {
                double similarity = StringSimilarity.similarity(message, lastMsg);
                double threshold = plugin.getSimilarityThreshold();
                
                if (similarity >= threshold) {
                    // Block similar message
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "消息过于相似（相似度：" + 
                        String.format("%.0f", similarity * 100) + "%）");
                    plugin.getLogger().info("AntiSpam: Blocked chat from " + event.getPlayer().getName() + 
                        " (similar message, similarity=" + String.format("%.2f", similarity * 100) + 
                        "%, threshold=" + String.format("%.2f", threshold * 100) + "%)");
                    plugin.getStatsManager().recordViolation(id, "similarity", message);
                    return;
                }
            }
        }
        
        // Update last message time and content only for successful messages
        lastMessageTime.put(id, now);
        lastMessageContent.put(id, message);
        
        // Update message intervals
        if (interval > 0) {
            updateMessageIntervals(id, interval);
        }
        
        // Clear bot flag if player sends a successful message (pattern broken)
        if (botFlaggedPlayers.containsKey(id)) {
            plugin.getLogger().info("Player " + id + " sent successful message, clearing bot flag");
            botFlaggedPlayers.remove(id);
            expectedInterval.remove(id);
        }
        
        // Record successful message
        plugin.getStatsManager().recordMessage(id);
    }
}
