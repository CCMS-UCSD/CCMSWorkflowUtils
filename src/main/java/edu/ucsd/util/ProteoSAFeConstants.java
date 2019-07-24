package edu.ucsd.util;

import java.util.regex.Pattern;

public class ProteoSAFeConstants
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	// constants pertaining to ProteoSAFe params.xml result/peak file mapping
	public static final String EXTRACTED_FILE_DELIMITER = "#";
	public static final Pattern EXTRACTED_FILE_DELIMITER_PATTERN =
		Pattern.compile("((?i)mztab|mzid)" + EXTRACTED_FILE_DELIMITER);
}
