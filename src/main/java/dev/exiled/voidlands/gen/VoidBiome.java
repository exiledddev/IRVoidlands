package dev.exiled.voidlands.gen;

/**
 * The five Voidlands biomes and the shape of their terrain. Each one has a matching biome JSON in
 * the bundled datapack, registered as {@code voidlands:<id>}.
 */
public enum VoidBiome {
    //               id                  offset rolling dunes mountain plateauCap
    BLACKSTONE_PEAKS("blackstone_peaks",  4,     6,      0,    1.0,     0.0),
    BASALT_PLATEAUS("basalt_plateaus",    2,     3,      0,    0.55,    0.3),
    SOUL_SOIL_HILLS("soul_soil_hills",    0,     8,      0,    0.25,    0.0),
    SOUL_SAND_DUNES("soul_sand_dunes",   -5,     2,      5,    0.05,    0.0),
    MUD_MIRE("mud_mire",                 -8,     1.5,    0,    0.0,     0.0);

    static final VoidBiome[] VALUES = values();

    private final String id;
    /** Ground level relative to the base height, in blocks. */
    final double offset;
    /** Amplitude of the gentle rolling hills, in blocks. */
    final double rolling;
    /** Height of the long dune ridges, in blocks. */
    final double dunes;
    /** Fraction of the configured mountain height that this biome's patches rise to. */
    final double mountain;
    /** When above 0, mountains are flattened off at this fraction of the mountain height. */
    final double plateauCap;

    VoidBiome(String id, double offset, double rolling, double dunes, double mountain, double plateauCap) {
        this.id = id;
        this.offset = offset;
        this.rolling = rolling;
        this.dunes = dunes;
        this.mountain = mountain;
        this.plateauCap = plateauCap;
    }

    /** The biome's key in the {@code voidlands} namespace, also used for its config.yml weight. */
    public String id() {
        return id;
    }
}
