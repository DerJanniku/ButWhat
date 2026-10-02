package de.derjannik.butwhat;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

final class TwistManager implements Listener {

    private final ButWhat plugin;
    private final Map<String, Twist> twists = new LinkedHashMap<>();
    private BossBar rouletteBar;
    private BukkitTask rouletteTask;
    private int rouletteLeft;

    TwistManager(ButWhat plugin) {
        this.plugin = plugin;
        for (Twist twist : Twists.all()) {
            twist.bind(plugin);
            twists.put(twist.id(), twist);
        }
    }

    void loadEnabled() {
        for (String id : plugin.getConfig().getStringList("enabled")) {
            Twist twist = twists.get(id);
            if (twist != null) {
                twist.start();
            }
        }
    }

    Collection<Twist> all() {
        return twists.values();
    }

    Twist get(String id) {
        return twists.get(id.toLowerCase());
    }

    void set(Twist twist, boolean enabled) {
        if (enabled) {
            twist.start();
        } else {
            twist.stop();
        }
        save();
    }

    void disableAll() {
        twists.values().forEach(Twist::stop);
        save();
    }

    private void save() {
        List<String> enabled = new ArrayList<>();
        for (Twist twist : twists.values()) {
            if (twist.active()) {
                enabled.add(twist.id());
            }
        }
        plugin.getConfig().set("enabled", enabled);
        plugin.saveConfig();
    }

    void shutdown() {
        stopRoulette();
        twists.values().forEach(Twist::stop);
    }

    boolean rouletteRunning() {
        return rouletteTask != null;
    }

    void startRoulette(int minutes) {
        stopRoulette();
        int seconds = Math.max(1, minutes) * 60;
        rouletteBar = Bukkit.createBossBar("", BarColor.PINK, BarStyle.SOLID);
        rouletteLeft = 0;
        rouletteTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (rouletteLeft <= 0) {
                spin();
                rouletteLeft = seconds;
            }
            String time = String.format("%d:%02d", rouletteLeft / 60, rouletteLeft % 60);
            rouletteBar.setTitle(ButWhat.color(plugin.getConfig().getString("messages.roulette-bar", ""))
                    .replace("{time}", time));
            rouletteBar.setProgress(Math.max(0.0, Math.min(1.0, rouletteLeft / (double) seconds)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!rouletteBar.getPlayers().contains(player)) {
                    rouletteBar.addPlayer(player);
                }
            }
            rouletteLeft--;
        }, 0L, 20L);
    }

    void stopRoulette() {
        if (rouletteTask != null) {
            rouletteTask.cancel();
            rouletteTask = null;
        }
        if (rouletteBar != null) {
            rouletteBar.removeAll();
            rouletteBar = null;
        }
    }

    // Replaces the active twists with a fresh random set
    void spin() {
        List<Twist> pool = new ArrayList<>(twists.values());
        Collections.shuffle(pool);
        int count = Math.max(1, Math.min(pool.size(), plugin.getConfig().getInt("roulette.count", 2)));
        twists.values().forEach(Twist::stop);
        StringJoiner names = new StringJoiner(" §7+ §f", "§f", "");
        for (Twist twist : pool.subList(0, count)) {
            twist.start();
            names.add(twist.name());
        }
        save();
        String title = ButWhat.color(plugin.getConfig().getString("messages.roulette-title", ""));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, names.toString(), 10, 60, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.7f);
        }
    }

    // Undoes leftovers of twists that were stopped while the player was offline
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Twist twist : twists.values()) {
            if (!twist.active()) {
                twist.clean(event.getPlayer());
            }
        }
    }
}
