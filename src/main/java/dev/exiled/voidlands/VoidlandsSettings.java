package dev.exiled.voidlands;

import dev.exiled.voidlands.gen.VoidBiome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The generator settings from config.yml.
 *
 * @param patchSize      average width of a biome patch, in blocks
 * @param borderWarp     how far patch borders wander, in blocks
 * @param borderDither   width of the mixed-block band at patch borders, in blocks
 * @param weights        relative chance of each biome
 * @param baseHeight     average ground level
 * @param blendWidth     how far terrain blends into the neighbouring patch, in blocks
 * @param mountainHeight height of the biggest mountains above the ground, in blocks
 */
public record VoidlandsSettings(
        double patchSize,
        double borderWarp,
        double borderDither,
        Map<VoidBiome, Double> weights,
        int baseHeight,
        double blendWidth,
        double mountainHeight) {

    public VoidlandsSettings {
        require(patchSize >= 32, "biomes.patch-size must be at least 32");
        require(borderWarp >= 0, "biomes.border-warp can't be negative");
        require(borderDither >= 0, "biomes.border-dither can't be negative");
        require(baseHeight >= 8 && baseHeight <= 200, "terrain.base-height must be between 8 and 200");
        require(blendWidth >= 1, "terrain.blend-width must be at least 1");
        require(mountainHeight >= 0, "terrain.mountain-height can't be negative");

        Map<VoidBiome, Double> copy = new EnumMap<>(VoidBiome.class);
        double total = 0;
        for (VoidBiome biome : VoidBiome.values()) {
            double weight = weights.getOrDefault(biome, 0.0);
            require(weight >= 0, "biomes.weights." + biome.id() + " can't be negative");
            copy.put(biome, weight);
            total += weight;
        }
        require(total > 0, "at least one biome weight must be above 0");
        weights = Collections.unmodifiableMap(copy);
    }

    public static VoidlandsSettings defaults() {
        Map<VoidBiome, Double> weights = new EnumMap<>(VoidBiome.class);
        for (VoidBiome biome : VoidBiome.values()) {
            weights.put(biome, 1.0);
        }
        return new VoidlandsSettings(384, 72, 6, weights, 60, 80, 100);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
