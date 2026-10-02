package de.derjannik.butwhat;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class WorldReset {

    private static final String PENDING = "pending-reset.yml";
    private static final String SETTINGS = "data/minecraft/world_gen_settings.dat";
    private static final String[] CHUNK_DIRS = {"region", "entities", "poi"};
    private static final String[] STATE_FILES = {"raids.dat", "chunk_tickets.dat", "ender_dragon_fight.dat",
            "scheduled_events.dat"};
    private static final byte[] SEED_TAG = {4, 0, 4, 's', 'e', 'e', 'd'};

    record Options(boolean overworld, boolean nether, boolean end, boolean newSeed, boolean players) {
        boolean any() {
            return overworld || nether || end || newSeed || players;
        }
    }

    private WorldReset() {
    }

    // Collects everything to delete, then restarts, the files are removed before the worlds load again
    static void request(ButWhat plugin, Options options) {
        World main = Bukkit.getWorlds().get(0);
        Set<File> targets = new LinkedHashSet<>();
        if (options.overworld()) {
            addDimension(targets, main, main, "overworld", null);
        }
        if (options.nether()) {
            addDimension(targets, main, find(main, World.Environment.NETHER, "_nether"), "the_nether", "DIM-1");
        }
        if (options.end()) {
            addDimension(targets, main, find(main, World.Environment.THE_END, "_the_end"), "the_end", "DIM1");
        }
        if (options.players()) {
            for (String name : new String[]{"playerdata", "stats", "advancements", "players"}) {
                add(targets, new File(levelRoot(main), name));
            }
        }
        long seed = new Random().nextLong();
        List<String> seedFiles = new ArrayList<>();
        if (options.newSeed()) {
            for (World world : Bukkit.getWorlds()) {
                File root = levelRoot(world);
                if (modern(root)) {
                    // 26.x keeps the seed per dimension and refuses to start without these files
                    File settings = new File(world.getWorldFolder(), SETTINGS);
                    if (settings.exists()) {
                        seedFiles.add(settings.getAbsolutePath());
                    }
                } else {
                    add(targets, new File(root, "level.dat"));
                    add(targets, new File(root, "level.dat_old"));
                }
            }
            writeSeed(plugin, seed);
        }

        List<String> paths = new ArrayList<>();
        for (File target : targets) {
            paths.add(target.getAbsolutePath());
        }
        YamlConfiguration pending = new YamlConfiguration();
        pending.set("paths", paths);
        pending.set("seed", seed);
        pending.set("seed-files", seedFiles);
        try {
            pending.save(new File(plugin.getDataFolder(), PENDING));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save the pending world reset: " + e.getMessage());
            return;
        }
        plugin.getConfig().set("last-reset", System.currentTimeMillis());
        plugin.getConfig().set("last-reset-relocate", options.overworld() || options.newSeed());
        plugin.saveConfig();

        String kick = plugin.message("reset-kick");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kickPlayer(kick);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.spigot().restart(), 20L);
    }

    // Since 26.1 the world folder is a dimension folder deep inside the level folder
    private static File levelRoot(World world) {
        try {
            Path container = Bukkit.getWorldContainer().getCanonicalFile().toPath();
            Path folder = world.getWorldFolder().getCanonicalFile().toPath();
            if (folder.startsWith(container) && !folder.equals(container)) {
                return container.resolve(container.relativize(folder).getName(0)).toFile();
            }
        } catch (IOException ignored) {
            // Falls back to the world folder itself
        }
        return world.getWorldFolder();
    }

    private static World find(World main, World.Environment environment, String suffix) {
        World named = Bukkit.getWorld(main.getName() + suffix);
        if (named != null) {
            return named;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) {
                return world;
            }
        }
        return null;
    }

    // Covers the classic layout (world_nether/DIM-1) and the 26.x layout (world/dimensions/minecraft/the_nether)
    private static void addDimension(Set<File> targets, World main, World world, String key, String legacyDir) {
        if (world == null) {
            return;
        }
        List<File> roots = new ArrayList<>();
        roots.add(world.getWorldFolder());
        if (legacyDir != null) {
            roots.add(new File(world.getWorldFolder(), legacyDir));
        }
        roots.add(new File(world.getWorldFolder(), "dimensions/minecraft/" + key));
        roots.add(new File(levelRoot(main), "dimensions/minecraft/" + key));
        for (File root : roots) {
            for (String dir : CHUNK_DIRS) {
                add(targets, new File(root, dir));
            }
            for (String state : STATE_FILES) {
                add(targets, new File(root, "data/minecraft/" + state));
            }
            // Classic layout: only raids and the dragon fight live here
            if (legacyDir != null && root.getName().equals(legacyDir)) {
                add(targets, new File(root, "data"));
            }
        }
    }

    private static boolean modern(File levelRoot) {
        return new File(levelRoot, "dimensions").isDirectory();
    }

    // Files can still appear during the shutdown save, so existence is only checked when deleting
    private static void add(Set<File> targets, File file) {
        targets.add(file);
    }

    // A new world only picks up a new seed from server.properties
    private static void writeSeed(ButWhat plugin, long seed) {
        File file = new File("server.properties");
        Properties properties = new Properties();
        try {
            if (file.exists()) {
                try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            properties.setProperty("level-seed", String.valueOf(seed));
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                properties.store(writer, "Minecraft server properties");
            }
            plugin.getLogger().info("New world seed: " + seed);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write the new seed to server.properties: " + e.getMessage());
        }
    }

    // Runs in onLoad, before any world is loaded
    static void applyPending(ButWhat plugin) {
        File file = new File(plugin.getDataFolder(), PENDING);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration pending = YamlConfiguration.loadConfiguration(file);
        List<String> paths = pending.getStringList("paths");
        file.delete();
        Path container;
        try {
            container = Bukkit.getWorldContainer().getCanonicalFile().toPath();
        } catch (IOException e) {
            plugin.getLogger().severe("World reset skipped: " + e.getMessage());
            return;
        }
        int deleted = 0;
        for (String raw : paths) {
            try {
                Path path = new File(raw).getCanonicalFile().toPath();
                // Never touch anything outside the world container
                if (!path.startsWith(container) || path.equals(container) || !Files.exists(path)) {
                    continue;
                }
                delete(path);
                deleted++;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not delete " + raw + ": " + e.getMessage());
            }
        }
        for (String raw : pending.getStringList("seed-files")) {
            try {
                Path path = new File(raw).getCanonicalFile().toPath();
                if (path.startsWith(container) && Files.exists(path)) {
                    patchSeed(path, pending.getLong("seed"));
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not set the new seed in " + raw + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("World reset applied, " + deleted + " world data entries removed.");
    }

    // Overwrites the long tag "seed" inside the gzipped NBT file
    private static void patchSeed(Path path, long seed) throws IOException {
        byte[] data;
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(path))) {
            data = in.readAllBytes();
        }
        int index = indexOf(data, SEED_TAG);
        if (index < 0 || index + SEED_TAG.length + 8 > data.length) {
            throw new IOException("no seed tag found");
        }
        ByteBuffer.wrap(data, index + SEED_TAG.length, 8).putLong(seed);
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(data);
        }
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void delete(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
