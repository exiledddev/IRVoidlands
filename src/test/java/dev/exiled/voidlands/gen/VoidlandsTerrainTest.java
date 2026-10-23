package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidlandsTerrainTest {

    private static final VoidlandsSettings DEFAULTS = VoidlandsSettings.defaults();

    @Test
    void sameSeedGivesSameTerrain() {
        VoidlandsTerrain first = new VoidlandsTerrain(42, DEFAULTS);
        VoidlandsTerrain second = new VoidlandsTerrain(42, DEFAULTS);
        for (int x = -2000; x <= 2000; x += 37) {
            for (int z = -2000; z <= 2000; z += 41) {
                assertEquals(first.column(x, z), second.column(x, z));
            }
        }
        assertEquals(first.spikes().spikesNear(-1000, -1000, 1000, 1000),
                second.spikes().spikesNear(-1000, -1000, 1000, 1000));
    }

    @Test
    void differentSeedsGiveDifferentTerrain() {
        VoidlandsTerrain first = new VoidlandsTerrain(1, DEFAULTS);
        VoidlandsTerrain second = new VoidlandsTerrain(2, DEFAULTS);
        int differences = 0;
        for (int x = 0; x < 8000; x += 100) {
            if (!first.column(x, x).equals(second.column(x, x))) {
                differences++;
            }
        }
        assertTrue(differences > 30, "only " + differences + " of 80 columns differ");
    }

    @Test
    void islandsCoverTheConfiguredShareOfTheWorld() {
        for (long seed : new long[] {3, 4}) {
            VoidlandsTerrain terrain = new VoidlandsTerrain(seed, DEFAULTS);
            int solid = 0;
            int samples = 0;
            for (int x = -6000; x < 6000; x += 24) {
                for (int z = -6000; z < 6000; z += 24) {
                    if (terrain.column(x, z).solid()) {
                        solid++;
                    }
                    samples++;
                }
            }
            double coverage = solid / (double) samples;
            assertTrue(Math.abs(coverage - DEFAULTS.islands().coverage()) < 0.07, "coverage " + coverage);
        }
    }

    @Test
    void islandsFloatInsideTheWorld() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(2024, DEFAULTS);
        for (int x = -4000; x < 4000; x += 7) {
            for (int z = -4000; z < 4000; z += 53) {
                VoidlandsTerrain.Column column = terrain.column(x, z);
                if (column.solid()) {
                    assertTrue(column.bottom() > 10, "underside reaches down to " + column.bottom());
                    assertTrue(column.top() < 240, "ground reaches up to " + column.top());
                }
            }
        }
    }

    @Test
    void spawnIsOnLand() {
        for (long seed = 0; seed < 20; seed++) {
            assertTrue(new VoidlandsTerrain(seed, DEFAULTS).column(0, 0).solid(), "no land at spawn for seed " + seed);
        }
    }

    @Test
    void islandGroundHasNoCliffs() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(12345, DEFAULTS);
        int size = 1024;
        VoidlandsTerrain.Column[] previousRow = new VoidlandsTerrain.Column[size];
        for (int x = 0; x < size; x++) {
            VoidlandsTerrain.Column previous = null;
            for (int z = 0; z < size; z++) {
                VoidlandsTerrain.Column column = terrain.column(x - size / 2, z - size / 2);
                if (column.solid()) {
                    assertGentle(column, previous, x, z);
                    assertGentle(column, previousRow[z], x, z);
                }
                previousRow[z] = column;
                previous = column;
            }
        }
    }

    private static void assertGentle(VoidlandsTerrain.Column column, VoidlandsTerrain.Column neighbour, int x, int z) {
        if (neighbour != null && neighbour.solid()) {
            assertTrue(Math.abs(column.top() - neighbour.top()) <= 6, "cliff at " + x + ", " + z);
        }
    }

    @Test
    void columnBiomeMatchesBiomeProvider() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(7, DEFAULTS);
        for (int x = -3000; x <= 3000; x += 13) {
            int z = x * 3 % 2000;
            assertEquals(terrain.biomeAt(x, z), terrain.column(x, z).biome());
        }
    }

    @Test
    void everyBiomeAppearsInSimilarAmounts() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(99, DEFAULTS);
        Map<VoidBiome, Integer> counts = new EnumMap<>(VoidBiome.class);
        int samples = 0;
        for (int x = -8000; x < 8000; x += 32) {
            for (int z = -8000; z < 8000; z += 32) {
                counts.merge(terrain.biomeAt(x, z), 1, Integer::sum);
                samples++;
            }
        }
        for (VoidBiome biome : VoidBiome.values()) {
            double share = counts.getOrDefault(biome, 0) / (double) samples;
            assertTrue(share > 0.12 && share < 0.28, biome + " covers " + share);
        }
    }

    @Test
    void zeroWeightBiomeNeverAppears() {
        Map<VoidBiome, Double> weights = new EnumMap<>(DEFAULTS.biomes().weights());
        weights.put(VoidBiome.MUD_MIRE, 0.0);
        VoidlandsSettings.Biomes biomes = new VoidlandsSettings.Biomes(DEFAULTS.biomes().patchSize(),
                DEFAULTS.biomes().borderWarp(), DEFAULTS.biomes().borderDither(), weights);
        VoidlandsSettings settings = new VoidlandsSettings(biomes, DEFAULTS.islands(), DEFAULTS.terrain(), DEFAULTS.spikes());
        VoidlandsTerrain terrain = new VoidlandsTerrain(5, settings);
        for (int x = -6000; x < 6000; x += 24) {
            for (int z = -6000; z < 6000; z += 96) {
                VoidlandsTerrain.Column column = terrain.column(x, z);
                assertNotEquals(VoidBiome.MUD_MIRE, column.biome());
                assertNotEquals(VoidBiome.MUD_MIRE, column.surface());
            }
        }
    }

    @Test
    void blackstoneRisesIntoBigMountainsAndMudStaysLow() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(2024, DEFAULTS);
        Map<VoidBiome, Integer> highest = new EnumMap<>(VoidBiome.class);
        for (int x = -6000; x < 6000; x += 8) {
            for (int z = -6000; z < 6000; z += 64) {
                VoidlandsTerrain.Column column = terrain.column(x, z);
                if (column.solid()) {
                    highest.merge(column.biome(), column.top(), Math::max);
                }
            }
        }
        int peak = highest.get(VoidBiome.BLACKSTONE_PEAKS);
        int island = DEFAULTS.islands().height();
        assertTrue(peak > island + 50, "highest blackstone peak is only " + peak);
        assertTrue(highest.get(VoidBiome.MUD_MIRE) < peak - 30, "mud reaches " + highest.get(VoidBiome.MUD_MIRE));
    }

    @Test
    void neverMoreThanOneSpikePerChunk() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(77, DEFAULTS);
        List<SpikeField.Spike> spikes = terrain.spikes().spikesNear(-4000, -4000, 4000, 4000);
        Set<Long> chunks = new HashSet<>();
        for (SpikeField.Spike spike : spikes) {
            long chunk = ((long) (spike.x() >> 4) << 32) | ((spike.z() >> 4) & 0xFFFFFFFFL);
            assertTrue(chunks.add(chunk), "two spikes in chunk of " + spike);
        }
        // Often, but rare: one per roughly 20 to 80 chunks of island.
        int islandChunks = 0;
        for (int x = -4000; x < 4000; x += 16) {
            for (int z = -4000; z < 4000; z += 16) {
                if (terrain.column(x + 8, z + 8).solid()) {
                    islandChunks++;
                }
            }
        }
        double chunksPerSpike = islandChunks / (double) spikes.size();
        assertTrue(chunksPerSpike > 20 && chunksPerSpike < 80, "one spike per " + chunksPerSpike + " island chunks");
    }

    @Test
    void spikesStandOnIslandGroundAndAvoidSpawn() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(78, DEFAULTS);
        for (SpikeField.Spike spike : terrain.spikes().spikesNear(-3000, -3000, 3000, 3000)) {
            VoidlandsTerrain.Column ground = terrain.column(spike.x(), spike.z());
            assertTrue(ground.solid(), "spike over the void: " + spike);
            assertTrue(spike.baseY() < ground.top(), "spike floats above the ground: " + spike);
            assertTrue(Math.hypot(spike.x(), spike.z()) >= 24, "spike at spawn: " + spike);
            assertTrue(spike.height() >= DEFAULTS.spikes().minHeight() && spike.height() <= DEFAULTS.spikes().maxHeight());
        }
    }

    @Test
    void spikesDrawTheSameWhenSplitAcrossChunks() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(79, DEFAULTS);
        SpikeField field = terrain.spikes();
        List<SpikeField.Spike> spikes = field.spikesNear(-2000, -2000, 2000, 2000);
        assertFalse(spikes.isEmpty());
        for (SpikeField.Spike spike : spikes.subList(0, Math.min(10, spikes.size()))) {
            int minX = spike.x() - 32;
            int minZ = spike.z() - 32;
            Map<List<Integer>, SpikeField.Block> whole = new HashMap<>();
            field.forEachBlock(spike, minX, minZ, minX + 63, minZ + 63, (x, y, z, block) -> whole.put(List.of(x, y, z), block));

            Map<List<Integer>, SpikeField.Block> pieces = new HashMap<>();
            for (int chunkX = Math.floorDiv(minX, 16); chunkX <= Math.floorDiv(minX + 63, 16); chunkX++) {
                for (int chunkZ = Math.floorDiv(minZ, 16); chunkZ <= Math.floorDiv(minZ + 63, 16); chunkZ++) {
                    int fromX = Math.max(minX, chunkX * 16);
                    int fromZ = Math.max(minZ, chunkZ * 16);
                    int toX = Math.min(minX + 63, chunkX * 16 + 15);
                    int toZ = Math.min(minZ + 63, chunkZ * 16 + 15);
                    assertTrue(field.spikesNear(fromX, fromZ, toX, toZ).contains(spike)
                            || noBlocksIn(field, spike, fromX, fromZ, toX, toZ), "chunk lost part of " + spike);
                    field.forEachBlock(spike, fromX, fromZ, toX, toZ, (x, y, z, block) -> pieces.put(List.of(x, y, z), block));
                }
            }
            assertTrue(whole.size() > 50, "suspiciously small spike: " + spike);
            assertEquals(whole, pieces);
        }
    }

    private static boolean noBlocksIn(SpikeField field, SpikeField.Spike spike, int minX, int minZ, int maxX, int maxZ) {
        boolean[] any = {false};
        field.forEachBlock(spike, minX, minZ, maxX, maxZ, (x, y, z, block) -> any[0] = true);
        return !any[0];
    }

    @Test
    void spikesCanBeTurnedOff() {
        VoidlandsSettings.Spikes off = new VoidlandsSettings.Spikes(false, 64, 1, 12, 32);
        VoidlandsSettings settings = new VoidlandsSettings(DEFAULTS.biomes(), DEFAULTS.islands(), DEFAULTS.terrain(), off);
        assertTrue(new VoidlandsTerrain(1, settings).spikes().spikesNear(-3000, -3000, 3000, 3000).isEmpty());
    }

    @Test
    void settingsRejectBadValues() {
        assertThrows(IllegalArgumentException.class, () -> new VoidlandsSettings.Biomes(10, 72, 6, DEFAULTS.biomes().weights()));
        Map<VoidBiome, Double> noWeights = new EnumMap<>(VoidBiome.class);
        for (VoidBiome biome : VoidBiome.values()) {
            noWeights.put(biome, 0.0);
        }
        assertThrows(IllegalArgumentException.class, () -> new VoidlandsSettings.Biomes(384, 72, 6, noWeights));
        assertThrows(IllegalArgumentException.class, () -> new VoidlandsSettings.Islands(0.95, 320, 110, 25, 45, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new VoidlandsSettings.Spikes(true, 30, 0.7, 12, 32));
        assertThrows(IllegalArgumentException.class, () -> new VoidlandsSettings.Spikes(true, 64, 0.7, 20, 10));
    }
}
