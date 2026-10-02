package de.derjannik.butwhat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class Twists {

    private Twists() {
    }

    static List<Twist> all() {
        return List.of(new RandomDrops(), new RandomMobDrops(), new ExplosiveMining(), new JumpStack(), new Blink(),
                new SharedHealth(), new FloorIsLava(), new MobMultiplier(), new ItemRain(), new GravityFlip(),
                new SpeedRamp(), new OneHeart(), new MidasTouch(), new JumpHurts(), new CraftRoulette(),
                new MobSwap(), new ExplosiveArrows(), new Sunburn(), new DeadlyWater(), new RandomTeleport(),
                new XpLottery(), new EverythingFalls(), new InventoryShuffle(), new ExplodingMobs());
    }

    // The attribute key lost its generic. prefix in 1.21.2
    private static AttributeInstance maxHealth(Player player) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (attribute == null) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
        }
        return attribute == null ? null : player.getAttribute(attribute);
    }

    private static boolean isBoss(Entity entity) {
        return entity instanceof EnderDragon || entity instanceof Wither;
    }

    private static boolean unbreakable(Block block) {
        Material type = block.getType();
        return type.getHardness() < 0 || type == Material.OBSIDIAN || block.getState() instanceof TileState;
    }

    static final class RandomDrops extends Twist {
        RandomDrops() {
            super("random-drops", "Random Drops", Material.CHEST, "Every block drops a scrambled item");
        }

        @EventHandler(ignoreCancelled = true)
        public void onDrop(BlockDropItemEvent event) {
            if (!affects(event.getPlayer())) {
                return;
            }
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                Material scrambled = plugin.pools().scrambled(stack.getType(), "block");
                item.setItemStack(plugin.pools().stack(scrambled, stack.getAmount()));
            }
        }
    }

    static final class RandomMobDrops extends Twist {
        RandomMobDrops() {
            super("random-mob-drops", "Random Mob Loot", Material.ROTTEN_FLESH, "Mobs drop completely random items");
        }

        @EventHandler
        public void onDeath(EntityDeathEvent event) {
            if (event.getEntity() instanceof Player) {
                return;
            }
            event.getDrops().clear();
            int amount = 1 + RANDOM.nextInt(3);
            for (int i = 0; i < amount; i++) {
                event.getDrops().add(new ItemStack(plugin.pools().randomItem(RANDOM)));
            }
        }
    }

    static final class ExplosiveMining extends Twist {
        ExplosiveMining() {
            super("explosive-mining", "Explosive Mining", Material.TNT, "Mined blocks sometimes blow up");
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            if (affects(event.getPlayer()) && RANDOM.nextInt(100) < setting("chance-percent", 15)) {
                Location location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
                later(1, () -> location.getWorld().createExplosion(location, (float) setting("power", 2.5), false, true));
            }
        }
    }

    static final class JumpStack extends Twist {
        private final Map<UUID, Integer> jumps = new HashMap<>();

        JumpStack() {
            super("jump-stack", "Jump Stacking", Material.RABBIT_FOOT, "Every jump makes the next one higher");
        }

        @EventHandler
        public void onJump(PlayerStatisticIncrementEvent event) {
            Player player = event.getPlayer();
            if (event.getStatistic() != Statistic.JUMP || !affects(player)) {
                return;
            }
            int count = jumps.merge(player.getUniqueId(), 1, Integer::sum);
            int level = Math.min(setting("max-level", 20), count / Math.max(1, setting("jumps-per-level", 5)));
            if (level > 0) {
                mark(player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION,
                        level - 1, true, false));
            }
        }

        @EventHandler
        public void onDeath(PlayerDeathEvent event) {
            jumps.remove(event.getEntity().getUniqueId());
        }

        @Override
        protected void onStop() {
            jumps.clear();
        }

        @Override
        protected void cleanup(Player player) {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    static final class Blink extends Twist {
        Blink() {
            super("blink", "Sneak Blink", Material.ENDER_PEARL, "Sneaking teleports you forward");
        }

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            Player player = event.getPlayer();
            if (!event.isSneaking() || !affects(player)) {
                return;
            }
            double distance = setting("distance", 8);
            RayTraceResult hit = player.rayTraceBlocks(distance);
            if (hit != null) {
                distance = Math.max(0, hit.getHitPosition().distance(player.getEyeLocation().toVector()) - 1);
            }
            Location target = player.getLocation().add(player.getEyeLocation().getDirection().multiply(distance));
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3);
            player.teleport(target);
            player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        }
    }

    static final class SharedHealth extends Twist {
        private boolean killing;

        SharedHealth() {
            super("shared-health", "Shared Health", Material.GOLDEN_APPLE, "All players share one health bar");
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDamage(EntityDamageEvent event) {
            if (event.getEntity() instanceof Player player && affects(player)) {
                later(1, () -> sync(player));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onRegain(EntityRegainHealthEvent event) {
            if (event.getEntity() instanceof Player player && affects(player)) {
                later(1, () -> sync(player));
            }
        }

        @EventHandler
        public void onDeath(PlayerDeathEvent event) {
            if (killing) {
                return;
            }
            killing = true;
            for (Player other : players()) {
                if (other != event.getEntity()) {
                    other.setHealth(0);
                }
            }
            killing = false;
        }

        private void sync(Player source) {
            if (source.isDead() || !source.isOnline()) {
                return;
            }
            for (Player other : players()) {
                AttributeInstance max = maxHealth(other);
                double cap = max == null ? 20 : max.getValue();
                other.setHealth(Math.max(0.5, Math.min(source.getHealth(), cap)));
            }
        }
    }

    static final class FloorIsLava extends Twist {
        private final Set<Block> melting = new HashSet<>();

        FloorIsLava() {
            super("floor-is-lava", "The Floor Is Lava", Material.MAGMA_BLOCK, "Blocks you stand on melt into lava");
        }

        @Override
        protected long period() {
            return 5;
        }

        @Override
        protected void tick() {
            for (Player player : players()) {
                Block below = player.getLocation().subtract(0, 0.1, 0).getBlock();
                if (!below.getType().isSolid() || below.getType() == Material.MAGMA_BLOCK || unbreakable(below)
                        || !melting.add(below)) {
                    continue;
                }
                later(setting("magma-after-ticks", 40), () -> {
                    if (below.getType().isSolid()) {
                        below.setType(Material.MAGMA_BLOCK);
                    }
                });
                later(setting("lava-after-ticks", 80), () -> {
                    melting.remove(below);
                    if (below.getType() == Material.MAGMA_BLOCK) {
                        below.setType(Material.LAVA);
                    }
                });
            }
        }

        @Override
        protected void onStop() {
            melting.clear();
        }
    }

    static final class MobMultiplier extends Twist {
        MobMultiplier() {
            super("mob-multiplier", "Hydra Mobs", Material.ZOMBIE_HEAD, "Every killed mob comes back as two");
        }

        @EventHandler
        public void onDeath(EntityDeathEvent event) {
            if (!(event.getEntity() instanceof Mob mob) || mob.getKiller() == null || isBoss(mob)) {
                return;
            }
            int nearby = 0;
            for (Entity entity : mob.getNearbyEntities(16, 16, 16)) {
                if (entity.getType() == mob.getType()) {
                    nearby++;
                }
            }
            if (nearby >= setting("nearby-limit", 40)) {
                return;
            }
            Location location = mob.getLocation();
            later(1, () -> {
                for (int i = 0; i < setting("copies", 2); i++) {
                    location.getWorld().spawnEntity(location, mob.getType());
                }
            });
        }
    }

    static final class ItemRain extends Twist {
        ItemRain() {
            super("item-rain", "Random Item Timer", Material.HOPPER, "Everyone gets a random item every 30 seconds");
        }

        @Override
        protected long period() {
            return 20L * Math.max(1, setting("seconds", 30));
        }

        @Override
        protected void tick() {
            for (Player player : players()) {
                ItemStack stack = new ItemStack(plugin.pools().randomItem(RANDOM));
                for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
            }
        }
    }

    static final class GravityFlip extends Twist {
        GravityFlip() {
            super("gravity-flip", "Gravity Flip", Material.FEATHER, "Gravity flips every minute, then drops you");
        }

        @Override
        protected long period() {
            return 20L * Math.max(5, setting("seconds", 60));
        }

        @Override
        protected void tick() {
            int ticks = 20 * Math.max(1, setting("float-seconds", 5));
            for (Player player : players()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, ticks, 2, true, false));
                player.sendTitle("", "§dGravity flipped!", 5, 30, 10);
            }
        }
    }

    static final class SpeedRamp extends Twist {
        private int level;

        SpeedRamp() {
            super("speed-ramp", "Speed Ramp", Material.SUGAR, "You get faster and faster every minute");
        }

        @Override
        protected long period() {
            return 20L * Math.max(5, setting("seconds", 60));
        }

        @Override
        protected void tick() {
            level = Math.min(setting("max-level", 10), level + 1);
            players().forEach(this::apply);
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent event) {
            later(1, () -> apply(event.getPlayer()));
        }

        private void apply(Player player) {
            if (level > 0) {
                mark(player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION,
                        level - 1, true, false));
            }
        }

        @Override
        protected void onStop() {
            level = 0;
        }

        @Override
        protected void cleanup(Player player) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    static final class OneHeart extends Twist {
        OneHeart() {
            super("one-heart", "One Heart", Material.REDSTONE, "Everyone has a single heart");
        }

        @Override
        protected void onStart() {
            players().forEach(this::apply);
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            apply(event.getPlayer());
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent event) {
            later(1, () -> apply(event.getPlayer()));
        }

        private void apply(Player player) {
            AttributeInstance max = maxHealth(player);
            if (max != null) {
                mark(player);
                max.setBaseValue(Math.max(1, setting("hearts", 1)) * 2.0);
            }
        }

        @Override
        protected void cleanup(Player player) {
            AttributeInstance max = maxHealth(player);
            if (max != null) {
                max.setBaseValue(20.0);
            }
        }
    }

    static final class MidasTouch extends Twist {
        MidasTouch() {
            super("midas-touch", "Midas Touch", Material.GOLD_BLOCK, "Everything you walk on or touch turns to gold");
        }

        @EventHandler(ignoreCancelled = true)
        public void onMove(PlayerMoveEvent event) {
            if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())
                    || !affects(event.getPlayer())) {
                return;
            }
            gild(event.getTo().clone().subtract(0, 0.1, 0).getBlock());
        }

        @EventHandler
        public void onInteract(PlayerInteractEvent event) {
            if (event.getHand() == EquipmentSlot.HAND && event.getClickedBlock() != null
                    && event.getAction() == Action.LEFT_CLICK_BLOCK && affects(event.getPlayer())) {
                gild(event.getClickedBlock());
            }
        }

        private void gild(Block block) {
            if (block.getType().isSolid() && block.getType() != Material.GOLD_BLOCK && !unbreakable(block)) {
                block.setType(Material.GOLD_BLOCK);
            }
        }
    }

    static final class JumpHurts extends Twist {
        JumpHurts() {
            super("jump-hurts", "Jumping Hurts", Material.SLIME_BLOCK, "Every jump costs you a heart");
        }

        @EventHandler
        public void onJump(PlayerStatisticIncrementEvent event) {
            if (event.getStatistic() == Statistic.JUMP && affects(event.getPlayer())) {
                event.getPlayer().damage(setting("damage", 2.0));
            }
        }
    }

    static final class CraftRoulette extends Twist {
        CraftRoulette() {
            super("craft-roulette", "Scrambled Crafting", Material.CRAFTING_TABLE, "Every recipe crafts a different item");
        }

        @EventHandler
        public void onPrepare(PrepareItemCraftEvent event) {
            ItemStack result = event.getInventory().getResult();
            if (event.getRecipe() == null || result == null || result.getType().isAir()) {
                return;
            }
            Material scrambled = plugin.pools().scrambled(result.getType(), "craft");
            event.getInventory().setResult(plugin.pools().stack(scrambled, result.getAmount()));
        }
    }

    static final class MobSwap extends Twist {
        MobSwap() {
            super("mob-swap", "Mob Swap", Material.EGG, "Hitting a mob turns it into another mob");
        }

        @EventHandler(ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof Player player) || !affects(player)
                    || !(event.getEntity() instanceof Mob mob) || isBoss(mob)) {
                return;
            }
            event.setCancelled(true);
            Location location = mob.getLocation();
            World world = mob.getWorld();
            mob.remove();
            world.spawnParticle(Particle.POOF, location.clone().add(0, 0.5, 0), 15, 0.3, 0.4, 0.3, 0.02);
            world.playSound(location, Sound.ENTITY_CHICKEN_EGG, 1f, 0.7f);
            later(1, () -> world.spawnEntity(location, plugin.pools().randomMob(RANDOM)));
        }
    }

    static final class ExplosiveArrows extends Twist {
        ExplosiveArrows() {
            super("explosive-arrows", "Explosive Arrows", Material.SPECTRAL_ARROW, "Every arrow explodes on impact");
        }

        @EventHandler
        public void onHit(ProjectileHitEvent event) {
            if (event.getEntity() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player) {
                Location location = arrow.getLocation();
                arrow.remove();
                location.getWorld().createExplosion(location, (float) setting("power", 2.0), false, true);
            }
        }
    }

    static final class Sunburn extends Twist {
        Sunburn() {
            super("sunburn", "Sunburn", Material.SUNFLOWER, "You burn in daylight unless you wear a helmet");
        }

        @Override
        protected long period() {
            return 20;
        }

        @Override
        protected void tick() {
            for (Player player : players()) {
                World world = player.getWorld();
                long time = world.getTime();
                ItemStack helmet = player.getInventory().getHelmet();
                if (world.getEnvironment() == World.Environment.NORMAL && (time < 12300 || time > 23850)
                        && !world.hasStorm() && player.getEyeLocation().getBlock().getLightFromSky() == 15
                        && (helmet == null || helmet.getType().isAir())) {
                    player.setFireTicks(60);
                }
            }
        }
    }

    static final class DeadlyWater extends Twist {
        DeadlyWater() {
            super("deadly-water", "Deadly Water", Material.WATER_BUCKET, "Touching water hurts");
        }

        @Override
        protected long period() {
            return 10;
        }

        @Override
        protected void tick() {
            for (Player player : players()) {
                if (player.isInWater()) {
                    player.damage(setting("damage", 2.0));
                }
            }
        }
    }

    static final class RandomTeleport extends Twist {
        RandomTeleport() {
            super("random-teleport", "Random Teleport", Material.CHORUS_FRUIT, "Everyone gets teleported every two minutes");
        }

        @Override
        protected long period() {
            return 20L * Math.max(5, setting("seconds", 120));
        }

        @Override
        protected void tick() {
            int radius = Math.max(1, setting("radius", 50));
            for (Player player : players()) {
                World world = player.getWorld();
                if (world.getEnvironment() != World.Environment.NORMAL) {
                    continue;
                }
                Location target = player.getLocation().add(RANDOM.nextInt(radius * 2 + 1) - radius, 0,
                        RANDOM.nextInt(radius * 2 + 1) - radius);
                target.setY(world.getHighestBlockYAt(target) + 1.0);
                player.teleport(target);
                world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1f);
            }
        }
    }

    static final class XpLottery extends Twist {
        private static final List<PotionEffectType> EFFECTS = List.of(PotionEffectType.SPEED,
                PotionEffectType.SLOWNESS, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
                PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE,
                PotionEffectType.FIRE_RESISTANCE, PotionEffectType.INVISIBILITY, PotionEffectType.BLINDNESS,
                PotionEffectType.NIGHT_VISION, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
                PotionEffectType.POISON, PotionEffectType.LEVITATION, PotionEffectType.SLOW_FALLING,
                PotionEffectType.GLOWING, PotionEffectType.NAUSEA);

        XpLottery() {
            super("xp-lottery", "XP Lottery", Material.EXPERIENCE_BOTTLE, "Every level-up rolls a random potion effect");
        }

        @EventHandler
        public void onLevel(PlayerLevelChangeEvent event) {
            Player player = event.getPlayer();
            if (event.getNewLevel() <= event.getOldLevel() || !affects(player)) {
                return;
            }
            PotionEffectType type = EFFECTS.get(RANDOM.nextInt(EFFECTS.size()));
            player.addPotionEffect(new PotionEffect(type, 20 * setting("effect-seconds", 30), RANDOM.nextInt(2)));
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.2f);
        }
    }

    static final class EverythingFalls extends Twist {
        EverythingFalls() {
            super("everything-falls", "Everything Falls", Material.SAND, "Blocks above a broken block come crashing down");
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            if (!affects(event.getPlayer())) {
                return;
            }
            Block broken = event.getBlock();
            later(1, () -> {
                for (int i = 1; i <= setting("height", 12); i++) {
                    Block block = broken.getRelative(0, i, 0);
                    if (block.getType().isAir() || block.isLiquid() || unbreakable(block)) {
                        return;
                    }
                    BlockData data = block.getBlockData();
                    block.setType(Material.AIR);
                    block.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), data);
                }
            });
        }
    }

    static final class InventoryShuffle extends Twist {
        InventoryShuffle() {
            super("inventory-shuffle", "Inventory Shuffle", Material.ENDER_CHEST, "Your inventory gets shuffled all the time");
        }

        @Override
        protected long period() {
            return 20L * Math.max(1, setting("seconds", 20));
        }

        @Override
        protected void tick() {
            for (Player player : players()) {
                List<ItemStack> contents = new ArrayList<>(Arrays.asList(player.getInventory().getStorageContents()));
                Collections.shuffle(contents, RANDOM);
                player.getInventory().setStorageContents(contents.toArray(new ItemStack[0]));
                player.playSound(player.getLocation(), Sound.UI_LOOM_TAKE_RESULT, 0.8f, 1f);
            }
        }
    }

    static final class ExplodingMobs extends Twist {
        ExplodingMobs() {
            super("exploding-mobs", "Exploding Mobs", Material.CREEPER_HEAD, "Every mob explodes when it dies");
        }

        @EventHandler
        public void onDeath(EntityDeathEvent event) {
            if (event.getEntity() instanceof Mob mob && !isBoss(mob)) {
                Location location = mob.getLocation();
                later(1, () -> location.getWorld().createExplosion(location, (float) setting("power", 2.0), false, true));
            }
        }
    }
}
