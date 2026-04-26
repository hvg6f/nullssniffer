package ru.nulls.sniffer.spec;

import com.github.retrooper.packetevents.PacketEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class SpectatePlugin extends JavaPlugin implements Listener {

    private final Map<UUID, SpectateSession> sessions = new HashMap<>();
    private final Map<UUID, Integer> cpsCounter = new HashMap<>();
    private BukkitTask hudTask;
    private PacketEventsCpsTracker packetEventsCpsTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        registerPacketEventsTracker();

        PluginCommand spec = getCommand("spec");
        if (spec != null) {
            SpecCommand command = new SpecCommand(this);
            spec.setExecutor(command);
            spec.setTabCompleter(command);
        } else {
            getLogger().severe("Команда /spec не зарегистрирована в plugin.yml");
        }

        startHudTask();
    }

    @Override
    public void onDisable() {
        if (hudTask != null) {
            hudTask.cancel();
        }
        unregisterPacketEventsTracker();
        for (SpectateSession session : sessions.values()) {
            session.bossBar().removeAll();
        }
        sessions.clear();
        cpsCounter.clear();
    }

    public boolean startSpectating(Player watcher, Player target) {
        if (watcher.equals(target)) {
            watcher.sendMessage(colorize(getConfig().getString("messages.cannot-self", "&cНельзя следить за собой.")));
            return false;
        }

        stopSpectating(watcher, false);

        GameMode previous = watcher.getGameMode();
        watcher.setGameMode(GameMode.SPECTATOR);
        watcher.setSpectatorTarget(target);

        String bossBarText = colorize(getConfig().getString("bossbar.text", "&eВы в режиме слежки"));
        BarColor barColor = parseBarColor(getConfig().getString("bossbar.color", "YELLOW"));
        BarStyle barStyle = parseBarStyle(getConfig().getString("bossbar.style", "SOLID"));

        BossBar bossBar = Bukkit.createBossBar(bossBarText, barColor, barStyle);
        bossBar.setProgress(1.0D);
        bossBar.addPlayer(watcher);

        sessions.put(watcher.getUniqueId(), new SpectateSession(watcher.getUniqueId(), target.getUniqueId(), previous, bossBar));

        watcher.sendMessage(colorize(getConfig().getString("messages.started", "&aВы начали следить за игроком &f%target%&a.")
            .replace("%target%", target.getName())));
        return true;
    }

    public void stopSpectating(Player watcher, boolean notify) {
        SpectateSession session = sessions.remove(watcher.getUniqueId());
        if (session == null) {
            if (notify) {
                watcher.sendMessage(colorize(getConfig().getString("messages.not-spectating", "&cВы не в режиме слежки.")));
            }
            return;
        }

        session.bossBar().removeAll();
        watcher.setSpectatorTarget(null);
        watcher.setGameMode(session.previousGameMode());
        watcher.sendTitle("", "", 0, 1, 0);

        if (notify) {
            watcher.sendMessage(colorize(getConfig().getString("messages.stopped", "&eРежим слежки отключен.")));
        }
    }

    public boolean isSpectating(Player watcher) {
        return sessions.containsKey(watcher.getUniqueId());
    }

    public SpectateSession getSession(Player watcher) {
        return sessions.get(watcher.getUniqueId());
    }

    public void incrementCps(Player player) {
        cpsCounter.merge(player.getUniqueId(), 1, Integer::sum);
    }

    private void registerPacketEventsTracker() {
        if (!Bukkit.getPluginManager().isPluginEnabled("packetevents")
            && !Bukkit.getPluginManager().isPluginEnabled("PacketEvents")) {
            getLogger().warning("PacketEvents не найден. CPS в HUD будет всегда 0.");
            return;
        }

        packetEventsCpsTracker = new PacketEventsCpsTracker(this);
        PacketEvents.getAPI().getEventManager().registerListener(packetEventsCpsTracker);
    }

    private void unregisterPacketEventsTracker() {
        if (packetEventsCpsTracker == null) {
            return;
        }
        PacketEvents.getAPI().getEventManager().unregisterListener(packetEventsCpsTracker);
        packetEventsCpsTracker = null;
    }

    private void startHudTask() {
        long tickRate = Math.max(1L, getConfig().getLong("hud.update-ticks", 20L));
        hudTask = Bukkit.getScheduler().runTaskTimer(this, this::updateHud, 20L, tickRate);
    }

    private void updateHud() {
        String subtitleFormat = getConfig().getString(
            "hud.subtitle-format",
            "&fНик: &e%target% &7| &fОС: &b%device% &7| &fПинг: &a%ping%ms &7| &fCPS: &6%cps%"
        );

        for (Map.Entry<UUID, SpectateSession> entry : sessions.entrySet()) {
            Player watcher = Bukkit.getPlayer(entry.getKey());
            if (watcher == null || !watcher.isOnline()) {
                continue;
            }

            Player target = Bukkit.getPlayer(entry.getValue().targetId());
            if (target == null || !target.isOnline()) {
                stopSpectating(watcher, false);
                watcher.sendMessage(colorize(getConfig().getString("messages.target-left", "&cИгрок вышел, слежка остановлена.")));
                continue;
            }

            if (watcher.getGameMode() != GameMode.SPECTATOR) {
                watcher.setGameMode(GameMode.SPECTATOR);
            }
            watcher.setSpectatorTarget(target);

            String subtitle = subtitleFormat
                .replace("%target%", target.getName())
                .replace("%device%", PlaceholderSupport.resolveDevice(this, target))
                .replace("%ping%", String.valueOf(target.getPing()))
                .replace("%cps%", String.valueOf(cpsCounter.getOrDefault(target.getUniqueId(), 0)));

            watcher.sendTitle("", colorize(subtitle), 0, Math.max(10, (int) getConfig().getLong("hud.update-ticks", 20L) + 5), 0);
        }

        cpsCounter.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopSpectating(player, false);

        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().targetId().equals(player.getUniqueId())) {
                Player watcher = Bukkit.getPlayer(entry.getKey());
                if (watcher != null && watcher.isOnline()) {
                    watcher.sendMessage(colorize(getConfig().getString("messages.target-left", "&cИгрок вышел, слежка остановлена.")));
                    stopSpectating(watcher, false);
                }
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cpsCounter.remove(event.getPlayer().getUniqueId());
    }

    private BarColor parseBarColor(String color) {
        try {
            return BarColor.valueOf(color.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle parseBarStyle(String style) {
        try {
            return BarStyle.valueOf(style.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BarStyle.SOLID;
        }
    }

    public String colorize(String text) {
        return text == null ? "" : text.replace('&', '§');
    }
}
