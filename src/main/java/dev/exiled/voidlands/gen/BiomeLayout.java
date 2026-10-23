package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;

/**
 * Splits the world into big biome patches. The world is covered by a jittered grid of Voronoi cells,
 * each rolling a weighted random biome; neighbouring cells that roll the same biome merge into one
 * larger patch. The input coordinates are warped by noise first, so borders wander organically
 * instead of running in straight lines.
 */
final class BiomeLayout {

    /** Cells checked in each direction around a point. Two reaches well past any blend distance. */
    private static final int SEARCH = 2;
    private static final int CELLS = (2 * SEARCH + 1) * (2 * SEARCH + 1);
    private static final int SALT_JITTER_X = 1;
    private static final int SALT_JITTER_Z = 2;
    private static final int SALT_BIOME = 3;

    /**
     * What the layout looks like around one block column.
     *
     * @param biome     biome of the nearest cell; this is the F3 biome
     * @param neighbour nearest different biome, or null if there is none within reach
     * @param border    distance to the border with {@code neighbour}, in blocks
     * @param weights   blend weight of each biome (indexed by ordinal), summing to 1
     */
    record Sample(VoidBiome biome, VoidBiome neighbour, double border, double[] weights) {
    }

    private final long seed;
    private final double cellSize;
    private final double warp;
    private final double warpScale;
    private final double blendWidth;
    private final SimplexNoise warpX;
    private final SimplexNoise warpZ;
    private final VoidBiome[] choices;
    private final double[] thresholds;

    BiomeLayout(long seed, VoidlandsSettings settings) {
        this.seed = seed;
        this.cellSize = settings.biomes().patchSize();
        this.warp = settings.biomes().borderWarp();
        this.warpScale = settings.biomes().patchSize();
        this.blendWidth = settings.terrain().blendWidth();
        this.warpX = new SimplexNoise(seed ^ 0x5DEECE66DL);
        this.warpZ = new SimplexNoise(seed ^ 0x2545F4914F6CDD1DL);

        var weights = settings.biomes().weights();
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        this.choices = VoidBiome.VALUES.clone();
        this.thresholds = new double[choices.length];
        double cumulative = 0;
        for (int i = 0; i < choices.length; i++) {
            cumulative += weights.get(choices[i]) / total;
            thresholds[i] = cumulative;
        }
    }

    /** The biome at a block column. Same as {@code sample(x, z).biome()}, but cheaper. */
    VoidBiome biomeAt(double x, double z) {
        double gx = warpedX(x, z);
        double gz = warpedZ(x, z);
        int baseX = floor(gx);
        int baseZ = floor(gz);
        VoidBiome nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int cx = baseX - SEARCH; cx <= baseX + SEARCH; cx++) {
            for (int cz = baseZ - SEARCH; cz <= baseZ + SEARCH; cz++) {
                double distance = distanceSquared(cx, cz, gx, gz);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = biomeOf(cx, cz);
                }
            }
        }
        return nearest;
    }

    Sample sample(double x, double z) {
        double gx = warpedX(x, z);
        double gz = warpedZ(x, z);
        int baseX = floor(gx);
        int baseZ = floor(gz);

        double[] distances = new double[CELLS];
        VoidBiome[] biomes = new VoidBiome[CELLS];
        int nearest = 0;
        int i = 0;
        for (int cx = baseX - SEARCH; cx <= baseX + SEARCH; cx++) {
            for (int cz = baseZ - SEARCH; cz <= baseZ + SEARCH; cz++, i++) {
                distances[i] = Math.sqrt(distanceSquared(cx, cz, gx, gz)) * cellSize;
                biomes[i] = biomeOf(cx, cz);
                if (distances[i] < distances[nearest]) {
                    nearest = i;
                }
            }
        }

        VoidBiome own = biomes[nearest];
        VoidBiome neighbour = null;
        double border = Double.POSITIVE_INFINITY;
        double[] weights = new double[VoidBiome.VALUES.length];
        double total = 0;
        for (i = 0; i < CELLS; i++) {
            // Half the difference in distance approximates the distance to the border between two
            // cells, and unlike the true border distance it changes smoothly everywhere.
            double gap = (distances[i] - distances[nearest]) * 0.5;
            double weight = 1.0 - smoothstep(gap / blendWidth);
            weights[biomes[i].ordinal()] += weight;
            total += weight;
            if (biomes[i] != own && gap < border) {
                border = gap;
                neighbour = biomes[i];
            }
        }
        for (int b = 0; b < weights.length; b++) {
            weights[b] /= total;
        }
        return new Sample(own, neighbour, border, weights);
    }

    // The fine warp layers are kept weak (persistence 0.4): strong ones squeeze space unevenly, which
    // would make some mountain flanks much steeper than others.
    private double warpedX(double x, double z) {
        return (x + warp * warpX.fbm(x / warpScale, z / warpScale, 3, 0.4)) / cellSize;
    }

    private double warpedZ(double x, double z) {
        return (z + warp * warpZ.fbm(x / warpScale, z / warpScale, 3, 0.4)) / cellSize;
    }

    /** Squared distance, in cell units, from a point to the jittered centre of cell (cx, cz). */
    private double distanceSquared(int cx, int cz, double gx, double gz) {
        double dx = cx + jitter(cx, cz, SALT_JITTER_X) - gx;
        double dz = cz + jitter(cx, cz, SALT_JITTER_Z) - gz;
        return dx * dx + dz * dz;
    }

    private double jitter(int cx, int cz, int salt) {
        return 0.15 + 0.7 * Hash.unit(Hash.hash(seed, cx, cz, salt));
    }

    private VoidBiome biomeOf(int cx, int cz) {
        double roll = Hash.unit(Hash.hash(seed, cx, cz, SALT_BIOME));
        for (int i = 0; i < choices.length; i++) {
            if (roll < thresholds[i]) {
                return choices[i];
            }
        }
        // Rounding can leave the last threshold a hair under 1; fall back to the last enabled biome.
        for (int i = choices.length - 1; ; i--) {
            if (i == 0 || thresholds[i] > thresholds[i - 1]) {
                return choices[i];
            }
        }
    }

    static double smoothstep(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        return t * t * (3 - 2 * t);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
