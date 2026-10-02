# Changelog

## 2.0.2 — 2026-10-02

- **Portal spots use your height.** For a Nether waypoint seen from the Overworld (or the other way round), the
  matching portal spot kept the waypoint's own Y, so it could be underground or in the sky (an Overworld base at
  Y 200 is above the Nether roof). It's now always your current Y: in the compass bar, the ⟳ hover in chat,
  `/cway info` and the menu. The console sees `~` (as in `/tp`, "stay level").

## 2.0.1 — 2026-10-01

Fixes from a review of 2.0.0. Update both jars; a 2.0.0 client jar still works with a 2.0.1 server and the other
way round.

- **Better sync tip.** Players who have sharedwaypoints-client but no (or an unsupported) Xaero's Minimap were told
  to install sharedwaypoints-client. They're now told, a few seconds after their first join, to install Xaero's
  Minimap, with a link. A client mod of a different version is told to update. `/cway sync` says the same.
  (This needs the 2.0.1 client jar: it now announces itself even without Xaero.)
- **Big waypoint lists can't disconnect players.** The full list used to go out as one packet, and a list over the
  protocol's limits (roughly 7,000 waypoints with long names) would disconnect players with the client mod. It's now
  sent in parts. Texts from a hand-edited `waypoints.json` are clipped to what the protocol allows.
- **No file write on every join and leave.** Last-seen times were saved by rewriting `waypoints.json` each time a
  player joined or left. They're now written at most once a minute, and when the server stops.
- **Losing access stops the sync.** A player whose view permission is taken away while online (e.g. with
  LuckPerms) no longer gets waypoint updates; their client is told the sync is off.
- `/cway sync` shows the server's actual Minecraft version instead of a fixed "1.21.11".
- Docs: without Xaero's Minimap the client jar doesn't "do nothing": the menu still works, only the sync is off.

## 2.0.0 — 2026-09-30

**The command is now `/cway`** (it was `/waypoints`, which is gone). Every subcommand is the same, e.g.
`/cway add`, `/cway route go`, and the chat buttons use the new name.

**Two jars now**
- `sharedwaypoints-server` is the mod server owners install. It has everything 1.5.0 had, unchanged, and it's
  still all the server needs.
- `sharedwaypoints-client` is a new, **optional** client mod: the waypoint menu, plus it keeps
  **Xaero's Minimap** in sync with the server:
  - every shared waypoint appears in Xaero, in its own **"Shared"** waypoint set, in the right dimension, with its
    category colour;
  - adds, edits and deletes show up live, and the set is reconciled with the server on every join;
  - players' own waypoints are never touched.
  - Tested with Xaero's Minimap 26.5.0 (Fabric 1.21.11). If Xaero is missing or incompatible, the client mod logs
    one warning and the sync is off; the waypoint menu still works.

**New on the server (for everyone, no client mod needed)**
- **Join summary:** "3 new waypoints since you last played", with the usual [Add to Xaero] / [Copy coords] / [Go]
  buttons. Last-seen times per player are stored in `waypoints.json` (`lastSeen`).
- **How to get the sync:** players without the client mod get one line in chat on their first join, with
  **[Download]** and **[How it works]**. `/cway sync` shows the steps, or says the sync is already on.
- Config: `joinSummary`, `syncToClientMod`, `clientModTip` and `clientModUrl` (where [Download] points; defaults
  to the latest GitHub release). `announceNewWaypoints` still controls the live broadcast of new waypoints.

**How the sync works**
- The client mod says hello on join, with a protocol version. Only then does the server send it the full list,
  and after that every change. Players without the client mod get nothing new: the chat works exactly as before.
- `/cway reload` resends the full list to synced players.

**Waypoint menu**
- Players now get the menu from `sharedwaypoints-client`: one jar for the menu and the Xaero sync. The server jar
  is no longer needed on the client (it still works there).
- A **✦ Waypoints** button in the top-right corner of the Esc menu opens the waypoint menu, for players who'd
  rather not use the J key. It only appears on servers running SharedWaypoints.

**Other**
- The mod's author field is now `steelaspect`.
- The build is a Gradle multi-project (`protocol`, `server`, `client`); `./gradlew build` produces both jars.

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
