package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import edu.ucsd.util.CommonUtils;
import edu.ucsd.util.FileIOUtils;

public class CopyCollection
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE =
		"java -cp CCMSWorkflowUtils.jar edu.ucsd.workflow.CopyCollection " +
		"\n\t-source         <SourceDirectory>" +
		"\n\t[-collection    <CollectionName>" +
			"(optional subdirectory name under \"-source\")]" +
		"\n\t-destination    <DestinationDirectory>" +
		"\n\t[-preservePaths true/false (default false; " +
			"if specified, source files will be un-flattened by consulting " +
			"params.xml mappings before being copied to the destination)]" +
		"\n\t[-params        <ProteoSAFeParametersFile> " +
			"(if \"-preservePaths\" is set to true)]";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		CopyCollectionOperation copy = extractArguments(args);
		if (copy == null)
			die(USAGE);
		try {
			// if paths are not to be preserved, simply copy
			// the named collection subdirectory from the
			// source directory to the destination directory
			if (copy.preservePaths == false)
				FileUtils.copyDirectory(
					copy.sourceDirectory, copy.destinationDirectory);
			// otherwise, copy each file from the source directory to the
			// destination directory, preserving original file paths
			else for (File source : copy.sourceDirectory.listFiles()) {
				String path =
					FileIOUtils.getMappedPath(source.getName(), copy.filenames);
				File destination = new File(copy.destinationDirectory, path);
				FileUtils.copyFile(source, destination);
			}
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
		private Map<String, String> filenames;
		private File                sourceDirectory;
		private File                destinationDirectory;
		private boolean             preservePaths;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public CopyCollectionOperation(
			File sourceDirectory, String collection, File destinationDirectory,
			Boolean preservePaths, File parameters
		) throws IOException {
			// validate source directory
			if (sourceDirectory == null)
				throw new NullPointerException(
					"Source directory cannot be null.");
			if (collection != null && collection.trim().isEmpty() == false)
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
			// set path preservation flag, if present (default false)
			if (preservePaths == null)
				this.preservePaths = false;
			else this.preservePaths = preservePaths;
			// if paths are to be preserved, consult
			// parameters for file mappings
			if (this.preservePaths) {
				// validate XML parameters file
				if (parameters == null)
					throw new NullPointerException(
						"Parameters file must be provided if " +
						"\"-preservePaths\" is set to true.");
				else if (parameters.isFile() == false)
					throw new IllegalArgumentException(String.format(
						"Parameters file [%s] must be a regular file.",
						parameters.getName()));
				else if (parameters.canRead() == false)
					throw new IllegalArgumentException(String.format(
						"Parameters file [%s] must be readable.",
						parameters.getName()));
				// read XML document from params file
				Document document = FileIOUtils.parseXML(parameters);
				if (document == null)
					throw new NullPointerException(
						"Parameters XML document could not be parsed.");
				// generate mappings for all submitted filenames
				try {
					NodeList mappings = XPathAPI.selectNodeList(
						document, "//parameter[@name='upload_file_mapping']");
					filenames =
						new LinkedHashMap<String, String>(mappings.getLength());
					if (mappings != null && mappings.getLength() > 0) {
						for (int i=0; i<mappings.getLength(); i++) {
							String value =
								mappings.item(i).getFirstChild().getNodeValue();
							String[] tokens = value.split("\\|");
							if (tokens == null || tokens.length != 2)
								throw new IllegalArgumentException(
									String.format("\"upload_file_mapping\" " +
									"parameter value \"%s\" is invalid - it " +
									"should contain two tokens separated by " +
									"a pipe (\"|\") character.", value));
							filenames.put(tokens[0], tokens[1]);
						}
					}
				} catch (RuntimeException error) {
					throw error;
				} catch (Throwable error) {
					throw new RuntimeException(error);
				}
			} else filenames = null;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static CopyCollectionOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File sourceDirectory = null;
		String collection = null;
		File destinationDirectory = null;
		Boolean preservePaths = null;
		File parameters = null;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-source"))
					sourceDirectory = new File(value);
				else if (argument.equals("-collection"))
					collection = value;
				else if (argument.equals("-destination"))
					destinationDirectory = new File(value);
				else if (argument.equals("-preservePaths")) {
					preservePaths = CommonUtils.parseBooleanColumn(value);
					if (preservePaths == null)
						throw new IllegalArgumentException(String.format(
							"Unrecognized value for \"-preservePaths\": [%s]",
							value));
				} else if (argument.equals("-params"))
					parameters = new File(value);
				else return null;
			}
		}
		try {
			return new CopyCollectionOperation(
				sourceDirectory, collection, destinationDirectory,
				preservePaths, parameters);
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
