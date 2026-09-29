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

Loom 1.18+ needs a Java 25 JVM to run Gradle. 1.17.21 is the newest Loom that runs on Java 21 and still builds the
obfuscated 1.21.11.

## Building and testing

```bash
./gradlew build          # compile, unit tests, jar in build/libs/
./gradlew runGametest    # start a headless 1.21.11 server and run the end-to-end GameTest
```

- **Unit tests** (`src/test`) cover the Xaero share format against `XaeroParserReplica`, a copy of Xaero's Minimap
  26.5.0's parser. They also cover the JSON stores, navigation maths, paging and relative times.
- **GameTest** (`src/gametest`) runs every command as two ordinary players and an op on a real server. It checks
  chat output, click events, tab completion, permissions, navigation, favourites and the saved files. It runs in
  Fabric's GameTest mode, which needs no `eula.txt`, and never ends up in the released jar.

## Branches and jar names

| Branch | Purpose | Jar in `build/libs/` | Version in-game |
|---|---|---|---|
| `main` | releases | `sharedwaypoints-<version>.jar` | `<version>` |
| `dev` and others | testing | `sharedwaypoints-dev-<version>.jar` | `<version>+dev` |

The branch is read from git (or `GITHUB_REF_NAME` in CI). Force either with `-Prelease=true` / `-Prelease=false`.
`build/devlibs/` only holds a development jar that still uses Mojang names; it won't load on a normal server.

Work happens on `dev`. When it's ready, `main` is fast-forwarded to it.

## Releasing

1. Set `mod_version` in `gradle.properties` and add a `## <version>` section to `CHANGELOG.md`.
2. Bring `main` up to date with `dev` and push.
3. Either push a tag (`git tag v<version> && git push origin v<version>`), or on GitHub go to
   **Releases → Draft a new release**, create the tag `v<version>` on the right branch, and publish.

The **Release** workflow (`.github/workflows/release.yml`) handles both. It checks that the tag matches
`mod_version`, builds with `-Prelease=true`, and runs the unit tests and the GameTest. Then it creates the release,
or updates the one you published, with `sharedwaypoints-<version>.jar` attached. If the release has no notes, it
fills them in from that version's changelog section. The **Build**
workflow runs the same checks on every push to `main`/`dev` and on pull requests, and keeps the jar as a
downloadable artifact.

## Code layout

```
src/main/java/io/github/steelaspect/sharedwaypoints/
  SharedWaypoints.java                 entrypoint: lifecycle, tick and disconnect events, commands
  ModContext.java                      config + waypoints + favourites + navigation for the running server
  command/WaypointCommand.java         the /waypoints Brigadier tree and tab completion
  config/ModConfig.java                config.json
  nav/NavMath.java                     portal projection, distances, compass arrows (pure maths)
  nav/NavigationManager.java           boss-bar compass, beacon particles, arrival
  permission/WaypointPermissions.java  permission nodes and fallbacks
  text/WaypointText.java               chat lines, hover cards, buttons, page footers
  text/Viewer.java                     who is reading (distance, favourites, which buttons)
  text/Formats.java                    "3 days ago"
  util/                                Dimensions, Gsons, JsonFiles (atomic writes), Page
  waypoint/                            Category, Waypoint (record = JSON shape), WaypointStore, FavoritesStore
  xaero/XaeroShareFormat.java          builds xaero-waypoint: lines
src/test/java/...                      unit tests
src/gametest/...                       headless-server end-to-end test
docs/PROGRESS.txt                      timestamped development log
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

- Categories are a fixed set: `storage`, `farms`, `bases`, `portals`, `other`.
- Waypoints have a stable `id`, so favourites and navigation survive renames. Version 1 files are upgraded on load.
- The waypoint Y is the block the player stands in. Distances in lists are horizontal; arrival also counts height.
- Chat replies follow the vanilla `sendCommandFeedback` gamerule, like vanilla commands.
- In singleplayer, every world shares the same `config/sharedwaypoints/` list.
