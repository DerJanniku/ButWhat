package de.derjannik.butwhat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class Twist implements Listener {

    protected static final Random RANDOM = new Random();

    private final String id;
    private final String name;
    private final String description;
    private final Material icon;
    protected ButWhat plugin;
    private NamespacedKey markKey;
    private BukkitTask task;
    private boolean active;

    protected Twist(String id, String name, Material icon, String description) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description;
    }

    final void bind(ButWhat plugin) {
        this.plugin = plugin;
        this.markKey = new NamespacedKey(plugin, "twist_" + id.replace('-', '_'));
    }

    final void start() {
        if (active) {
            return;
        }
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long period = period();
        if (period > 0) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        }
        onStart();
    }

    final void stop() {
        if (!active) {
            return;
        }
        active = false;
        HandlerList.unregisterAll(this);
        if (task != null) {
            task.cancel();
            task = null;
        }
        onStop();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clean(player);
        }
    }

    protected void onStart() {
    }

    protected void onStop() {
    }

    // Ticks between two tick() calls, 0 means no timer
    protected long period() {
        return 0;
    }

    protected void tick() {
    }

    // Undoes lasting changes on a marked player
    protected void cleanup(Player player) {
    }

    // Marked players get cleaned up on stop or on their next join
    protected final void mark(Player player) {
        player.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
    }

    final void clean(Player player) {
        if (player.getPersistentDataContainer().has(markKey, PersistentDataType.BYTE)) {
            player.getPersistentDataContainer().remove(markKey);
            cleanup(player);
        }
    }

    protected int setting(String key, int fallback) {
        return plugin.getConfig().getInt("twists." + id + "." + key, fallback);
    }

    protected double setting(String key, double fallback) {
        return plugin.getConfig().getDouble("twists." + id + "." + key, fallback);
    }

    protected boolean affects(Player player) {
        if (player.isDead()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return !plugin.getConfig().getBoolean("ignore-creative", true)
                || (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR);
    }

    protected List<Player> players() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (affects(player)) {
                players.add(player);
            }
        }
        return players;
    }

    protected void later(long ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active) {
                action.run();
            }
        }, ticks);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Material icon() {
        return icon;
    }

    public boolean active() {
        return active;
    }
}
