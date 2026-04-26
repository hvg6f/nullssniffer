package ru.nulls.sniffer.spec;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlaceholderSupport {

    private PlaceholderSupport() {
    }

    public static String resolveDevice(SpectatePlugin plugin, Player target) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String result = PlaceholderAPI.setPlaceholders(target, "%floodgate_device%");
            if (result != null && !result.isBlank() && !result.equals("%floodgate_device%")) {
                return result;
            }
        }
        return plugin.getConfig().getString("hud.device-fallback", "unknown");
    }
}
