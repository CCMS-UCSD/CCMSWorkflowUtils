package edu.ucsd.workflow.test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import edu.ucsd.util.FileIOUtils;
import edu.ucsd.util.WorkflowParameterUtils;

public class CopyLegacyWorkflow
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -cp CCMSWorkflowUtils.jar " +
		"edu.ucsd.workflow.test.CopyLegacyWorkflow" +
		"\n\t-file   <LegacyWorkflowSpecificationFile>" +
		"\n\t-query  <WorkflowParameterQueryString>" +
		"\n\t[-tasks <PathToTaskSpace>]";
	private static final File DEFAULT_TASK_SPACE =
		new File("/data/ccms-data/tasks");
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		CopyLegacyWorkflowOperation copy = extractArguments(args);
		if (copy == null)
			die(USAGE);
		// report query operation
		System.out.println(String.format("Copying legacy workflow " +
			"specification file \"%s\" to all tasks matching the following " +
			"query:", copy.specificationFile.getAbsolutePath()));
		for (String key : copy.query.keySet())
			System.out.println(String.format("    %s = %s",
				key, copy.query.get(key)));
		// traverse task space directory
		for (File user : copy.taskSpace.listFiles()) {
			// only process valid task directories
			if (user == null || user.isDirectory() == false ||
				user.canRead() == false || user.canWrite() == false)
				continue;
			for (File task : user.listFiles()) {
				if (task == null || task.isDirectory() == false ||
					task.canRead() == false || task.canWrite() == false)
					continue;
				// fetch params.xml
				Map<String, Collection<String>> parameters = null;
				try {
					parameters = WorkflowParameterUtils.extractParameters(
						FileIOUtils.parseXML(
							new File(task, "params/params.xml")));
				} catch (Throwable error) {
				} finally {
					// if this task doesn't have a valid params.xml, skip it
					if (parameters == null || parameters.isEmpty())
						continue;
				}
				// run query against workflow parameters
				boolean passed = true;
				for (String key : copy.query.keySet()) {
					Collection<String> values = parameters.get(key);
					// this query fails if this task doesn't have a parameter
					// value matching each element of the query
					if (values == null || values.isEmpty() ||
						values.contains(copy.query.get(key)) == false) {
						passed = false;
						break;
					}
				}
				// if the query passed, copy the legacy workflow specification
				// file into this task's "workflow" subdirectory
				if (passed) {
					File destination = new File(
						task, "workflow/" + copy.specificationFile.getName());
					try {
						FileUtils.copyFile(copy.specificationFile, destination);
					} catch (Throwable error) {
						die(String.format("There was an error copying " +
							"legacy workflow specification file \"%s\" to " +
							"task directory destination \"%s\"",
							copy.specificationFile.getAbsolutePath(),
							destination.getAbsolutePath()), error);
					}
					System.out.println(String.format("Copied to \"%s\"",
						destination.getAbsolutePath()));
				}
			}
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each legacy workflow copy operation.
	 */
	private static class CopyLegacyWorkflowOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File taskSpace;
		private File specificationFile;
		private Map<String, String> query;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public CopyLegacyWorkflowOperation(
			File taskSpace, File specificationFile, String queryString
		) throws IOException {
			// verify task space
			if (taskSpace != null)
				this.taskSpace = taskSpace;
			else this.taskSpace = DEFAULT_TASK_SPACE;
			if (this.taskSpace.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format("Task space \"%s\" must be a directory.",
						this.taskSpace.getAbsolutePath()));
			else if (this.taskSpace.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Task space \"%s\" must be readable.",
						this.taskSpace.getAbsolutePath()));
			else if (this.taskSpace.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Task space \"%s\" must be writable.",
						this.taskSpace.getAbsolutePath()));
			// validate specification file to be copied into legacy tasks
			if (specificationFile == null)
				throw new NullPointerException(
					"Specification file cannot be null.");
			else if (specificationFile.isDirectory())
				throw new IllegalArgumentException(
					String.format("Specification file \"%s\" " +
						"must be a normal (non-directory) file.",
						specificationFile.getAbsolutePath()));
			else if (specificationFile.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Specification file \"%s\" must be readable.",
						specificationFile.getAbsolutePath()));
			this.specificationFile = specificationFile;
			// parse query string
			if (queryString == null)
				throw new NullPointerException("Query string cannot be null.");
			String[] queries = queryString.split("&");
			this.query = new HashMap<String, String>(queries.length);
			for (String query : queries) {
				String[] tokens = query.split("=");
				if (tokens.length != 2)
					throw new IllegalArgumentException(
						String.format("Expected a query string encoding " +
							"workflow parameter queries in the form " +
							"\"key=value\", but found query \"%s\".", query));
				else this.query.put(tokens[0], tokens[1]);
			}
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static CopyLegacyWorkflowOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File taskSpace = null;
		File specificationFile = null;
		String query = null;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-tasks"))
					taskSpace = new File(value);
				else if (argument.equals("-file"))
					specificationFile = new File(value);
				else if (argument.equals("-query"))
					query = value;
				else return null;
			}
		}
		try {
			return new CopyLegacyWorkflowOperation(
				taskSpace, specificationFile, query);
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
			message = "There was an error copying " +
				"a legacy workflow specification file";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
