package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;

import static dev.exiled.voidlands.gen.BiomeLayout.smoothstep;

/**
 * Turns a biome sample into a ground height.
 *
 * <p>The gentle parts of each biome's profile (ground offset, rolling hills, dunes) are blended
 * across patch borders with the sample's weights. Mountains work differently: they start at zero on
 * a patch's border and rise towards its middle over twice the blend width. That gives every
 * mountain a broad base and long foothills instead of the thin spikes of vanilla terrain, and it
 * keeps the ground continuous at borders without blending mountain heights between patches.
 */
final class TerrainShaper {

    private final double baseHeight;
    private final double mountainHeight;
    private final double riseWidth;
    private final SimplexNoise rolling;
    private final SimplexNoise massif;
    private final SimplexNoise ridges;
    private final SimplexNoise dunes;
    private final SimplexNoise detail;
    private final SimplexNoise depth;

    TerrainShaper(long seed, VoidlandsSettings settings) {
        this.baseHeight = settings.baseHeight();
        this.mountainHeight = settings.mountainHeight();
        this.riseWidth = settings.blendWidth() * 2;
        this.rolling = new SimplexNoise(seed + 1);
        this.massif = new SimplexNoise(seed + 2);
        this.ridges = new SimplexNoise(seed + 3);
        this.dunes = new SimplexNoise(seed + 4);
        this.detail = new SimplexNoise(seed + 5);
        this.depth = new SimplexNoise(seed + 6);
    }

    double height(BiomeLayout.Sample sample, double x, double z) {
        double offset = 0;
        double rollingAmplitude = 0;
        double duneAmplitude = 0;
        double[] weights = sample.weights();
        for (VoidBiome biome : VoidBiome.VALUES) {
            double weight = weights[biome.ordinal()];
            offset += weight * biome.offset;
            rollingAmplitude += weight * biome.rolling;
            duneAmplitude += weight * biome.dunes;
        }

        double height = baseHeight + offset;
        height += rollingAmplitude * rolling.fbm(x / 160.0, z / 160.0, 3);
        if (duneAmplitude > 0) {
            height += duneAmplitude * dune(x, z);
        }
        height += mountain(sample, x, z);
        height += 1.5 * detail.fbm(x / 24.0, z / 24.0, 2);
        return height;
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
