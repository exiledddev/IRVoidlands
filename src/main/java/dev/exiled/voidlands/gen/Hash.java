package dev.exiled.voidlands.gen;

/**
 * Deterministic coordinate hashing (SplitMix64 finaliser). Everything random in the Voidlands goes
 * through this, so a chunk always regenerates exactly the same way for the same seed.
 */
final class Hash {

    private Hash() {
    }

    static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h = mix(h ^ (a * 0xC2B2AE3D27D4EB4FL));
        return mix(h ^ (b * 0x165667B19E3779F9L));
    }

    /** Maps a hash to a uniform double in [0, 1). */
    static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
