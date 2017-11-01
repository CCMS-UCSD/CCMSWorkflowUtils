package edu.ucsd.util;

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
}
