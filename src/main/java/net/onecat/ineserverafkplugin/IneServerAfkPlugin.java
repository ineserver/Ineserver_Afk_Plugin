package net.onecat.ineserverafkplugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IneServerAfkPlugin extends JavaPlugin implements CommandExecutor, Listener {

    // AFK中のプレイヤーと理由を保存
    private final Map<UUID, String> afkPlayers = new HashMap<>();
    
    private LuckPerms luckPerms;
    // この権限が付与されると、LuckPerms側で設定したサフィックス(離席中)が表示される想定
    private static final String AFK_PERMISSION_NODE = "group.afk";

    @Override
    public void onEnable() {
        // LuckPermsの取得
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        } else {
            getLogger().severe("LuckPermsが見つかりません！プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // コマンド登録
        if (getCommand("afk") != null) {
            getCommand("afk").setExecutor(this);
        }
        
        // イベントリスナー登録
        getServer().getPluginManager().registerEvents(this, this);

        // パーティクル表示タスク
        new BukkitRunnable() {
            @Override
            public void run() {
                displayParticles();
            }
        }.runTaskTimer(this, 0L, 10L);
        
        getLogger().info("IneServerAfkPlugin が有効化されました！");
    }

    @Override
    public void onDisable() {
        // サーバー停止時は全員のAFK権限を剥奪してクリーンにする
        // (TransientDataを使うため本来は不要ですが、Mapのクリアのために残します)
        for (UUID uuid : afkPlayers.keySet()) {
            modifyAfkPermission(uuid, false);
        }
        afkPlayers.clear();
        
        getLogger().info("IneServerAfkPlugin が無効化されました。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ実行可能です。");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (afkPlayers.containsKey(uuid)) {
            // --- AFK 解除 (コマンドによる手動解除) ---
            removeAfk(player);
        } else {
            // --- AFK 開始 ---
            String reason = args.length > 0 ? String.join(" ", args) : "その他";
            afkPlayers.put(uuid, reason);
            
            modifyAfkPermission(uuid, true); // 権限付与
            
            player.sendMessage(ChatColor.YELLOW + "AFK中: " + ChatColor.WHITE + reason);
            
            // 全体通知（afk.notice.off権限を持たないプレイヤーのみ）
            broadcastAfkNotice(player.getName() + ChatColor.GOLD + "さんが離席しました: " + ChatColor.WHITE + reason);
        }

        return true;
    }

    // --- 共通のAFK解除処理 ---
    private void removeAfk(Player player) {
        UUID uuid = player.getUniqueId();
        if (afkPlayers.containsKey(uuid)) {
            afkPlayers.remove(uuid);
            modifyAfkPermission(uuid, false); // 権限剥奪
            
            player.sendMessage(ChatColor.GREEN + "AFKを解除しました。");
            player.sendTitle("", "", 0, 10, 0);
            
            // 全体通知（afk.notice.off権限を持たないプレイヤーのみ）
            broadcastAfkNotice(ChatColor.GREEN + player.getName() + "さんが戻りました");
        }
    }
    
    // --- 通知送信（afk.notice.off権限を持たないプレイヤーのみ） ---
    private void broadcastAfkNotice(String message) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission("afk.notice.off")) {
                onlinePlayer.sendMessage(ChatColor.GOLD + message);
            }
        }
    }

    // --- イベント検知系 ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (afkPlayers.containsKey(uuid)) {
            afkPlayers.remove(uuid);
            // ログアウト時にTransientDataは自動で消えますが、念のため明示的に消去
            modifyAfkPermission(uuid, false);
        }
    }

    // 【追加】移動したらAFKを解除する処理
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // AFK中のプレイヤーでなければ無視
        if (!afkPlayers.containsKey(uuid)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // 移動先がない、または視点移動(回転)のみの場合は無視
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }

        // 移動したのでAFK解除
        removeAfk(player);
    }

    // --- 内部処理 ---

    // 【修正】ここで不具合対策を行っています
    private void modifyAfkPermission(UUID uuid, boolean add) {
        if (luckPerms == null) return;
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return;

        // ノード作成
        Node node = Node.builder(AFK_PERMISSION_NODE).build();

        // 【重要変更点】 .data() ではなく .transientData() を使用します
        // これにより、データは「一時的」になり、保存されず、再起動で自動消滅します。
        if (add) {
            user.transientData().add(node);
        } else {
            user.transientData().remove(node);
        }

        // 【重要変更点】 saveUser() は削除しました。
        // Transientデータは保存する必要がなく、即座に反映されます。
    }

    private void displayParticles() {
        for (Map.Entry<UUID, String> entry : afkPlayers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            String reason = entry.getValue().toLowerCase();
            Location loc = player.getLocation().add(0, 2.2, 0);

            if (reason.contains("睡眠") || reason.contains("寝") || reason.contains("sleep")) {
                player.getWorld().spawnParticle(Particle.CLOUD, loc, 2, 0.1, 0.05, 0.1, 0.02);
            } else if (reason.contains("ご飯") || reason.contains("飯") || reason.contains("food") || reason.contains("eat")) {
                player.getWorld().spawnParticle(Particle.HEART, loc, 1, 0.2, 0.2, 0.2);
            } else if (reason.contains("風呂") || reason.contains("bath") || reason.contains("shower")) {
                player.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 3, 0.2, 0.2, 0.2);
            } else if (reason.contains("トイレ") || reason.contains("wc")) {
                player.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 2, 0.1, 0.1, 0.1);
            } else if (reason.contains("散歩") || reason.contains("運動") || reason.contains("walk") || reason.contains("run")) {
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.3, 0.2, 0.3);
            } else if (reason.contains("作業") || reason.contains("work")) {
                player.getWorld().spawnParticle(Particle.ENCHANT, loc, 5, 0.3, 0.5, 0.3, 0.5);
            } else {
                player.getWorld().spawnParticle(Particle.NOTE, loc, 1, 0.2, 0.2, 0.2, 0.5);
            }
        }
    }
}
