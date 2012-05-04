package edu.ucsd.workflow;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

public class FilterCollection
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool filterCollection" +
		"\n\t-input <InputDirectory>" +
		"\n\t-output <OutputDirectory>" +
		"\n\t-pattern <FilenamePattern>";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		FilterCollectionOperation filter = extractArguments(args);
		if (filter == null)
			die(USAGE);
		// read all files in the input directory, and copy
		// all matching files to the output directory
		try {
			for (File file : filter.inputDirectory.listFiles(filter.filter))
				FileUtils.copyFileToDirectory(file, filter.outputDirectory);
		} catch (Throwable error) {
			die(null, error);
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each filter collection operation.
	 */
	private static class FilterCollectionOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File inputDirectory;
		private File outputDirectory;
		private FilenameFilter filter;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public FilterCollectionOperation(
			File inputDirectory, File outputDirectory, String filenamePattern
		) throws IOException {
			// validate input directory
			if (inputDirectory == null)
				throw new NullPointerException(
					"Input directory cannot be null.");
			else if (inputDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format("Input directory \"%s\" must be a directory.",
						inputDirectory.getAbsolutePath()));
			else if (inputDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input directory \"%s\" must be readable.",
						inputDirectory.getAbsolutePath()));
			this.inputDirectory = inputDirectory;
			// validate output directory
			if (outputDirectory == null)
				throw new NullPointerException(
					"Output directory cannot be null.");
			else if (outputDirectory.isFile())
				throw new IllegalArgumentException(
					String.format(
						"Output directory \"%s\" must be a directory.",
						outputDirectory.getAbsolutePath()));
			this.outputDirectory = outputDirectory;
			// ensure that output directory exists
			this.outputDirectory.mkdirs();
			// set filename filter from provided pattern
			this.filter = new CollectionFilter(filenamePattern);
		}
	}
	
	/**
	 * FilenameFilter implementing the provided filename pattern filter.
	 */
	private static class CollectionFilter
	implements FilenameFilter {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private Pattern filenamePattern;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public CollectionFilter(String filenamePattern) {
			// validate filename pattern
			if (filenamePattern == null)
				throw new NullPointerException(
					"Filename pattern cannot be null.");
			// process filename pattern
			this.filenamePattern = processFilenamePattern(filenamePattern);
			if (this.filenamePattern == null)
				throw new NullPointerException("Filename pattern must " +
					"be parsable into a valid regular expression.");
		}
		
		/*====================================================================
		 * Public interface methods
		 *====================================================================*/
		public boolean accept(File directory, String filename) {
			if (filename == null)
				return false;
			else return filenamePattern.matcher(filename).matches();
		}
		
		/*====================================================================
		 * Convenience methods
		 *====================================================================*/
		private Pattern processFilenamePattern(String filenamePattern) {
			if (filenamePattern == null)
				return null;
			try {
				// escape all .'s
				filenamePattern = filenamePattern.replaceAll("\\.", "\\\\.");
				// replace all *'s with ".*" to make this a proper regex
				filenamePattern = filenamePattern.replaceAll("\\*", ".*");
				// return compiled regex
				return Pattern.compile(filenamePattern);
			} catch (Throwable error) {
				return null;
			}
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static FilterCollectionOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File inputDirectory = null;
		File outputDirectory = null;
		String filenamePattern = null;
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
					outputDirectory = new File(value);
				else if (argument.equals("-pattern"))
					filenamePattern = value;
				else return null;
			}
		}
		try {
			return new FilterCollectionOperation(
				inputDirectory, outputDirectory, filenamePattern);
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
			message = "There was an error merging workflow output files";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
