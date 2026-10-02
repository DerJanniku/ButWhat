package de.derjannik.butwhat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ButWhat extends JavaPlugin {

    private static final List<String> SUBCOMMANDS = List.of("menu", "list", "enable", "disable", "roulette", "spin",
            "off", "reset", "reload", "info");

    private final Pools pools = new Pools();
    private TwistManager twists;
    private TwistMenu menu;
    private ResetMenu resetMenu;

    @Override
    public void onLoad() {
        WorldReset.applyPending(this);
    }

    @Override
    public void onEnable() {
        Banner.print(this);
        saveDefaultConfig();
        // Add keys from newer versions to an existing config
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();
        pools.load(this);
        this.twists = new TwistManager(this);
        this.menu = new TwistMenu(this);
        this.resetMenu = new ResetMenu(this);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(twists, this);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(resetMenu, this);

        twists.loadEnabled();
        getLogger().info(twists.all().size() + " twists loaded.");
    }

    @Override
    public void onDisable() {
        if (twists != null) {
            twists.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase();
        if (sub.equals("info")) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "ButWhat " + ChatColor.GRAY + "v" + getDescription().getVersion()
                    + " by " + ChatColor.YELLOW + "DerJannik");
            return true;
        }
        if (!sender.hasPermission("butwhat.admin")) {
            sender.sendMessage(message("no-permission"));
            return true;
        }
        switch (sub) {
            case "list" -> {
                for (Twist twist : twists.all()) {
                    sender.sendMessage((twist.active() ? ChatColor.GREEN + "● " : ChatColor.RED + "○ ")
                            + ChatColor.WHITE + twist.id() + ChatColor.DARK_GRAY + " - " + ChatColor.GRAY
                            + twist.description());
                }
            }
            case "enable", "disable" -> {
                Twist twist = args.length < 2 ? null : twists.get(args[1]);
                if (twist == null) {
                    sender.sendMessage(message("unknown"));
                    return true;
                }
                twists.set(twist, sub.equals("enable"));
                announce(twist);
            }
            case "roulette" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("off")) {
                    twists.stopRoulette();
                    broadcast("roulette-off");
                    return true;
                }
                int minutes = getConfig().getInt("roulette.minutes", 5);
                if (args.length > 1) {
                    try {
                        minutes = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                        sender.sendMessage("Usage: /butwhat roulette [minutes|off]");
                        return true;
                    }
                }
                twists.startRoulette(minutes);
                broadcast("roulette-on", "{minutes}", String.valueOf(minutes));
            }
            case "spin" -> twists.spin();
            case "off" -> {
                twists.stopRoulette();
                twists.disableAll();
                broadcast("all-disabled");
            }
            case "reset" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    List<String> parts = List.of(args).subList(2, args.length);
                    WorldReset.Options options = new WorldReset.Options(parts.contains("overworld"),
                            parts.contains("nether"), parts.contains("end"), parts.contains("seed"),
                            parts.contains("players"));
                    if (!options.any()) {
                        sender.sendMessage("Usage: /butwhat reset confirm <overworld|nether|end|seed|players>...");
                        return true;
                    }
                    reset(options);
                } else if (sender instanceof Player player) {
                    resetMenu.open(player);
                } else {
                    sender.sendMessage("Usage: /butwhat reset confirm <overworld|nether|end|seed|players>...");
                }
            }
            case "reload" -> {
                reloadConfig();
                pools.load(this);
                sender.sendMessage(message("reloaded"));
            }
            default -> {
                if (sender instanceof Player player) {
                    menu.open(player);
                } else {
                    sender.sendMessage("Usage: /butwhat <list|enable|disable|roulette|spin|off|reset|reload|info>");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : SUBCOMMANDS) {
                if (option.startsWith(args[0].toLowerCase())) {
                    out.add(option);
                }
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            for (String option : args.length == 2 ? List.of("confirm")
                    : List.of("overworld", "nether", "end", "seed", "players")) {
                if (option.startsWith(args[args.length - 1].toLowerCase())) {
                    out.add(option);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            for (Twist twist : twists.all()) {
                if (twist.id().startsWith(args[1].toLowerCase())) {
                    out.add(twist.id());
                }
            }
        }
        return out;
    }

    void reset(WorldReset.Options options) {
        twists.stopRoulette();
        broadcast("reset-broadcast");
        WorldReset.request(this, options);
    }

    void announce(Twist twist) {
        broadcast(twist.active() ? "enabled" : "disabled", "{twist}", twist.name());
    }

    void broadcast(String key, String... replacements) {
        String text = message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        Bukkit.broadcastMessage(color(getConfig().getString("messages.prefix", "")) + text);
    }

    String message(String key) {
        return color(getConfig().getString("messages." + key, ""));
    }

    static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    Pools pools() {
        return pools;
    }

    TwistMenu menu() {
        return menu;
    }

    ResetMenu resetMenu() {
        return resetMenu;
    }

    TwistManager twists() {
        return twists;
    }
}
