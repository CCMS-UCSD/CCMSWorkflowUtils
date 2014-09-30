package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.ucsd.util.FileIOUtils;

public class CopyCollectionToUserSpace
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool   copyCollectionToUserSpace" +
		"\n\t-params <ProteoSAFeParametersFile>" +
		"\n\t-source <SourceDirectory>" +
		"\n\t-root   <UserSpaceRootDirectory>";
	private static final String WORKFLOW_OUTPUT_COPY_DIRECTORY = "ccms_output";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		UserSpaceCopyOperation copy = extractArguments(args);
		if (copy == null)
			die(USAGE);
		// copy the named collection subdirectory from the source directory
		// to the destination directory, preserving original file paths
		try {
			for (File source : copy.sourceDirectory.listFiles()) {
				String path = getMappedPath(source.getName(), copy.filenames);
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
	 * Struct to maintain context data for each operation to
	 * copy a workflow's output to a user's private data space.
	 */
	private static class UserSpaceCopyOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private Map<String, String> filenames;
		private File                sourceDirectory;
		private File                destinationDirectory;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public UserSpaceCopyOperation(
			File parameters, File sourceDirectory, File userSpaceRoot
		) throws IOException {
			// validate XML parameters file
			if (parameters == null)
				throw new NullPointerException(
					"Parameters file cannot be null.");
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
			// extract relevant parameters from document
			String user = null;
			try {
				// extract workflow user
				Node parameter = XPathAPI.selectSingleNode(
					document, "//parameter[@name='user']");
				if (parameter == null)
					throw new NullPointerException(
						"A \"user\" parameter could not be found " +
						"in the parsed parameters XML document.");
				else user = parameter.getFirstChild().getNodeValue();
				// generate mappings for all submitted filenames
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
							throw new IllegalArgumentException(String.format(
								"\"upload_file_mapping\" parameter value " +
								"\"%s\" is invalid - it should contain two " +
								"tokens separated by a pipe (\"|\") character.",
								value));
						filenames.put(tokens[0], tokens[1]);
					}
				}
			} catch (Throwable error) {
				throw new RuntimeException(error);
			}
			// validate source directory
			if (sourceDirectory == null)
				throw new NullPointerException(
					"Source directory cannot be null.");
			else if (sourceDirectory.isDirectory() == false)
				throw new IllegalArgumentException(String.format(
					"Source directory [%s] must be a directory.",
					sourceDirectory.getName()));
			else if (sourceDirectory.canRead() == false)
				throw new IllegalArgumentException(String.format(
					"Source directory [%s] must be readable.",
					sourceDirectory.getName()));
			this.sourceDirectory = sourceDirectory;
			// validate user space root directory
			if (userSpaceRoot == null)
				throw new NullPointerException(
					"User space root directory cannot be null.");
			else if (userSpaceRoot.isDirectory() == false)
				throw new IllegalArgumentException(String.format(
					"User space root directory [%s] must be a directory.",
					userSpaceRoot.getName()));
			// generate actual workflow output copy destination for this user
			File userRoot = new File(userSpaceRoot, user);
			if (userRoot.isDirectory() == false)
				throw new IllegalArgumentException(String.format(
					"User root directory [%s] must be a directory.",
					userRoot.getName()));
			destinationDirectory =
				new File(userRoot, WORKFLOW_OUTPUT_COPY_DIRECTORY);
			// ensure that destination directory exists
			destinationDirectory.mkdirs();
			if (destinationDirectory.isDirectory() == false)
				throw new IllegalArgumentException(String.format(
					"Workflow output copy directory [%s] must be a directory.",
					destinationDirectory.getName()));
			else if (destinationDirectory.canWrite() == false)
				throw new IllegalArgumentException(String.format(
					"Workflow output copy directory [%s] must be writable.",
					destinationDirectory.getName()));
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static UserSpaceCopyOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File parameters = null;
		File sourceDirectory = null;
		File userSpaceRoot = null;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-params"))
					parameters = new File(value);
				else if (argument.equals("-source"))
					sourceDirectory = new File(value);
				else if (argument.equals("-root"))
					userSpaceRoot = new File(value);
				else return null;
			}
		}
		try {
			return new UserSpaceCopyOperation(
				parameters, sourceDirectory, userSpaceRoot);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static String getMappedPath(
		String filename, Map<String, String> filenames
	) {
		if (filename == null)
			return null;
		else if (filenames == null || filenames.isEmpty())
			return filename;
		// first try to find a literal match for the filename in the map
		String path = filenames.get(filename);
		// if no literal match was found, compare filename bases
		if (path == null) {
			String baseFilename = FilenameUtils.getBaseName(filename);
			String extension = FilenameUtils.getExtension(filename);
			for (String mapped : filenames.keySet()) {
				if (baseFilename.equals(FilenameUtils.getBaseName(mapped))) {
					path = changeExtension(filenames.get(mapped), extension);
					break;
				}
			}
		}
		// if no good match was found, return the original filename
		if (path == null)
			return filename;
		else return path;
	}
	
	private static String changeExtension(String filename, String extension) {
		if (filename == null)
			return null;
		// if the new extension is null, then just remove the extension
		else if (extension == null)
			return String.format("%s%s", FilenameUtils.getPath(filename),
				FilenameUtils.getBaseName(filename));
		// otherwise change the old extension to the new one
		else return String.format("%s%s.%s", FilenameUtils.getPath(filename),
			FilenameUtils.getBaseName(filename), extension);
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
