# SharedWaypoints

A server-side Fabric mod for **Minecraft Java 1.21.11**. It keeps one shared list of server waypoints that players
can browse in chat, copy, add to **Xaero's Minimap** with a click, and **navigate to with a live on-screen compass**.

**Highlights**

* **Live navigation:** `/waypoints go <name>` (or click **[Go]**) turns the boss bar into a compass. It shows an
  arrow relative to where you're looking, the distance, how far up or down, and a progress bar. A particle
  beacon marks the spot, and an **Arrived!** title with a sound plays when you get there. Between the Overworld and
  the Nether it points at the matching portal spot (÷8 / ×8).
* **Around you:** `/waypoints near` lists waypoints sorted by distance with a compass direction.
  `/waypoints nearest [category]` finds the closest one.
* **Rich chat:** hover a waypoint name for a card showing distance and direction, description, creator and age.
  Your ★ favourites are marked. Long lists page with clickable **« Prev / Next »**.
* **Sharing:** everyone online is told when a waypoint is added, with the usual buttons. `/waypoints search`
  searches names, descriptions and creators. Waypoints can have a short description.
* **Xaero's Minimap:** **[Add to Xaero]** opens Xaero's own add dialog (checked against Xaero 26.5.0).
* **Ops:** a **[Teleport]** button and `/waypoints tp`.

Only the server needs the mod. Vanilla clients and Xaero's Minimap clients join without installing anything. The mod
uses only vanilla commands and argument types, chat click and hover events, boss bars, particles, titles and
sounds.

| | |
|---|---|
| Mod ID | `sharedwaypoints` |
| Package | `io.github.steelaspect.sharedwaypoints` |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.141.6+1.21.11 (required on the server) |
| Java | 21 |
| Mappings | **Official Mojang mappings** (Yarn is not maintained after 1.21.11) |
| Bundled | [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) 0.6.1, included in the jar |
| Build tools | Fabric Loom 1.17.21 (`net.fabricmc.fabric-loom-remap`) and Gradle 9.7.1 (wrapper) |

> Loom 1.18+ needs a Java 25 JVM to run Gradle. 1.17.21 is the newest Loom that runs on Java 21 and still builds
> the obfuscated 1.21.11.

## Building

```bash
./gradlew build          # Windows: gradlew.bat build
```

The jar to install is **`build/libs/sharedwaypoints-1.1.0.jar`**. `build/libs/sharedwaypoints-1.1.0-sources.jar` holds
only the sources.

`build` also runs the unit tests. To start a real headless 1.21.11 server and run the end-to-end tests:

```bash
./gradlew runGametest
```

## Installing

Put `sharedwaypoints-1.1.0.jar` and Fabric API in the server's `mods/` folder and start the server. Files live in
`config/sharedwaypoints/`: `waypoints.json`, `favorites.json` and `config.json`.

## Commands

| Command | What it does | Permission (default) |
|---|---|---|
| `/waypoints [page <n>]` | List all waypoints, grouped by category, one page at a time | `sharedwaypoints.view` (everyone) |
| `/waypoints <category> [page]` | List one category: `storage`, `farms`, `bases`, `portals`, `other` | `sharedwaypoints.view` (everyone) |
| `/waypoints categories` | List the categories, how many waypoints each has, and its Xaero colour | `sharedwaypoints.view` (everyone) |
| `/waypoints info <name>` | Details: coordinates, portal-side coordinates, distance, creator, date, description, buttons | `sharedwaypoints.view` (everyone) |
| `/waypoints search <text>` | Search names, descriptions and creators | `sharedwaypoints.view` (everyone) |
| `/waypoints near [radius]` | Waypoints around you, closest first (default radius 512) | `sharedwaypoints.view` (everyone) |
| `/waypoints nearest [category]` | The closest waypoint, optionally of one category | `sharedwaypoints.view` (everyone) |
| `/waypoints go <name>` | Start live navigation (boss-bar compass and beacon) | `sharedwaypoints.view` (everyone) |
| `/waypoints stop` | Stop navigating | `sharedwaypoints.view` (everyone) |
| `/waypoints favorite <name>` | Add or remove one of your ★ favourites | `sharedwaypoints.view` (everyone) |
| `/waypoints favorites` | List your favourites | `sharedwaypoints.view` (everyone) |
| `/waypoints xaero <name>` | Send yourself the Xaero share line. This is what **[Add to Xaero]** runs | `sharedwaypoints.view` (everyone) |
| `/waypoints add <name> <category> [x y z] [dimension]` | Add a waypoint. Without coordinates it uses your block position and dimension. With coordinates it uses your current dimension unless you give one | `sharedwaypoints.add` (everyone) |
| `/waypoints remove <name>` | Remove a waypoint | `sharedwaypoints.remove` (op level 2), **or** you created it |
| `/waypoints rename <old> <new>` | Rename a waypoint | `sharedwaypoints.edit` (op level 2), **or** you created it |
| `/waypoints describe <name> [text]` | Set a short description (max 120 characters). Leave the text out to clear it | `sharedwaypoints.edit` (op level 2), **or** you created it |
| `/waypoints tp <name>` | Teleport to a waypoint | `sharedwaypoints.teleport` (op level 2) |

* Names are 1–32 characters. 32 is Xaero's limit for shared waypoints. Names are unique regardless of case, and
  `§`, `<` and `>` are not allowed. Put names with spaces in quotes: `/waypoints add "Main Storage" storage`.
* Coordinates take normal vanilla syntax, including `~ ~ ~`. The dimension argument takes any dimension id,
  e.g. `minecraft:the_nether`.
* Tab completion covers categories, waypoint names (quoted when needed, with coordinates as the tooltip),
  coordinates and dimensions. `remove`, `rename` and `describe` only suggest waypoints you're allowed to change.
* From the console or a command block, `add` needs coordinates. `go`, `near`, `nearest`, `favorite` and `tp` need
  a player.
* Tip: `/execute as @a run waypoints xaero "Spawn"` pushes a waypoint to every online Xaero user, and
  `/execute as @a run waypoints go "Event"` sends everyone's compass there.

## Navigation

`/waypoints go <name>`, or the **[Go]** button on any line, starts navigation for you only:

```
↗  Iron-Farm  224m  ▲12                       ← boss bar, in the category's colour
```

* **Arrow:** points relative to where you're looking (↑ straight ahead, → turn right, ↓ behind you) and updates
  4 times a second.
* **▲/▼:** how many blocks above or below you the waypoint is.
* **Progress bar:** fills as you get closer.
* **Beacon:** within 256 blocks, a column of end-rod sparks with a category-coloured base marks the spot. Only
  the navigating player sees it.
* **Arrival:** within `arrivalRadius` blocks (default 6) you get an **Arrived!** title, the level-up sound and a
  chat message, and navigation ends.
* **Overworld ↔ Nether:** the compass points at the matching spot on your side (Overworld ÷ 8, Nether × 8) with
  "⟳ via Nether portal". When you reach it, it tells you to take a portal. `/waypoints info` shows the
  portal-side coordinates with a copy button.
* **Other dimensions** (the End, modded): the bar says which dimension to travel to.
* Navigation survives respawning. It stops on `/waypoints stop`, on disconnect, or when the waypoint is
  removed.

## Chat output

Each waypoint is shown as:

```
[Category] Name — X Y Z (dimension) [Add to Xaero] [Copy coords] [Go]
```

The format you asked for, plus **[Go]** at the end for players. A gold ★ follows the name if it's one of your
favourites.

For example, `/waypoints`:

```
=== Shared Waypoints (3) ===
— Storage (1) —
[Storage] Main Storage — 120 64 -340 (overworld) [Add to Xaero] [Copy coords] [Go]
— Farms (1) —
[Farms] Iron-Farm — 100 64 200 (overworld) [Add to Xaero] [Copy coords] [Go]
— Portals (1) —
[Portals] Hub ★ — 10 70 -20 (the_nether) [Add to Xaero] [Copy coords] [Go]
```

With more waypoints than `pageSize` (default 8), a footer `« Prev   Page 1/3   Next »` is added. The arrows are
clickable.

`/waypoints near`:

```
=== Within 512m (3) ===
1m S [Storage] Main Storage — 0 64 0 (overworld) …
179m NE ⟳ [Portals] Hub — 10 70 -20 (the_nether) …      ← through a Nether portal
224m SE [Farms] Iron-Farm — 100 64 200 (overworld) …
```

* **[Category]** uses the category colour. Clicking it lists that category.
* **Name**: click for `/waypoints info`. Hover for a card with the distance and direction from you (or "via
  Nether portal" or "In the_end"), the description, who added it and how long ago.
* **[Add to Xaero]** runs `/waypoints xaero <name>` (see below).
* **[Copy coords]** is a `copy_to_clipboard` click event that copies `X Y Z`.
* **[Go]** starts navigation (see above).

When someone adds a waypoint, everyone else online who has `sharedwaypoints.view` sees
`✦ Steve shared a new waypoint:` followed by its line, buttons included. Turn this off with
`announceNewWaypoints`.

| Category | Chat colour | Xaero colour index |
|---|---|---|
| storage | aqua (`§b`) | 11 (Aqua) |
| farms | green (`§a`) | 10 (Green) |
| bases | gold (`§6`) | 6 (Gold) |
| portals | purple (`§d`, `light_purple`) | 13 (what Xaero calls "Purple") |
| other | white (`§f`) | 15 (White) |

Xaero's colour index is the position in its `WaypointColor` list. Indexes 0–15 follow the vanilla `§0`–`§f` order, so
the chat colour and the minimap colour always match. Xaero names `§d` "Purple" and `§5` "Dark Purple", so portals use
`§d`.

## Xaero's Minimap: how it was checked

I checked this against **Xaero's Minimap 26.5.0 for Fabric 1.21.11** (released 2026-09-10, the newest 1.21.11 build
on Modrinth when this was written). I decompiled its `WaypointSharingHandler`, `ClientEvents` and its chat mixins.

**Format: confirmed.** Xaero's own share message is

```
xaero-waypoint:<name>:<initials>:<x>:<y>:<z>:<colorIndex>:<rotate>:<yaw>:Internal-<dim>-waypoints
```

and SharedWaypoints sends exactly that, for example:

```
xaero-waypoint:Main Storage:M:120:64:-340:11:false:0:Internal-overworld-waypoints
```

**System and server messages: yes, Xaero reads them.** Xaero's `MixinChatListener` hooks
`ChatListener.handleSystemMessage` for normal (non action bar) system messages. That goes to
`ClientEvents.handleClientSystemChatReceivedEvent`, which checks every system message for `xaero-waypoint:`. It
hides the raw line and shows its own message instead:

> Server shared a waypoint called "Main Storage" from overworld! **[Add]**

So no workaround is needed. The server sends the share line as an ordinary system message.

Details that matter:

1. **The share line has to be a message on its own.** Xaero takes everything after `xaero-waypoint:` as fields
   and replaces the whole message with its own. If the share text were inside the listing line, Xaero users would
   lose `[Copy coords]` and vanilla players would see raw text. That's why **[Add to Xaero]** runs
   `/waypoints xaero <name>`, and the server replies with only the share line. The player clicks
   **[Add to Xaero]**, then Xaero's **[Add]**, and Xaero's "add waypoint" screen opens pre-filled for the right
   dimension.
2. **Escaping.** Xaero escapes text fields as `:` → `^col^`, `-` → `^min^`, `_` → `-`, `*` → `^ast^`, and the mod
   does the same. Names like `Iron-Farm_2` come through unchanged.
3. **Dimension field.** It is `overworld`, `the_nether` or `the_end` for the vanilla dimensions. Xaero matches these
   against the client's dimension list. Modded dimensions use Xaero's directory name, `dim%<namespace>$<path>`. The
   field goes through the same escaping, so the Nether is sent as `Internal-the-nether-waypoints`, exactly as Xaero
   sends it. The literal `Internal-the_nether-waypoints` form in the spec parses the same way (there is a test for
   it). Keep the `-waypoints` ending: Xaero reads the dimension up to the **last** `-`.
4. Players without Xaero who click **[Add to Xaero]** just see the raw `xaero-waypoint:` line. The hover text on the
   button says Xaero is needed.

*Checked but not used:* a one-click button whose `run_command` is `/xaero_waypoint_add:<same fields>` also works,
because Xaero intercepts it client-side (it hooks `ClientPacketListener.sendUnattendedCommand`). Your spec asks for
the share format, and on vanilla clients that button shows an "unknown command" prompt, so the mod uses the share
format.

The unit tests include `XaeroParserReplica`, a copy of Xaero 26.5.0's parsing steps. They check that names, colours,
coordinates and every dimension type come out of Xaero unchanged.

## Permissions

The mod uses [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api), which is bundled in the jar.
With a permissions mod such as LuckPerms, the nodes decide. Without one, the defaults apply:

| Node | Default |
|---|---|
| `sharedwaypoints.view` | everyone |
| `sharedwaypoints.add` | everyone |
| `sharedwaypoints.remove` | op level 2. The creator can always remove their own waypoints |
| `sharedwaypoints.edit` | op level 2. The creator can always rename or describe their own waypoints |
| `sharedwaypoints.teleport` | op level 2 |

Like vanilla commands, the chat replies follow the `sendCommandFeedback` gamerule.

## Configuration

`config/sharedwaypoints/config.json` is created on first start and read each time the server starts:

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
| `arrivalRadius` | Blocks from the waypoint at which navigation counts as arrived (1–64) |
| `pageSize` | Waypoint lines per page (3–30) |
| `nearRadius` | Default radius for `/waypoints near` |

## Storage

The waypoints file is `config/sharedwaypoints/waypoints.json`. It is loaded when the server starts and saved after every add,
remove or rename. The mod writes a temporary file and then moves it into place, so a crash can't leave half-written
JSON. If the file can't be parsed, the mod keeps a copy as `waypoints.json.broken-<time>` and starts with an empty
list, so nothing is overwritten silently.

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
    },
    {
      "id": "a7d9e0f4-1b2c-4d3e-8f5a-6b7c8d9e0f1a",
      "name": "Hub",
      "category": "portals",
      "x": 10,
      "y": 70,
      "z": -20,
      "dimension": "minecraft:the_nether",
      "creatorUuid": "00000000-0000-0000-0000-000000000000",
      "creatorName": "Server",
      "created": "2026-09-29T12:05:00Z"
    }
  ]
}
```

* `id` is a stable id. Favourites and navigation use it, so they survive renames. Version 1 files (from the first
  release, without ids or descriptions) are upgraded automatically on load.
* `description` is optional (`null` or missing means none).
* `created` is an ISO-8601 UTC timestamp. Epoch milliseconds are also accepted when you edit the file by hand.
* `creatorUuid` is all zeros for waypoints added from the console or a command block.
* If you edit the file by hand, an unknown `category` becomes `other`. Entries with no name or dimension are skipped
  with a warning in the log. Hand edits are picked up on the next server start.

Favourites are stored per player in `config/sharedwaypoints/favorites.json`, as
`{"version": 1, "favorites": {"<player uuid>": ["<waypoint id>", ...]}}`. Removing a waypoint also removes it from
everyone's favourites.

## Project layout

```
build.gradle, gradle.properties, settings.gradle, gradlew, gradlew.bat, gradle/wrapper/
src/main/resources/fabric.mod.json
src/main/java/io/github/steelaspect/sharedwaypoints/
  SharedWaypoints.java               entrypoint: lifecycle, tick and disconnect events, commands
  ModContext.java                    config + waypoints + favourites + navigation for the running server
  command/WaypointCommand.java       the /waypoints Brigadier tree and tab completion
  config/ModConfig.java              config.json
  nav/NavMath.java                   portal projection, distances, compass arrows (pure maths)
  nav/NavigationManager.java         boss-bar compass, beacon particles, arrival
  permission/WaypointPermissions.java  permission nodes and fallbacks
  text/WaypointText.java             chat lines, hover cards, buttons, page footers
  text/Viewer.java                   who is reading (distance, favourites, which buttons)
  text/Formats.java                  "3 days ago"
  util/Dimensions.java, Gsons.java, JsonFiles.java, Page.java
  waypoint/Category.java             categories, chat colours, Xaero colour indexes
  waypoint/Waypoint.java             the waypoint record (also the JSON shape)
  waypoint/WaypointStore.java        in-memory list, search, Gson load and save
  waypoint/FavoritesStore.java       per-player favourites
  xaero/XaeroShareFormat.java        builds xaero-waypoint: lines
src/test/java/...                    unit tests (Xaero parser replica, stores, navigation maths, formats)
src/gametest/...                     headless-server end-to-end test (not in the release jar)
PROGRESS.txt                         build progress log
```

## Assumptions

* **Categories are fixed** to `storage`, `farms`, `bases`, `portals` and `other`, one per colour in the spec.
  `add` rejects anything else and lists the valid ones.
* **Rename and describe** share the node `sharedwaypoints.edit`, with the same rule as remove: op level 2, or the
  creator. (It was called `sharedwaypoints.rename` in the first build.)
* **`/waypoints xaero <name>`** was added because **[Add to Xaero]** needs a server command to send the share line.
* **`[dimension]`** can follow `x y z`, so waypoints can be added for other dimensions and from the console.
* `fabric.mod.json` uses `"environment": "*"`, so the mod also works in singleplayer and LAN worlds. On a dedicated
  server, clients still don't need it. In singleplayer, every world shares the same `config/` waypoint list.
* The waypoint Y is the block the player stands in (feet position, rounded down).
* Distances in lists and hover cards are horizontal. Arrival also counts height.
* The compass arrow uses 8 directions. Finer steps would change the bar text more often without being more useful.
* No license is set. Add one to `fabric.mod.json` if you plan to publish the mod.
