package io.github.steelaspect.sharedwaypoints.xaero;

/**
 * A copy of how Xaero's Minimap 26.5.0 (Fabric 1.21.11) reads a shared waypoint, cut down to the parsing steps.
 * It was written from the decompiled {@code xaero.hud.minimap.waypoint.WaypointSharingHandler}
 * ({@code onWaypointReceived} -> [Add] click -> {@code onWaypointAdd} -> {@code getReceivedDestinationWorld}).
 * Tests use it to check that our share lines come out the same as a real Xaero client would read them.
 */
final class XaeroParserReplica {
	private static final String WAYPOINT_SHARE_PREFIX = "xaero-waypoint:";
	private static final String WAYPOINT_ADD_PREFIX = "xaero_waypoint_add:";
	private static final String INTERNAL = "Internal";
	private static final String INTERNAL_HYPHEN = "Internal-";

	/** What Xaero would put in its "Add waypoint" screen. */
	record Parsed(String name, String initials, int x, int y, int z, int colorIndex, boolean rotation, int yaw,
			String dimensionNode) {
	}

	private XaeroParserReplica() {
	}

	/** Runs the received-message and [Add]-click steps. Throws if Xaero would reject the line. */
	static Parsed parse(String chatText) {
		return parseAddCommand(addCommandFor(chatText));
	}

	/** The command Xaero's [Add] button would run for this share line (onWaypointReceived). */
	static String addCommandFor(String chatText) {
		// --- onWaypointReceived(playerName, text)
		String text = chatText.replaceAll("§.", "");
		if (!text.contains(WAYPOINT_SHARE_PREFIX)) {
			throw new IllegalArgumentException("Xaero would not detect this line");
		}
		String[] args = text.substring(text.indexOf(WAYPOINT_SHARE_PREFIX)).split(":");
		if (args.length < 9) {
			throw new IllegalArgumentException("Error 0: fewer than 9 fields");
		}
		args[1] = restoreFormatting(args[1]);
		args[2] = restoreFormatting(args[2]);
		StringBuilder addCommand = new StringBuilder(WAYPOINT_ADD_PREFIX).append(args[1]);
		for (int i = 2; i < args.length; i++) {
			addCommand.append(':').append(args[i]);
		}
		return addCommand.toString();
	}

	/** What Xaero does with an add command (handleClientSendChatEvent -> onWaypointAdd). */
	static Parsed parseAddCommand(String addCommand) {
		if (!addCommand.startsWith(WAYPOINT_ADD_PREFIX)) {
			throw new IllegalArgumentException("Xaero would not intercept this command");
		}
		String[] add = addCommand.split(":");
		String name = add[1].replace("^col^", ":");
		if (name.isEmpty() || name.length() > 32) {
			throw new IllegalArgumentException("Error 1: bad name length");
		}
		String initials = add[2].replace("^col^", ":");
		if (initials.isEmpty() || initials.length() > 3) {
			throw new IllegalArgumentException("Error 2: bad initials length");
		}
		int x = Integer.parseInt(add[3]);
		int y = Integer.parseInt(add[4]);
		int z = Integer.parseInt(add[5]);
		int color = Integer.parseInt(add[6]);
		if (add[8].length() > 4) {
			throw new IllegalArgumentException("Error 4: yaw too long");
		}
		int yaw = Integer.parseInt(add[8]);
		boolean rotation = add[7].equals("true");
		String dimensionNode = add.length > 9 ? destinationNode(add[9]) : null;
		return new Parsed(name, initials, x, y, z, color, rotation, yaw, dimensionNode);
	}

	/** getReceivedDestinationWorld + getReceivedDimId, up to the dimension name Xaero looks up. */
	private static String destinationNode(String destination) {
		if (!destination.startsWith(INTERNAL_HYPHEN) || destination.equals(INTERNAL_HYPHEN)) {
			throw new IllegalArgumentException("Error 12: bad destination");
		}
		int divider = destination.lastIndexOf('-');
		if (divider == INTERNAL.length()) {
			divider = destination.length();
		}
		String path = restoreFormatting(destination.substring(INTERNAL_HYPHEN.length(), divider).replace("^col^", ":"));
		if (path.contains("\\")) {
			throw new IllegalArgumentException("Error 13");
		}
		String[] nodes = path.split("/");
		if (nodes.length != 1 || nodes[0].isEmpty()) {
			throw new IllegalArgumentException("Error 8/11: bad container path");
		}
		String node = nodes[0];
		// Non-"dim%" names are matched against the client's dimension list by their path with odd characters removed.
		if (!node.startsWith("dim%") && !node.replaceAll("[^a-zA-Z0-9_]+", "").equals(node)) {
			throw new IllegalArgumentException("Error 18: bad dimension name");
		}
		return node;
	}

	private static String restoreFormatting(String s) {
		return s.replace("^ast^", "*").replace("-", "_").replace("^min^", "-");
	}
}
