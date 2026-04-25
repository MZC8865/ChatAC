package com.yourname.antispam.commands;

import com.yourname.antispam.AntiSpamPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command handler for /chat antispam commands with tab completion support.
 * Usage: /chat antispam <subcommand> [args]
 */
public class AntiSpamCommand implements CommandExecutor, TabCompleter {
    private final AntiSpamPlugin plugin;

    public AntiSpamCommand(AntiSpamPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if this is /chat antispam command
        if (args.length >= 1 && args[0].equalsIgnoreCase("antispam")) {
            return handleAntiSpamCommand(sender, args);
        }
        
        // Check if this is /chat whispercheck command
        if (args.length >= 1 && args[0].equalsIgnoreCase("whispercheck")) {
            return handleWhisperCheckCommand(sender, args);
        }
        
        // Check if this is /chat ban command
        if (args.length >= 1 && args[0].equalsIgnoreCase("ban")) {
            return handleBanCommand(sender, args);
        }
        
        // Check if this is /chat unban command
        if (args.length >= 1 && args[0].equalsIgnoreCase("unban")) {
            return handleUnbanCommand(sender, args);
        }
        
        // Check if this is /chat whitelist command
        if (args.length >= 1 && args[0].equalsIgnoreCase("whitelist")) {
            return handleWhitelistCommand(sender, args);
        }
        
        // Check if this is /chat stats command
        if (args.length >= 1 && args[0].equalsIgnoreCase("stats")) {
            return handleStatsCommand(sender, args);
        }
        
        return false; // Let other handlers deal with it
    }
    
    private boolean handleAntiSpamCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("antispam.admin")) {
            sender.sendMessage("你没有权限使用此命令。");
            return true;
        }

        // Remove "antispam" from args
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        // /chat antispam delay <ms>
        if (subArgs.length >= 2 && subArgs[0].equalsIgnoreCase("delay")) {
            try {
                String raw = subArgs[1];
                String cleaned = raw.replaceAll("[^0-9]", "");
                if (cleaned.isEmpty()) {
                    throw new NumberFormatException("No digits found");
                }
                long ms = Long.parseLong(cleaned);
                plugin.setAntiSpamDelayMs(ms);
                plugin.saveConfigValue("anti-spam.delay-ms", ms);
                sender.sendMessage("AntiSpam 延迟已设置为 " + ms + " ms");
            } catch (NumberFormatException e) {
                sender.sendMessage("用法错误: /chat antispam delay <毫秒数>");
            }
            return true;
        }
        
        // /chat antispam similarity <0.0-1.0>
        if (subArgs.length >= 2 && subArgs[0].equalsIgnoreCase("similarity")) {
            try {
                double threshold = Double.parseDouble(subArgs[1]);
                if (threshold < 0.0 || threshold > 1.0) {
                    sender.sendMessage("相似度阈值必须在 0.0 到 1.0 之间");
                    return true;
                }
                plugin.setSimilarityThreshold(threshold);
                plugin.saveConfigValue("anti-spam.similarity-threshold", threshold);
                sender.sendMessage("相似度阈值已设置为 " + String.format("%.2f", threshold * 100));
            } catch (NumberFormatException e) {
                sender.sendMessage("用法错误: /chat antispam similarity <0.0-1.0>");
            }
            return true;
        }
        
        // /chat antispam toggle similarity
        if (subArgs.length >= 2 && subArgs[0].equalsIgnoreCase("toggle") && subArgs[1].equalsIgnoreCase("similarity")) {
            boolean newState = !plugin.isSimilarityCheckEnabled();
            plugin.setSimilarityCheckEnabled(newState);
            plugin.saveConfigValue("anti-spam.similarity-check", newState);
            sender.sendMessage("相似度检测已" + (newState ? "启用" : "禁用"));
            return true;
        }
        
        // /chat antispam toggle profanity
        if (subArgs.length >= 2 && subArgs[0].equalsIgnoreCase("toggle") && subArgs[1].equalsIgnoreCase("profanity")) {
            boolean newState = !plugin.isProfanityFilterEnabled();
            plugin.setProfanityFilterEnabled(newState);
            plugin.saveConfigValue("anti-spam.profanity-filter", newState);
            sender.sendMessage("违规词过滤已" + (newState ? "启用" : "禁用"));
            return true;
        }
        
        // /chat antispam toggle interval
        if (subArgs.length >= 2 && subArgs[0].equalsIgnoreCase("toggle") && subArgs[1].equalsIgnoreCase("interval")) {
            boolean newState = !plugin.isIntervalCheckEnabled();
            plugin.setIntervalCheckEnabled(newState);
            plugin.saveConfigValue("anti-spam.interval-check", newState);
            sender.sendMessage("消息间隔检测已" + (newState ? "启用" : "禁用"));
            return true;
        }
        
        // /chat antispam toggle automute
        if (subArgs.length >= 2 && subArgs[0].equalsIgnoreCase("toggle") && subArgs[1].equalsIgnoreCase("automute")) {
            boolean newState = !plugin.isAutoMuteEnabled();
            plugin.setAutoMuteEnabled(newState);
            plugin.saveConfigValue("anti-spam.auto-mute.enabled", newState);
            sender.sendMessage("自动禁言已" + (newState ? "启用" : "禁用"));
            return true;
        }
        
        // /chat antispam automute threshold <number>
        if (subArgs.length >= 3 && subArgs[0].equalsIgnoreCase("automute") && subArgs[1].equalsIgnoreCase("threshold")) {
            try {
                int violations = Integer.parseInt(subArgs[2]);
                if (violations < 1) {
                    sender.sendMessage("违规次数必须大于0");
                    return true;
                }
                plugin.setAutoMuteViolationsPerMinute(violations);
                plugin.saveConfigValue("anti-spam.auto-mute.violations-per-minute", violations);
                sender.sendMessage("自动禁言阈值已设置为 " + violations + " 次/分钟");
            } catch (NumberFormatException e) {
                sender.sendMessage("用法错误: /chat antispam automute threshold <次数>");
            }
            return true;
        }
        
        // /chat antispam automute duration <time>
        if (subArgs.length >= 3 && subArgs[0].equalsIgnoreCase("automute") && subArgs[1].equalsIgnoreCase("duration")) {
            long seconds = parseTime(subArgs[2]);
            if (seconds <= 0) {
                sender.sendMessage("无效的时间格式: " + subArgs[2]);
                sender.sendMessage("时间格式: 数字+单位 (s=秒, m=分钟, h=小时, d=天)");
                return true;
            }
            plugin.setAutoMuteDuration(seconds);
            plugin.saveConfigValue("anti-spam.auto-mute.mute-duration", seconds);
            String timeStr = plugin.getMuteManager().formatTime(seconds);
            sender.sendMessage("自动禁言时长已设置为 " + timeStr);
            return true;
        }
        
        // /chat antispam status
        if (subArgs.length >= 1 && subArgs[0].equalsIgnoreCase("status")) {
            sender.sendMessage("AntiSpam Status:");
            sender.sendMessage("- delayMs: " + plugin.getAntiSpamDelayMs() + " ms");
            sender.sendMessage("- similarityCheck: " + (plugin.isSimilarityCheckEnabled() ? "启用" : "禁用"));
            sender.sendMessage("- similarityThreshold: " + String.format("%.2f", plugin.getSimilarityThreshold() * 100) + "%");
            sender.sendMessage("- minLengthForCheck: " + plugin.getMinLengthForCheck());
            sender.sendMessage("- profanityFilter: " + (plugin.isProfanityFilterEnabled() ? "启用" : "禁用"));
            sender.sendMessage("- blockedWords: " + plugin.getProfanityFilter().getBlockedWordCount() + " 个");
            sender.sendMessage("- whisperCheck: " + (plugin.isWhisperCheckEnabled() ? "启用" : "禁用"));
            sender.sendMessage("- intervalCheck: " + (plugin.isIntervalCheckEnabled() ? "启用" : "禁用"));
            sender.sendMessage("- autoMute: " + (plugin.isAutoMuteEnabled() ? "启用" : "禁用") + 
                " (阈值: " + plugin.getAutoMuteViolationsPerMinute() + " 次/分钟, 时长: " + 
                plugin.getAutoMuteDuration() + " 秒)");
            return true;
        }
        
        // /chat antispam reload
        if (subArgs.length >= 1 && subArgs[0].equalsIgnoreCase("reload")) {
            plugin.reloadFromConfig();
            sender.sendMessage("AntiSpam config reloaded");
            return true;
        }

        // Show usage
        sender.sendMessage("AntiSpam 命令:");
        sender.sendMessage("/chat antispam delay <毫秒数> - 设置消息间隔延迟");
        sender.sendMessage("/chat antispam similarity <0.0-1.0> - 设置相似度阈值");
        sender.sendMessage("/chat antispam toggle similarity - 开关相似度检测");
        sender.sendMessage("/chat antispam toggle profanity - 开关违规词过滤");
        sender.sendMessage("/chat antispam toggle interval - 开关消息间隔检测");
        sender.sendMessage("/chat antispam toggle automute - 开关自动禁言");
        sender.sendMessage("/chat antispam automute threshold <次数> - 设置自动禁言阈值");
        sender.sendMessage("/chat antispam automute duration <时间> - 设置自动禁言时长");
        sender.sendMessage("/chat antispam status - 查看当前配置");
        sender.sendMessage("/chat antispam reload - 重新加载配置");
        return true;
    }
    
    private boolean handleWhisperCheckCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("antispam.admin")) {
            sender.sendMessage("你没有权限使用此命令。");
            return true;
        }
        
        // /chat whispercheck on
        if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
            plugin.setWhisperCheckEnabled(true);
            plugin.saveConfigValue("anti-spam.whisper-check", true);
            sender.sendMessage("私信检测已启用并保存到配置文件");
            return true;
        }
        
        // /chat whispercheck off
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            plugin.setWhisperCheckEnabled(false);
            plugin.saveConfigValue("anti-spam.whisper-check", false);
            sender.sendMessage("私信检测已禁用并保存到配置文件");
            return true;
        }
        
        // Show current status
        sender.sendMessage("私信检测状态: " + (plugin.isWhisperCheckEnabled() ? "启用" : "禁用"));
        sender.sendMessage("用法:");
        sender.sendMessage("/chat whispercheck on - 启用私信检测");
        sender.sendMessage("/chat whispercheck off - 禁用私信检测");
        return true;
    }
    
    private boolean handleBanCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("antispam.admin")) {
            sender.sendMessage("你没有权限使用此命令。");
            return true;
        }
        
        // /chat ban <player> <time>
        if (args.length < 3) {
            sender.sendMessage("用法: /chat ban <玩家名> <时间>");
            sender.sendMessage("时间格式: 数字+单位 (s=秒, m=分钟, h=小时, d=天)");
            sender.sendMessage("例如: /chat ban Player 30m (禁言30分钟)");
            return true;
        }
        
        String playerName = args[1];
        String timeStr = args[2];
        
        // Find player
        org.bukkit.entity.Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("玩家 " + playerName + " 不在线或不存在");
            return true;
        }
        
        // Parse time
        long seconds = parseTime(timeStr);
        if (seconds <= 0) {
            sender.sendMessage("无效的时间格式: " + timeStr);
            sender.sendMessage("时间格式: 数字+单位 (s=秒, m=分钟, h=小时, d=天)");
            sender.sendMessage("例如: 30s, 5m, 2h, 1d");
            return true;
        }
        
        // Mute player
        plugin.getMuteManager().mutePlayer(target.getUniqueId(), target.getName(), seconds);
        String timeFormatted = plugin.getMuteManager().formatTime(seconds);
        
        sender.sendMessage("已禁言玩家 " + target.getName() + "，时长：" + timeFormatted);
        target.sendMessage(org.bukkit.ChatColor.RED + "你已被禁言，时长：" + timeFormatted);
        
        return true;
    }
    
    private boolean handleUnbanCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("antispam.admin")) {
            sender.sendMessage("你没有权限使用此命令。");
            return true;
        }
        
        // /chat unban <player>
        if (args.length < 2) {
            sender.sendMessage("用法: /chat unban <玩家名>");
            return true;
        }
        
        String playerName = args[1];
        
        // Find player
        org.bukkit.entity.Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("玩家 " + playerName + " 不在线或不存在");
            return true;
        }
        
        // Check if player is muted
        if (!plugin.getMuteManager().isMuted(target.getUniqueId())) {
            sender.sendMessage("玩家 " + target.getName() + " 没有被禁言");
            return true;
        }
        
        // Unmute player
        plugin.getMuteManager().unmutePlayer(target.getUniqueId(), target.getName());
        
        sender.sendMessage("已解除玩家 " + target.getName() + " 的禁言");
        target.sendMessage(org.bukkit.ChatColor.GREEN + "你的禁言已被解除");
        
        return true;
    }
    
    private boolean handleWhitelistCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("antispam.admin")) {
            sender.sendMessage("你没有权限使用此命令。");
            return true;
        }
        
        // /chat whitelist add <player>
        if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
            String playerName = args[2];
            org.bukkit.entity.Player target = plugin.getServer().getPlayer(playerName);
            
            if (target == null) {
                sender.sendMessage("玩家 " + playerName + " 不在线或不存在");
                return true;
            }
            
            boolean added = plugin.getWhitelistManager().addPlayer(target.getUniqueId(), target.getName());
            if (added) {
                sender.sendMessage("已将玩家 " + target.getName() + " 添加到白名单");
                target.sendMessage(org.bukkit.ChatColor.GREEN + "你已被添加到反垃圾白名单");
            } else {
                sender.sendMessage("玩家 " + target.getName() + " 已在白名单中");
            }
            return true;
        }
        
        // /chat whitelist del <player>
        if (args.length >= 3 && args[1].equalsIgnoreCase("del")) {
            String playerName = args[2];
            org.bukkit.entity.Player target = plugin.getServer().getPlayer(playerName);
            
            if (target == null) {
                sender.sendMessage("玩家 " + playerName + " 不在线或不存在");
                return true;
            }
            
            boolean removed = plugin.getWhitelistManager().removePlayer(target.getUniqueId(), target.getName());
            if (removed) {
                sender.sendMessage("已将玩家 " + target.getName() + " 从白名单移除");
                target.sendMessage(org.bukkit.ChatColor.YELLOW + "你已被从反垃圾白名单移除");
            } else {
                sender.sendMessage("玩家 " + target.getName() + " 不在白名单中");
            }
            return true;
        }
        
        // /chat whitelist list
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            java.util.Set<java.util.UUID> whitelisted = plugin.getWhitelistManager().getWhitelistedPlayers();
            
            if (whitelisted.isEmpty()) {
                sender.sendMessage("白名单为空");
                return true;
            }
            
            sender.sendMessage("白名单玩家 (" + whitelisted.size() + " 个):");
            for (java.util.UUID uuid : whitelisted) {
                org.bukkit.entity.Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) {
                    sender.sendMessage("- " + p.getName() + " (在线)");
                } else {
                    sender.sendMessage("- " + uuid.toString() + " (离线)");
                }
            }
            return true;
        }
        
        // Show usage
        sender.sendMessage("白名单命令:");
        sender.sendMessage("/chat whitelist add <玩家名> - 添加玩家到白名单");
        sender.sendMessage("/chat whitelist del <玩家名> - 从白名单移除玩家");
        sender.sendMessage("/chat whitelist list - 查看白名单");
        return true;
    }
    
    private boolean handleStatsCommand(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("antispam.admin")) {
            sender.sendMessage("你没有权限使用此命令。");
            return true;
        }
        
        // /chat stats <player>
        if (args.length < 2) {
            sender.sendMessage("用法: /chat stats <玩家名> [list [时间]]");
            return true;
        }
        
        String playerName = args[1];
        org.bukkit.entity.Player target = plugin.getServer().getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage("玩家 " + playerName + " 不在线或不存在");
            return true;
        }
        
        com.yourname.antispam.managers.StatsManager.PlayerStats stats = 
            plugin.getStatsManager().getStats(target.getUniqueId());
        
        if (stats == null) {
            sender.sendMessage("玩家 " + target.getName() + " 没有统计数据");
            return true;
        }
        
        // /chat stats <player> list [time]
        if (args.length >= 3 && args[2].equalsIgnoreCase("list")) {
            return handleStatsListCommand(sender, target, stats, args);
        }
        
        // Default stats display with colors
        sender.sendMessage(org.bukkit.ChatColor.GREEN + "=== " + target.getName() + " 的统计数据 ===");
        sender.sendMessage(org.bukkit.ChatColor.AQUA + "消息数量: " + org.bukkit.ChatColor.WHITE + stats.getMessages());
        sender.sendMessage(org.bukkit.ChatColor.AQUA + "违规次数: " + org.bukkit.ChatColor.WHITE + stats.getViolations());
        
        if (stats.getMessages() > 0) {
            double violationRate = (double) stats.getViolations() / stats.getMessages() * 100;
            org.bukkit.ChatColor rateColor = violationRate > 50 ? org.bukkit.ChatColor.RED : 
                                             violationRate > 20 ? org.bukkit.ChatColor.YELLOW : 
                                             org.bukkit.ChatColor.GREEN;
            sender.sendMessage(org.bukkit.ChatColor.AQUA + "违规率: " + rateColor + String.format("%.2f", violationRate) + "%");
        }
        
        return true;
    }
    
    /**
     * Handle /chat stats <player> list [time] command.
     * Shows violation history within the specified time period.
     * Groups consecutive identical violations together.
     */
    private boolean handleStatsListCommand(CommandSender sender, org.bukkit.entity.Player target, 
            com.yourname.antispam.managers.StatsManager.PlayerStats stats, String[] args) {
        // Parse time parameter (default 5 minutes)
        int minutes = 5;
        if (args.length >= 4) {
            String timeStr = args[3];
            long seconds = parseTime(timeStr);
            if (seconds > 0) {
                minutes = (int) (seconds / 60);
                if (minutes == 0) minutes = 1; // At least 1 minute
            } else {
                sender.sendMessage(org.bukkit.ChatColor.RED + "无效的时间格式: " + timeStr);
                sender.sendMessage("时间格式: 数字+单位 (s=秒, m=分钟, h=小时, d=天)");
                return true;
            }
        }
        
        // Get recent violations
        java.util.List<com.yourname.antispam.managers.StatsManager.ViolationRecord> violations = 
            stats.getRecentViolations(minutes);
        
        if (violations.isEmpty()) {
            sender.sendMessage(org.bukkit.ChatColor.YELLOW + "玩家 " + target.getName() + 
                " 在过去 " + minutes + " 分钟内没有违规记录");
            return true;
        }
        
        // Group consecutive identical violations
        java.util.List<GroupedViolation> groupedViolations = groupViolations(violations);
        
        // Display violations
        sender.sendMessage(org.bukkit.ChatColor.GREEN + "=== " + target.getName() + 
            " 的违规记录（过去 " + minutes + " 分钟）===");
        sender.sendMessage(org.bukkit.ChatColor.GRAY + "共 " + violations.size() + " 条违规，" + 
            groupedViolations.size() + " 组记录：");
        
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("HH:mm:ss");
        
        for (GroupedViolation group : groupedViolations) {
            String startTime = dateFormat.format(new java.util.Date(group.startTimestamp));
            String endTime = dateFormat.format(new java.util.Date(group.endTimestamp));
            String type = group.type;
            String message = group.message;
            int count = group.count;
            
            // Format: [开始时间 - 结束时间] 原因: 消息内容 (*次数)
            StringBuilder sb = new StringBuilder();
            sb.append(org.bukkit.ChatColor.GRAY);
            
            if (count == 1) {
                // Single violation: [时间] 原因: 消息
                sb.append("[").append(startTime).append("] ");
            } else {
                // Multiple violations: [开始时间 - 结束时间] 原因: 消息 (*次数)
                sb.append("[").append(startTime).append(" - ").append(endTime).append("] ");
            }
            
            sb.append(org.bukkit.ChatColor.RED).append(type).append(": ");
            sb.append(org.bukkit.ChatColor.YELLOW).append(message.isEmpty() ? "(无消息内容)" : message);
            
            if (count > 1) {
                sb.append(org.bukkit.ChatColor.GOLD).append(" (x").append(count).append(")");
            }
            
            sender.sendMessage(sb.toString());
        }
        
        return true;
    }
    
    /**
     * Group consecutive violations with the same type and message.
     */
    private java.util.List<GroupedViolation> groupViolations(
            java.util.List<com.yourname.antispam.managers.StatsManager.ViolationRecord> violations) {
        java.util.List<GroupedViolation> grouped = new java.util.ArrayList<>();
        
        if (violations.isEmpty()) {
            return grouped;
        }
        
        GroupedViolation current = null;
        
        for (com.yourname.antispam.managers.StatsManager.ViolationRecord record : violations) {
            String key = record.getType() + "|" + record.getMessage();
            
            if (current == null) {
                // First record
                current = new GroupedViolation(record.getTimestamp(), record.getTimestamp(), 
                    record.getType(), record.getMessage(), 1);
            } else {
                String currentKey = current.type + "|" + current.message;
                
                if (key.equals(currentKey)) {
                    // Same violation, update end time and increment count
                    current.endTimestamp = record.getTimestamp();
                    current.count++;
                } else {
                    // Different violation, save current and start new group
                    grouped.add(current);
                    current = new GroupedViolation(record.getTimestamp(), record.getTimestamp(), 
                        record.getType(), record.getMessage(), 1);
                }
            }
        }
        
        // Add the last group
        if (current != null) {
            grouped.add(current);
        }
        
        return grouped;
    }
    
    /**
     * Helper class to group consecutive identical violations.
     */
    private static class GroupedViolation {
        long startTimestamp;
        long endTimestamp;
        String type;
        String message;
        int count;
        
        GroupedViolation(long startTimestamp, long endTimestamp, String type, String message, int count) {
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
            this.type = type;
            this.message = message;
            this.count = count;
        }
    }
    
    /**
     * Parse time string to seconds.
     * Format: <number><unit> where unit is s/m/h/d
     * Examples: 30s, 5m, 2h, 1d
     */
    private long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return -1;
        }
        
        timeStr = timeStr.toLowerCase().trim();
        
        // Extract number and unit
        String numberPart = timeStr.replaceAll("[^0-9]", "");
        String unitPart = timeStr.replaceAll("[0-9]", "");
        
        if (numberPart.isEmpty()) {
            return -1;
        }
        
        try {
            long number = Long.parseLong(numberPart);
            
            // Default to seconds if no unit specified
            if (unitPart.isEmpty()) {
                return number;
            }
            
            // Convert based on unit
            switch (unitPart) {
                case "s":
                case "秒":
                    return number;
                case "m":
                case "分":
                case "分钟":
                    return number * 60;
                case "h":
                case "时":
                case "小时":
                    return number * 3600;
                case "d":
                case "天":
                    return number * 86400;
                default:
                    return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        // /chat <tab> -> suggest "antispam", "whispercheck", "ban", "unban", "whitelist", "stats"
        if (args.length == 1) {
            String[] commands = {"antispam", "whispercheck", "ban", "unban", "whitelist", "stats"};
            for (String cmd : commands) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
            return completions;
        }
        
        // Handle whispercheck tab completion
        if (args[0].equalsIgnoreCase("whispercheck")) {
            if (args.length == 2) {
                String[] options = {"on", "off"};
                for (String opt : options) {
                    if (opt.startsWith(args[1].toLowerCase())) {
                        completions.add(opt);
                    }
                }
            }
            return completions;
        }
        
        // Handle ban tab completion
        if (args[0].equalsIgnoreCase("ban")) {
            if (args.length == 2) {
                // Suggest online players
                for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (args.length == 3) {
                // Suggest time formats
                String[] suggestions = {"30s", "5m", "10m", "30m", "1h", "2h", "1d"};
                for (String s : suggestions) {
                    if (s.startsWith(args[2].toLowerCase())) {
                        completions.add(s);
                    }
                }
            }
            return completions;
        }
        
        // Handle unban tab completion
        if (args[0].equalsIgnoreCase("unban")) {
            if (args.length == 2) {
                // Suggest online players
                for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
            return completions;
        }
        
        // Handle whitelist tab completion
        if (args[0].equalsIgnoreCase("whitelist")) {
            if (args.length == 2) {
                String[] options = {"add", "del", "list"};
                for (String opt : options) {
                    if (opt.startsWith(args[1].toLowerCase())) {
                        completions.add(opt);
                    }
                }
            } else if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("del"))) {
                // Suggest online players
                for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
            return completions;
        }
        
        // Handle stats tab completion
        if (args[0].equalsIgnoreCase("stats")) {
            if (args.length == 2) {
                // Suggest online players
                for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (args.length == 3) {
                // Suggest "list" subcommand
                if ("list".startsWith(args[2].toLowerCase())) {
                    completions.add("list");
                }
            } else if (args.length == 4 && args[2].equalsIgnoreCase("list")) {
                // Suggest time formats
                String[] suggestions = {"5m", "10m", "30m", "1h", "2h", "1d"};
                for (String s : suggestions) {
                    if (s.startsWith(args[3].toLowerCase())) {
                        completions.add(s);
                    }
                }
            }
            return completions;
        }
        
        // Only handle if first arg is "antispam"
        if (!args[0].equalsIgnoreCase("antispam")) {
            return completions;
        }
        
        // /chat antispam <tab> -> suggest subcommands
        if (args.length == 2) {
            String[] subcommands = {"delay", "similarity", "toggle", "automute", "status", "reload"};
            for (String sub : subcommands) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        
        // /chat antispam delay <tab> -> suggest common values
        if (args.length == 3 && args[1].equalsIgnoreCase("delay")) {
            String[] suggestions = {"20", "50", "100", "500", "1000"};
            for (String s : suggestions) {
                if (s.startsWith(args[2])) {
                    completions.add(s);
                }
            }
            return completions;
        }
        
        // /chat antispam similarity <tab> -> suggest common values
        if (args.length == 3 && args[1].equalsIgnoreCase("similarity")) {
            String[] suggestions = {"0.5", "0.6", "0.7", "0.8", "0.9", "1.0"};
            for (String s : suggestions) {
                if (s.startsWith(args[2])) {
                    completions.add(s);
                }
            }
            return completions;
        }
        
        // /chat antispam toggle <tab> -> suggest "similarity"
        if (args.length == 3 && args[1].equalsIgnoreCase("toggle")) {
            String[] options = {"similarity", "profanity", "interval", "automute"};
            for (String opt : options) {
                if (opt.startsWith(args[2].toLowerCase())) {
                    completions.add(opt);
                }
            }
            return completions;
        }
        
        // /chat antispam automute <tab> -> suggest "threshold", "duration"
        if (args.length == 3 && args[1].equalsIgnoreCase("automute")) {
            String[] options = {"threshold", "duration"};
            for (String opt : options) {
                if (opt.startsWith(args[2].toLowerCase())) {
                    completions.add(opt);
                }
            }
            return completions;
        }
        
        // /chat antispam automute threshold <tab> -> suggest numbers
        if (args.length == 4 && args[1].equalsIgnoreCase("automute") && args[2].equalsIgnoreCase("threshold")) {
            String[] suggestions = {"3", "5", "10", "15", "20"};
            for (String s : suggestions) {
                if (s.startsWith(args[3])) {
                    completions.add(s);
                }
            }
            return completions;
        }
        
        // /chat antispam automute duration <tab> -> suggest time formats
        if (args.length == 4 && args[1].equalsIgnoreCase("automute") && args[2].equalsIgnoreCase("duration")) {
            String[] suggestions = {"30s", "1m", "5m", "10m", "30m", "1h"};
            for (String s : suggestions) {
                if (s.startsWith(args[3].toLowerCase())) {
                    completions.add(s);
                }
            }
            return completions;
        }
        
        return completions;
    }
}
