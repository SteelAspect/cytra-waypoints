<img src="src/main/resources/assets/sharedwaypoints/icon.png" alt="" width="96" align="right">

# SharedWaypoints

[![Download 2.0.0](https://img.shields.io/badge/download-2.0.0-2ea44f)](https://github.com/SteelAspect/sharedwaypoints/releases/latest)
[![Tested](https://img.shields.io/badge/tested-unit%20%C2%B7%20server%20%C2%B7%20client-blue)](https://github.com/SteelAspect/sharedwaypoints/actions/workflows/build.yml)
![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62B47A)
![Fabric](https://img.shields.io/badge/loader-Fabric-DBD0B4)
![Server-side](https://img.shields.io/badge/side-server%2C%20client%20optional-blue)
[![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

**A shared waypoint list for your Fabric server.** Players browse waypoints in chat, add them to
**Xaero's Minimap** with a click, navigate to them with a **live on-screen compass**, follow **routes** stop by stop,
and see everything on your **BlueMap or squaremap** web map.

Only the server needs the mod. Players join with a vanilla client or with Xaero's Minimap, with nothing extra to
install. Players who **also** install it on their client get an optional **waypoint menu** (press **J**).

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

**Routes**
- A **route** is a named list of waypoints to visit in order, e.g. a Nether highway tour or a round of your farms.
- **[Go]** on a route and the compass takes you to each stop in turn. When you arrive it shows "Stop 2/5" and moves
  on to the next stop by itself. **[Skip stop]** jumps ahead, and you can start from any stop.
- Anyone can create routes. Creators (and ops) add, remove and reorder stops, and rename, describe or delete the
  route. Deleting a waypoint removes it from every route; deleting a route keeps its waypoints.
- Routes show their stop count and total length (Nether legs counted in the Nether), and appear as gold lines on
  web maps.

**Optional client menu**
- Install the same jar on your client and press **J** on a server that runs SharedWaypoints: a full screen with
  search, category and ★ favourite filters, sorting by name or distance, and a details panel.
- Every action is a button: **Go / Stop**, **Add to Xaero** (opens Xaero's add screen directly), **Copy coords**,
  **Favourite**, **Edit** (name and description), **Remove**, **Teleport** (ops) and **+ Add** with your position
  pre-filled.
- A **Routes** screen lists every route and its stops. It has Start / Stop, Skip stop, **+ Stop** (pick a
  waypoint), **− Stop**, **↑ / ↓** to reorder, Edit and Delete.
- The server still decides everything, so permissions are the same as in chat. The menu updates live when
  anyone changes a waypoint. Players without the client mod keep using chat as before.
- The **[player guide](docs/PLAYER-GUIDE.md)** explains how to install the menu, what every button does, and how
  to use routes.
- The menu needs a matching mod version on both sides: 1.5.0 or newer on the client and on the server. If one side is
  older, J says so, and chat keeps working as normal.

  ![The waypoint menu](docs/images/client-menu.png)

**Web maps**
- **BlueMap and squaremap:** if either is installed, every waypoint shows up on the web map in a
  toggleable "Shared Waypoints" layer. Pins use the category colour, and clicking one shows its details.
  Routes are drawn as gold lines between their stops.
- **Always in sync:** adding, renaming or removing a waypoint or route updates the map straight away.

**For server owners**
- **Your own categories:** shops, mines, farms, whatever fits your server, each with its own colour (see
  [Configuration](#configuration)).
- **`/waypoints reload`** applies config changes and hand edits without a restart.
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
4. Optional: players who want the menu put the same jar (with Fabric API) in their own `mods/` folder and press
   **J**. See the [player guide](docs/PLAYER-GUIDE.md) for step-by-step instructions you can share with them.

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
| `/waypoints <category> [page]` | List one category, e.g. `storage` (see `/waypoints categories`) |
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
| `/waypoints route [list]` | List routes with their stop count and length |
| `/waypoints route info <route>` | A route's numbered stops, with buttons |
| `/waypoints route go <route> [stop]` | Follow a route, optionally starting at a later stop |
| `/waypoints route skip` | Go straight to the next stop |
| `/waypoints route create <name>` | Create an empty route |
| `/waypoints route add <route> <waypoint>` | Add a waypoint as the last stop (up to 64 stops) |
| `/waypoints route drop <route> <stop>` | Remove stop number *n* (the waypoint stays) |
| `/waypoints route move <route> <from> <to>` | Move a stop to another position |
| `/waypoints route rename <old> <new>` | Rename a route |
| `/waypoints route describe <route> [text]` | Set or clear a route's description |
| `/waypoints route delete <route>` | Delete a route (its waypoints stay) |
| `/waypoints reload` | Reload `config.json`, waypoints and favourites from disk (ops) |
| `/waypoints xaero <name>` | Send the Xaero share message (what **[Add to Xaero]** runs) |

- Put names with spaces in quotes: `/waypoints add "Main Storage" storage`. Names are 1–32 characters (Xaero's limit)
  and unique regardless of case.
- Coordinates use normal command syntax, including `~ ~ ~`.
- Tip: `/execute as @a run waypoints go "Event"` points everyone's compass at a waypoint.

## Permissions

With a permissions mod such as LuckPerms the nodes decide. Without one, these defaults apply:

| Node | Default |
|---|---|
| `sharedwaypoints.view` | everyone: list, search, info, navigate, follow routes, favourites, Xaero |
| `sharedwaypoints.add` | everyone |
| `sharedwaypoints.route` | everyone: create routes |
| `sharedwaypoints.edit` | op level 2, and creators can always rename or describe their own waypoints and change their own routes |
| `sharedwaypoints.remove` | op level 2, and creators can always remove their own waypoints and routes |
| `sharedwaypoints.teleport` | op level 2 |
| `sharedwaypoints.reload` | op level 2 |

## Configuration

`config/sharedwaypoints/config.json` is created on first start:

```json
{
  "announceNewWaypoints": true,
  "navigationParticles": true,
  "arrivalRadius": 6,
  "pageSize": 8,
  "nearRadius": 512,
  "webMapMarkers": true,
  "webMapLayerName": "Shared Waypoints",
  "categories": [
    { "id": "storage", "name": "Storage", "color": "aqua" },
    { "id": "farms", "name": "Farms", "color": "green" },
    { "id": "bases", "name": "Bases", "color": "gold" },
    { "id": "portals", "name": "Portals", "color": "light_purple" },
    { "id": "other", "name": "Other", "color": "white" }
  ]
}
```

| Option | Meaning |
|---|---|
| `announceNewWaypoints` | Tell everyone online when a waypoint is added |
| `navigationParticles` | Show the particle beacon while navigating |
| `arrivalRadius` | Distance in blocks that counts as "arrived" (1–64) |
| `pageSize` | Waypoints per page (3–30) |
| `nearRadius` | Default radius for `/waypoints near` |
| `webMapMarkers` | Show waypoints on BlueMap / squaremap when installed |
| `webMapLayerName` | Name of the layer on the web map |
| `categories` | Your categories, in the order they're listed |

Each category has an `id` (one lower-case word, used in commands), a `name` shown in chat, and a `color`. The
colour is one of the 16 chat colours: `black`, `dark_blue`, `dark_green`, `dark_aqua`, `dark_red`,
`dark_purple`, `gold`, `gray`, `dark_gray`, `blue`, `green`, `aqua`, `red`, `light_purple`, `yellow` or
`white`. The same colour is used in chat, on the boss-bar compass (closest match), in Xaero's Minimap and on web
maps.

If you remove a category, its waypoints keep it and show up in gray until you add it back or move them.
Invalid entries are skipped with a warning in the server log.

Apply changes with `/waypoints reload`, or restart the server.

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
- Routes are stored in `routes.json`, with each stop as a waypoint `id`:

```json
{
  "version": 1,
  "routes": [
    {
      "id": "9b2e41f0-3c55-4a1e-8d7f-0f4a6c1d2e33",
      "name": "Farm Run",
      "stops": ["3f1c2b8e-6a0d-4e53-9d7c-2b1f6e4a9c10", "7d0e5a92-1b4f-4c3a-9e2d-5f6a7b8c9d01"],
      "description": "Collect everything before the raid",
      "creatorUuid": "8667ba71-b85a-4004-af54-457a9734eed7",
      "creatorName": "Steve",
      "created": "2026-09-30T12:00:00Z"
    }
  ]
}
```

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

## Web maps

With [BlueMap](https://modrinth.com/plugin/bluemap) or [squaremap](https://modrinth.com/plugin/squaremap)
installed, a **Shared Waypoints** layer appears on every map. Each waypoint is a pin in its category colour, and
clicking it shows the name, category, coordinates, description and creator. Nothing needs setting up; turn it off
with `"webMapMarkers": false`. Tested against BlueMap 5.16 and squaremap 1.3.12 for 1.21.11.

## Building from source

```bash
./gradlew build
```

The jar appears in `build/libs/`. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for tests, the release process and
how the code is organised. The [changelog](CHANGELOG.md) lists what changed in each version.

## License

[MIT](LICENSE) © 2026 SteelAspect
