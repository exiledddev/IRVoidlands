package dev.exiled.voidlands.gen;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Places the Voidlands biomes from the bundled datapack. The biome is the same at every height, and
 * it comes from the same layout the generator uses for the surface blocks, so F3 matches the ground.
 */
final class VoidlandsBiomeProvider extends BiomeProvider {

    private final VoidlandsChunkGenerator generator;
    private final Map<VoidBiome, Biome> biomes;
    private final List<Biome> allBiomes;

    VoidlandsBiomeProvider(VoidlandsChunkGenerator generator, Map<VoidBiome, Biome> biomes) {
        this.generator = generator;
        this.biomes = Map.copyOf(biomes);
        this.allBiomes = List.copyOf(new LinkedHashSet<>(biomes.values()));
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return biomes.get(generator.terrain(worldInfo).biomeAt(x, z));
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return allBiomes;
    }
}
