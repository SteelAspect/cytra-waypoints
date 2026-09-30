# Player guide

SharedWaypoints is a list of waypoints that everyone on the server shares. You can use it in two ways:

- **In chat.** This works for everyone and needs nothing installed: type `/waypoints` and click the buttons.
- **With the waypoint menu.** Install the mod on your own game too and press **J**. You get one screen with
  everything on buttons.

You don't need the menu. It's just quicker than typing commands.

## Installing the menu

You need Minecraft **1.21.11** with **Fabric**.

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for 1.21.11, if you haven't already.
2. Put these two files in your `.minecraft/mods/` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11
   - `sharedwaypoints-<version>.jar`, the same file the server uses (ask your server admin, or download it from
     the latest release)
3. Start Minecraft with the Fabric profile and join the server.
4. Press **J**.

On a server without SharedWaypoints, **J** just shows "SharedWaypoints isn't installed on this server".

To use a different key, go to **Options → Controls → Key Binds**. The setting is at the bottom, under
**SharedWaypoints**.

![The key setting](images/guide/keybind.png)

## The menu

![The waypoint menu](images/guide/menu.png)

**Left: the list.** Each waypoint shows its category colour, name, coordinates and dimension. On the right is how
far away it is and in which direction. A ⟳ means it's in another dimension, so the distance is the portal-side
distance.

**Top bar.**
- **Search** by name, description or who added it.
- **All / ★ Favourites / a category:** click to cycle through the filters.
- **By category / By name / By distance:** click to change the sort order.
- **+ Add:** add a new waypoint.

**Right: the selected waypoint.** You see its coordinates, the matching Nether or Overworld coordinates, its
description, and who added it and when.

**Bottom right:** **Routes** opens the [routes screen](#routes), and **Done** closes the menu.

## Buttons

| Button | What it does |
|---|---|
| **▶ Go** / **■ Stop** | Turns the boss bar into a compass pointing at the waypoint. You get an "Arrived!" title when you reach it. |
| **Add to Xaero** | Opens Xaero's Minimap's add-waypoint screen with everything filled in. Only works if you have Xaero's Minimap installed. |
| **Copy coords** | Copies `X Y Z` to your clipboard. |
| **☆ Favourite** | Marks it with ★ just for you. Use the ★ Favourites filter to see only yours. |
| **Edit** | Change the name or description. Available for waypoints you added (and for admins). |
| **Remove** | Deletes it for everyone, after asking you first. Available for waypoints you added (and for admins). |
| **Teleport** | Only shown to server admins. |

A greyed-out button means you aren't allowed to use it; hover it to see why. The line at the bottom left shows
the server's answer to your last action, e.g. "Teleported to Iron Farm".

![What an admin sees](images/guide/op-view.png)

## Adding and editing

**+ Add** opens a form with your current position already filled in. Type a name, click **Category** to change
it, and press **Add**. **Here** resets the coordinates to where you're standing. Everyone on the server sees the
new waypoint straight away.

![Adding a waypoint](images/guide/add.png)

**Edit** changes the name and the description (a short note such as "bring shulkers").

![Editing a waypoint](images/guide/edit.png)

## Favourites

Favourites are per player: yours don't change what anyone else sees.

![Only favourites](images/guide/favourites.png)

## Routes

A route is a list of waypoints to visit in order, like a tour of the farms or a Nether highway. When you follow a
route, the compass at the top of the screen points at one stop at a time. On arrival it shows "Stop 2/5" and moves
on to the next stop by itself.

![Following a route: the compass shows the stop number](images/guide/route-compass.png)

Click **Routes** in the menu to open the routes screen. Routes are on the left. On the right are the selected
route's stops, with their distance from you.

![The routes screen](images/guide/routes.png)

| Button | What it does |
|---|---|
| **▶ Start** / **■ Stop** | Follow the route from the first stop. If a later stop is selected, it says **▶ From stop N** and starts there. |
| **Skip stop** | While following the route, go straight to the next stop. |
| **+ Stop** | Pick a waypoint to add as the last stop. |
| **− Stop** | Remove the selected stop from the route. The waypoint itself stays. |
| **↑ / ↓** | Move the selected stop earlier or later. |
| **Edit** | Change the route's name or description. |
| **Delete** | Delete the route for everyone, after asking you first. Its waypoints stay. |
| **+ New route** | Create a route (top right), then add stops with **+ Stop**. |

Anyone can create a route. Only its creator and admins can change or delete it, but everyone can follow it.

![Picking a stop](images/guide/route-picker.png)

Everything also works in chat. `/waypoints route` lists the routes, and `/waypoints route info <name>` shows the
stops with buttons: **[Go from here]**, and for the creator **[↑]** and **[✕]**.

![A route in chat](images/guide/route-chat.png)

## Good to know

- The server decides everything. The menu can only do what you're allowed to do with commands.
- The menu needs 1.5.0 or newer on both your game and the server. If the server is older, **J** tells you, and chat
  works as normal.
- The list updates by itself when anyone adds, renames or removes a waypoint.
- Everything in the menu also works in chat. See the [command list](../README.md#commands).
