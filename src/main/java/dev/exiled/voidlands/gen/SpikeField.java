package dev.exiled.voidlands.gen;

import dev.exiled.voidlands.VoidlandsSettings;

import java.util.ArrayList;
import java.util.List;

import static dev.exiled.voidlands.gen.BiomeLayout.smoothstep;

/**
 * Places the glowing "corruption" spikes. The world is split into square cells and each cell may
 * hold one spike, placed in the cell's middle half. That keeps any two spikes at least half a cell
 * apart, and with cells of 46 blocks or more, no chunk ever holds two of them.
 *
 * <p>Everything about a spike comes from hashing its cell, so every chunk it overlaps works out the
 * same spike independently and draws only its own part of it. Spikes are never cut off at chunk
 * borders and can be any size.
 */
public final class SpikeField {

    public enum Block { OBSIDIAN, CRYING_OBSIDIAN, GLOW, SCULK }

    @FunctionalInterface
    public interface BlockSink {
        void place(int x, int y, int z, Block block);
    }

    @FunctionalInterface
    interface ColumnSource {
        VoidlandsTerrain.Column at(int x, int z);
    }

    /**
     * @param x      block x of the spike's centre
     * @param z      block z of the spike's centre
     * @param baseY  lowest y of the spike, a little below the ground
     * @param height how tall the spike is from its base, in blocks
     * @param radius radius at the base, in blocks
     * @param leanX  sideways drift per block of height along x
     * @param leanZ  sideways drift per block of height along z
     */
    public record Spike(int x, int z, int baseY, int height, double radius, double leanX, double leanZ) {
    }

    private static final int SALT_ROLL = 21;
    private static final int SALT_X = 22;
    private static final int SALT_Z = 23;
    private static final int SALT_SHAPE = 24;
    private static final int SALT_LEAN = 25;
    private static final int SALT_LEAN_AMOUNT = 26;
    private static final int SALT_SCULK = 27;
    private static final double MAX_LEAN = 0.2;
    private static final double JAG = 0.5;
    private static final double SCULK_REACH = 3.5;
    /** No spikes this close to the world spawn, so players don't arrive inside one. */
    private static final double SPAWN_CLEARANCE = 24;

    private final long seed;
    private final VoidlandsSettings.Spikes config;
    private final ColumnSource columns;
    private final SimplexNoise veins;
    private final SimplexNoise facets;
    private final double maxReach;

    SpikeField(long seed, VoidlandsSettings.Spikes config, ColumnSource columns) {
        this.seed = seed;
        this.config = config;
        this.columns = columns;
        this.veins = new SimplexNoise(seed + 201);
        this.facets = new SimplexNoise(seed + 202);
        double maxRadius = baseRadius(config.maxHeight()) + JAG;
        this.maxReach = maxRadius + Math.max(MAX_LEAN * config.maxHeight(), SCULK_REACH) + 1;
    }

    /** Every spike that could place blocks inside the given block area (inclusive bounds). */
    public List<Spike> spikesNear(int minX, int minZ, int maxX, int maxZ) {
        List<Spike> found = new ArrayList<>(2);
        if (!config.enabled()) {
            return found;
        }
        int reach = (int) Math.ceil(maxReach);
        int fromX = cell(minX - reach);
        int toX = cell(maxX + reach);
        int fromZ = cell(minZ - reach);
        int toZ = cell(maxZ + reach);
        for (int cx = fromX; cx <= toX; cx++) {
            for (int cz = fromZ; cz <= toZ; cz++) {
                Spike spike = spikeInCell(cx, cz);
                if (spike != null && spike.x() + reach >= minX && spike.x() - reach <= maxX
                        && spike.z() + reach >= minZ && spike.z() - reach <= maxZ) {
                    found.add(spike);
                }
            }
        }
        return found;
    }

    /** Emits every block of {@code spike} that falls inside the given block area (inclusive bounds). */
    public void forEachBlock(Spike spike, int minX, int minZ, int maxX, int maxZ, BlockSink sink) {
        placeSculk(spike, minX, minZ, maxX, maxZ, sink);
        for (int dy = 0; dy <= spike.height(); dy++) {
            int y = spike.baseY() + dy;
            double t = dy / (double) spike.height();
            double centreX = spike.x() + 0.5 + spike.leanX() * dy;
            double centreZ = spike.z() + 0.5 + spike.leanZ() * dy;
            double radius = spike.radius() * Math.pow(1 - t, 0.75);
            int bound = (int) Math.ceil(radius + JAG);
            int fromX = Math.max(minX, (int) Math.floor(centreX) - bound);
            int toX = Math.min(maxX, (int) Math.floor(centreX) + bound);
            int fromZ = Math.max(minZ, (int) Math.floor(centreZ) - bound);
            int toZ = Math.min(maxZ, (int) Math.floor(centreZ) + bound);
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    Block block = blockAt(spike, x, z, dy, centreX, centreZ, radius);
                    if (block != null) {
                        sink.place(x, y, z, block);
                    }
                }
            }
        }
    }

    private Block blockAt(Spike spike, int x, int z, int dy, double centreX, double centreZ, double radius) {
        double dx = x + 0.5 - centreX;
        double dz = z + 0.5 - centreZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        // Direction around the spike, as a point on the unit circle so the noise has no seam.
        double ux = distance > 1e-6 ? dx / distance : 1;
        double uz = distance > 1e-6 ? dz / distance : 0;
        double offset = spike.x() * 0.37 + spike.z() * 0.71;

        double edge = radius + JAG * facets.noise(ux * 1.3 + dy * 0.16 + offset, uz * 1.3 - dy * 0.11);
        boolean core = distance < 0.75;
        if (distance > edge && !core) {
            return null;
        }
        if (dy >= spike.height() - 1) {
            return Block.GLOW;
        }
        double vein = veins.noise(ux * 1.5 + dy * 0.12 + offset, uz * 1.5 - dy * 0.09 - offset);
        if (vein > 0.55) {
            return Block.GLOW;
        }
        return vein > 0.05 ? Block.CRYING_OBSIDIAN : Block.OBSIDIAN;
    }

    /** A ragged patch of sculk spreading over the ground around the spike's foot. */
    private void placeSculk(Spike spike, int minX, int minZ, int maxX, int maxZ, BlockSink sink) {
        double reach = spike.radius() + SCULK_REACH;
        int bound = (int) Math.ceil(reach);
        for (int x = Math.max(minX, spike.x() - bound); x <= Math.min(maxX, spike.x() + bound); x++) {
            for (int z = Math.max(minZ, spike.z() - bound); z <= Math.min(maxZ, spike.z() + bound); z++) {
                double dx = x - spike.x();
                double dz = z - spike.z();
                double fade = smoothstep((Math.sqrt(dx * dx + dz * dz) - spike.radius()) / SCULK_REACH);
                if (Hash.unit(Hash.hash(seed, x, z, SALT_SCULK)) < 1 - fade) {
                    VoidlandsTerrain.Column column = columns.at(x, z);
                    if (column.solid() && Math.abs(column.top() - spike.baseY()) <= 6) {
                        sink.place(x, column.top(), z, Block.SCULK);
                    }
                }
            }
        }
    }

    private Spike spikeInCell(int cx, int cz) {
        double roll = Hash.unit(Hash.hash(seed, cx, cz, SALT_ROLL));
        if (roll >= config.chance()) {
            return null;
        }
        double spacing = config.spacing();
        int x = (int) Math.floor((cx + 0.25 + 0.5 * Hash.unit(Hash.hash(seed, cx, cz, SALT_X))) * spacing);
        int z = (int) Math.floor((cz + 0.25 + 0.5 * Hash.unit(Hash.hash(seed, cx, cz, SALT_Z))) * spacing);
        if ((double) x * x + (double) z * z < SPAWN_CLEARANCE * SPAWN_CLEARANCE) {
            return null;
        }
        VoidlandsTerrain.Column centre = columns.at(x, z);
        if (!centre.solid() || roll >= config.chance() * centre.biome().spikes) {
            return null;
        }

        long shape = Hash.hash(seed, cx, cz, SALT_SHAPE);
        int height = config.minHeight() + (int) (Hash.unit(shape) * (config.maxHeight() - config.minHeight() + 1));
        double radius = baseRadius(height);

        // Stand on the lowest ground under the foot, sunk in a little, so it never floats on a slope.
        int ground = centre.top();
        int foot = (int) Math.ceil(radius) + 1;
        int[][] around = {{foot, 0}, {-foot, 0}, {0, foot}, {0, -foot}};
        for (int[] step : around) {
            VoidlandsTerrain.Column column = columns.at(x + step[0], z + step[1]);
            if (!column.solid()) {
                return null;
            }
            ground = Math.min(ground, column.top());
        }

        double angle = Hash.unit(Hash.hash(seed, cx, cz, SALT_LEAN)) * Math.PI * 2;
        double amount = MAX_LEAN * Hash.unit(Hash.hash(seed, cx, cz, SALT_LEAN_AMOUNT));
        return new Spike(x, z, ground - 2, height, radius, Math.cos(angle) * amount, Math.sin(angle) * amount);
    }

    private int cell(int block) {
        return (int) Math.floor(block / config.spacing());
    }

    private static double baseRadius(int height) {
        return 1.6 + height / 10.0;
    }
}
