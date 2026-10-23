# Voidlands

A world generator for Paper / Purpur **1.21.11**. It makes the Voidlands, a dimension for story
scenes: floating islands of blackstone, mud, smooth basalt, soul sand and soul soil hanging in a
teal Soul Sand Valley void, with glowing corrupted spikes and black smoke drifting through the air.

- No portals, vanilla structures, caves, ores or mobs. Staff teleport players in and out.
- Works with Multiverse-Core: `/mv create voidlands nether -g Voidlands`.
- The particles are spawned by each player's game, so they cost the server nothing.

## Biomes

| Biome | Ground | Terrain |
|---|---|---|
| `voidlands:blackstone_peaks` | blackstone | broad, massive mountains with long foothills and soft ridges |
| `voidlands:basalt_plateaus` | smooth basalt | wide highlands with flat tops and sloped sides |
| `voidlands:soul_soil_hills` | soul soil | gentle rolling hills |
| `voidlands:soul_sand_dunes` | soul sand, soul soil below | low ground with long dune ridges |
| `voidlands:mud_mire` | mud | flat, low basins |

Patches are about 384 blocks across, so an island is usually one or two biomes. Patch borders
wander and mix both biomes' blocks for a few blocks.

## Islands

The whole world is floating islands over an open void. There is no ground, bedrock floor or
ceiling, and falling off kills you.

- Islands are about 150 to 450 blocks across, with lobed, ragged coastlines. Some merge into
  chains. About 30% of the world is island.
- Each island floats at its own height, from about y 85 to y 135, plus its hills and mountains.
- Undersides are blackstone and hang down in a cone, deepest under the middle (up to about 45
  blocks), with rocky drips.
- Mountains fade in from the coast, so the edges are low rims rather than cliffs.
- Small floating rocks are scattered through the gaps.
- There is always an island under the world spawn at 0, 0.

## Spikes

Glowing "corruption" spikes stand on the islands: obsidian streaked with purple crying obsidian,
with pearlescent froglight veins and a glowing tip, and a ragged patch of sculk spreading around
the foot.

- 12 to 32 blocks tall, slightly leaning, wide at the base.
- Never more than one per chunk. On average about one every 100 blocks of island, most often on
  blackstone peaks and least often in the mud.
- None within 24 blocks of spawn.

All five biomes:
- use the Soul Sand Valley fog colour and ambient sounds, with no music;
- have no mob spawns;
- let water be placed;
- don't let you sleep or set a spawn point in beds, and beds don't explode.

## Requirements

- Paper or Purpur 1.21.11, Java 21
- Multiverse-Core 5 (optional, but it's the easy way to create and load the world)

## Install

1. Put `IRVoidlands-<version>.jar` in `plugins/` and restart the server.
2. Check the startup log for `Ready. Create a world with: /mv create <name> nether -g Voidlands`
   and no errors about missing `voidlands:` biomes. `/datapack list enabled` should include the
   Voidlands pack.
3. Run `/mv generators`. `Voidlands` should be in the list.

## Create the world

```
/mv create voidlands nether -g Voidlands
```

Use the **nether** environment. That gives the fog-only Soul Sand Valley sky, with no sun, moon or
stars. With `normal` the world still generates, but under an overworld sky, and the plugin logs a
warning.

## Teleporting players

```
/mv tp <player> voidlands
/execute in minecraft:voidlands run tp <player> <x> <y> <z>
```

`/mv tp` puts players on the spawn island. The second form works in command blocks and functions.
Because the ground is floating islands, stand where you want players to arrive and use those exact
coordinates (F3), so nobody lands in rock or falls into the void. Use tab-completion on
`/execute in` to see the world's exact name.

## Tuning

**Terrain** is set in `plugins/Voidlands/config.yml`:
- biome patches: size, border wiggle, weights;
- islands: coverage, size, height, height variation, underside depth, floating rocks;
- mountains: how broad and how tall;
- spikes: on/off, how often, size.

Changing any of these after the world
exists makes new chunks not line up with old ones, so experiment on a throwaway world
(`/mv create voidtest nether -g Voidlands`, then `/mv delete voidtest`).

**Sky, particles and sounds** are in the datapack the plugin unpacks to
`plugins/Voidlands/datapack/data/voidlands/worldgen/biome/<biome>.json`. Edit the files and restart
the server; your edits are kept. To get the defaults back, delete a file (or the whole `datapack`
folder) and restart. Useful values in each file:

- `minecraft:visual/ambient_particles`: the black smoke. `probability` sets how often a puff
  appears around the player. Raise it for more smoke, lower it for less. Other dark particles to try are
  `minecraft:ash` (Soul Sand Valley's drifting flakes) and `minecraft:squid_ink`.
- `minecraft:visual/fog_color`: the sky and fog colour.
- `minecraft:audio/ambient_sounds`: the eerie loop and mood sounds.
- `minecraft:audio/background_music`: `{}` means no music. If a client still plays music, add
  `"minecraft:audio/music_volume": 0.0`.
- `minecraft:visual/fog_end_distance`: add this to see further than the Nether's 96 blocks.

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`. GitHub Actions builds it on every push too; download it from the
workflow run's artifacts. `./gradlew runServer` starts a local 1.21.11 test server. Put the
Multiverse-Core jar in `run/plugins/` first if you want to test `/mv create`.

## Notes

- The world needs the plugin installed for good. Without it, Multiverse can't load the world's
  generator, and new chunks generate as vanilla Nether.
- Lava still flows fast, like in the Nether. Minecraft only lets the whole dimension set that, not
  a biome. The Voidlands has no natural lava, so this only matters if you place some.
- If `nether-ceiling-void-damage-height` is set in Paper's world config, it also applies here:
  players above that height take damage. Mountains reach about y 200, so keep it at 0 (the default).
- Worlds created with version 1.0.0 had continuous ground. This version makes floating islands, so
  create a new world for it; new chunks in an old world won't match.
- Mojang marks the biome attribute format (`attributes` in the JSON files) as experimental. A future
  Minecraft version may need small edits to those five files.
