# Alloy Waypoints 2.9.0 — Nocturne menu + settings screen 

Goal: the waypoint menu and its sub-screens look like Alloy Hub (Nocturne, Alloy UI), and the mod gets its own
settings screen that shares one option list with its Alloy Hub page. Nothing changes on the server, in the
protocol, in chat, or in `/cway`. 2.8 servers and clients keep talking to 2.9.

## 1. Library
- Depend on Alloy UI 1.3.0 jar-in-jar (client code only), `"alloy-ui": ">=1.3.0 <2.0.0"` in fabric.mod.json.
- A dedicated server never loads the client source set, so the server jar keeps working without Alloy UI
  (it's bundled anyway, and the server ignores it).
- `Colors.java` goes away; colours come from the theme. Category colours (storage, farms, …) stay, but only as
  the small dot/stripe in front of a name.

## 2. Screens (all become `AlloyScreen`)

| Now | After |
|---|---|
| Waypoint menu (list + detail, 9 grey buttons) | Header: title "Alloy Waypoints", search field "Search waypoints", **+ Add** (primary), settings ⚙, close. Left: filter chips (All / ★ / category / Projects) as a Segmented row or dropdown, sort dropdown (Name / Distance), scrolling list (name + one line: distance + direction). Right: detail card (name, category · dimension, coords, Nether-side coords, note, status tag, "Added by … · when"). Actions: **Go / Stop** (primary), Add to Xaero, Copy coords, ★ toggle; Edit, Set status…, Teleport (ops) and Remove (red, last, confirm dialog) in a ⋯ menu. |
| Routes screen | Same frame: route list left, stops right. Start/Stop primary; Skip, + Stop, − Stop, ↑ ↓ as small icon buttons on the stop list; Edit and Delete (confirm) in ⋯. |
| Add / Edit waypoint, Edit route, Status, Waypoint picker | Small dialog-sized `AlloyScreen`s with TextFields, a category dropdown, a status Segmented (Planned / WIP / Done / Broken) and Save (primary) / Cancel. |

- Narrow window (< ~400 wide): the detail replaces the list with a back arrow (two panes at most).
- Feedback (copied, added, saved, server said no) becomes a toast instead of only chat.
- A "Server doesn't have Alloy Waypoints" / "version too old" state is an empty-state card in the frame instead
  of a chat line.
- All new text goes into `lang/en_us.json` (`sharedwaypoints.*`). Server chat text stays as it is (vanilla
  players can't translate it).

## 3. Settings (own screen + Hub page, one `Options` list)

Opened from ⚙ in the menu header, Mod Menu, and the Alloy Hub page (which shows the same rows).
Stored in `config/sharedwaypoints/client.json` (existing file, new keys, old files keep working).

| Option | Default | Notes |
|---|---|---|
| Enabled | on | existing master switch (Hub already uses it) |
| Sync into Xaero's Minimap | on | turn off only the Xaero sync, menu stays |
| Esc-menu button | on | hide the ✦ Waypoints button |
| Default sort | Distance | Name / Distance |
| Remember last filter | on | reopen the menu on the last filter and selection |
| Open menu key | J | keybind picker |

Server settings (`config.json`: announcements, particles, page size, web maps…) stay server-side, edited in
the file + `/cway reload`. Not in this screen.

Mod Menu: add `modmenu` as a compile-only optional dependency and an entrypoint that opens the settings screen.

## 4. Hub page
- `cytra-hub` keeps its `WaypointsClient` integration; it gains `.settings(...)` built from the same option
  list (exposed as a small public method on the mod's client side, guarded like the existing calls so older
  Hub/mod pairs show "incompatible" instead of crashing). That change lives in the cytra-hub repo, done
  by the Hub chat after this ships.

## 5. Tests
- Client GameTest: menu opens, list/detail render, Go/Copy/★ work, Remove asks for confirmation, settings
  screen toggles write `client.json`, Esc-button hide works, narrow-window layout. New screenshots replace the
  ones in `docs/images` (menu, routes, add, edit, status, settings).
- Existing server GameTest and Xaero sync test unchanged.

## 6. Version, docs
- 2.9.0 on dev. README, PLAYER-GUIDE (new screenshots, ⚙ settings), CHANGELOG 2.9.0.
- Merge to main and release through cytra-releases only after you've tried it in game.

## Decisions (2026-10-20, built without waiting, per the workspace rules)
1. Filters: chips All / ★ / Projects plus one dropdown (categories, or statuses while Projects is on), plus sort.
2. Settings: the six in the table (the key is a keybind row on the own screen only); nothing extra.
3. Edit / Set status / Teleport / Remove live in a ⋯ menu; Remove confirms in a red dialog.
4. Hub page settings: the Hub chat builds them from `WaypointOptions.create()` (public).
