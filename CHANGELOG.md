# Changelog

## 2.0.0 — unreleased

(in progress on dev)

## 1.5.0 — 2026-09-30

**New: routes**
- A **route** is a named list of waypoints to visit in order. Use `/waypoints route go <route>` or **[Go]** and the
  boss-bar compass takes you from stop to stop. On arrival it shows "Stop 2/5" and moves on to the next stop by
  itself; finishing shows "Route complete!".
- `/waypoints route` commands to list, create, add, drop and move stops, rename, describe, delete, skip a stop, and
  start from any stop. Chat lists have [Go], [Go from here], [↑] and [✕] buttons.
- Permissions: `sharedwaypoints.route` (everyone) to create routes. Creators and ops (`sharedwaypoints.edit` /
  `sharedwaypoints.remove`) change and delete them.
- Deleting a waypoint removes it from every route, and someone following the route moves on to the next stop.
  Deleting a route keeps its waypoints.
- Routes show their stop count and length. They're saved in `routes.json` and reloaded with `/waypoints reload`.
- **Web maps:** routes are drawn as gold lines on BlueMap and squaremap.
- **Client menu:** a new **Routes** screen with Start / Stop, Skip stop, + Stop (waypoint picker), − Stop, ↑ / ↓,
  Edit and Delete.

**Changed**
- The client menu's network channels were renamed for the new data, so the client and the server both need 1.5.0
  or newer for the menu. With an older server, J now says so instead of "not installed".
- Text fields in the menu's forms keep keyboard focus when opened.

## 1.4.1 — 2026-09-30

- **Player guide** ([docs/PLAYER-GUIDE.md](docs/PLAYER-GUIDE.md)): how to install the waypoint menu, what each
  button does, and how to change the key, with screenshots. Server owners can send it to players.
- The README badges work while the repository is private.
- Releases can be started from GitHub's Actions tab (**Release → Run workflow**); the workflow creates the tag.
- The client test no longer times out on slow CI machines.

## 1.4.0 — 2026-09-29

**New**
- **Optional client menu:** install the jar on your client too and press **J** (rebindable) on a server running
  SharedWaypoints.
  - Search, category and ★ favourite filters, and sorting by category, name or distance.
  - A details panel with distance and direction, portal-side coordinates, the description and the creator.
  - Buttons for Go / Stop, Add to Xaero (opens Xaero's add screen directly), Copy coords, Favourite, Edit (name
    and description), Remove, Teleport (ops) and + Add (your position and dimension pre-filled).
- The menu updates live when anyone changes a waypoint.
- Menu actions run the normal /waypoints commands on the server, so permissions and validation are the same as
  in chat.
- Servers still don't require the client mod, and vanilla and Xaero-only players are unaffected.

## 1.3.0 — 2026-09-29

**New**
- **Web maps:** waypoints show up on **BlueMap** and **squaremap** in a toggleable "Shared Waypoints" layer. Pins
  use the category colour, and clicking one shows its details. Changes appear immediately. Both maps are optional.
- **Custom categories:** define your own in `config.json` (id, name, one of the 16 chat colours). The colour
  carries over to chat, the compass, Xaero's Minimap and web maps.
- **`/waypoints reload`** (op level 2, `sharedwaypoints.reload`) reloads the config, waypoints and favourites
  without a restart.
- Mod icon, and the mod is now MIT licensed.

**Changed**
- `/waypoints` replies are shown even when the server has `sendCommandFeedback` turned off.
- A waypoint whose category was removed from the config keeps it (shown in gray) instead of being moved to
  "other".

## 1.2.0 — 2026-09-29

First public release. It contains everything from 1.0.0–1.1.1, plus:

- New package `io.github.steelaspect.sharedwaypoints` and author field. Nothing changes for server owners: the mod
  id, commands, permissions and data files are the same.
- Download links, a build status badge and a clearer README. Developer notes moved to `docs/DEVELOPMENT.md`.
- Every release on GitHub now has the jar attached, built and tested by GitHub Actions.

**All features**

- Clickable waypoint list in chat, grouped and coloured by category, with page buttons.
- **[Add to Xaero]** (Xaero's Minimap's own add screen), **[Copy coords]** and **[Go]** on every line.
- Live compass navigation in the boss bar, with a particle beacon, an "Arrived!" title and Overworld ↔ Nether
  portal-spot guidance.
- `/waypoints near`, `/waypoints nearest`, `/waypoints search`.
- Descriptions, hover cards (distance, direction, description, creator, age) and per-player ★ favourites.
- New waypoints are announced to everyone online.
- Op teleport (`/waypoints tp` and a **[Teleport]** button).
- Permissions via fabric-permissions-api with op-level fallbacks. Creators manage their own waypoints.
- JSON storage with atomic saves and backups of unreadable files, plus a config file.

## 1.1.1

- Release builds (`main`) and test builds (`dev`) produce differently named jars.

## 1.1.0

- Live navigation: boss-bar compass, beacon, arrival title and sound, Nether portal projection.
- `/waypoints near`, `nearest`, `search`, `describe`, `favorite`, `favorites`, `tp`, `stop`, and paged lists.
- Hover cards, new-waypoint announcements, `config.json`.
- Waypoints get stable ids (data format version 2; version 1 files are upgraded automatically).
- The `sharedwaypoints.rename` permission became `sharedwaypoints.edit`. Added `sharedwaypoints.teleport`.

## 1.0.0

- Shared waypoint list with `/waypoints` (list, category, categories, info, add, remove, rename).
- **[Add to Xaero]** via Xaero's waypoint-share message, and **[Copy coords]**.
- JSON storage, fabric-permissions-api permissions, tab completion.
