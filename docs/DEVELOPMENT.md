# Development

## Toolchain

| | |
|---|---|
| Minecraft | 1.21.11 |
| Mappings | Official Mojang mappings (Yarn is not maintained after 1.21.11) |
| Fabric Loader / API | 0.19.5 / 0.141.6+1.21.11 |
| Fabric Loom | 1.17.21 (`net.fabricmc.fabric-loom-remap`) |
| Gradle | 9.7.1 (wrapper) |
| Java | 21 |
| Mod ID / package | `sharedwaypoints` / `io.github.steelaspect.sharedwaypoints` |
| Bundled | fabric-permissions-api 0.6.1 (jar-in-jar) |
| Optional | BlueMap API 2.7.4 and squaremap API 1.3.12 (compile-only; checked against BlueMap 5.16 and squaremap 1.3.12) |

Loom 1.18+ needs a Java 25 JVM to run Gradle. 1.17.21 is the newest Loom that runs on Java 21 and still builds the
obfuscated 1.21.11.

## Projects

One Gradle build, three projects:

| Project | Jar | What it is |
|---|---|---|
| `server/` | `sharedwaypoints-server-<v>.jar` (mod id `sharedwaypoints`) | The mod server owners install: commands, chat, menu (its `client` source set), routes, web maps, join summary, sync sender. |
| `client/` | `sharedwaypoints-client-<v>.jar` (mod id `sharedwaypoints-client`) | The one optional jar players install. It receives the sync and writes Xaero's "Shared" set (all Xaero code is in `XaeroBridge`), and it bundles the server mod jar-in-jar, which brings the menu, the J key and the Esc menu button. |
| `protocol/` | bundled in both (mod id `sharedwaypoints-protocol`) | The sync payloads and `SyncProtocol.VERSION`. Jar-in-jar in both mods, so a player with both installed loads one copy. |

Versions are pinned in `gradle.properties`. `mod_version` applies to all three projects.

## Building and testing

```bash
./gradlew build                     # compile everything, unit tests, both jars
./gradlew runGametest               # headless 1.21.11 server: the end-to-end GameTest (server project)
xvfb-run -a ./gradlew --no-daemon :server:runClientGametest   # real client: the J menu, saves screenshots
xvfb-run -a ./gradlew --no-daemon :client:runClientGametest   # real client + Xaero's Minimap 26.5.0: the sync
xvfb-run -a ./gradlew --no-daemon :client:runInstalledClientTest   # the built client jar, installed like a player would
```

- **Install test** (`client/src/prodtest`): a production game (real Fabric Loader, remapped jars) whose mods folder
  has only the built `sharedwaypoints-client` jar, Fabric API and Xaero's Minimap, like a player's. It checks the
  server mod loads from inside the client jar, the J keybind is registered, and the Esc menu's **✦ Waypoints**
  button opens the menu. The dev-run tests above load the mods from source, so they can't catch a jar that's
  missing something.

- **Xaero sync test** (`client/src/clientgametest`): runs the real game with the server mod, the client mod and
  Xaero's Minimap 26.5.0. Xaero is dropped unchanged into the test game's `mods/` folder, because its bundled
  XaeroLib only loads that way. The test checks that:
  - adds reach Xaero's "Shared" set in the right dimension;
  - renames and deletes follow live;
  - stale entries and a waypoint deleted while the player was away are removed on rejoin;
  - Xaero's saved files have the Shared set with category colours, and the default set is untouched.

- **Unit tests** (`src/test`) cover the Xaero share format against `XaeroParserReplica`, a copy of Xaero's Minimap
  26.5.0's parser. They also cover the JSON stores, categories, navigation maths, paging, relative times and web-map markers
  (including HTML escaping of player-written text).
- **Client GameTest** (`src/clientgametest`) starts a real client in a singleplayer world and opens the menu with
  the J key. It adds a waypoint through the form, favourites and edits one, then creates a route, adds stops with
  the picker, reorders them and starts following it. It checks each change reached the server, and saves
  screenshots to `build/clientgametest/screenshots/`. It needs a display, so it runs under
  `xvfb-run` locally and in CI, where the screenshots are uploaded as an artifact.
- **GameTest** (`src/gametest`) runs every command as two ordinary players and an op on a real server. It checks
  chat output, click events, tab completion, permissions, navigation, favourites, routes (including following one
  stop by stop and deleting a stop mid-route), the menu's network handler and the saved files. It runs in
  Fabric's GameTest mode, which needs no `eula.txt`, and never ends up in the released jar.
  The test server also runs **squaremap 1.3.12**: `copyGametestMods` drops the unmodified jar from Modrinth into
  `build/gametest/mods/`, and the test checks the markers in squaremap's layer. BlueMap isn't run in tests
  because it first needs a manual download of Minecraft's resources. Its layer is compiled against, and
  checked against, the real BlueMap 5.16 jar.

## Branches and jar names

| Branch | Purpose | Jars | Version in-game |
|---|---|---|---|
| `main` | releases | `sharedwaypoints-server-<version>.jar`, `sharedwaypoints-client-<version>.jar` | `<version>` |
| `dev` and others | testing | `sharedwaypoints-server-dev-<version>.jar`, `sharedwaypoints-client-dev-<version>.jar` | `<version>+dev` |

The branch is read from git (or `GITHUB_REF_NAME` in CI). Force either with `-Prelease=true` / `-Prelease=false`.
The jars are in `server/build/libs/` and `client/build/libs/`. `build/devlibs/` only holds development jars that
still use Mojang names; they won't load in a normal game.

Work happens on `dev`. When it's ready, `main` is fast-forwarded to it.

## Releasing

1. Set `mod_version` in `gradle.properties`, add a `## <version>` section to `CHANGELOG.md`, and update the
   version in the README's download badge. `./gradlew check` fails if either of the last two is missing.
2. Bring `main` up to date with `dev` and push.
3. Do one of these:
   - Push a tag: `git tag v<version> && git push origin v<version>`.
   - On GitHub, go to **Releases → Draft a new release**, create the tag `v<version>` on the right branch, and
     publish.
   - On GitHub, go to **Actions → Release → Run workflow** and enter the tag. Optionally enter a commit; the
     default is the latest commit of the chosen branch. The workflow creates the tag itself.

The **Release** workflow (`.github/workflows/release.yml`) handles all three. It checks that the tag matches
`mod_version`, builds with `-Prelease=true`, and runs the unit tests and the GameTest. Then it creates the release,
or updates the one you published, with both jars attached. If the release has no notes, it
fills them in from that version's changelog section. The **Build**
workflow runs the same checks on every push to `main`/`dev` and on pull requests, and keeps the jar as a
downloadable artifact.

## Code layout

```
server/src/main/java/io/github/steelaspect/sharedwaypoints/ (common: everything the server needs)
  SharedWaypoints.java                 entrypoint: lifecycle, tick and disconnect events, commands
  ModContext.java                      config + waypoints + favourites + navigation for the running server
  command/WaypointCommand.java         the /waypoints Brigadier tree and tab completion
  config/ModConfig.java                config.json (including categories)
  map/                                 web maps: MapIntegrations, BlueMapLayer, SquaremapLayer, MapMarker, MapRoute (lines),
                                       MarkerIcons
  nav/NavMath.java                     portal projection, distances, compass arrows (pure maths)
  nav/NavigationManager.java           boss-bar compass, beacon particles, arrival, following routes stop by stop
  permission/WaypointPermissions.java  permission nodes and fallbacks
  text/WaypointText.java               chat lines, hover cards, buttons, page footers
  text/Viewer.java                     who is reading (distance, favourites, which buttons)
  text/Formats.java                    "3 days ago"
  util/                                Dimensions, Gsons, JsonFiles (atomic writes), Page
  waypoint/                            Category, CategoryRegistry, Waypoint (record = JSON shape), WaypointStore, FavoritesStore,
                                       Route (record = JSON shape), RouteStore
  network/                             optional client menu protocol: SyncPayload, ActionPayload, ResultPayload,
                                       MenuNetworking (actions run as the player's /waypoints command)
  xaero/XaeroShareFormat.java          builds xaero-waypoint: lines and Xaero's add command
  sync/SyncService.java                client-mod sync: handshake, full list, live upserts and deletes
  join/JoinSummary.java                "N new waypoints since you last played"
protocol/src/main/java/.../protocol/   SyncProtocol (version, registration) and the five payloads
client/src/main/java/.../xaerosync/    SharedWaypointsSync (entrypoint), SyncState, XaeroBridge (all Xaero code)
server/src/client/java/.../client/     optional client: J keybind, WaypointMenuScreen, AddWaypointScreen,
                                       EditWaypointScreen, WaypointList, RoutesScreen, RouteLists, EditRouteScreen,
                                       WaypointPickerScreen, ClientWaypoints (latest snapshot)
*/src/test/java/...                    unit tests (server, protocol codecs, client sync state)
server/src/gametest/...                headless-server end-to-end test
server/src/clientgametest/...          real-client test: menu via keybind and the Esc menu button, add through the form, screenshots
client/src/clientgametest/...          real-client test with Xaero's Minimap: the "Shared" set follows the server
PROGRESS.txt                           timestamped development log (repo root)
```

Only vanilla features are used: Brigadier with vanilla argument types, chat click and hover events, boss bars,
particles, titles and sounds. That's why clients don't need the mod.

## Xaero's Minimap format

This was checked against Xaero's Minimap 26.5.0 for Fabric 1.21.11 by decompiling `WaypointSharingHandler`,
`ClientEvents` and its chat mixins.

- **Format:** `xaero-waypoint:<name>:<initials>:<x>:<y>:<z>:<colorIndex>:<rotate>:<yaw>:Internal-<dim>-waypoints`
- **System messages are parsed.** Xaero's `MixinChatListener` hooks `ChatListener.handleSystemMessage`, so a
  server-sent system message works like a player's chat share. Xaero hides the raw line and shows its own
  "shared a waypoint … [Add]" message.
- **The share line must be its own message.** Xaero treats everything after the prefix as fields and replaces the
  whole message. That's why **[Add to Xaero]** runs `/waypoints xaero <name>`, which sends only the share line.
- **Escaping:** `:` → `^col^`, `-` → `^min^`, `_` → `-`, `*` → `^ast^`, the same as Xaero.
- **Dimension:** `overworld`, `the_nether` or `the_end` for vanilla, and `dim%<namespace>$<path>` for modded
  dimensions, escaped the same way (so the Nether is sent as `the-nether`). Xaero reads the dimension up to the
  last `-`, which is why the `-waypoints` suffix is required.
- **Colours:** Xaero's colour index is the position in its `WaypointColor` list, and 0–15 match `§0`–`§f`. The mod
  uses storage 11 (aqua), farms 10 (green), bases 6 (gold), portals 13 (Xaero's "Purple", `§d`) and other 15 (white).
- **Limits:** names can be at most 32 characters and initials 1–3 characters.
- **Alternative:** a `run_command` of `/xaero_waypoint_add:<fields>` also works in one click, because Xaero
  intercepts it in `ClientPacketListener.sendUnattendedCommand`. It isn't used because vanilla clients would get
  an "unknown command" prompt.

## Design notes

- Categories come from `config.json`. Ids are single lower-case words and can't be a /waypoints subcommand
  (`CategoryRegistry.RESERVED_IDS`; the GameTest fails if a new subcommand is missing from that list). Unknown
  ids stored on waypoints are kept and shown in gray.
- Web maps are optional. Their classes are only loaded after `FabricLoader.isModLoaded` says they're present,
  and a failure disables only that map. Player-written text is HTML-escaped before it reaches a map page.
- Waypoints have a stable `id`, so favourites and navigation survive renames. Version 1 files are upgraded on load.
- Routes store waypoint ids. Deleting a waypoint drops it from every route (`RouteStore.forgetWaypoint`).
  Navigation keeps the id of the stop it's heading to. It re-finds that stop's position whenever the route changes,
  so dropped, moved or deleted stops never make it skip one.
- The menu's channels carry a protocol version (`sync2`, `action2`, `result2` since 1.5.0). A client and server
  from different protocol versions never exchange data they can't read. The client sees the old `action` channel
  and tells the player the server is older. Change the suffix whenever a payload's layout changes.
- The waypoint Y is the block the player stands in. Distances in lists are horizontal; arrival also counts height.
- Chat replies are sent even when `sendCommandFeedback` is off, because for /waypoints the reply is the result.
- In singleplayer, every world shares the same `config/sharedwaypoints/` list.

## Xaero's Minimap auto-sync (sharedwaypoints-client)

Checked against **Xaero's Minimap 26.5.0 for Fabric 1.21.11** (`xaerominimap-fabric-1.21.11-26.5.0.jar`, Modrinth
version `VNYP3B0c`), by decompiling it. Xaero has no public API for adding waypoints. Its
`ThirdPartyWaypoints` is in-memory only and doesn't show up as a set in the waypoint screen, so the client mod uses
Xaero's own world and set classes, all through reflection in one class, `XaeroBridge`:

| Step | Xaero call |
|---|---|
| Current session | `xaero.hud.minimap.BuiltInHudModules.MINIMAP.getCurrentSession()` → `MinimapSession` |
| Ready? | `session.getWorldState().getAutoWorldPath() != null` (Xaero's own "can't add a waypoint at this time" check) |
| World for a dimension | Copied from `WaypointSharingHandler.getReceivedDestinationWorld`: container path = `worldState.getAutoRootContainerPath().resolve(dimensionHelper.getDimensionDirectoryName(dimKey))`; then the auto world if it's in that container, else `getFirstWorldConnectedTo(autoWorld)`, else `getFirstWorld()`, else `addWorld(worldStateUpdater.getPotentialWorldNode(dimKey, false))` |
| The "Shared" set | `world.getWaypointSet("Shared")`, or `world.addWaypointSet(WaypointSet.Builder.begin().setName("Shared").build())` |
| Waypoints | `new Waypoint(x, y, z, name, initials, WaypointColor.fromIndex(i), WaypointPurpose.NORMAL, false, true)`; `set.clear()` / `set.add(...)` |
| Save | `session.getWorldManagerIO().saveWorld(world)` |

The "Shared" set belongs to the mod. Each change rebuilds it for the affected dimensions from the list the server
sent, so personal sets are never read or written. The only thing kept from the old set is each waypoint's
"disabled" flag (matched by name), so hiding a shared waypoint in Xaero survives updates.

If any class or method is missing (a Xaero update renamed it), the bridge logs one warning and turns sync off for
the session. The game keeps running and the server's chat buttons still work.
