# Voidlands — custom world generator for Paper/Purpur 1.21.11

## Context

The story server needs a new "dimension" called **Voidlands**: terrain made of big patches of
blackstone, mud, smooth basalt, soul sand and soul soil, with the Soul Sand Valley sky (teal Nether fog)
and a few black smoke particles drifting around. It must be creatable as a **Multiverse-Core world**.
No portals (staff teleport players), no structures, no mobs.

Choices already made with the user:
- **Terrain:** each biome has its own character. Mountains should be broad and massive ("fatter"),
  with wide foothills and rounded tops, not thin vanilla spikes.
- **Mobs:** none.
- **Nether rules:** made normal (water can be placed, beds don't explode, lava flows at normal speed).
- **Sound:** Soul Sand Valley ambience, no music.

The repo (`/home/user/IRVoidlands`, branch `claude/intelligent-einstein-k011hs`) is empty: no commits
and no code to reuse.

## Decision: Paper plugin with a small datapack inside it

| | Datapack only | Plugin only | **Plugin + bundled datapack** |
|---|---|---|---|
| `/mv create` support | ✗ datapack dimensions are fixed level stems in the main world. MV can't create them | ✓ | ✓ |
| Soul Sand Valley sky | ✓ | ✓ (Nether environment) | ✓ |
| Black smoke particles | ✓ client-side biome particles | ✗ server would have to send particle packets to every player | ✓ client-side, **no server cost** |
| Terrain authoring | hard: density-function JSON | easy: Java | easy: Java |

Plugins can't register new biomes through the Paper API, and datapacks can't plug into Multiverse.
So we use both:

- **Plugin:** the `ChunkGenerator` (terrain) and a `BiomeProvider` (where the patches go). Multiverse
  uses it with `-g Voidlands`.
- **Datapack inside the jar:** defines only the 5 custom biomes (no dimension). A Paper bootstrapper
  loads it at startup through `LifecycleEvents.DATAPACK_DISCOVERY`.
- **The world is created with the `nether` environment.** The vanilla `the_nether` dimension type
  (checked in the 1.21.11 data) has `skybox: "none"` and fog from 10 to 96 blocks. That gives the
  Nether-style "sky" that is just fog. Our biomes then set the fog colour to Soul Sand Valley's
  `#1b4745`. In 1.21.11, biome environment attributes override dimension attributes, so our biomes
  can also switch off the Nether rules.

## How the 1.21.11 format works (checked against vanilla 1.21.11 data)

1.21.11 moved biome visuals and sounds into `attributes` ("environment attributes"). The data pack
format is **94.1**. Template for each biome JSON
(`src/main/resources/voidlands_pack/data/voidlands/worldgen/biome/<name>.json`):

```json
{
  "has_precipitation": false, "temperature": 2.0, "downfall": 0.0,
  "effects": { "water_color": "#3f76e4" },
  "attributes": {
    "minecraft:visual/fog_color": "#1b4745",
    "minecraft:visual/ambient_particles": [
      { "particle": { "type": "minecraft:large_smoke" }, "probability": 0.0015 },
      { "particle": { "type": "minecraft:smoke" },       "probability": 0.0025 }
    ],
    "minecraft:audio/ambient_sounds": { /* copied verbatim from vanilla soul_sand_valley */ },
    "minecraft:audio/background_music": {},
    "minecraft:gameplay/water_evaporates": false,
    "minecraft:gameplay/fast_lava": false,
    "minecraft:gameplay/bed_rule": { "can_sleep": "never", "can_set_spawn": "never", "explodes": false }
  },
  "spawners": { "ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [],
                "underground_water_creature": [], "water_ambient": [], "water_creature": [] },
  "spawn_costs": {}, "carvers": [], "features": []
}
```

- **Particles:** the client spawns them itself around each player, so they cost the server nothing.
  `smoke` and `large_smoke` render near-black. The starting probabilities produce far fewer particles
  than Soul Sand Valley's ash (0.00625) and will be tuned in game. If smoke reads wrong, alternatives
  are `minecraft:ash` (dark grey drifting flakes) or `minecraft:squid_ink`.
- **No music:** set `background_music` to `{}`. If music still plays, add `"minecraft:audio/music_volume": 0.0`.
- **Respawn anchors:** left at the Nether default.

## The 5 biomes

| Biome key | Surface block | Character | Mountain weight |
|---|---|---|---|
| `voidlands:blackstone_peaks` | blackstone | massive, broad mountains with soft ridges | 1.0 |
| `voidlands:basalt_plateaus` | smooth_basalt | wide flat-topped highlands with sloped sides | 0.55 |
| `voidlands:soul_soil_hills` | soul_soil | gentle rolling hills | 0.25 |
| `voidlands:soul_sand_dunes` | soul_sand (soul_soil under it) | low dunes and valleys | 0.05 |
| `voidlands:mud_mire` | mud | flat, low basins | 0.0 |

Below the surface layer (3–5 blocks deep, varied with noise) is a blackstone rock layer, then a jagged
bedrock floor (y 0–4) like the Nether's. There is no ceiling.

## Generation design

**Patch layout** (`BiomeLayout`): warped Voronoi cells.
- Jittered grid cells about `patch-size` (384) blocks across. Each cell's biome is picked by a seeded,
  weighted hash. Neighbouring cells with the same biome merge into larger patches.
- The input coordinates are warped with simplex noise (`border-warp` ≈ 72) so borders look organic, not polygonal.
- It also returns the distance to the nearest cell with a *different* biome, using the exact bisector
  formula `(d2² − d1²) / (2·|c2 − c1|)`. Height blending and border dithering both use this distance.
- The same function at block resolution feeds both the surface blocks and the `BiomeProvider`, so the
  blocks match the F3 biome.

**Heights** (`TerrainShaper`). This is where the "fat mountains" come from:
- Base ground at y≈60. Each biome adds a profile: offset, rolling amplitude, and mountain weight.
- Profiles blend across borders over a **wide** `blend-width` (≈80 blocks, smoothstep). A blackstone
  patch rises as one broad massif with long foothills instead of a cliff at the border.
- Massif shape: low-frequency fBm (wavelength ≈512) → `pow(smoothstep(0.25, 0.85, n), 1.4)` → times
  `mountain-height` (≈100). This makes big rounded bodies with wide bases.
- Ridges: `(1 − |simplex|)²` at wavelength ≈160. Their amplitude scales with the massif value, so ridge
  lines only show on upper slopes and never form spikes.
- Small detail noise of ±3 blocks or less keeps slopes smooth.
- Basalt plateaus: a soft cap (smooth min) gives mesa-like flat tops. Dunes use stretched, wave-like
  noise. The mire is almost flat.
- Steep-slope rock: where the slope between neighbouring columns is above ~1.3, the surface becomes
  blackstone in non-rock biomes. This looks like exposed rock. It can be turned off in the config.
- Border dither: within `border-dither` (≈6) blocks of a border, a deterministic per-block hash mixes
  in the neighbour's surface block, so edges look ragged and natural.

**Generator** (`VoidlandsChunkGenerator`):
- `generateNoise`: compute an 18×18 height grid (16×16 plus a 1-block border for the slope check), then
  fill each column with `ChunkData.setRegion` (bedrock → blackstone → surface layers).
- All `shouldGenerate*()` methods return `false` (noise, surface, caves, decorations, mobs, structures),
  so no vanilla Nether terrain, fortresses or bastions.
- `getBaseHeight` returns the terrain height. `getFixedSpawnLocation` returns (0.5, h+1, 0.5).
- `getDefaultBiomeProvider` returns `VoidlandsBiomeProvider`.
- Thread safety: worldgen runs on several threads. The noise objects are immutable, built once per
  world seed (`worldInfo.getSeed()`) and cached. Nothing mutable is shared during generation.
- Noise uses Bukkit's built-in `org.bukkit.util.noise.SimplexNoiseGenerator`, so the jar has no
  shaded dependencies.

**Biome provider** (`VoidlandsBiomeProvider`): `getBiome` → `layout.biomeAt(x, z)` → cached Bukkit
`Biome`. The 5 biomes are looked up once from `RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)`.
`getBiomes` returns all 5. This also makes `/locate biome voidlands:...` work.

## Project layout (to create)

```
build.gradle.kts / settings.gradle.kts / gradlew (Gradle wrapper)
  - Java 21 toolchain, compileOnly io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT
  - xyz.jpenilla.run-paper for a local test server (Paper 1.21.11 + Multiverse-Core 5 via downloadPlugins)
.github/workflows/build.yml       builds the jar and uploads it as an artifact
README.md                         install, /mv create command, teleport commands, how to tune
src/main/resources/
  paper-plugin.yml                name: Voidlands, bootstrapper + main, api-version '1.21.11',
                                  dependencies.server.Multiverse-Core: { load: AFTER, required: false }
  config.yml                      patch-size, border-warp, border-dither, biome weights,
                                  base-height, blend-width, mountain-height, steep-slope-rock
  voidlands_pack/pack.mcmeta      format 94.1
  voidlands_pack/data/voidlands/worldgen/biome/*.json   (5 files, template above)
src/main/java/dev/exiled/voidlands/      (package name is a placeholder)
  VoidlandsBootstrap.java         DATAPACK_DISCOVERY: copy voidlands_pack from the jar to
                                  plugins/Voidlands/datapack/ if it isn't there, then discoverPack(path,
                                  "voidlands") with auto-enable, so admins can edit the JSONs and restart
  VoidlandsPlugin.java            onEnable: load the settings record, resolve the biomes (if they're
                                  missing, log a clear error saying the datapack didn't load);
                                  getDefaultWorldGenerator(world, id) → new VoidlandsChunkGenerator
  gen/VoidBiome.java              enum: key, surface/under/rock Material, height profile, weight
  gen/BiomeLayout.java            warped Voronoi → (biome, edge distance, neighbour biome)
  gen/TerrainShaper.java          blended heights, massif/ridge shaping, plateau cap
  gen/VoidlandsChunkGenerator.java
  gen/VoidlandsBiomeProvider.java
  VoidlandsSettings.java          record parsed from config.yml
```

The generator logs one warning if the world's environment isn't `NETHER`, telling the admin to create
it with `nether` to get the Soul Sand Valley sky.

## Usage (for the README)

```
/mv generators                                   # Voidlands should be listed
/mv create voidlands nether -g Voidlands         # or: --generator Voidlands
/mv tp <player> voidlands
/execute in minecraft:voidlands run tp <player> 0 100 0   # for command blocks and story scripts
```

The `load: AFTER` entry makes the plugin enable before Multiverse, so Multiverse finds the generator
when it reloads its worlds after a restart.

## Verification

1. Run `./gradlew build` and check that `build/libs/IRVoidlands-*.jar` is produced. **This container's
   network policy blocks `repo.papermc.io`, where the Paper API is hosted, so the build can't run here
   unless that host is added under Allowed domains.** Otherwise the GitHub Actions workflow builds the jar.
2. Start a test server: Paper or Purpur 1.21.11 with Multiverse-Core 5 (`./gradlew runServer`, or a
   real server).
   - The startup log shows the Voidlands datapack being discovered.
   - `/datapack list enabled` includes it.
   - `/mv generators` lists `Voidlands`.
3. Run `/mv create voidlands nether -g Voidlands`, then `/mv tp voidlands`. Check:
   - F3 shows `voidlands:*` biomes, and there are large patches of all 5 blocks with ragged borders.
   - Blackstone patches rise into broad massifs with long foothills.
   - Teal Soul Sand Valley fog and no sky, sun or stars. A few black smoke particles drift by.
   - Soul Sand Valley ambient sounds play, with no music.
   - No mobs spawn. A water bucket places water. A bed doesn't explode.
   - `/locate biome voidlands:mud_mire` finds a patch.
4. Restart the server. The world reloads through Multiverse with no "generator not found / plugin not
   enabled" warnings, and new chunks join old ones without seams.
5. Optional: pregenerate about 1000×1000 blocks with Chunky and confirm TPS stays healthy (spark).

## Notes and risks

- Changing `config.yml` terrain settings or removing the plugin after the world exists leaves visible
  seams in newly generated chunks. Only tune on a throwaway world.
- Mojang marks environment attributes as experimental, so the biome JSONs may need small edits when
  updating Minecraft. All of that is contained in the 5 JSON files.
- Nether fog ends at 96 blocks, so big mountains appear as silhouettes when you get near them. This is
  part of the Soul Sand Valley look. If wanted, it can be widened per biome with `visual/fog_end_distance`.
- The exact Paper `DatapackRegistrar` configurer method names, such as auto-enable, will be checked
  against the 1.21.11 API while implementing.
