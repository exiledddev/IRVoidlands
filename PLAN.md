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
- **Nether rules:** made normal (water can be placed, beds don't explode). Lava still flows fast:
  `gameplay/fast_lava` can only be set on a dimension type, not on a biome.
- **Sound:** Soul Sand Valley ambience, no music.

The repo (`/home/user/IRVoidlands`, branch `claude/intelligent-einstein-k011hs`) is empty: no commits
and no code to reuse.

## Revision 2 (v1.1.0): floating islands and spikes

The user asked to turn the continuous terrain into floating islands and to add glowing spikes:
often, but never more than one per chunk. This supersedes the continuous ground and bedrock floor
described further down. The biome patches, biome profiles, mountains and datapack are unchanged.

- **Islands** (`gen/IslandShaper.java`): one island centre per jittered grid cell (cell =
  1.3 × `islands.size`).
  - Each island gets its own radius (0.45–1.0 × size/2, with 15% "giants" at 1.35×) and its own
    altitude (± `height-variation`). 20% of cells are empty.
  - The island field is `max(1 − distance/radius)` over the 3×3 nearby cells. Distances are measured
    on a warped point, which gives lobes and arms, and the coast is roughened with noise. The
    threshold is the sampled quantile for `islands.coverage`.
  - Merged islands blend their altitudes with weights `(field + 0.4)^4`, so the ground stays
    continuous.
  - Where the field is above the threshold, its distance above it is the "inland" value. It sets
    the underside depth (a cone up to `thickness`, plus drips), the rim drop at coasts, and the
    mountain fade-in `smoothstep(inland / 4)`. The fade-in keeps mountains from ending in coastal
    cliffs.
  - A spawn island is always forced at 0, 0. Islets (small rocks) come from a separate noise, only
    out in the void.
- **Spikes** (`gen/SpikeField.java`): at most one per `spikes.spacing` (64) cell, placed in the
  cell's middle half. Spikes are therefore at least 32 blocks apart, so never two in one chunk.
  - Each cell rolls `spikes.chance` × a per-biome factor, and only places a spike over island
    ground with solid ground under its foot.
  - Shape: a leaning, jagged cone 12–32 blocks tall. Blocks: obsidian, crying obsidian veins,
    pearlescent froglight veins and tip, and a sculk patch at the foot.
  - Each chunk works out every spike that reaches into it from the hashed cells and draws only its
    own part, so spikes are never cut at chunk borders. A unit test checks this.
- **Measured** (3 seeds, 2048² each): coverage ≈ 30%, ground y 25–208, adjacent island columns
  differ by at most 6 blocks (under 0.01% by more than 4), and about one spike per 40 island chunks.

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
- Base ground at y≈60. Each biome adds a profile: offset, rolling amplitude, dune height and
  mountain weight. Offset, rolling hills and dunes blend across borders over `blend-width` (≈80 blocks).
- Mountains start at 0 on a patch's border and rise towards its middle over twice the blend width
  (smoothstep). A blackstone patch rises as one broad massif with long foothills, never as a cliff
  at the border, and the ground stays continuous without blending mountain heights.
- Low-frequency swells (wavelength ≈260) give each massif several rounded summits of different
  heights. Ridges `(1 − sqrt(n² + 0.02))²` at wavelength ≈150 are added high up only, with rounded
  crests instead of knife edges. Small detail noise of ±1.5 blocks.
- The border warp keeps its fine layers weak (persistence 0.4). Strong fine warp squeezes space and
  made some flanks much steeper than others. Measured on 3 seeds: only 1–2% of mountain ground is
  steeper than 45°, the maximum is about 63°, and adjacent columns never differ by more than 2 blocks.
- Basalt plateaus: a smooth min caps the mountain, giving mesa-like flat tops. Dunes use stretched
  ridged noise. The mire is almost flat.
- Border dither: within `border-dither` (≈6) blocks of a border, a deterministic per-block hash mixes
  in the neighbour's surface block, so edges look ragged and natural.
- Dropped from the first draft: "steep-slope rock". Slopes in the soft biomes never get steep enough
  for it to show.

**Generator** (`VoidlandsChunkGenerator`):
- `generateNoise`: for each of the 16×16 columns, fill it with `ChunkData.setRegion`
  (blackstone → soul soil under soul sand → surface layers), then the jagged bedrock floor.
- All `shouldGenerate*()` methods return `false` (noise, surface, caves, decorations, mobs, structures),
  so no vanilla Nether terrain, fortresses or bastions.
- `getBaseHeight` returns the terrain height. `getFixedSpawnLocation` returns (0.5, h+1, 0.5).
- `getDefaultBiomeProvider` returns `VoidlandsBiomeProvider`.
- Thread safety: worldgen runs on several threads. The noise objects are immutable, built once per
  world seed (`worldInfo.getSeed()`) and cached. Nothing mutable is shared during generation.
- Noise is a small seeded simplex implementation in the plugin (`SimplexNoise`). The terrain code has no
  Bukkit imports, so it can be unit-tested and previewed without a server, and the jar has no
  shaded dependencies.

**Biome provider** (`VoidlandsBiomeProvider`): `getBiome` → `layout.biomeAt(x, z)` → cached Bukkit
`Biome`. The 5 biomes are looked up once from `RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)`.
`getBiomes` returns all 5. This also makes `/locate biome voidlands:...` work.

## Project layout (to create)

```
build.gradle.kts / settings.gradle.kts / gradlew (Gradle wrapper)
  - Java 21 toolchain, compileOnly io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT
  - xyz.jpenilla.run-paper 3.1.0 (needs Gradle 9.7+, wrapper pinned to 9.7.1) for a local test server
.github/workflows/build.yml       builds the jar and uploads it as an artifact
README.md                         install, /mv create command, teleport commands, how to tune
src/main/resources/
  paper-plugin.yml                name: Voidlands, bootstrapper + main, api-version '1.21.11',
                                  dependencies.server.Multiverse-Core: { load: AFTER, required: false }
  config.yml                      patch-size, border-warp, border-dither, biome weights,
                                  base-height, blend-width, mountain-height
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
