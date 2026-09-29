# Changelog

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
