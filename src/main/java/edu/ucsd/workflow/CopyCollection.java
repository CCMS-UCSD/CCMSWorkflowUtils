package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

public class CopyCollection
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool copyCollection" +
		"\n\t-collection <CollectionName>" +
		"\n\t-source <SourceDirectory>" +
		"\n\t-destination <DestinationDirectory>";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		CopyCollectionOperation copy = extractArguments(args);
		if (copy == null)
			die(USAGE);
		// copy the named collection subdirectory from the
		// source directory to the destination directory
		try {
			FileUtils.copyDirectory(
				copy.sourceDirectory, copy.destinationDirectory);
		} catch (Throwable error) {
			die(null, error);
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each copy collection operation.
	 */
	private static class CopyCollectionOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File sourceDirectory;
		private File destinationDirectory;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public CopyCollectionOperation(
			String collection, File sourceDirectory, File destinationDirectory
		) throws IOException {
			// validate source directory
			if (collection == null)
				throw new NullPointerException(
					"Collection name cannot be null.");
			else if (sourceDirectory == null)
				throw new NullPointerException(
					"Source directory cannot be null.");
			sourceDirectory = new File(sourceDirectory, collection);
			if (sourceDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format("Source directory \"%s\" must be a " +
						"directory.", sourceDirectory.getAbsolutePath()));
			else if (sourceDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Source directory \"%s\" must be readable.",
						sourceDirectory.getAbsolutePath()));
			this.sourceDirectory = sourceDirectory;
			// validate destination directory
			if (destinationDirectory == null)
				throw new NullPointerException(
					"Destination directory cannot be null.");
			else if (destinationDirectory.isFile())
				throw new IllegalArgumentException(
					String.format(
						"Destination directory \"%s\" must be a directory.",
						destinationDirectory.getAbsolutePath()));
			this.destinationDirectory = destinationDirectory;
			// ensure that destination directory exists
			this.destinationDirectory.mkdirs();
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static CopyCollectionOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		String collection = null;
		File sourceDirectory = null;
		File destinationDirectory = null;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-collection"))
					collection = value;
				else if (argument.equals("-source"))
					sourceDirectory = new File(value);
				else if (argument.equals("-destination"))
					destinationDirectory = new File(value);
				else return null;
			}
		}
		try {
			return new CopyCollectionOperation(
				collection, sourceDirectory, destinationDirectory);
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
			message = "There was an error copying a collection";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
