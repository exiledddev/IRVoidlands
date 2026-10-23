package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;

import java.util.Arrays;
import java.util.SplittableRandom;

import static dev.exiled.voidlands.gen.BiomeLayout.smoothstep;

/**
 * Decides where the floating islands are, how high they float and how deep their undersides hang.
 *
 * <p>Each island grows around a centre on a jittered grid, with its own size and its own height, so
 * neighbouring islands float at clearly different levels. A noisy "island field" falls off from each
 * centre, and wherever it is above a threshold there is land. The threshold is picked by sampling
 * the field, so the configured coverage holds for any seed. Islands that grow into each other merge,
 * and their heights blend so the merged ground stays continuous.
 *
 * <p>How far the field is above the threshold doubles as "how far inland" a point is. That shapes
 * the underside into a deep cone under the middle and keeps mountains off the coasts. A second,
 * finer noise scatters small floating rocks in the gaps.
 */
final class IslandShaper {

    /**
     * One point of the island field.
     *
     * @param inland   0 on a coast, growing towards an island's middle (about 3 at the centre of a
     *                 big one), negative out over the void
     * @param altitude the island's ground level here, before biome relief
     */
    record Sample(double inland, double altitude) {
    }

    /** Field distance from a coast to where a point counts as inland. */
    private static final double INLAND_RANGE = 0.2;
    /** How strongly the coastlines are roughened, in field units. */
    private static final double COAST_NOISE = 0.3;
    /** Islets stay at least this far (in field units) out from the big islands' coasts. */
    private static final double ISLET_GAP = 0.1;
    private static final double ISLET_RANGE = 0.1;
    /** The island under the world spawn reaches at least this far out, in blocks. */
    private static final double SPAWN_ISLAND_RADIUS = 110;
    private static final int THRESHOLD_SAMPLES = 20_000;
    private static final int SALT_X = 31;
    private static final int SALT_Z = 32;
    private static final int SALT_SIZE = 33;
    private static final int SALT_ALTITUDE = 34;
    private static final int SALT_PRESENT = 35;
    private static final int SALT_GIANT = 36;

    private final long seed;
    private final double cellSize;
    private final double size;
    private final double height;
    private final double heightVariation;
    private final double thickness;
    private final double threshold;
    private final double isletThreshold;
    private final SimplexNoise coast;
    private final SimplexNoise lobesX;
    private final SimplexNoise lobesZ;
    private final SimplexNoise underside;
    private final SimplexNoise drips;
    private final SimplexNoise islets;
    private final SimplexNoise isletAltitude;

    IslandShaper(long seed, VoidlandsSettings settings) {
        VoidlandsSettings.Islands config = settings.islands();
        this.seed = seed;
        this.size = config.size();
        this.cellSize = config.size() * 1.3;
        this.height = config.height();
        this.heightVariation = config.heightVariation();
        this.thickness = config.thickness();
        this.coast = new SimplexNoise(seed + 101);
        this.lobesX = new SimplexNoise(seed + 107);
        this.lobesZ = new SimplexNoise(seed + 108);
        this.underside = new SimplexNoise(seed + 103);
        this.drips = new SimplexNoise(seed + 104);
        this.islets = new SimplexNoise(seed + 105);
        this.isletAltitude = new SimplexNoise(seed + 106);

        SplittableRandom random = new SplittableRandom(seed);
        this.threshold = quantile(random, 1 - config.coverage(), (x, z) -> field(x, z, null));
        // At islets = 1, about 6% of the open void holds a rock; at 0 there are none.
        this.isletThreshold = config.islets() <= 0
                ? Double.POSITIVE_INFINITY
                : quantile(random, 1 - 0.06 * config.islets(), this::isletNoise);
    }

    Sample sample(double x, double z) {
        double[] altitude = new double[1];
        double value = field(x, z, altitude);
        return new Sample((value - threshold) / INLAND_RANGE, altitude[0]);
    }

    /** Lowers the top near the coast so islands get rounded rims rather than sheer edges. */
    double rimDrop(double inland) {
        return 3 * (1 - smoothstep(inland / 0.4));
    }

    /** How far the underside hangs below the ground level: thin at the coast, a deep cone under the middle. */
    double depthBelow(double x, double z, double inland) {
        double core = Math.pow(Math.clamp(inland / 3.0, 0, 1), 0.8);
        double depth = 3 + (thickness - 3) * core * (0.8 + 0.2 * underside.fbm(x / 70.0, z / 70.0, 2));
        // Rocky drips hanging off the bottom.
        double drip = Math.max(0, 1 - Math.abs(drips.noise(x / 13.0, z / 13.0)) - 0.6) / 0.4;
        return depth + 12 * Math.min(1, inland) * drip * drip;
    }

    /**
     * How far into a small floating rock a point is (0 to 1), or a negative number if there's none.
     * Only out in the void between big islands.
     */
    double islet(double x, double z, double inland) {
        if (inland * INLAND_RANGE > -ISLET_GAP) {
            return -1;
        }
        return (isletNoise(x, z) - isletThreshold) / ISLET_RANGE;
    }

    /** The y a small rock floats at. Neighbouring rocks drift to different heights. */
    double isletAltitude(double x, double z) {
        return height + 35 * isletAltitude.noise(x / 90.0, z / 90.0);
    }

    /**
     * The island field at a point: about 1 at an island's centre, falling to 0 at its nominal radius
     * and below 0 beyond, roughened by noise. Also writes the blended island height into
     * {@code altitudeOut[0]} when it isn't null.
     */
    private double field(double x, double z, double[] altitudeOut) {
        // The spawn island: always present and at the configured height, so players arrive on land.
        double best = 1 - Math.sqrt(x * x + z * z) / SPAWN_ISLAND_RADIUS;
        double weight = blendWeight(best);
        double weightSum = weight;
        double altitudeSum = weight * height;

        // Warping the point before measuring distances bends the round islands into lobes and arms.
        double lobeScale = size * 0.6;
        double wx = x + size * 0.3 * lobesX.fbm(x / lobeScale, z / lobeScale, 2);
        double wz = z + size * 0.3 * lobesZ.fbm(x / lobeScale, z / lobeScale, 2);

        int baseX = (int) Math.floor(x / cellSize);
        int baseZ = (int) Math.floor(z / cellSize);
        for (int cx = baseX - 1; cx <= baseX + 1; cx++) {
            for (int cz = baseZ - 1; cz <= baseZ + 1; cz++) {
                // Some cells stay empty, which breaks up the grid and leaves wider stretches of void.
                if (unit(cx, cz, SALT_PRESENT) > 0.8) {
                    continue;
                }
                double centreX = (cx + 0.2 + 0.6 * unit(cx, cz, SALT_X)) * cellSize;
                double centreZ = (cz + 0.2 + 0.6 * unit(cx, cz, SALT_Z)) * cellSize;
                double radius = size * 0.5 * (0.45 + 0.55 * unit(cx, cz, SALT_SIZE));
                if (unit(cx, cz, SALT_GIANT) < 0.15) {
                    radius *= 1.35;
                }
                double dx = wx - centreX;
                double dz = wz - centreZ;
                double value = 1 - Math.sqrt(dx * dx + dz * dz) / radius;
                best = Math.max(best, value);
                if (altitudeOut != null) {
                    double w = blendWeight(value);
                    weightSum += w;
                    altitudeSum += w * (height + heightVariation * (2 * unit(cx, cz, SALT_ALTITUDE) - 1));
                }
            }
        }
        if (altitudeOut != null) {
            altitudeOut[0] = weightSum > 0 ? altitudeSum / weightSum : height;
        }
        return best + COAST_NOISE * coast.fbm(x / 80.0, z / 80.0, 3);
    }

    /** How much an island's height counts at a point; strongly favours the island the point is on. */
    private static double blendWeight(double fieldValue) {
        double w = Math.max(0, fieldValue + 0.4);
        return w * w * w * w;
    }

    private double unit(int cx, int cz, int salt) {
        return Hash.unit(Hash.hash(seed, cx, cz, salt));
    }

    private double isletNoise(double x, double z) {
        return islets.fbm(x / 60.0, z / 60.0, 2);
    }

    private interface Field {
        double at(double x, double z);
    }

    /** The value that {@code fraction} of the field lies below, sampled over a wide area. */
    private double quantile(SplittableRandom random, double fraction, Field field) {
        double[] values = new double[THRESHOLD_SAMPLES];
        double extent = cellSize * 30;
        for (int i = 0; i < values.length; i++) {
            values[i] = field.at(random.nextDouble(-extent, extent), random.nextDouble(-extent, extent));
        }
        Arrays.sort(values);
        return values[(int) Math.min(values.length - 1, Math.round(fraction * (values.length - 1)))];
    }
}
