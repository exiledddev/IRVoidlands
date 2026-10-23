package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    @Test
    void differentSeedsGiveDifferentTerrain() {
        VoidlandsTerrain first = new VoidlandsTerrain(1, DEFAULTS);
        VoidlandsTerrain second = new VoidlandsTerrain(2, DEFAULTS);
        int differences = 0;
        for (int x = 0; x < 4000; x += 100) {
            if (first.column(x, x).height() != second.column(x, x).height()) {
                differences++;
            }
        }
        assertTrue(differences > 20, "only " + differences + " of 40 columns differ");
    }

    @Test
    void groundHasNoCliffs() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(12345, DEFAULTS);
        int size = 768;
        int[] previousRow = new int[size];
        for (int x = 0; x < size; x++) {
            int previous = Integer.MIN_VALUE;
            for (int z = 0; z < size; z++) {
                int height = terrain.column(x - size / 2, z - size / 2).height();
                if (previous != Integer.MIN_VALUE) {
                    assertTrue(Math.abs(height - previous) <= 3, "cliff at " + x + ", " + z);
                }
                if (x > 0) {
                    assertTrue(Math.abs(height - previousRow[z]) <= 3, "cliff at " + x + ", " + z);
                }
                previousRow[z] = height;
                previous = height;
            }
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
        Map<VoidBiome, Double> weights = new EnumMap<>(DEFAULTS.weights());
        weights.put(VoidBiome.MUD_MIRE, 0.0);
        VoidlandsSettings settings = new VoidlandsSettings(DEFAULTS.patchSize(), DEFAULTS.borderWarp(),
                DEFAULTS.borderDither(), weights, DEFAULTS.baseHeight(), DEFAULTS.blendWidth(),
                DEFAULTS.mountainHeight());
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
        int highestPeak = Integer.MIN_VALUE;
        int highestMud = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        for (int x = -6000; x < 6000; x += 8) {
            for (int z = -6000; z < 6000; z += 64) {
                VoidlandsTerrain.Column column = terrain.column(x, z);
                lowest = Math.min(lowest, column.height());
                if (column.biome() == VoidBiome.BLACKSTONE_PEAKS) {
                    highestPeak = Math.max(highestPeak, column.height());
                } else if (column.biome() == VoidBiome.MUD_MIRE) {
                    highestMud = Math.max(highestMud, column.height());
                }
            }
        }
        assertTrue(highestPeak > DEFAULTS.baseHeight() + 80, "highest blackstone peak is only " + highestPeak);
        assertTrue(highestPeak < 240, "blackstone peak at " + highestPeak + " is too close to the build limit");
        assertTrue(highestMud < highestPeak - 50, "mud reaches " + highestMud);
        assertTrue(lowest > 30, "ground dips to " + lowest);
    }

    @Test
    void bedrockFloorIsSolidAtTheBottomAndJaggedAbove() {
        VoidlandsTerrain terrain = new VoidlandsTerrain(3, DEFAULTS);
        int[] perLayer = new int[VoidlandsTerrain.BEDROCK_LAYERS + 1];
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                for (int layer = 0; layer <= VoidlandsTerrain.BEDROCK_LAYERS; layer++) {
                    if (terrain.bedrock(x, layer, z)) {
                        perLayer[layer]++;
                    }
                }
            }
        }
        assertEquals(10_000, perLayer[0]);
        for (int layer = 1; layer < VoidlandsTerrain.BEDROCK_LAYERS; layer++) {
            assertTrue(perLayer[layer] < perLayer[layer - 1], "layer " + layer + " isn't thinner than the one below");
        }
        assertEquals(0, perLayer[VoidlandsTerrain.BEDROCK_LAYERS]);
    }

    @Test
    void settingsRejectBadValues() {
        assertThrows(IllegalArgumentException.class, () -> new VoidlandsSettings(10, 72, 6,
                DEFAULTS.weights(), 60, 80, 100));
        Map<VoidBiome, Double> noWeights = new EnumMap<>(VoidBiome.class);
        for (VoidBiome biome : VoidBiome.values()) {
            noWeights.put(biome, 0.0);
        }
        assertThrows(IllegalArgumentException.class, () -> new VoidlandsSettings(384, 72, 6,
                noWeights, 60, 80, 100));
    }
}
