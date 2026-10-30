# Alloy Waypoints

Shared waypoints, routes and navigation for a server and its players. Workspace rules: `../CLAUDE.md`. Full dev docs: `docs/DEVELOPMENT.md` (read its Releasing section before a release).

## What it is
- id `sharedwaypoints`, display name "Alloy Waypoints", jar `alloy-waypoints-<mod_version>.jar` (2.10.0 at last check; non-`main` branches build `alloy-waypoints-dev-<v>.jar`, in-game `<v>+dev`).
- Repo `SteelAspect/cytra-waypoints` (kept that name; the rename back was the user's call). Work on `dev`; `main` is fast-forwarded to it. `environment: *`: server (commands, chat buttons, routes, BlueMap/squaremap layers, sync sender) and client (J menu, Xaero sync, settings).
- Docs: `README.md`, `DESIGN.md` (2.9.0 Nocturne menu), `CHANGELOG.md`, `docs/PLAYER-GUIDE.md`, `PROGRESS.txt`.

## Stack
- MC 1.21.11, Loader 0.19.5, Fabric API 0.141.6+1.21.11, Loom 1.17.21 (`fabric-loom-remap`), Gradle 9.7.1, **Mojang mappings**, Java 21 (`~/.local/share/PrismLauncher/java/java-runtime-delta`). Loom 1.18+ would need Java 25.
- Alloy UI `alloyui_version=1.3.1` (jar-in-jar, client only; fmj `>=1.3.0 <2.0.0`), fabric-permissions-api 0.6.1 (jar-in-jar), Mod Menu 17.0.1.
- Soft: LuckPerms, BlueMap API 2.7.4, squaremap API 1.3.12 (compile-only), Xaero's Minimap (sync, tested with 26.5.0).
- Gradle multi-project: `protocol/` (sync payloads + `SyncProtocol.VERSION`, bundled), `server/` (the mod, source sets main/client/test/gametest/clientgametest), `tests/` (never released).

## Code map (`io.github.steelaspect.sharedwaypoints`, under `server/src`)
- `SharedWaypoints` (main), `client/SharedWaypointsClient`, `xaerosync/SharedWaypointsSync` (client), `client/compat/ModMenuIntegration`. No mixins.
- Main: `command/WaypointCommand` (`/cway ...`), `waypoint/` (`WaypointStore`, `RouteStore`, `FavoritesStore`, `Category*`), `nav/` (`NavigationManager`, `NavMath`), `portal/`, `map/` (BlueMap + squaremap layers), `network/` (`MenuNetworking`, `ActionPayload`, `ResultPayload`, `SyncPayload`), `sync/SyncService`, `permission/WaypointPermissions`, `join/` (`JoinSummary`, `ClientModTip`), `xaero/XaeroShareFormat`, `config/ModConfig` (`config/sharedwaypoints/config.json`).
- Client: `WaypointMenuScreen`, `WaypointsScreen`, `RoutesScreen`, `StatusScreen`, `SettingsScreen` + `WaypointOptions` (one option list shared with the Hub page), forms and picker, `ClientSettings`, `ClientWaypoints`, **`WaypointsApi`** (public: `openAdd`, `openWaypoint`, `canAdd`; used by Alloy Map by reflection).
- Keys: J opens the menu; menu keys G/C/F/E/S/Del and `/` or Ctrl+F search; an Esc-menu button "Waypoints" (switchable). Lang: `server/src/*/resources/assets`.

## Build and test
```bash
export JAVA_HOME=~/.local/share/PrismLauncher/java/java-runtime-delta
./gradlew build                                          # unit tests + checkDocs + jar (server/build/libs)
./gradlew runGametest                                    # server GameTest: commands as 2 players + op, squaremap layer, files
./gradlew --no-daemon :server:runClientGametest          # J menu tour, screenshots in build/clientgametest/screenshots
./gradlew --no-daemon :tests:runClientGametest           # Xaero sync with the real Xaero's Minimap
./gradlew --no-daemon :tests:runInstalledClientTest [-PwithoutXaero]   # built jar installed like a player
./gradlew :tests:runInstalledServerTest                  # built jar on a production dedicated server (no display)
```
Client tests need a display: run them headless (`xvfb-run -a ...` or kwin virtual as in `../alloy-map/headless.sh`), muted. `./gradlew check` fails if README's download badge or CHANGELOG lack `## <mod_version>`. `-Prelease=true|false` forces release/dev naming.

## Cross-mod links
- Offers `WaypointsApi` to **alloy-map** (ask its chat before changing signatures). Hub page lives in cytra-hub (`integration/waypoints`); the setting list is `WaypointOptions` here.
- Uses Alloy UI (**alloy-ui** chat) and Xaero's Minimap's waypoint format (`XaeroParserReplica` test copies its parser).

## Decisions and gotchas
- A 2.8 server/client must keep working with 2.9+: do not change protocol, chat or `/cway` without bumping `SyncProtocol.VERSION`.
- The mod `breaks sharedwaypoints-client` (old two-jar layout); an old client jar alone still works with a 2.3+ server.
- Client asks for the SYNC snapshot as soon as the server registers the menu channel (fix in 2.10.0); keep that or `WaypointsApi.canAdd` is false right after joining.
- Xaero client test needs `-Dfabric.client.gametest.disableNetworkSynchronizer` (already set on `:tests:runClientGametest`).
- BlueMap isn't run in tests (needs manual resources download); its layer is only compiled and checked against BlueMap 5.16.
- Player-written text in web-map markers must stay HTML-escaped (unit-tested).

## Release
- Bump `mod_version`, CHANGELOG `## <v>`, README badge, merge `dev` -> `main`, then `python ../cytra-releases/release.py server/build/libs/alloy-waypoints-<v>.jar "<changelog>"`. `release.yml` also exists for Modrinth. Never release without asking.

## Gaps and improvement ideas
- CI (`.github/workflows/build.yml`) needs the `ALLOY_PACKAGES_TOKEN` secret to fetch Alloy UI from GitHub Packages; PROGRESS says it was still missing at 2.10.0.
- `alloyui_version=1.3.1` vs alloy-ui 1.4.0 (open/close motion); `server/.../client/Frame.java`, `Ui.java` and `SelectList.java` are own helpers that could move to `AlloyScreen`/Alloy UI widgets.
- No client test of the 2.10.0 row context menus/keys beyond the menu tour; add a test for `/` search and `Del` confirm in `server/src/clientgametest`.
- BlueMap layer has no runtime test (see gotchas).
- `DESIGN.md` still describes 2.9.0 only; 2.10.0 (WaypointsApi) is documented in CHANGELOG/PROGRESS.
