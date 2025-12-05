package net.inecat.ineserverstatusplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatusManager {

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final Map<UUID, StatusType> playerStatuses = new HashMap<>();
    private final Map<UUID, String> afkReasons = new HashMap<>();
    private final Map<UUID, Long> lastActivityTime = new HashMap<>();
    private final Map<UUID, StatusType> preAfkStatus = new HashMap<>();
    private static final long AFK_THRESHOLD = 5 * 60 * 1000; // 5 minutes

    public enum StatusType {
        NORMAL("通常", null, null),
        CHAT_WELCOME("雑談歓迎", null, "group.chat"),
        AFK("AFK", null, "group.afk"), // Particles handled separately
        WORKING("作業中", null, "group.work"),
        RECORDING("撮影中", null, "group.rec"),
        CAT("ねこ", null, "group.scat");

        private final String displayName;
        private final Particle particle;
        private final String permissionNode;

        StatusType(String displayName, Particle particle, String permissionNode) {
            this.displayName = displayName;
            this.particle = particle;
            this.permissionNode = permissionNode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Particle getParticle() {
            return particle;
        }

        public String getPermissionNode() {
            return permissionNode;
        }
    }

    public StatusManager(JavaPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        startParticleTask();
        startAutoAfkTask();
    }

    public void setStatus(Player player, StatusType status) {
        setStatus(player, status, null);
    }

    public void setStatus(Player player, StatusType status, String reason) {
        // Handle pre-AFK status logic
        if (status == StatusType.AFK) {
            StatusType current = getStatus(player);
            if (current != StatusType.AFK) {
                preAfkStatus.put(player.getUniqueId(), current);
            }
        } else {
            preAfkStatus.remove(player.getUniqueId());
        }

        // Remove old status permission
        StatusType oldStatus = getStatus(player);
        if (oldStatus != StatusType.NORMAL && oldStatus.getPermissionNode() != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                user.transientData().remove(Node.builder(oldStatus.getPermissionNode()).build());
                luckPerms.getUserManager().saveUser(user);
            }
        }

        if (status == StatusType.NORMAL) {
            playerStatuses.remove(player.getUniqueId());
            afkReasons.remove(player.getUniqueId());
            player.sendMessage(Component.text("ステータスをリセットしました。", NamedTextColor.GREEN));
        } else {
            playerStatuses.put(player.getUniqueId(), status);

            if (status == StatusType.AFK && reason != null && !reason.isEmpty()) {
                afkReasons.put(player.getUniqueId(), reason);
                player.sendMessage(Component.text("ステータスを " + status.getDisplayName() + " (" + reason + ") に変更しました。",
                        NamedTextColor.YELLOW));
            } else {
                afkReasons.remove(player.getUniqueId());
                player.sendMessage(
                        Component.text("ステータスを " + status.getDisplayName() + " に変更しました。", NamedTextColor.YELLOW));
            }

            // Add new status permission
            if (status.getPermissionNode() != null) {
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    user.transientData().add(Node.builder(status.getPermissionNode()).build());
                    luckPerms.getUserManager().saveUser(user);
                }
            }
        }
    }

    public StatusType getStatus(Player player) {
        return playerStatuses.getOrDefault(player.getUniqueId(), StatusType.NORMAL);
    }

    public void removeStatus(Player player) {
        setStatus(player, StatusType.NORMAL);
    }

    public void updateActivity(Player player) {
        lastActivityTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void restorePreAfkStatus(Player player) {
        if (getStatus(player) == StatusType.AFK) {
            StatusType pre = preAfkStatus.getOrDefault(player.getUniqueId(), StatusType.NORMAL);
            setStatus(player, pre);
        }
    }

    public void saveStatus(Player player) {
        StatusType current = getStatus(player);
        StatusType toSave = current;
        if (current == StatusType.AFK) {
            toSave = preAfkStatus.getOrDefault(player.getUniqueId(), StatusType.NORMAL);
        }
        plugin.getConfig().set("status." + player.getUniqueId(), toSave.name());
        plugin.saveConfig();
    }

    public void loadStatus(Player player) {
        String statusName = plugin.getConfig().getString("status." + player.getUniqueId());
        if (statusName != null) {
            try {
                StatusType status = StatusType.valueOf(statusName);
                setStatus(player, status);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid status found for " + player.getName() + ": " + statusName);
            }
        }
        updateActivity(player);
    }

    private void startAutoAfkTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (getStatus(player) == StatusType.AFK)
                        continue;

                    long lastActive = lastActivityTime.getOrDefault(player.getUniqueId(), now);
                    if (now - lastActive > AFK_THRESHOLD) {
                        setStatus(player, StatusType.AFK, "Automatic");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, StatusType> entry : playerStatuses.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline())
                        continue;

                    StatusType status = entry.getValue();
                    Location loc = player.getLocation().add(0, 2.2, 0);

                    if (status == StatusType.AFK) {
                        String reason = afkReasons.getOrDefault(player.getUniqueId(), "").toLowerCase();
                        if (reason.contains("睡眠") || reason.contains("寝") || reason.contains("sleep")) {
                            player.getWorld().spawnParticle(Particle.CLOUD, loc, 2, 0.1, 0.05, 0.1, 0.02);
                        } else if (reason.contains("ご飯") || reason.contains("飯") || reason.contains("food")
                                || reason.contains("eat")) {
                            player.getWorld().spawnParticle(Particle.HEART, loc, 1, 0.2, 0.2, 0.2);
                        } else if (reason.contains("風呂") || reason.contains("bath") || reason.contains("shower")) {
                            player.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 3, 0.2, 0.2, 0.2);
                        } else if (reason.contains("トイレ") || reason.contains("wc")) {
                            player.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 2, 0.1, 0.1, 0.1);
                        } else if (reason.contains("散歩") || reason.contains("運動") || reason.contains("walk")
                                || reason.contains("run")) {
                            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.3, 0.2, 0.3);
                        } else if (reason.contains("作業") || reason.contains("work")) {
                            player.getWorld().spawnParticle(Particle.ENCHANT, loc, 5, 0.3, 0.5, 0.3, 0.5);
                        } else {
                            player.getWorld().spawnParticle(Particle.NOTE, loc, 1, 0.2, 0.2, 0.2, 0.5);
                        }
                    } else if (status.getParticle() != null) {
                        player.getWorld().spawnParticle(status.getParticle(), loc, 5, 0.3, 0.5, 0.3, 0.5);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public void cleanup() {
        for (UUID uuid : playerStatuses.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                saveStatus(player);
                removeStatus(player);
            }
        }
        playerStatuses.clear();
        afkReasons.clear();
        lastActivityTime.clear();
        preAfkStatus.clear();
    }

    public void removeActivityData(Player player) {
        lastActivityTime.remove(player.getUniqueId());
    }
}
