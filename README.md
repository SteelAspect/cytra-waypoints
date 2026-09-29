# SharedWaypoints

[![Build](https://github.com/SteelAspect/sharedwaypoints/actions/workflows/build.yml/badge.svg)](https://github.com/SteelAspect/sharedwaypoints/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/SteelAspect/sharedwaypoints?label=download)](https://github.com/SteelAspect/sharedwaypoints/releases/latest)
![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62B47A)
![Fabric](https://img.shields.io/badge/loader-Fabric-DBD0B4)
![Server-side](https://img.shields.io/badge/side-server%20only-blue)

**A shared waypoint list for your Fabric server.** Players browse waypoints in chat, add them to
**Xaero's Minimap** with a click, and navigate to them with a **live on-screen compass**.

Only the server needs the mod. Players join with a vanilla client or with Xaero's Minimap, with nothing extra to
install.

```
=== Shared Waypoints (3) ===
— Storage (1) —
[Storage] Main Storage — 120 64 -340 (overworld) [Add to Xaero] [Copy coords] [Go]
— Farms (1) —
[Farms] Iron-Farm — 100 64 200 (overworld) [Add to Xaero] [Copy coords] [Go]
— Portals (1) —
[Portals] Hub ★ — 10 70 -20 (the_nether) [Add to Xaero] [Copy coords] [Go]
```

## Features

**Browse and share**
- **Clickable waypoint list** in chat, grouped and coloured by category (storage, farms, bases, portals, other),
  with page buttons when it gets long.
- **[Add to Xaero]** opens Xaero's Minimap's own "add waypoint" screen, pre-filled for the right dimension and
  colour.
- **[Copy coords]** copies `X Y Z` to your clipboard.
- **Hover cards:** hover a name to see its distance and direction from you, description, who added it and when.
- **Search** by name, description or creator, and give waypoints short **descriptions**.
- **Announcements:** everyone online sees new waypoints as they're added, buttons included.
- **★ Favourites** per player, marked in every list.

**Navigate**
- **[Go] / `/waypoints go`:** the boss bar becomes a compass. It shows an arrow relative to where you look, the
  distance, how far up or down, and a progress bar.
- **Beacon:** a particle column only you can see marks the destination. You get an **Arrived!** title and a sound
  when you reach it.
- **Overworld ↔ Nether aware:** the compass points you to the matching portal spot (÷8 / ×8), and the details
  view shows the portal-side coordinates.
- **Near and nearest:** `/waypoints near` lists what's around you, closest first. `/waypoints nearest [category]`
  finds the closest one.
- **Teleport** for ops: a **[Teleport]** button and `/waypoints tp`.

**For server owners**
- **Permissions** through [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) (bundled), so it
  works with LuckPerms and falls back to vanilla op levels.
- **Players manage their own waypoints:** anyone can add, and creators can rename, describe or remove what they
  added.
- **Plain JSON storage** in `config/sharedwaypoints/`, saved safely after every change, plus a small config file.
- **Tab completion** everywhere: categories, names (with coordinates as the tooltip), coordinates and dimensions.

## Download and install

1. Download `sharedwaypoints-<version>.jar` from the
   **[latest release](https://github.com/SteelAspect/sharedwaypoints/releases/latest)**.
2. Put it in your server's `mods/` folder together with [Fabric API](https://modrinth.com/mod/fabric-api).
3. Start the server. Players don't need to install anything.

| Requirement | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | any 1.21.11 build |
| Java | 21 |

The mod also works in singleplayer and LAN worlds.

## Commands

| Command | What it does |
|---|---|
| `/waypoints [page <n>]` | List all waypoints, grouped by category |
| `/waypoints <category> [page]` | List one category: `storage`, `farms`, `bases`, `portals`, `other` |
| `/waypoints categories` | Categories with their counts and Xaero colours |
| `/waypoints info <name>` | Details, portal-side coordinates, distance and all buttons |
| `/waypoints search <text>` | Search names, descriptions and creators |
| `/waypoints near [radius]` | Waypoints around you, closest first (default 512 blocks) |
| `/waypoints nearest [category]` | The closest waypoint |
| `/waypoints go <name>` | Start compass navigation |
| `/waypoints stop` | Stop navigating |
| `/waypoints favorite <name>` | Add or remove a ★ favourite |
| `/waypoints favorites` | List your favourites |
| `/waypoints add <name> <category> [x y z] [dimension]` | Add a waypoint at your position, or at the given coordinates |
| `/waypoints rename <old> <new>` | Rename a waypoint |
| `/waypoints describe <name> [text]` | Set a description (max 120 characters). Leave the text out to clear it |
| `/waypoints remove <name>` | Remove a waypoint |
| `/waypoints tp <name>` | Teleport to a waypoint (ops) |
| `/waypoints xaero <name>` | Send the Xaero share message (what **[Add to Xaero]** runs) |

- Put names with spaces in quotes: `/waypoints add "Main Storage" storage`. Names are 1–32 characters (Xaero's limit)
  and unique regardless of case.
- Coordinates use normal command syntax, including `~ ~ ~`.
- Tip: `/execute as @a run waypoints go "Event"` points everyone's compass at a waypoint.

## Permissions

With a permissions mod such as LuckPerms the nodes decide. Without one, these defaults apply:

| Node | Default |
|---|---|
| `sharedwaypoints.view` | everyone: list, search, info, navigate, favourites, Xaero |
| `sharedwaypoints.add` | everyone |
| `sharedwaypoints.edit` | op level 2, and creators can always rename or describe their own waypoints |
| `sharedwaypoints.remove` | op level 2, and creators can always remove their own waypoints |
| `sharedwaypoints.teleport` | op level 2 |

## Configuration

`config/sharedwaypoints/config.json` is created on first start:

```json
{
  "announceNewWaypoints": true,
  "navigationParticles": true,
  "arrivalRadius": 6,
  "pageSize": 8,
  "nearRadius": 512
}
```

| Option | Meaning |
|---|---|
| `announceNewWaypoints` | Tell everyone online when a waypoint is added |
| `navigationParticles` | Show the particle beacon while navigating |
| `arrivalRadius` | Distance in blocks that counts as "arrived" (1–64) |
| `pageSize` | Waypoints per page (3–30) |
| `nearRadius` | Default radius for `/waypoints near` |

Changes take effect on the next server start.

## Data files

Waypoints are stored in `config/sharedwaypoints/waypoints.json` and saved after every change:

```json
{
  "version": 2,
  "waypoints": [
    {
      "id": "3f1c2b8e-6a0d-4e53-9d7c-2b1f6e4a9c10",
      "name": "Main Storage",
      "category": "storage",
      "x": 120,
      "y": 64,
      "z": -340,
      "dimension": "minecraft:overworld",
      "description": "Sorted chests, bring shulkers",
      "creatorUuid": "8667ba71-b85a-4004-af54-457a9734eed7",
      "creatorName": "Steve",
      "created": "2026-09-29T12:00:00Z"
    }
  ]
}
```

- You can edit the file by hand while the server is stopped. An unknown `category` becomes `other`, and broken
  entries are skipped with a warning.
- If the file can't be read at all, it's kept as `waypoints.json.broken-<time>` rather than overwritten.
- Favourites are stored per player in `favorites.json`.

## Xaero's Minimap

**[Add to Xaero]** makes the server send Xaero's standard waypoint-share message:

```
xaero-waypoint:Main Storage:M:120:64:-340:11:false:0:Internal-overworld-waypoints
```

Xaero's Minimap shows it as *Server shared a waypoint called "Main Storage" from overworld! **[Add]***. Clicking
**[Add]** opens Xaero's add-waypoint screen. This was checked against Xaero's Minimap 26.5.0 for 1.21.11. Category
colours map to the matching Xaero colours: storage aqua, farms green, bases gold, portals purple, other white.
Players without Xaero just see the raw line. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#xaeros-minimap-format)
for how the format was verified.

## Building from source

```bash
./gradlew build
```

The jar appears in `build/libs/`. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for tests, the release process and
how the code is organised. The [changelog](CHANGELOG.md) lists what changed in each version.
