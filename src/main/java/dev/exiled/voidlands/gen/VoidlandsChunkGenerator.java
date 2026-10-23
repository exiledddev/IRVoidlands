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
 * Builds the Voidlands terrain: a jagged bedrock floor, blackstone rock, and 3 to 5 blocks of the
 * biome's surface material on top. Every vanilla generation step is switched off, so there are no
 * caves, ores, Nether features, structures or mobs.
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
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                VoidlandsTerrain.Column column = terrain.column(x, z);
                int top = clampHeight(column.height(), minY, maxY);
                int surfaceBottom = top - column.surfaceDepth() + 1;

                chunk.setRegion(localX, minY, localZ, localX + 1, top + 1, localZ + 1, Material.BLACKSTONE);
                if (column.surface() == VoidBiome.SOUL_SAND_DUNES) {
                    chunk.setRegion(localX, surfaceBottom - SOUL_SOIL_UNDER_SOUL_SAND, localZ,
                            localX + 1, surfaceBottom, localZ + 1, Material.SOUL_SOIL);
                }
                chunk.setRegion(localX, surfaceBottom, localZ, localX + 1, top + 1, localZ + 1,
                        surfaceBlock(column.surface()));
                for (int layer = 0; layer < VoidlandsTerrain.BEDROCK_LAYERS; layer++) {
                    if (terrain.bedrock(x, layer, z)) {
                        chunk.setBlock(localX, minY + layer, localZ, Material.BEDROCK);
                    }
                }
            }
        }
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        int height = terrain(worldInfo).column(x, z).height();
        return clampHeight(height, worldInfo.getMinHeight(), worldInfo.getMaxHeight()) + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
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

    /** Keeps the top block above the bedrock and leaves room to stand at the build limit. */
    private static int clampHeight(int height, int minY, int maxY) {
        return Math.clamp(height, minY + VoidlandsTerrain.BEDROCK_LAYERS + 3, maxY - 3);
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
}
