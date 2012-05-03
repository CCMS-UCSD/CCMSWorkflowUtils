package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;

import edu.ucsd.util.FileIOUtils;

public class SelectFirst
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool selectFirst" +
		"\n\t-input <InputDirectory>" +
		"\n\t-output <CopyOfFirstInputFile>";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		FirstSelection select = extractArguments(args);
		if (select == null)
			die(USAGE);
		// just grab the first file from the input directory,
		// and copy it to the output file
		File[] files = select.inputDirectory.listFiles();
		if (files == null || files.length < 1)
			die(String.format(
				"No input files were found in directory \"%s\"",
				select.inputDirectory.getAbsolutePath()));
		File file = files[0];
		try {
			if (FileIOUtils.copyFile(file, select.output) == false)
				throw new IOException();
		} catch (Throwable error) {
			die(String.format("There was an error copying " +
				"input file \"%s\" to output file \"%s\"",
				file.getAbsolutePath(), select.output.getAbsolutePath()),
				error);
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each operation to select the first
	 * file from a directory, and copy it to a separate output file.
	 */
	private static class FirstSelection {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File inputDirectory;
		private File output;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public FirstSelection(File inputDirectory, File output)
		throws IOException {
			// validate input directory
			if (inputDirectory == null)
				throw new NullPointerException(
					"Input directory cannot be null.");
			else if (inputDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format(
						"Input directory \"%s\" must be a directory.",
						inputDirectory.getAbsolutePath()));
			else if (inputDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input directory \"%s\" must be readable.",
						inputDirectory.getAbsolutePath()));
			this.inputDirectory = inputDirectory;
			// attempt to create output file and test its writeability
			if (output.createNewFile() == false || output.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be writable.",
						output.getAbsolutePath()));
			this.output = output;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static FirstSelection extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File inputDirectory = null;
		File output = null;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-input"))
					inputDirectory = new File(value);
				else if (argument.equals("-output"))
					output = new File(value);
				else return null;
			}
		}
		try {
			return new FirstSelection(inputDirectory, output);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error copying the first file " +
				"of the input directory";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
