package edu.ucsd.workflow;

import edu.ucsd.data.ProteoSAFeFile;

public class CountSpectra
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -cp CCMSWorkflowUtils.jar " +
		"edu.ucsd.workflow.CountSpectra <File>";;
		
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		// get argument file
		ProteoSAFeFile file = extractArgument(args);
		if (file == null)
			die(USAGE);
		// count spectra in argument file
		Integer spectra = file.getSpectra();
		if (spectra == null)
			System.out.println("0");
		else System.out.println(Integer.toString(spectra));
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static ProteoSAFeFile extractArgument(String[] args) {
		if (args == null || args.length < 1)
			die(String.format("Please provide an argument file " +
				"for which to count spectra:\n%s", USAGE));
		else if (args.length > 1)
			die(String.format("Expected at most one argument:\n%s", USAGE));
		// get argument file
		ProteoSAFeFile file = new ProteoSAFeFile(args[0]);
		// verify specified file
		if (file.exists() == false)
			die(String.format("Argument file [%s] could not be found.",
				file.getAbsolutePath()));
		else if (file.isFile() == false)
			die(String.format("Argument file [%s] is not a regular file.",
				file.getAbsolutePath()));
		else if (file.canRead() == false)
			die(String.format("Argument file [%s] is not readable.",
				file.getAbsolutePath()));
		return file;
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error counting spectra in argument file";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
