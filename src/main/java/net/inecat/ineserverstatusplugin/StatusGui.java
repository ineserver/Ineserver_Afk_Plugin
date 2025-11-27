package net.inecat.ineserverstatusplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class StatusGui implements InventoryHolder, Listener {

    private final StatusManager statusManager;
    private final Inventory inventory;

    public StatusGui(StatusManager statusManager) {
        this.statusManager = statusManager;
        this.inventory = Bukkit.createInventory(this, 9, Component.text("ステータス選択"));
        initializeItems();
    }

    private void initializeItems() {
        inventory.setItem(0, createGuiItem(Material.OAK_SIGN, "雑談歓迎", "雑談したいときはこれ！"));
        inventory.setItem(1, createGuiItem(Material.RED_BED, "離席中", "離席するときはこれ！"));
        inventory.setItem(2, createGuiItem(Material.IRON_PICKAXE, "作業中", "集中して作業したいときはこれ！"));
        inventory.setItem(3, createGuiItem(Material.SPYGLASS, "撮影中", "動画撮影中などはこれ！"));
        inventory.setItem(4, createGuiItem(Material.CAT_SPAWN_EGG, "ねこ", "にゃーん（きまぐれだよ～）"));
        inventory.setItem(8, createGuiItem(Material.BARRIER, "リセット", "ステータスを解除します"));
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> loreComponents = Arrays.stream(lore)
                .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,
                        false))
                .toList();
        meta.lore(loreComponents);

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void openInventory(Player player) {
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null)
            return;

        // Plain text comparison for simplicity, or use persistent data container for
        // robustness
        String displayName = ((net.kyori.adventure.text.TextComponent) meta.displayName()).content();

        switch (displayName) {
            case "雑談歓迎":
                statusManager.setStatus(player, StatusManager.StatusType.CHAT_WELCOME);
                break;
            case "離席中":
                statusManager.setStatus(player, StatusManager.StatusType.AFK);
                break;
            case "作業中":
                statusManager.setStatus(player, StatusManager.StatusType.WORKING);
                break;
            case "撮影中":
                statusManager.setStatus(player, StatusManager.StatusType.RECORDING);
                break;
            case "ねこ":
                statusManager.setStatus(player, StatusManager.StatusType.CAT);
                break;
            case "リセット":
                statusManager.setStatus(player, StatusManager.StatusType.NORMAL);
                break;
        }

        player.closeInventory();
    }
}
