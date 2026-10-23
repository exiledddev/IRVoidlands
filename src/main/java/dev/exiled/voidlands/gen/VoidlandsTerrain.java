package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;

/**
 * Everything about a Voidlands world's shape for one seed: which biome is where and how high the
 * ground is. It has no Bukkit dependencies and is immutable, so it is safe to share between world
 * generation threads.
 */
public final class VoidlandsTerrain {

    /** Bedrock can reach this many blocks above the bottom of the world. */
    public static final int BEDROCK_LAYERS = 5;

    private static final int SALT_DITHER = 11;
    private static final int SALT_BEDROCK = 12;

    /**
     * One block column.
     *
     * @param biome        the F3 biome
     * @param surface      biome whose blocks cover this column (differs from {@code biome} near borders)
     * @param height       y of the top block, before clamping to the world's height
     * @param surfaceDepth how many blocks of surface material sit on the rock
     */
    public record Column(VoidBiome biome, VoidBiome surface, int height, int surfaceDepth) {
    }

    private final long seed;
    private final double borderDither;
    private final BiomeLayout layout;
    private final TerrainShaper shaper;

    public VoidlandsTerrain(long seed, VoidlandsSettings settings) {
        this.seed = seed;
        this.borderDither = settings.borderDither();
        this.layout = new BiomeLayout(seed, settings);
        this.shaper = new TerrainShaper(seed, settings);
    }

    public VoidBiome biomeAt(int x, int z) {
        return layout.biomeAt(x, z);
    }

    public Column column(int x, int z) {
        BiomeLayout.Sample sample = layout.sample(x, z);
        int height = (int) Math.floor(shaper.height(sample, x, z));

        VoidBiome surface = sample.biome();
        if (sample.neighbour() != null && sample.border() < borderDither) {
            // Mix in the neighbour's blocks near the border: half and half right on it, fading out.
            double chance = 0.5 * (1.0 - sample.border() / borderDither);
            if (Hash.unit(Hash.hash(seed, x, z, SALT_DITHER)) < chance) {
                surface = sample.neighbour();
            }
        }
        return new Column(sample.biome(), surface, height, shaper.surfaceDepth(x, z));
    }

    /** Whether the block {@code layer} blocks above the bottom of the world is bedrock. Jagged like the Nether floor. */
    public boolean bedrock(int x, int layer, int z) {
        if (layer <= 0) {
            return true;
        }
        if (layer >= BEDROCK_LAYERS) {
            return false;
        }
        double chance = (BEDROCK_LAYERS - layer) / (double) BEDROCK_LAYERS;
        return Hash.unit(Hash.hash(seed, x, z, SALT_BEDROCK + layer)) < chance;
    }
}
