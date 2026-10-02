package de.derjannik.butwhat;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

final class Pools {

    private static final Set<String> BLOCKED_MOBS = Set.of("ENDER_DRAGON", "WITHER", "WARDEN", "GIANT",
            "ELDER_GUARDIAN", "ILLUSIONER", "ZOMBIE_HORSE");

    private final List<Material> items = new ArrayList<>();
    private final List<EntityType> mobs = new ArrayList<>();
    private long seed;

    void load(ButWhat plugin) {
        items.clear();
        mobs.clear();
        List<String> blacklist = plugin.getConfig().getStringList("random-item-blacklist");
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_") || material.isAir() || !material.isItem()
                    || blocked(material.name(), blacklist)) {
                continue;
            }
            items.add(material);
        }
        for (EntityType type : EntityType.values()) {
            Class<?> entityClass = type.getEntityClass();
            if (type.isSpawnable() && entityClass != null && Mob.class.isAssignableFrom(entityClass)
                    && !BLOCKED_MOBS.contains(type.name())) {
                mobs.add(type);
            }
        }
        seed = plugin.getConfig().getLong("seed");
        if (seed == 0) {
            seed = new Random().nextLong();
            plugin.getConfig().set("seed", seed);
            plugin.saveConfig();
        }
    }

    private boolean blocked(String name, List<String> blacklist) {
        for (String entry : blacklist) {
            String pattern = entry.toUpperCase();
            if (pattern.contains("*") ? name.contains(pattern.replace("*", "")) : name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    Material randomItem(Random random) {
        return items.get(random.nextInt(items.size()));
    }

    // The same source always maps to the same result, so players can learn the scramble
    Material scrambled(Material source, String salt) {
        return randomItem(new Random(seed ^ (source.name() + salt).hashCode() * 31L));
    }

    ItemStack stack(Material material, int amount) {
        return new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
    }

    EntityType randomMob(Random random) {
        return mobs.get(random.nextInt(mobs.size()));
    }
}
