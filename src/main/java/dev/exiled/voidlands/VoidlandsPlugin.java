package dev.exiled.voidlands;

import dev.exiled.voidlands.gen.VoidBiome;
import dev.exiled.voidlands.gen.VoidlandsChunkGenerator;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Provides the Voidlands world generator. Multiverse-Core (or anything else that creates worlds)
 * asks for it by plugin name: {@code /mv create voidlands nether -g Voidlands}.
 */
public final class VoidlandsPlugin extends JavaPlugin {

    private static final String BIOME_NAMESPACE = "voidlands";

    private VoidlandsSettings settings = VoidlandsSettings.defaults();
    private Map<VoidBiome, Biome> biomes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = readSettings();
        biomes();
        getLogger().info("Ready. Create a world with: /mv create <name> nether -g " + getName());
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidlandsChunkGenerator(settings, biomes(), getLogger());
    }

    private Map<VoidBiome, Biome> biomes() {
        if (biomes == null) {
            biomes = lookUpBiomes();
        }
        return biomes;
    }

    /** Finds the biomes registered by the bundled datapack, or falls back to Soul Sand Valley if it didn't load. */
    private Map<VoidBiome, Biome> lookUpBiomes() {
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        Map<VoidBiome, Biome> found = new EnumMap<>(VoidBiome.class);
        for (VoidBiome biome : VoidBiome.values()) {
            Biome registered = registry.get(NamespacedKey.fromString(BIOME_NAMESPACE + ":" + biome.id()));
            if (registered == null) {
                getLogger().severe("Biome " + BIOME_NAMESPACE + ":" + biome.id() + " is missing, so the Voidlands"
                        + " datapack did not load. Check the startup log and /datapack list. Until this is fixed,"
                        + " Voidlands worlds generate with the vanilla Soul Sand Valley biome (with its mobs).");
                for (VoidBiome fallback : VoidBiome.values()) {
                    found.put(fallback, Biome.SOUL_SAND_VALLEY);
                }
                return found;
            }
            found.put(biome, registered);
        }
        return found;
    }

    private VoidlandsSettings readSettings() {
        FileConfiguration config = getConfig();
        VoidlandsSettings defaults = VoidlandsSettings.defaults();
        Map<VoidBiome, Double> weights = new EnumMap<>(VoidBiome.class);
        for (VoidBiome biome : VoidBiome.values()) {
            weights.put(biome, config.getDouble("biomes.weights." + biome.id(), defaults.weights().get(biome)));
        }
        try {
            return new VoidlandsSettings(
                    config.getDouble("biomes.patch-size", defaults.patchSize()),
                    config.getDouble("biomes.border-warp", defaults.borderWarp()),
                    config.getDouble("biomes.border-dither", defaults.borderDither()),
                    weights,
                    config.getInt("terrain.base-height", defaults.baseHeight()),
                    config.getDouble("terrain.blend-width", defaults.blendWidth()),
                    config.getDouble("terrain.mountain-height", defaults.mountainHeight()));
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid config.yml: " + e.getMessage() + ". Using the default settings instead.");
            return defaults;
        }
    }
}
