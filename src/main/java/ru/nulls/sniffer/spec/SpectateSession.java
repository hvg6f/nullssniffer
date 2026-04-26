package ru.nulls.sniffer.spec;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.boss.BossBar;

public record SpectateSession(UUID watcherId, UUID targetId, GameMode previousGameMode, BossBar bossBar) {
}
