package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;

import static dev.exiled.voidlands.gen.BiomeLayout.smoothstep;

/**
 * Everything about a Voidlands world's shape for one seed: which biome is where, where the floating
 * islands are and how high their ground sits, and where the spikes stand. It has no Bukkit
 * dependencies and is immutable, so it is safe to share between world generation threads.
 */
public final class VoidlandsTerrain {

    private static final int SALT_DITHER = 11;

    /**
     * One block column. Only columns over an island are solid; the rest is open void.
     *
     * @param biome        the F3 biome
     * @param surface      biome whose blocks cover this column (differs from {@code biome} near borders)
     * @param top          y of the top block
     * @param bottom       y of the lowest block of the island's underside
     * @param surfaceDepth how many blocks of surface material sit on the rock
     */
    public record Column(VoidBiome biome, VoidBiome surface, int top, int bottom, int surfaceDepth) {

        public boolean solid() {
            return top >= bottom;
        }
    }

    private final long seed;
    private final double borderDither;
    private final BiomeLayout layout;
    private final TerrainShaper shaper;
    private final IslandShaper islands;
    private final SpikeField spikes;

    public VoidlandsTerrain(long seed, VoidlandsSettings settings) {
        this.seed = seed;
        this.borderDither = settings.biomes().borderDither();
        this.layout = new BiomeLayout(seed, settings);
        this.shaper = new TerrainShaper(seed, settings);
        this.islands = new IslandShaper(seed, settings);
        this.spikes = new SpikeField(seed, settings.spikes(), this::column);
    }

    public VoidBiome biomeAt(int x, int z) {
        return layout.biomeAt(x, z);
    }

    public SpikeField spikes() {
        return spikes;
    }

    public Column column(int x, int z) {
        BiomeLayout.Sample sample = layout.sample(x, z);
        VoidBiome surface = sample.biome();
        if (sample.neighbour() != null && sample.border() < borderDither) {
            // Mix in the neighbour's blocks near the border: half and half right on it, fading out.
            double chance = 0.5 * (1.0 - sample.border() / borderDither);
            if (Hash.unit(Hash.hash(seed, x, z, SALT_DITHER)) < chance) {
                surface = sample.neighbour();
            }
        }
        int surfaceDepth = shaper.surfaceDepth(x, z);

        IslandShaper.Sample island = islands.sample(x, z);
        double inland = island.inland();
        if (inland > 0) {
            double ground = island.altitude() + shaper.groundOffset(sample);
            // Mountains fade in over a wide band from the coast, so they never end in a cliff at the edge.
            double top = ground + shaper.relief(sample, x, z, smoothstep(inland / 4)) - islands.rimDrop(inland);
            double bottom = ground - islands.depthBelow(x, z, inland);
            return new Column(sample.biome(), surface, floor(top), floor(bottom), surfaceDepth);
        }

        double islet = islands.islet(x, z, inland);
        if (islet > 0) {
            double core = Math.min(1, islet);
            double ground = islands.isletAltitude(x, z);
            double top = ground + 2 * core;
            double bottom = ground - 1 - 9 * Math.pow(core, 0.8);
            return new Column(sample.biome(), surface, floor(top), floor(bottom), Math.min(surfaceDepth, 2));
        }
        return new Column(sample.biome(), surface, Integer.MIN_VALUE, Integer.MAX_VALUE, surfaceDepth);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
