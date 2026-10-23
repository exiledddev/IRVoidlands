package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Builds the Voidlands: floating islands of blackstone with 3 to 5 blocks of the biome's surface
 * material on top, open void between them, and the occasional glowing spike. Every vanilla
 * generation step is switched off, so there are no caves, ores, Nether features, structures or mobs.
 */
public final class VoidlandsChunkGenerator extends ChunkGenerator {

    private static final int SOUL_SOIL_UNDER_SOUL_SAND = 2;

    private final VoidlandsSettings settings;
    private final Logger logger;
    private final VoidlandsBiomeProvider biomeProvider;
    private volatile VoidlandsTerrain terrain;

    public VoidlandsChunkGenerator(VoidlandsSettings settings, Map<VoidBiome, Biome> biomes, Logger logger) {
        this.settings = settings;
        this.logger = logger;
        this.biomeProvider = new VoidlandsBiomeProvider(this, biomes);
    }

    /** The terrain for this generator's world, built on first use because the seed isn't known before. */
    VoidlandsTerrain terrain(WorldInfo world) {
        VoidlandsTerrain current = terrain;
        if (current == null) {
            synchronized (this) {
                current = terrain;
                if (current == null) {
                    if (world.getEnvironment() != World.Environment.NETHER) {
                        logger.warning("World '" + world.getName() + "' uses the " + world.getEnvironment()
                                + " environment. Create Voidlands worlds with the nether environment"
                                + " (/mv create <name> nether -g Voidlands) to get the Soul Sand Valley sky.");
                    }
                    current = new VoidlandsTerrain(world.getSeed(), settings);
                    terrain = current;
                }
            }
        }
        return current;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunk) {
        VoidlandsTerrain terrain = terrain(worldInfo);
        int minY = chunk.getMinHeight();
        int maxY = chunk.getMaxHeight();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                VoidlandsTerrain.Column column = terrain.column(baseX + localX, baseZ + localZ);
                if (!column.solid()) {
                    continue;
                }
                int top = Math.min(column.top(), maxY - 1);
                int bottom = Math.max(column.bottom(), minY);
                if (top < bottom) {
                    continue;
                }
                int surfaceBottom = Math.max(bottom, top - column.surfaceDepth() + 1);

                chunk.setRegion(localX, bottom, localZ, localX + 1, top + 1, localZ + 1, Material.BLACKSTONE);
                if (column.surface() == VoidBiome.SOUL_SAND_DUNES) {
                    chunk.setRegion(localX, Math.max(bottom, surfaceBottom - SOUL_SOIL_UNDER_SOUL_SAND), localZ,
                            localX + 1, surfaceBottom, localZ + 1, Material.SOUL_SOIL);
                }
                chunk.setRegion(localX, surfaceBottom, localZ, localX + 1, top + 1, localZ + 1,
                        surfaceBlock(column.surface()));
            }
        }

        SpikeField spikes = terrain.spikes();
        int maxX = baseX + 15;
        int maxZ = baseZ + 15;
        for (SpikeField.Spike spike : spikes.spikesNear(baseX, baseZ, maxX, maxZ)) {
            spikes.forEachBlock(spike, baseX, baseZ, maxX, maxZ, (x, y, z, block) -> {
                if (y >= minY && y < maxY) {
                    chunk.setBlock(x - baseX, y, z - baseZ, spikeBlock(block));
                }
            });
        }
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        VoidlandsTerrain.Column column = terrain(worldInfo).column(x, z);
        if (!column.solid()) {
            return worldInfo.getMinHeight();
        }
        return Math.min(column.top(), worldInfo.getMaxHeight() - 1) + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        // The island noise always puts land under the origin, so this is solid ground.
        return new Location(world, 0.5, getBaseHeight(world, random, 0, 0, HeightMap.WORLD_SURFACE), 0.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return biomeProvider;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    private static Material surfaceBlock(VoidBiome biome) {
        return switch (biome) {
            case BLACKSTONE_PEAKS -> Material.BLACKSTONE;
            case BASALT_PLATEAUS -> Material.SMOOTH_BASALT;
            case SOUL_SOIL_HILLS -> Material.SOUL_SOIL;
            case SOUL_SAND_DUNES -> Material.SOUL_SAND;
            case MUD_MIRE -> Material.MUD;
        };
    }

    private static Material spikeBlock(SpikeField.Block block) {
        return switch (block) {
            case OBSIDIAN -> Material.OBSIDIAN;
            case CRYING_OBSIDIAN -> Material.CRYING_OBSIDIAN;
            case GLOW -> Material.PEARLESCENT_FROGLIGHT;
            case SCULK -> Material.SCULK;
        };
    }
}
