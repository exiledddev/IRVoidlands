package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;

import static dev.exiled.voidlands.gen.BiomeLayout.smoothstep;

/**
 * Shapes the ground on top of an island from a biome sample.
 *
 * <p>The gentle parts of each biome's profile (ground offset, rolling hills, dunes) are blended
 * across patch borders with the sample's weights. Mountains work differently: they start at zero on
 * a patch's border and rise towards its middle over twice the blend width. That gives every
 * mountain a broad base and long foothills instead of the thin spikes of vanilla terrain, and it
 * keeps the ground continuous at borders without blending mountain heights between patches.
 */
final class TerrainShaper {

    private final double mountainHeight;
    private final double riseWidth;
    private final SimplexNoise rolling;
    private final SimplexNoise massif;
    private final SimplexNoise ridges;
    private final SimplexNoise dunes;
    private final SimplexNoise detail;
    private final SimplexNoise depth;

    TerrainShaper(long seed, VoidlandsSettings settings) {
        this.mountainHeight = settings.terrain().mountainHeight();
        this.riseWidth = settings.terrain().blendWidth() * 2;
        this.rolling = new SimplexNoise(seed + 1);
        this.massif = new SimplexNoise(seed + 2);
        this.ridges = new SimplexNoise(seed + 3);
        this.dunes = new SimplexNoise(seed + 4);
        this.detail = new SimplexNoise(seed + 5);
        this.depth = new SimplexNoise(seed + 6);
    }

    /** The biomes' blended ground level, relative to the island's height. */
    double groundOffset(BiomeLayout.Sample sample) {
        double offset = 0;
        double[] weights = sample.weights();
        for (VoidBiome biome : VoidBiome.VALUES) {
            offset += weights[biome.ordinal()] * biome.offset;
        }
        return offset;
    }

    /**
     * Hills, dunes and mountains on top of the ground level.
     *
     * @param mountainScale 0 to 1; how much of the mountains to build (islands keep them off their edges)
     */
    double relief(BiomeLayout.Sample sample, double x, double z, double mountainScale) {
        double rollingAmplitude = 0;
        double duneAmplitude = 0;
        double[] weights = sample.weights();
        for (VoidBiome biome : VoidBiome.VALUES) {
            double weight = weights[biome.ordinal()];
            rollingAmplitude += weight * biome.rolling;
            duneAmplitude += weight * biome.dunes;
        }

        double relief = rollingAmplitude * rolling.fbm(x / 160.0, z / 160.0, 3);
        if (duneAmplitude > 0) {
            relief += duneAmplitude * dune(x, z);
        }
        if (mountainScale > 0) {
            relief += mountainScale * mountain(sample, x, z);
        }
        relief += 1.5 * detail.fbm(x / 24.0, z / 24.0, 2);
        return relief;
    }

    /** How many blocks of the surface material sit on top of the rock: 3 to 5. */
    int surfaceDepth(double x, double z) {
        return 3 + (int) Math.round(depth.noise(x / 40.0, z / 40.0) + 1.0);
    }

    /** Long dune ridges running diagonally, in [0, 1]. */
    private double dune(double x, double z) {
        double u = x * 0.866 + z * 0.5;
        double v = z * 0.866 - x * 0.5;
        double ridge = 1.0 - Math.abs(dunes.noise(u / 90.0, v / 28.0));
        return ridge * ridge;
    }

    private double mountain(BiomeLayout.Sample sample, double x, double z) {
        VoidBiome biome = sample.biome();
        if (biome.mountain <= 0) {
            return 0;
        }
        // 0 on the patch border, easing up to 1 deep inside: a smooth S-shaped flank.
        double rise = smoothstep(sample.border() / riseWidth);
        if (rise <= 0) {
            return 0;
        }
        // Broad swells so one patch holds several rounded summits of different heights.
        double variation = 0.5 + 0.5 * (massif.fbm(x / 260.0, z / 260.0, 2) * 0.5 + 0.5);
        // Ridge lines, only strong high up on the mountain. The crease is rounded off (instead of
        // a plain abs) so ridges have smooth crests rather than knife edges.
        double n = ridges.fbm(x / 150.0, z / 150.0, 2);
        double ridge = 1.0 - Math.sqrt(n * n + 0.02);
        double mountain = mountainHeight * biome.mountain
                * (rise * variation + 0.25 * rise * rise * ridge * ridge);
        if (biome.plateauCap > 0) {
            mountain = smoothMin(mountain, biome.plateauCap * mountainHeight, 8.0);
        }
        return mountain;
    }

    /** Polynomial smooth minimum: like {@code Math.min}, but rounds off the corner over {@code k} blocks. */
    private static double smoothMin(double a, double b, double k) {
        double h = Math.clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
        return b + (a - b) * h - k * h * (1 - h);
    }
}
