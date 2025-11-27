package net.inecat.ineserverstatusplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StatusCommand implements CommandExecutor, TabCompleter {

    private final StatusManager statusManager;
    private final StatusGui statusGui;

    public StatusCommand(StatusManager statusManager, StatusGui statusGui) {
        this.statusManager = statusManager;
        this.statusGui = statusGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("プレイヤーのみ実行可能です。", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            if (command.getName().equalsIgnoreCase("afk")) {
                statusManager.setStatus(player, StatusManager.StatusType.AFK);
            } else {
                statusGui.openInventory(player);
            }
            return true;
        }

        String subCommand = args[0];

        // Handle /afk [reason]
        if (command.getName().equalsIgnoreCase("afk")) {
            String reason = String.join(" ", args);
            statusManager.setStatus(player, StatusManager.StatusType.AFK, reason);
            return true;
        }

        // Handle direct status setting
        switch (subCommand.toLowerCase()) {
            case "afk":
                String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
                statusManager.setStatus(player, StatusManager.StatusType.AFK, reason);
                break;
            case "作業中":
            case "work":
            case "working":
                statusManager.setStatus(player, StatusManager.StatusType.WORKING);
                break;
            case "撮影中":
            case "rec":
            case "recording":
                statusManager.setStatus(player, StatusManager.StatusType.RECORDING);
                break;
            case "ねこ":
            case "cat":
            case "neko":
                statusManager.setStatus(player, StatusManager.StatusType.CAT);
                break;
            case "雑談":
            case "chat":
                statusManager.setStatus(player, StatusManager.StatusType.CHAT_WELCOME);
                break;
            case "reset":
            case "clear":
            case "off":
            case "解除":
                statusManager.setStatus(player, StatusManager.StatusType.NORMAL);
                break;
            default:
                player.sendMessage(Component.text("不明なステータスです。GUIから選択してください。", NamedTextColor.RED));
                statusGui.openInventory(player);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("afk")) {
            if (args.length == 1) {
                return Arrays.asList("ご飯", "お風呂", "トイレ", "寝る", "外出");
            }
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("afk", "作業中", "撮影中", "ねこ", "雑談", "解除");
            return subCommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("afk")) {
            return Arrays.asList("ご飯", "お風呂", "トイレ", "寝る", "外出");
        }
        return new ArrayList<>();
    }
}
