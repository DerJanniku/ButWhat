package de.derjannik.butwhat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class TwistMenu implements Listener {

    private static final int SIZE = 54;
    private static final int SLOT_DISABLE_ALL = 45;
    private static final int SLOT_ROULETTE = 49;
    private static final int SLOT_SPIN = 53;

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ButWhat plugin;

    TwistMenu(ButWhat plugin) {
        this.plugin = plugin;
    }

    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, SIZE, "§5§lMinecraft, but...");
        render(holder.inventory);
        player.openInventory(holder.inventory);
    }

    private void render(Inventory inventory) {
        inventory.clear();
        List<Twist> twists = new ArrayList<>(plugin.twists().all());
        for (int i = 0; i < twists.size() && i < SLOT_DISABLE_ALL; i++) {
            Twist twist = twists.get(i);
            inventory.setItem(i, item(twist.icon(), (twist.active() ? "§a§l" : "§c§l") + twist.name(), twist.active(),
                    "§7" + twist.description(), "", twist.active() ? "§aActive §8- §7click to disable"
                            : "§cInactive §8- §7click to enable"));
        }
        inventory.setItem(SLOT_DISABLE_ALL, item(Material.BARRIER, "§c§lDisable all", false,
                "§7Stops every twist at once"));
        boolean roulette = plugin.twists().rouletteRunning();
        inventory.setItem(SLOT_ROULETTE, item(Material.CLOCK, "§d§lRoulette", roulette,
                "§7Swaps the twists every " + plugin.getConfig().getInt("roulette.minutes", 5) + " minutes", "",
                roulette ? "§aRunning §8- §7click to stop" : "§cStopped §8- §7click to start"));
        inventory.setItem(SLOT_SPIN, item(Material.ENDER_EYE, "§b§lSurprise me", false,
                "§7Picks random twists right now"));
    }

    private ItemStack item(Material material, String name, boolean glint, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        meta.setEnchantmentGlintOverride(glint);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
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
        List<Twist> twists = new ArrayList<>(plugin.twists().all());
        if (slot >= 0 && slot < twists.size() && slot < SLOT_DISABLE_ALL) {
            Twist twist = twists.get(slot);
            plugin.twists().set(twist, !twist.active());
            plugin.announce(twist);
        } else if (slot == SLOT_DISABLE_ALL) {
            plugin.twists().stopRoulette();
            plugin.twists().disableAll();
            plugin.broadcast("all-disabled");
        } else if (slot == SLOT_ROULETTE) {
            if (plugin.twists().rouletteRunning()) {
                plugin.twists().stopRoulette();
                plugin.broadcast("roulette-off");
            } else {
                int minutes = plugin.getConfig().getInt("roulette.minutes", 5);
                plugin.twists().startRoulette(minutes);
                plugin.broadcast("roulette-on", "{minutes}", String.valueOf(minutes));
            }
        } else if (slot == SLOT_SPIN) {
            plugin.twists().spin();
        } else {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        render(holder.inventory);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
