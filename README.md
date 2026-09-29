# SharedWaypoints

A server-side Fabric mod for **Minecraft Java 1.21.11**. It keeps one shared list of server waypoints that players
can browse in chat, copy, and add to **Xaero's Minimap** with a click.

Only the server needs the mod. Vanilla clients and Xaero's Minimap clients join without installing anything. The mod
uses only vanilla commands, vanilla argument types and vanilla chat click and hover events.

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

The jar to install is **`build/libs/sharedwaypoints-1.0.0.jar`**. `build/libs/sharedwaypoints-1.0.0-sources.jar` holds
only the sources.

`build` also runs the unit tests. To start a real headless 1.21.11 server and run the end-to-end tests:

```bash
./gradlew runGametest
```

## Installing

Put `sharedwaypoints-1.0.0.jar` and Fabric API in the server's `mods/` folder and start the server. Waypoints are
saved in `config/sharedwaypoints/waypoints.json`.

## Commands

| Command | What it does | Permission (default) |
|---|---|---|
| `/waypoints` | List all waypoints, grouped by category | `sharedwaypoints.view` (everyone) |
| `/waypoints <category>` | List one category: `storage`, `farms`, `bases`, `portals`, `other` | `sharedwaypoints.view` (everyone) |
| `/waypoints categories` | List the categories, how many waypoints each has, and its Xaero colour | `sharedwaypoints.view` (everyone) |
| `/waypoints info <name>` | Coordinates, dimension, creator and date | `sharedwaypoints.view` (everyone) |
| `/waypoints add <name> <category> [x y z] [dimension]` | Add a waypoint. Without coordinates it uses your block position and dimension. With coordinates it uses your current dimension unless you give one | `sharedwaypoints.add` (everyone) |
| `/waypoints remove <name>` | Remove a waypoint | `sharedwaypoints.remove` (op level 2), **or** you created it |
| `/waypoints rename <old> <new>` | Rename a waypoint | `sharedwaypoints.rename` (op level 2), **or** you created it |
| `/waypoints xaero <name>` | Send yourself the Xaero share line. This is what **[Add to Xaero]** runs | `sharedwaypoints.view` (everyone) |

* Names are 1–32 characters. 32 is Xaero's limit for shared waypoints. Names are unique regardless of case, and
  `§`, `<` and `>` are not allowed. Put names with spaces in quotes: `/waypoints add "Main Storage" storage`.
* Coordinates take normal vanilla syntax, including `~ ~ ~`. The dimension argument takes any dimension id,
  e.g. `minecraft:the_nether`.
* Tab completion covers categories, waypoint names (quoted when needed, with coordinates as the tooltip),
  coordinates and dimensions. `remove` and `rename` only suggest waypoints you're allowed to change.
* From the console or a command block, `add` needs coordinates.
* Tip: `/execute as @a run waypoints xaero "Spawn"` pushes a waypoint to every online Xaero user.

## Chat output

Each waypoint is shown as:

```
[Category] Name — X Y Z (dimension) [Add to Xaero] [Copy coords]
```

For example, `/waypoints`:

```
=== Shared Waypoints (3) ===
— Storage (1) —
[Storage] Main Storage — 120 64 -340 (overworld) [Add to Xaero] [Copy coords]
— Farms (1) —
[Farms] Iron-Farm — 100 64 200 (overworld) [Add to Xaero] [Copy coords]
— Portals (1) —
[Portals] Hub — 10 70 -20 (the_nether) [Add to Xaero] [Copy coords]
```

* **[Category]** uses the category colour. Clicking it lists that category.
* **Name**: click for `/waypoints info`. Hover to see who added it.
* **[Add to Xaero]** runs `/waypoints xaero <name>` (see below).
* **[Copy coords]** is a `copy_to_clipboard` click event that copies `X Y Z`.

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
| `sharedwaypoints.rename` | op level 2. The creator can always rename their own waypoints |

Like vanilla commands, the chat replies follow the `sendCommandFeedback` gamerule.

## Storage

The file is `config/sharedwaypoints/waypoints.json`. It is loaded when the server starts and saved after every add,
remove or rename. The mod writes a temporary file and then moves it into place, so a crash can't leave half-written
JSON. If the file can't be parsed, the mod keeps a copy as `waypoints.json.broken-<time>` and starts with an empty
list, so nothing is overwritten silently.

```json
{
  "version": 1,
  "waypoints": [
    {
      "name": "Main Storage",
      "category": "storage",
      "x": 120,
      "y": 64,
      "z": -340,
      "dimension": "minecraft:overworld",
      "creatorUuid": "8667ba71-b85a-4004-af54-457a9734eed7",
      "creatorName": "Steve",
      "created": "2026-09-29T12:00:00Z"
    },
    {
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

* `created` is an ISO-8601 UTC timestamp. Epoch milliseconds are also accepted when you edit the file by hand.
* `creatorUuid` is all zeros for waypoints added from the console or a command block.
* If you edit the file by hand, an unknown `category` becomes `other`. Entries with no name or dimension are skipped
  with a warning in the log. Hand edits are picked up on the next server start.

## Project layout

```
build.gradle, gradle.properties, settings.gradle, gradlew, gradlew.bat, gradle/wrapper/
src/main/resources/fabric.mod.json
src/main/java/io/github/steelaspect/sharedwaypoints/
  SharedWaypoints.java               entrypoint: loads the store on server start, registers commands
  command/WaypointCommand.java       the /waypoints Brigadier tree and tab completion
  permission/WaypointPermissions.java  permission nodes and fallbacks
  text/WaypointText.java             chat lines, buttons, click and hover events
  util/Dimensions.java               dimension id -> short name
  waypoint/Category.java             categories, chat colours, Xaero colour indexes
  waypoint/Waypoint.java             the waypoint record (also the JSON shape)
  waypoint/WaypointStore.java        in-memory list, Gson load and save
  xaero/XaeroShareFormat.java        builds xaero-waypoint: lines
src/test/java/...                    unit tests (Xaero parser replica, JSON store)
src/gametest/...                     headless-server end-to-end test (not in the release jar)
PROGRESS.txt                         build progress log
```

## Assumptions

* **Categories are fixed** to `storage`, `farms`, `bases`, `portals` and `other`, one per colour in the spec.
  `add` rejects anything else and lists the valid ones.
* **Rename** has its own node, `sharedwaypoints.rename`, with the same rule as remove: op level 2, or the creator.
* **`/waypoints xaero <name>`** was added because **[Add to Xaero]** needs a server command to send the share line.
* **`[dimension]`** can follow `x y z`, so waypoints can be added for other dimensions and from the console.
* `fabric.mod.json` uses `"environment": "*"`, so the mod also works in singleplayer and LAN worlds. On a dedicated
  server, clients still don't need it. In singleplayer, every world shares the same `config/` waypoint list.
* The waypoint Y is the block the player stands in (feet position, rounded down).
* No license is set. Add one to `fabric.mod.json` if you plan to publish the mod.
