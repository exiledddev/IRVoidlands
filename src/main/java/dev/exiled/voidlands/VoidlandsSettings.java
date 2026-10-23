package dev.exiled.voidlands;

import dev.exiled.voidlands.gen.VoidBiome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** The generator settings from config.yml, one record per section. */
public record VoidlandsSettings(Biomes biomes, Islands islands, Terrain terrain, Spikes spikes) {

    public static VoidlandsSettings defaults() {
        return new VoidlandsSettings(Biomes.defaults(), Islands.defaults(), Terrain.defaults(), Spikes.defaults());
    }

    /**
     * @param patchSize    average width of a biome patch, in blocks
     * @param borderWarp   how far patch borders wander, in blocks
     * @param borderDither width of the mixed-block band at patch borders, in blocks
     * @param weights      relative chance of each biome
     */
    public record Biomes(double patchSize, double borderWarp, double borderDither, Map<VoidBiome, Double> weights) {

        public Biomes {
            require(patchSize >= 32, "biomes.patch-size must be at least 32");
            require(borderWarp >= 0, "biomes.border-warp can't be negative");
            require(borderDither >= 0, "biomes.border-dither can't be negative");

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

        public static Biomes defaults() {
            Map<VoidBiome, Double> weights = new EnumMap<>(VoidBiome.class);
            for (VoidBiome biome : VoidBiome.values()) {
                weights.put(biome, 1.0);
            }
            return new Biomes(384, 72, 6, weights);
        }
    }

    /**
     * @param coverage        share of the world covered by the big islands, 0 to 1
     * @param size            rough width of a big island, in blocks
     * @param height          average height of the island ground
     * @param heightVariation how far islands sit above or below that height, in blocks
     * @param thickness       depth of the biggest islands' undersides, in blocks
     * @param islets          how many small floating rocks fill the gaps, 0 (none) to 1 (lots)
     */
    public record Islands(double coverage, double size, int height, double heightVariation, double thickness,
                          double islets) {

        public Islands {
            require(coverage >= 0.05 && coverage <= 0.9, "islands.coverage must be between 0.05 and 0.9");
            require(size >= 32, "islands.size must be at least 32");
            require(height >= 60 && height <= 180, "islands.height must be between 60 and 180");
            require(heightVariation >= 0 && heightVariation <= 50, "islands.height-variation must be between 0 and 50");
            require(thickness >= 4 && thickness <= 80, "islands.thickness must be between 4 and 80");
            require(islets >= 0 && islets <= 1, "islands.islets must be between 0 and 1");
        }

        public static Islands defaults() {
            return new Islands(0.3, 320, 110, 25, 45, 0.5);
        }
    }

    /**
     * @param blendWidth     how far terrain blends into the neighbouring patch, in blocks
     * @param mountainHeight height of the biggest mountains above the island ground, in blocks
     */
    public record Terrain(double blendWidth, double mountainHeight) {

        public Terrain {
            require(blendWidth >= 1, "terrain.blend-width must be at least 1");
            require(mountainHeight >= 0 && mountainHeight <= 100, "terrain.mountain-height must be between 0 and 100");
        }

        public static Terrain defaults() {
            return new Terrain(80, 70);
        }
    }

    /**
     * @param enabled   whether glowing spikes generate at all
     * @param spacing   size of the grid cell that holds at most one spike, in blocks
     * @param chance    chance that a cell on island ground gets a spike, 0 to 1
     * @param minHeight shortest spike, in blocks
     * @param maxHeight tallest spike, in blocks
     */
    public record Spikes(boolean enabled, double spacing, double chance, int minHeight, int maxHeight) {

        /** Below this, two spikes could end up in the same chunk. */
        public static final double MIN_SPACING = 46;

        public Spikes {
            require(spacing >= MIN_SPACING, "spikes.spacing must be at least " + (int) MIN_SPACING);
            require(chance >= 0 && chance <= 1, "spikes.chance must be between 0 and 1");
            require(minHeight >= 4, "spikes.min-height must be at least 4");
            require(maxHeight >= minHeight && maxHeight <= 64, "spikes.max-height must be between min-height and 64");
        }

        public static Spikes defaults() {
            return new Spikes(true, 64, 0.7, 12, 32);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
