package ru.nulls.sniffer.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SpecCommand implements CommandExecutor, TabCompleter {

    private final SpectatePlugin plugin;

    public SpecCommand(SpectatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда только для игроков.");
            return true;
        }

        if (!player.hasPermission("spec.use")) {
            player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cНедостаточно прав.")));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            if (args.length < 2) {
                player.sendMessage(plugin.colorize("&eИспользование: /" + label + " start <ник>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.target-not-found", "&cИгрок не найден.")));
                return true;
            }
            plugin.startSpectating(player, target);
            return true;
        }

        if (sub.equals("stop")) {
            plugin.stopSpectating(player, true);
            return true;
        }

        sendUsage(player, label);
        return true;
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(plugin.colorize("&eПодсказки:"));
        player.sendMessage(plugin.colorize("&7/" + label + " start <ник>"));
        player.sendMessage(plugin.colorize("&7/" + label + " stop"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("spec.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return partial(args[0], List.of("start", "stop"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return partial(args[1], names);
        }

        return Collections.emptyList();
    }

    private List<String> partial(String current, List<String> source) {
        String lower = current.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }
}
