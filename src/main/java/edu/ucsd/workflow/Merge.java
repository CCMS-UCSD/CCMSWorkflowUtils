package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

public class Merge
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool merge" +
		"\n\t-input <InputDirectory>" +
		"\n\t-output <OutputFile>" +
		"\n\t[-header] (if specified, the tool will assume that there is a " +
		"header line that needs to be stripped from all files after the first)";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		MergeOperation merge = extractArguments(args);
		if (merge == null)
			die(USAGE);
		// read all files in the input directory, and merge their contents
		BufferedReader input = null;
		PrintWriter output = null;
		try {
			// set up output file writer
			output = new PrintWriter(merge.outputFile);
			boolean first = true;
			System.out.println(String.format(
				"Merging the contents of input directory \"%s\"\n" +
				"into output file \"%s\":",
				merge.inputDirectory.getAbsolutePath(),
				merge.outputFile.getAbsolutePath()));
			int filesMerged = 0;
			long totalFileSize = 0L;
			for (File inputFile : merge.inputDirectory.listFiles()) {
				if (inputFile.isDirectory())
					continue;
				else if (inputFile.canRead() == false)
					throw new IOException(String.format(
						"Input file \"%s\" is not readable.",
						inputFile.getAbsolutePath()));
				System.out.print(String.format(
					"\t%3d. Input file \"%s\" - size %,d bytes...",
					(filesMerged + 1), inputFile.getName(),
					inputFile.length()));
				input = new BufferedReader(new FileReader(inputFile));
				// read the first line, and if it's expected
				// to be a header, handle it appropriately
				String line = input.readLine();
				if (merge.header == false || first)
					output.println(line);
				// read the remaining lines, and write them to the output file
				while (true) {
					line = input.readLine();
					if (line == null)
						break;
					else output.println(line);
				}
				// close this input file
				input.close();
				if (first)
					first = false;
				// report success
				filesMerged++;
				totalFileSize += inputFile.length();
				System.out.println("merged.");
			}
			// flush the output stream and report the result of the merge
			output.flush();
			StringBuffer report = new StringBuffer("Merged ");
			report.append(filesMerged);
			report.append(" input file");
			if (filesMerged != 1)
				report.append("s");
			report.append(" into result file \"");
			report.append(merge.outputFile.getAbsolutePath());
			report.append("\".\nAfter merging, this file contains ");
			report.append(String.format("%,d", merge.outputFile.length()));
			report.append(" bytes of data.");
			if (merge.outputFile.length() != totalFileSize) {
				report.append(
					"\nWARNING: Expected the result file to contain ");
				report.append(String.format("%,d", totalFileSize));
				report.append(" bytes!");
			}
			System.out.println(report.toString());
		} catch (Throwable error) {
			die(null, error);
		} finally {
			if (input != null) try {
				input.close();
			} catch (Throwable error) {}
			if (output != null) try {
				output.close();
			} catch (Throwable error) {}
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each merge operation.
	 */
	private static class MergeOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File inputDirectory;
		private File outputFile;
		private boolean header;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public MergeOperation(
			File inputDirectory, File outputFile, boolean header
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
			// validate output file
			if (outputFile == null)
				throw new NullPointerException("Output file cannot be null.");
			else if (outputFile.isDirectory())
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" " +
						"must be a normal (non-directory) file.",
						outputFile.getAbsolutePath()));
			this.outputFile = outputFile;
			// attempt to create output file and test its writeability
			if (outputFile.createNewFile() == false ||
				outputFile.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be writable.",
						outputFile.getAbsolutePath()));
			// set header status
			this.header = header;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static MergeOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File inputDirectory = null;
		File outputFile = null;
		boolean header = false;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else if (argument.equals("-header"))
				header = true;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-input"))
					inputDirectory = new File(value);
				else if (argument.equals("-output"))
					outputFile = new File(value);
				else return null;
			}
		}
		try {
			return new MergeOperation(inputDirectory, outputFile, header);
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
