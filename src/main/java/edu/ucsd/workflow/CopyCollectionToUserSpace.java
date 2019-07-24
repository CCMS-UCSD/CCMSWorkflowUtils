package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import edu.ucsd.data.ProteoSAFeFileMappingContext;
import edu.ucsd.util.FileIOUtils;

public class CopyCollectionToUserSpace
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -cp CCMSWorkflowUtils.jar " +
		"edu.ucsd.workflow.CopyCollectionToUserSpace " +
		"\n\t-source <SourceDirectory>" +
		"\n\t-root   <UserSpaceRootDirectory>" +
		"\n\t-params <ProteoSAFeParametersFile>";
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
				String path = copy.context.getMappedPath(source.getName());
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
		private ProteoSAFeFileMappingContext context;
		private File                         sourceDirectory;
		private File                         destinationDirectory;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public UserSpaceCopyOperation(
			File sourceDirectory, File userSpaceRoot, File parameters
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
				// generate mapping context from params.xml
				context = new ProteoSAFeFileMappingContext(document);
			} catch (RuntimeException error) {
				throw error;
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
					sourceDirectory.getAbsolutePath()));
			else if (sourceDirectory.canRead() == false)
				throw new IllegalArgumentException(String.format(
					"Source directory [%s] must be readable.",
					sourceDirectory.getAbsolutePath()));
			this.sourceDirectory = sourceDirectory;
			// validate user space root directory
			if (userSpaceRoot == null)
				throw new NullPointerException(
					"User space root directory cannot be null.");
			else if (userSpaceRoot.isDirectory() == false)
				throw new IllegalArgumentException(String.format(
					"User space root directory [%s] must be a directory.",
					userSpaceRoot.getAbsolutePath()));
			// generate actual workflow output copy destination for this user
			File userRoot = new File(userSpaceRoot, user);
			if (userRoot.isDirectory() == false)
				throw new IllegalArgumentException(String.format(
					"User root directory [%s] must be a directory.",
					userRoot.getAbsolutePath()));
			destinationDirectory =
				new File(userRoot, WORKFLOW_OUTPUT_COPY_DIRECTORY);
			// ensure that destination directory exists
			try {
				destinationDirectory.mkdirs();
			} catch (Throwable error) {
				throw new RuntimeException(error);
			}
			if (destinationDirectory.isDirectory() == false)
				throw new IllegalArgumentException(String.format(
					"Workflow output copy directory [%s] must be a directory.",
					destinationDirectory.getAbsolutePath()));
			else if (destinationDirectory.canWrite() == false)
				throw new IllegalArgumentException(String.format(
					"Workflow output copy directory [%s] must be writable.",
					destinationDirectory.getAbsolutePath()));
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static UserSpaceCopyOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File sourceDirectory = null;
		File userSpaceRoot = null;
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
				else if (argument.equals("-root"))
					userSpaceRoot = new File(value);
				else if (argument.equals("-params"))
					parameters = new File(value);
				else return null;
			}
		}
		try {
			return new UserSpaceCopyOperation(
				sourceDirectory, userSpaceRoot, parameters);
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
