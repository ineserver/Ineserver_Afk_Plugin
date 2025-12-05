package net.inecat.ineserverstatusplugin;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class IneServerStatusPlugin extends JavaPlugin implements Listener {

    private StatusManager statusManager;
    private StatusGui statusGui;
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        // Initialize LuckPerms
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        } else {
            getLogger().severe("LuckPerms not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        this.statusManager = new StatusManager(this, luckPerms);
        this.statusGui = new StatusGui(statusManager);

        // Register commands
        PluginCommand statusCommand = getCommand("status");
        if (statusCommand != null) {
            StatusCommand executor = new StatusCommand(statusManager, statusGui);
            statusCommand.setExecutor(executor);
            statusCommand.setTabCompleter(executor);
        }

        PluginCommand afkCommand = getCommand("afk");
        if (afkCommand != null) {
            StatusCommand executor = new StatusCommand(statusManager, statusGui);
            afkCommand.setExecutor(executor);
            afkCommand.setTabCompleter(executor);
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(statusGui, this);

        // Load status for online players (reload support)
        for (Player player : Bukkit.getOnlinePlayers()) {
            statusManager.loadStatus(player);
        }

        getLogger().info("IneServerStatusPlugin が有効化されました！");
    }

    @Override
    public void onDisable() {
        if (statusManager != null) {
            statusManager.cleanup();
        }
        getLogger().info("IneServerStatusPlugin が無効化されました。");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        statusManager.loadStatus(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        statusManager.saveStatus(event.getPlayer());
        statusManager.removeStatus(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        statusManager.updateActivity(player);

        // Only check if player is AFK
        if (statusManager.getStatus(player) != StatusManager.StatusType.AFK) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // Ignore if no movement or only rotation
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }

        // Restore status
        statusManager.restorePreAfkStatus(player);
    }
}
