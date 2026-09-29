package io.github.steelaspect.sharedwaypoints.xaero;

import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.Locale;

/**
 * Builds Xaero's Minimap waypoint-share chat messages.
 *
 * <p>Format (checked against the decompiled {@code WaypointSharingHandler} of Xaero's Minimap 26.5.0 for
 * Fabric 1.21.11):
 *
 * <pre>
 * xaero-waypoint:&lt;name&gt;:&lt;initials&gt;:&lt;x&gt;:&lt;y&gt;:&lt;z&gt;:&lt;colorIndex&gt;:&lt;rotate&gt;:&lt;yaw&gt;:Internal-&lt;dim&gt;-waypoints
 * </pre>
 *
 * <p>Xaero reads this from <b>system</b> chat as well as player chat. Its {@code ChatListener.handleSystemMessage}
 * mixin checks every non-action-bar system message for {@code "xaero-waypoint:"}, hides the raw text and shows
 * "Server shared a waypoint called "&lt;name&gt;" from &lt;dim&gt;! [Add]" instead. So the server can send the
 * line as a normal system message.
 *
 * <p>Xaero escapes text fields itself, and this class does the same so names round-trip exactly:
 * {@code :} becomes {@code ^col^}, {@code -} becomes {@code ^min^}, {@code _} becomes {@code -} and {@code *}
 * becomes {@code ^ast^}. The dimension field goes through the same encoding, so {@code the_nether} is sent as
 * {@code the-nether}, exactly as Xaero sends it. Xaero reads the dimension as the text between
 * {@code "Internal-"} and the <em>last</em> {@code -}, which is why the trailing {@code -waypoints} is needed.
 */
public final class XaeroShareFormat {
	public static final String SHARE_PREFIX = "xaero-waypoint:";

	private XaeroShareFormat() {
	}

	/** The complete share line for a waypoint, ready to send as a system chat message. */
	public static String shareMessage(Waypoint waypoint) {
		return SHARE_PREFIX
				+ encode(waypoint.name()) + ':'
				+ encode(initials(waypoint.name())) + ':'
				+ waypoint.x() + ':'
				+ waypoint.y() + ':'
				+ waypoint.z() + ':'
				+ waypoint.category().xaeroColorIndex() + ':'
				+ "false" + ':' // rotate on teleport
				+ "0" + ':' // yaw
				+ destination(waypoint.dimension());
	}

	/** {@code Internal-<dim>-waypoints}, the "add to this dimension of the current server" destination. */
	static String destination(String dimensionId) {
		return "Internal-" + encode(containerKey(dimensionId)) + "-waypoints";
	}

	/**
	 * Xaero's name for a dimension: {@code overworld}, {@code the_nether} and {@code the_end} for vanilla
	 * (matched against the client's dimension list), or its directory name {@code dim%<namespace>$<path>} for
	 * modded dimensions ({@code /} becomes {@code %} and {@code .} becomes {@code ,}).
	 */
	static String containerKey(String dimensionId) {
		if (dimensionId.equals(Dimensions.OVERWORLD)) {
			return "overworld";
		}
		if (dimensionId.equals(Dimensions.NETHER)) {
			return "the_nether";
		}
		if (dimensionId.equals(Dimensions.END)) {
			return "the_end";
		}
		int colon = dimensionId.indexOf(':');
		String namespace = colon >= 0 ? dimensionId.substring(0, colon) : "minecraft";
		String path = colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
		return "dim%" + namespace + "$" + path.replace('/', '%').replace('.', ',');
	}

	/** One upper-case letter for the minimap icon (Xaero allows 1-3 characters). */
	static String initials(String name) {
		for (int i = 0; i < name.length(); ) {
			int codePoint = name.codePointAt(i);
			if (Character.isLetterOrDigit(codePoint)) {
				return new String(Character.toChars(codePoint)).toUpperCase(Locale.ROOT);
			}
			i += Character.charCount(codePoint);
		}
		return new String(Character.toChars(name.codePointAt(0)));
	}

	/** Xaero's escaping for text fields (same order as Xaero: colons first, then its "removeFormatting"). */
	static String encode(String text) {
		return text
				.replace(":", "^col^")
				.replace("-", "^min^")
				.replace("_", "-")
				.replace("*", "^ast^");
	}
}
