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
        try {
            return new VoidlandsSettings(
                    readBiomes(config, defaults.biomes()),
                    readIslands(config, defaults.islands()),
                    readTerrain(config, defaults.terrain()),
                    readSpikes(config, defaults.spikes()));
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid config.yml: " + e.getMessage() + ". Using the default settings instead.");
            return defaults;
        }
    }

    private static VoidlandsSettings.Biomes readBiomes(FileConfiguration config, VoidlandsSettings.Biomes defaults) {
        Map<VoidBiome, Double> weights = new EnumMap<>(VoidBiome.class);
        for (VoidBiome biome : VoidBiome.values()) {
            weights.put(biome, config.getDouble("biomes.weights." + biome.id(), defaults.weights().get(biome)));
        }
        return new VoidlandsSettings.Biomes(
                config.getDouble("biomes.patch-size", defaults.patchSize()),
                config.getDouble("biomes.border-warp", defaults.borderWarp()),
                config.getDouble("biomes.border-dither", defaults.borderDither()),
                weights);
    }

    private static VoidlandsSettings.Islands readIslands(FileConfiguration config, VoidlandsSettings.Islands defaults) {
        return new VoidlandsSettings.Islands(
                config.getDouble("islands.coverage", defaults.coverage()),
                config.getDouble("islands.size", defaults.size()),
                config.getInt("islands.height", defaults.height()),
                config.getDouble("islands.height-variation", defaults.heightVariation()),
                config.getDouble("islands.thickness", defaults.thickness()),
                config.getDouble("islands.islets", defaults.islets()));
    }

    private static VoidlandsSettings.Terrain readTerrain(FileConfiguration config, VoidlandsSettings.Terrain defaults) {
        return new VoidlandsSettings.Terrain(
                config.getDouble("terrain.blend-width", defaults.blendWidth()),
                config.getDouble("terrain.mountain-height", defaults.mountainHeight()));
    }

    private static VoidlandsSettings.Spikes readSpikes(FileConfiguration config, VoidlandsSettings.Spikes defaults) {
        return new VoidlandsSettings.Spikes(
                config.getBoolean("spikes.enabled", defaults.enabled()),
                config.getDouble("spikes.spacing", defaults.spacing()),
                config.getDouble("spikes.chance", defaults.chance()),
                config.getInt("spikes.min-height", defaults.minHeight()),
                config.getInt("spikes.max-height", defaults.maxHeight()));
    }
}
