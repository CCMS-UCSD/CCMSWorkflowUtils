package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.TransformerException;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import edu.ucsd.util.FileIOUtils;

public class TaskResultAnalyzer
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -cp CCMSWorkflowUtils.jar " +
		"edu.ucsd.workflow.TaskResultAnalyzer" +
		"\n\t[-directory <TaskDirectory>] OR" +
		"\n\t[-workflow <WorkflowID> -header <HeaderIndex> [-printTasks]]" +
		"\n\t-output <OutputFile>";
	private static final String USER_NOT_DIRECTORY =
		"Non-directory file found under users directory.";
	private static final String USER_NOT_READABLE =
		"User directory not readable.";
	private static final String TASK_NOT_DIRECTORY =
		"Non-directory file found under tasks directory.";
	private static final String TASK_NOT_READABLE =
		"Task directory not readable.";
	private static final String PARAMETERS_NOT_FOUND =
		"Task parameters file not found.";
	private static final String PARAMETERS_NOT_READABLE =
		"Task parameters file not readable.";
	private static final String PARAMETERS_POORLY_FORMATTED =
		"Task parameters file contains poorly formatted XML.";
	private static final String WORKFLOW_NOT_FOUND =
		"Workflow parameter not found in task parameters file.";
	private static final String RESULT_NOT_DIRECTORY =
		"Non-directory file found where result directory was expected.";
	private static final String RESULT_DIRECTORY_NOT_READABLE =
		"Task result directory not readable.";
	private static final String RESULT_NOT_FOUND =
		"Task result file not found.";
	private static final String MULTIPLE_RESULT_FILES_FOUND =
		"More than one result file was found.";
	private static final String RESULT_NOT_READABLE =
		"Task result file not readable.";
	private static final String HEADER_NOT_FOUND =
		"Task result header line not found.";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		TaskResultAnalysis analysis = extractArguments(args);
		if (analysis == null)
			die(USAGE);
		// determine tool mode, invoke proper action
		if (analysis.directory != null)
			search(analysis);
		else analyze(analysis);
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each task result analysis operation.
	 */
	private static class TaskResultAnalysis {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File directory;
		private File output;
		private String workflow;
		private Integer header;
		private Boolean printTasks;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public TaskResultAnalysis(
			File output, File directory, String workflow, String header,
			boolean printTasks
		) throws IOException {
			// determine tool mode
			RuntimeException searchError = null;
			try { validateSearch(directory, output); }
			catch (RuntimeException error) { searchError = error; }
			RuntimeException analyzeError = null;
			try { this.header = validateAnalysis(output, workflow, header); }
			catch (RuntimeException error) { analyzeError = error; }
			// if both modes failed, then there is
			// a legitimate command line problem
			if (searchError != null && analyzeError != null) {
				// if directory is not null, then the user
				// was probably trying to use search mode
				if (directory != null)
					throw searchError;
				else throw analyzeError;
			}
			// if only search mode failed, then it's analyze mode
			else if (searchError != null) {
				this.output = output;
				this.workflow = workflow;
				this.printTasks = printTasks;
			}
			// if only analyze mode failed (or somehow neither failed),
			// then it's search mode
			else {
				this.directory = directory;
				// attempt to create output file and test its writeability
				if (output.createNewFile() == false ||
					output.canWrite() == false)
					throw new IllegalArgumentException(
						String.format("Output file \"%s\" must be writable.",
							output.getAbsolutePath()));
				this.output = output;
			}
		}
		
		/*====================================================================
		 * Convenience methods
		 *====================================================================*/
		private void validateSearch(File directory, File output) {
			// validate task directory
			if (directory == null)
				throw new NullPointerException(
					"Task directory cannot be null.");
			else if (directory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format("Task directory \"%s\" must be a directory.",
						directory.getAbsolutePath()));
			else if (directory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Task directory \"%s\" must be readable.",
						directory.getAbsolutePath()));
			// validate output file
			if (output == null)
				throw new NullPointerException("Output file cannot be null.");
			else if (output.exists())
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must not exist before " +
						"analyzing task results.", output.getAbsolutePath()));
		}
		
		private int validateAnalysis(
			File output, String workflow, String header
		) {
			// validate output file written from previous search
			if (output == null)
				throw new NullPointerException("Output file cannot be null.");
			else if (output.isFile() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be a regular file.",
						output.getAbsolutePath()));
			else if (output.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be readable.",
						output.getAbsolutePath()));
			// validate workflow ID string
			if (workflow == null)
				throw new NullPointerException("Workflow ID cannot be null.");
			else if (workflow.trim().equals(""))
				throw new IllegalArgumentException("Workflow ID cannot be " +
					"an empty string or contain only whitespace.");
			// validate header index
			if (header == null)
				throw new NullPointerException("Header index cannot be null.");
			return Integer.parseInt(header);
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static TaskResultAnalysis extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File directory = null;
		File output = null;
		String workflow = null;
		String header = null;
		boolean printTasks = false;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else if (argument.equals("-printTasks"))
				printTasks = true;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-directory"))
					directory = new File(value);
				else if (argument.equals("-output"))
					output = new File(value);
				else if (argument.equals("-workflow"))
					workflow = value;
				else if (argument.equals("-header"))
					header = value;
				else return null;
			}
		}
		try {
			return new TaskResultAnalysis(
				output, directory, workflow, header, printTasks);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static void search(TaskResultAnalysis analysis) {
		if (analysis == null)
			return;
		// read task parameter files, gather information
		// about each task, write to output file
		PrintWriter output = null;
		try {
			// set up task statistics data structures
			// workflow name -> set of header lines found ->
			// task IDs with that header line
			Map<String, Map<String, Set<String>>> headers =
				new HashMap<String, Map<String, Set<String>>>();
			// user or task name -> set of errors found
			Map<String, Set<String>> errors =
				new HashMap<String, Set<String>>();
			// iterate over user task directories
			File[] users = analysis.directory.listFiles();
			for (File user : users) {
				if (user.isDirectory() == false) {
					addError(errors, user.getName(), USER_NOT_DIRECTORY);
					continue;
				} else if (user.canRead() == false) {
					addError(errors, user.getName(), USER_NOT_READABLE);
					continue;
				}
				// iterate over this user's task directories
				File[] tasks = user.listFiles();
				for (File task : tasks) {
					String taskName = user.getName() + "/" + task.getName();
					if (task.isDirectory() == false) {
						addError(errors, taskName, TASK_NOT_DIRECTORY);
						continue;
					} else if (task.canRead() == false) {
						addError(errors, taskName, TASK_NOT_READABLE);
						continue;
					}
					// get this task's parameters file
					File params = new File(task, "params/params.xml");
					if (params.exists() == false) {
						addError(errors, taskName, PARAMETERS_NOT_FOUND);
						continue;
					} else if (params.canRead() == false) {
						addError(errors, taskName, PARAMETERS_NOT_READABLE);
						continue;
					}
					// get this task's workflow
					String workflow = null;
					try {
						workflow = getWorkflow(params);
					} catch (IOException error) {
						addError(errors, taskName, PARAMETERS_POORLY_FORMATTED);
						continue;
					}
					if (workflow == null) {
						addError(errors, taskName, WORKFLOW_NOT_FOUND);
						continue;
					}
					// get this task's result directory
					File resultDirectory = new File(task, "result");
					if (resultDirectory.exists() == false) {
						addError(errors, taskName, RESULT_NOT_FOUND);
						continue;
					} else if (resultDirectory.isDirectory() == false) {
						addError(errors, taskName, RESULT_NOT_DIRECTORY);
						continue;
					} else if (resultDirectory.canRead() == false) {
						addError(errors, taskName,
							RESULT_DIRECTORY_NOT_READABLE);
						continue;
					}
					// validate this task's result directory
					File[] results = resultDirectory.listFiles();
					if (results == null || results.length < 1) {
						addError(errors, taskName, RESULT_NOT_FOUND);
						continue;
					} else if (results.length > 1) {
						addError(errors, taskName, MULTIPLE_RESULT_FILES_FOUND);
						continue;
					}
					// get this task's result file
					File result = results[0];
					if (result.exists() == false) {
						addError(errors, taskName, RESULT_NOT_FOUND);
						continue;
					} else if (result.canRead() == false) {
						addError(errors, taskName, RESULT_NOT_READABLE);
						continue;
					}
					// get this result file's header line
					String header = getHeader(result);
					if (header == null) {
						addError(errors, taskName, HEADER_NOT_FOUND);
						continue;
					}
					addHeader(headers, workflow, header, taskName);
				}
			}
			// set up output file writer
			output = new PrintWriter(analysis.output);
			// write header counts to both console and output file
			System.out.println();
			println("Unique header lines found:", output);
			for (String workflow : headers.keySet()) {
				System.out.print(workflow);
				System.out.print(" = ");
				Map<String, Set<String>> workflowHeaders =
					headers.get(workflow);
				System.out.println(workflowHeaders.size());
				// only write the actual header content to output file
				int i = 1;
				for (String header : workflowHeaders.keySet()) {
					output.print(workflow);
					output.print("[");
					output.print(i++);
					output.print("]\t");
					output.println(header);
					Set<String> tasks = workflowHeaders.get(header);
					for (String task : tasks)
						output.println(task);
				}
			}
			// write error count to both console and output file
			print("\nErroneous tasks found: (", output);
			print(errors.size(), output);
			println(")", output);
			// only write the actual error content to output file
			for (String directory : errors.keySet()) {
				output.print(directory);
				output.print(" = ");
				StringBuffer message = new StringBuffer();
				for (String error : errors.get(directory)) {
					message.append(error);
					message.append(", ");
				}
				// strip off last comma and space
				message.setLength(message.length() - 2);
				output.println(message.toString());
			}
		} catch (Throwable error) {
			die(null, error);
		} finally {
			if (output != null) try {
				output.close();
			} catch (Throwable error) {}
		}
	}
	
	private static void addHeader(
		Map<String, Map<String, Set<String>>> map,
		String workflow, String header, String task
	) {
		if (map == null || workflow == null || header == null || task == null)
			return;
		Map<String, Set<String>> headers = map.get(workflow);
		if (headers == null)
			headers = new HashMap<String, Set<String>>();
		Set<String> tasks = headers.get(header);
		if (tasks == null)
			tasks = new HashSet<String>();
		tasks.add(task);
		headers.put(header, tasks);
		map.put(workflow, headers);
	}
	
	private static void addError(
		Map<String, Set<String>> map, String resource, String error
	) {
		if (map == null || resource == null || error == null)
			return;
		Set<String> errors = map.get(resource);
		if (errors == null)
			errors = new HashSet<String>();
		errors.add(error);
		map.put(resource, errors);
	}
	
	private static String getWorkflow(File params)
	throws IOException, TransformerException {
		if (params == null)
			return null;
		Document document = FileIOUtils.parseXML(params);
		if (document == null)
			return null;
		Node node =
			XPathAPI.selectSingleNode(document, "//parameter[@name='tool']");
		if (node == null)
			return null;
		else return node.getFirstChild().getNodeValue();
	}
	
	private static String getHeader(File result) {
		if (result == null)
			return null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(result));
			return reader.readLine();
		} catch (IOException error) {
			die(String.format("Error reading header line of file \"%s\"",
				result.getAbsolutePath()), error);
			return null;
		} finally {
			if (reader != null) try {
				reader.close();
			} catch (IOException error) {}
		}
	}
	
	private static void print(Object message, PrintWriter writer) {
		if (message == null)
			return;
		System.out.print(message);
		if (writer != null)
			writer.print(message);
	}
	
	private static void println(Object message, PrintWriter writer) {
		if (message == null)
			message = "";
		print(message + "\n", writer);
	}
	
	private static void analyze(TaskResultAnalysis analysis) {
		if (analysis == null)
			return;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(analysis.output));
			String line = null;
			boolean found = false;
			while ((line = reader.readLine()) != null) {
				if (found == false) {
					// determine if this line corresponds to the workflow
					// result file header that is being analyzed
					if (line.startsWith(analysis.workflow + "[")) {
						line = line.substring(analysis.workflow.length() + 1);
						String index = line.substring(0, line.indexOf("]"));
						// if this line has the workflow ID and header index
						// that is being analyzed, then analyze it properly
						if (index.equals(Integer.toString(analysis.header))) {
							if (analysis.printTasks)
								found = true;
							else System.out.println(
								line.substring(line.indexOf("]") + 2));
						}
					}
				} else {
					// if a new header block is found, quit
					if (line.contains("["))
						break;
					else System.out.println(line);
				}
			}
		} catch (Throwable error) {
			die(null, error);
		} finally {
			if (reader != null) try {
				reader.close();
			} catch (IOException error) {}
		}
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error analyzing task results";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
