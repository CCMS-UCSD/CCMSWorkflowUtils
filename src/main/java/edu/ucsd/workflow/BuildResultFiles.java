package edu.ucsd.workflow;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.ucsd.result.ResultFactory;
import edu.ucsd.result.ResultViewXMLUtils;
import edu.ucsd.result.parsers.Result;
import edu.ucsd.util.FileIOUtils;
import edu.ucsd.util.OnDemandLoader;

public class BuildResultFiles
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE =
		"java -cp CCMSWorkflowUtils.jar edu.ucsd.workflow.BuildResultFiles" +
		"\n\t-task   <TaskID>" + 
		"\n\t-result <ResultXMLFile>" + 
		"\n\t-block  <ResultXMLBlockName> <ResultFile> <OutputDirectory>";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		BuildResultFilesOperation build = extractArguments(args);
		if (build == null)
			die(USAGE);
		// get temp directory
		File tempDirectory = new File("temp");
		tempDirectory.mkdirs();
		// pre-build all argument result files
		for (String block : build.results.keySet()) {
			ImmutablePair<File, File> files = build.results.get(block);
			// get this block's data specification
			Element dataSpec = ResultViewXMLUtils.getDataSpecification(
				ResultViewXMLUtils.getBlockSpecification(
					build.resultXML, block));
			// get this block's result
			Result result = ResultFactory.createResult(
				dataSpec, files.getLeft(), tempDirectory, files.getRight(),
				build.taskID, block, null);
			// load this block's result
			if (OnDemandLoader.load(result) == false)
				die(String.format("Result file [%s] for result " +
					"view block [%s] could not be written.",
					files.getLeft().getAbsolutePath(), block));
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each build result files operation.
	 */
	private static class BuildResultFilesOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private String                                 taskID;
		private Document                               resultXML;
		private Map<String, ImmutablePair<File, File>> results;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public BuildResultFilesOperation(
			String taskID, File resultXML,
			Map<String, ImmutablePair<File, File>> results
		) {
			// validate task ID
			if (taskID == null)
				throw new NullPointerException(
					"Argument task ID cannot be null.");
			else this.taskID = taskID;
			// validate result.xml file
			if (resultXML == null)
				throw new NullPointerException(
					"Argument result.xml file cannot be null.");
			else if (resultXML.isFile() == false ||
				resultXML.canRead() == false)
				throw new IllegalArgumentException(
					"Argument result.xml file must be a readable file.");
			// parse result.xml file into DOM document
			try {
				this.resultXML = FileIOUtils.parseXML(resultXML);
			} catch (RuntimeException error) {
				throw error;
			} catch (Throwable error) {
				throw new RuntimeException(error);
			}
			// validate result inputs
			if (results == null || results.isEmpty())
				throw new NullPointerException(
					"At least one distinct set of view name, result " +
					"file and output directory must be provided.");
			for (String block : results.keySet()) {
				ImmutablePair<File, File> files = results.get(block);
				// validate result file
				File resultFile = files.getLeft();
				if (resultFile == null)
					throw new NullPointerException(
						"Argument result file cannot be null.");
				else if (resultFile.isFile() == false ||
					resultFile.canRead() == false)
					throw new IllegalArgumentException(
						"Argument result file must be a readable file.");
				// validate output directory
				File outputDirectory = files.getRight();
				if (outputDirectory == null)
					throw new NullPointerException(
						"Output directory cannot be null.");
				else if (outputDirectory.isDirectory() == false)
					throw new IllegalArgumentException(String.format(
						"Output directory [%s] must be a directory.",
						outputDirectory.getAbsolutePath()));
				else if (outputDirectory.canWrite() == false)
					throw new IllegalArgumentException(String.format(
						"Output directory [%s] must be writable.",
						outputDirectory.getAbsolutePath()));
			}
			this.results = results;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static BuildResultFilesOperation extractArguments(
		String[] args
	) {
		if (args == null || args.length < 1)
			return null;
		String taskID = null;
		File resultXML = null;
		Map<String, ImmutablePair<File, File>> results =
			new LinkedHashMap<String, ImmutablePair<File, File>>();
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			i++;
			if (i >= args.length)
				return null;
			String value = args[i];
			if (argument.equals("-task"))
				taskID = value;
			else if (argument.equals("-result"))
				resultXML = new File(value);
			else if (argument.equals("-block")) {
				// arguments should be in groups of 3:
				// block name, result file, output directory
				i++;
				if (i >= args.length)
					return null;
				File resultFile = new File(args[i]);
				i++;
				if (i >= args.length)
					return null;
				File outputDirectory = new File(args[i]);
				results.put(value,
					new ImmutablePair<File, File>(resultFile, outputDirectory));
			}
		}
		try {
			return new BuildResultFilesOperation(taskID, resultXML, results);
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
			message =
				"There was an error pre-building ProteoSAFe task result files";
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
