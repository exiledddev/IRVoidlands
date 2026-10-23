package dev.exiled.voidlands.gen;

import java.util.SplittableRandom;

/**
 * Seeded 2D simplex noise, after Stefan Gustavson's public-domain reference implementation.
 * Immutable once built, so one instance can be shared by all world generation threads.
 */
final class SimplexNoise {

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double[] GRAD_X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0};
    private static final double[] GRAD_Y = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1};

    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];

    SimplexNoise(long seed) {
        short[] p = new short[256];
        for (short i = 0; i < 256; i++) {
            p[i] = i;
        }
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            short swap = p[i];
            p[i] = p[j];
            p[j] = swap;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = (short) (perm[i] % 12);
        }
    }

    /** Noise at a point, roughly in [-1, 1]. */
    double noise(double x, double y) {
        double s = (x + y) * F2;
        int i = floor(x + s);
        int j = floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);

        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;
        return 70.0 * (corner(permMod12[ii + perm[jj]], x0, y0)
                + corner(permMod12[ii + i1 + perm[jj + j1]], x1, y1)
                + corner(permMod12[ii + 1 + perm[jj + 1]], x2, y2));
    }

    /** Sum of {@code octaves} layers of noise, each twice as fine and half as strong, kept in [-1, 1]. */
    double fbm(double x, double y, int octaves) {
        return fbm(x, y, octaves, 0.5);
    }

    /** Like {@link #fbm(double, double, int)}, with each layer {@code persistence} times as strong as the last. */
    double fbm(double x, double y, int octaves, double persistence) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        for (int octave = 0; octave < octaves; octave++) {
            // Shift each octave so the layers don't all line up at the origin.
            sum += amplitude * noise(x + octave * 17.31, y - octave * 23.17);
            total += amplitude;
            amplitude *= persistence;
            x *= 2;
            y *= 2;
        }
        return sum / total;
    }

    private static double corner(int gradient, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0) {
            return 0;
        }
        t *= t;
        return t * t * (GRAD_X[gradient] * x + GRAD_Y[gradient] * y);
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
