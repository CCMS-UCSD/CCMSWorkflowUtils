package edu.ucsd.util;

import java.util.concurrent.TimeUnit;

public class CommonUtils
{
	/*========================================================================
	 * Static utility methods
	 *========================================================================*/
	public static Boolean parseBooleanColumn(String value) {
		if (value == null)
			return null;
		else value = value.trim();
		// boolean columns can only be interpreted
		// using standard boolean string values
		if (value.equals("1") ||
			value.equalsIgnoreCase("true") ||
			value.equalsIgnoreCase("yes") ||
			value.equalsIgnoreCase("on"))
			return true;
		else if (value.equals("0") ||
			value.equalsIgnoreCase("false") ||
			value.equalsIgnoreCase("no") ||
			value.equalsIgnoreCase("off"))
			return false;
		// any other value, even though present in the column,
		// cannot be interpreted and thus we call it null
		else return null;
	}
	
	public static String formatMilliseconds(long milliseconds) {
		long hours = TimeUnit.MILLISECONDS.toHours(milliseconds);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
			TimeUnit.HOURS.toMinutes(hours);
		long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
			TimeUnit.HOURS.toSeconds(hours) -
			TimeUnit.MINUTES.toSeconds(minutes);
		long remainder = milliseconds - TimeUnit.HOURS.toMillis(hours) -
			TimeUnit.MINUTES.toMillis(minutes) -
			TimeUnit.SECONDS.toMillis(seconds);
		String suffix = remainder == 0 ? "" : String.format(".%03d", remainder);
		if (hours > 0)
			return String.format("%dh %dm %d%ss",
				hours, minutes, seconds, suffix);
		else if (minutes > 0)
			return String.format("%dm %d%ss", minutes, seconds, suffix);
		else if (seconds > 0 )
			return String.format("%d%ss", seconds, suffix);
		else return String.format("%d ms", milliseconds);
	}
}
