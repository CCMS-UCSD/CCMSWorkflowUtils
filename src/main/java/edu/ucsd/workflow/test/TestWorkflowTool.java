package edu.ucsd.workflow.test;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class TestWorkflowTool
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -cp CCMSWorkflowUtils.jar " +
		"edu.ucsd.workflow.test.TestWorkflowTool" +
		"\n\t-input   <InputFile>" +
		"\n\t-output  <OutputDirectory>" +
		"\n\t[-copies <NumberOfInputFileCopiesToWriteToOutputDirectory> " +
			"(default 1)]" +
		"\n\t[-wait   <WaitTimeInMilliseconds> (default 0)]";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		long start = System.currentTimeMillis();
		TestToolRunOperation test = extractArguments(args);
		if (test == null)
			die(USAGE);
		// copy input file to output directory the specified number of times
		copyFile(test.inputFile, test.outputDirectory, test.copies);
		long elapsed = System.currentTimeMillis() - start;
		// wait the specified number of milliseconds
		if (elapsed < test.wait) try {
			Thread.sleep(test.wait - elapsed);
		} catch (Throwable error) {
			die(String.format(
				"There was an error waiting the specified %d milliseconds",
				test.wait), error);
		}
	}
	
	public static void copyFile(
		File inputFile, File outputDirectory, int copies
	) {
		// validate arguments
		if (copies < 1)
			return;
		validateInputFile(inputFile);
		validateOutputDirectory(outputDirectory);
		// get input file properties
		String filename = inputFile.getName();
		String basename = FilenameUtils.getBaseName(filename);
		String extension = FilenameUtils.getExtension(filename);
		// copy the input file into the output directory
		// the specified number of times
		for (int i=1; i<=copies; i++) {
			String destinationFilename =
				String.format("%s_%d.%s", basename, i, extension);
			File destination = new File(outputDirectory, destinationFilename);
			try {
				FileUtils.copyFile(inputFile, destination);
			} catch (Throwable error) {
				throw new RuntimeException(String.format(
					"There was an error copying input file [%s] " +
					"to output file [%s].", inputFile.getAbsolutePath(),
					destination.getAbsolutePath()), error);
			}
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each test tool run.
	 */
	private static class TestToolRunOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File inputFile;
		private File outputDirectory;
		private int  copies;
		private long wait;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public TestToolRunOperation(
			File inputFile, File outputDirectory, String copies, String wait
		) {
			// validate input file
			validateInputFile(inputFile);
			this.inputFile = inputFile;
			// validate output directory
			validateOutputDirectory(outputDirectory);
			this.outputDirectory = outputDirectory;
			// parse number of copies
			try {
				this.copies = Integer.parseInt(copies);
			} catch (NumberFormatException error) {
				throw new IllegalArgumentException(String.format(
					"Number of output file copies to write [%s] " +
					"must be parseable as an integer.", copies), error);
			}
			// validate number of copies (must be a positive integer)
			if (this.copies < 1)
				throw new IllegalArgumentException(String.format(
					"Number of output file copies to write [%s] " +
					"must be a positive integer.", copies));
			// parse wait time (in milliseconds)
			try {
				this.wait = Long.parseLong(wait);
			} catch (NumberFormatException error) {
				throw new IllegalArgumentException(String.format(
					"Wait time in milliseconds [%s] " +
					"must be parseable as a long.", wait), error);
			}
			// validate wait time (must be a non-negative integer)
			if (this.wait < 0)
				throw new IllegalArgumentException(String.format(
					"Wait time in milliseconds [%s] " +
					"must be a non-negative integer.", wait));
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static TestToolRunOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File inputFile = null;
		File outputDirectory = null;
		String copies = null;
		String wait = null;
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
					inputFile = new File(value);
				else if (argument.equals("-output"))
					outputDirectory = new File(value);
				else if (argument.equals("-copies"))
					copies = value;
				else if (argument.equals("-wait"))
					wait = value;
				else return null;
			}
		}
		try {
			return new TestToolRunOperation(
				inputFile, outputDirectory, copies, wait);
		} catch (Throwable error) {
			die("There was an error reading command line parameters " +
				"to set up mzTab validation operation.", error);
			return null;
		}
	}
	
	private static void validateInputFile(File inputFile) {
		if (inputFile == null)
			throw new NullPointerException(
				"Input file cannot be null.");
		else if (inputFile.isFile() == false)
			throw new IllegalArgumentException(
				String.format("Input file [%s] must be a regular file.",
					inputFile.getAbsolutePath()));
		else if (inputFile.canRead() == false)
			throw new IllegalArgumentException(
				String.format("Input file [%s] must be readable.",
					inputFile.getAbsolutePath()));
	}
	
	private static void validateOutputDirectory(File outputDirectory) {
		if (outputDirectory == null)
			throw new NullPointerException(
				"Output directory cannot be null.");
		else if (outputDirectory.isDirectory() == false)
			throw new IllegalArgumentException(
				String.format("Output directory [%s] must be a directory.",
					outputDirectory.getAbsolutePath()));
		else if (outputDirectory.canWrite() == false)
			throw new IllegalArgumentException(
				String.format("Output directory [%s] must be writable.",
					outputDirectory.getAbsolutePath()));
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error running the workflow test tool.";
		if (error == null) {
			if (message.endsWith(".") == false)
				message += ".";
		} else {
			if (message.endsWith("."))
				message = message.substring(0, message.length() - 1);
			if (message.endsWith(":") == false)
				message += ":";
		}
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
