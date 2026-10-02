package de.derjannik.butwhat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

final class ResetMenu implements Listener {

    private static final int SIZE = 27;
    private static final int SLOT_OVERWORLD = 10;
    private static final int SLOT_NETHER = 11;
    private static final int SLOT_END = 12;
    private static final int SLOT_SEED = 14;
    private static final int SLOT_PLAYERS = 15;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_CONTINUE = 22;
    private static final int SLOT_YES = 11;
    private static final int SLOT_SUMMARY = 13;
    private static final int SLOT_NO = 15;

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private boolean overworld = true;
        private boolean nether = true;
        private boolean end = true;
        private boolean seed = true;
        private boolean players = true;
        private boolean confirming;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        WorldReset.Options options() {
            return new WorldReset.Options(overworld, nether, end, seed, players);
        }
    }

    private final ButWhat plugin;
    private final NamespacedKey resetKey;

    ResetMenu(ButWhat plugin) {
        this.plugin = plugin;
        this.resetKey = new NamespacedKey(plugin, "last_reset");
    }

    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, SIZE, "§4§lWorld Reset");
        render(holder);
        player.openInventory(holder.inventory);
    }

    private void render(Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        if (holder.confirming) {
            inventory.setItem(SLOT_YES, TwistMenu.item(Material.LIME_CONCRETE, "§a§lYes, reset now", false,
                    "§7Everyone gets kicked and the", "§7server restarts.", "", "§cThis cannot be undone!"));
            inventory.setItem(SLOT_SUMMARY, TwistMenu.item(Material.PAPER, "§f§lThis will be reset", false,
                    summary(holder).toArray(new String[0])));
            inventory.setItem(SLOT_NO, TwistMenu.item(Material.RED_CONCRETE, "§c§lNo, go back", false,
                    "§7Nothing happens"));
            return;
        }
        inventory.setItem(SLOT_OVERWORLD, toggle(Material.GRASS_BLOCK, "Overworld", holder.overworld,
                "Deletes all overworld chunks"));
        inventory.setItem(SLOT_NETHER, toggle(Material.NETHERRACK, "Nether", holder.nether,
                "Deletes all Nether chunks"));
        inventory.setItem(SLOT_END, toggle(Material.END_STONE, "The End", holder.end,
                "Deletes all End chunks and the dragon fight"));
        inventory.setItem(SLOT_SEED, toggle(Material.WHEAT_SEEDS, "New random seed", holder.seed,
                "Generates the world with a fresh seed", "Off: the same world generates again"));
        inventory.setItem(SLOT_PLAYERS, toggle(Material.TOTEM_OF_UNDYING, "Player data", holder.players,
                "Wipes inventories, XP, stats", "and advancements of everyone"));
        inventory.setItem(SLOT_BACK, TwistMenu.item(Material.ARROW, "§7Back to twists", false));
        inventory.setItem(SLOT_CONTINUE, TwistMenu.item(Material.TNT, "§c§lReset...", false,
                "§7Shows a summary and asks", "§7for confirmation first"));
    }

    private ItemStack toggle(Material material, String name, boolean on, String... description) {
        List<String> lore = new ArrayList<>();
        for (String line : description) {
            lore.add("§7" + line);
        }
        lore.add("");
        lore.add(on ? "§aWill be reset §8- §7click to keep" : "§cWill be kept §8- §7click to reset");
        return TwistMenu.item(material, (on ? "§a§l" : "§c§l") + name, on, lore.toArray(new String[0]));
    }

    private List<String> summary(Holder holder) {
        List<String> lines = new ArrayList<>();
        lines.add(line("Overworld", holder.overworld));
        lines.add(line("Nether", holder.nether));
        lines.add(line("The End", holder.end));
        lines.add(line("New random seed", holder.seed));
        lines.add(line("Player data", holder.players));
        return lines;
    }

    private String line(String name, boolean on) {
        return (on ? "§a✔ " : "§8✘ ") + "§7" + name;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory
                || !player.hasPermission("butwhat.admin")) {
            return;
        }
        int slot = event.getSlot();
        if (holder.confirming) {
            if (slot == SLOT_YES) {
                player.closeInventory();
                plugin.reset(holder.options());
                return;
            }
            if (slot != SLOT_NO) {
                return;
            }
            holder.confirming = false;
        } else {
            switch (slot) {
                case SLOT_OVERWORLD -> holder.overworld = !holder.overworld;
                case SLOT_NETHER -> holder.nether = !holder.nether;
                case SLOT_END -> holder.end = !holder.end;
                case SLOT_SEED -> holder.seed = !holder.seed;
                case SLOT_PLAYERS -> holder.players = !holder.players;
                case SLOT_CONTINUE -> {
                    if (!holder.options().any()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                        return;
                    }
                    holder.confirming = true;
                }
                case SLOT_BACK -> {
                    // Opening another menu inside the click event gets dropped
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.menu().open(player));
                    return;
                }
                default -> {
                    return;
                }
            }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        render(holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // Old coordinates point into unknown terrain after a reset, so everyone starts at spawn once
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        long lastReset = plugin.getConfig().getLong("last-reset");
        Player player = event.getPlayer();
        if (lastReset == 0 || player.getPersistentDataContainer()
                .getOrDefault(resetKey, PersistentDataType.LONG, 0L) == lastReset) {
            return;
        }
        player.getPersistentDataContainer().set(resetKey, PersistentDataType.LONG, lastReset);
        if (plugin.getConfig().getBoolean("last-reset-relocate")) {
            Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            spawn.setY(spawn.getWorld().getHighestBlockYAt(spawn) + 1.0);
            player.teleport(spawn.add(0.5, 0, 0.5));
        }
    }
}
