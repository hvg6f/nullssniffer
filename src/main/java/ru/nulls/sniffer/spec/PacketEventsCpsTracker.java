package ru.nulls.sniffer.spec;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PacketEventsCpsTracker extends PacketListenerAbstract {

    private final SpectatePlugin plugin;

    public PacketEventsCpsTracker(SpectatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ANIMATION) {
            return;
        }

        UUID uuid = event.getUser().getUUID();
        if (uuid == null) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            plugin.incrementCps(player);
        }
    }
}
