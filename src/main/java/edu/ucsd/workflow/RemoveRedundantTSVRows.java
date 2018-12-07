package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class RemoveRedundantTSVRows
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-input <InputFile> (TSV file with header)" +
		"\n\t-output <OutputFile> (Input file with redundant rows removed)" +
		"\n\t-columns <ColumnHeaders> (Comma-separated list of columns " +
		"for which only the first row per unique combination of these " +
		"columns should be retained)";

	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		RemoveRedundantTSVRowsOperation removeRedundantTSVRows = extractArguments(args);
		if (removeRedundantTSVRows == null)
			die(USAGE);
		// remove redundant rows in the TSV
		BufferedReader input = null;
		PrintWriter output = null;
		Throwable thrownError = null;
		int linesInInputFile = 1;
		int linesInOutputFile = 1;
		try {
			input = new BufferedReader(new FileReader(
				removeRedundantTSVRows.inputFile));
			// set up output file writer
			output = new PrintWriter(removeRedundantTSVRows.outputFile);
			System.out.println(String.format(
				"Writing the non-redundant rows in file \"%s\" to output file \"%s\".",
				removeRedundantTSVRows.inputFile,
				removeRedundantTSVRows.outputFile));
			// read header
			String headerStr = input.readLine();
			List<String> header = new ArrayList<String>(
				Arrays.asList(headerStr.trim().split("\t")));
			String columnsStr = "";
			Set<List<String>> uniqueLineIDs = new HashSet<List<String>>();
			for (int i=0; i<header.size(); i++) {
				header.set(i, header.get(i).trim());
			}
			for (int i=0; i<removeRedundantTSVRows.columns.size(); i++) {
				String column = removeRedundantTSVRows.columns.get(i);
				if (header.contains(column) == false) {
					throw new IllegalArgumentException(
						String.format("Column \"%s\" was not found in input header.",
						column));
				}
				columnsStr += column + ", ";
			}
			if (columnsStr.endsWith(", "))
				columnsStr = columnsStr.substring(0, columnsStr.length() - ", ".length());
			System.out.println(String.format(
				"Retaining the first row in input file per unique combination of " +
				"the following columns: %s", columnsStr));
			output.println(headerStr);
			// read the remaining lines, and write the non-redundant ones to the output file
			String lineStr = null;
			while ((lineStr = input.readLine()) != null) {
				linesInInputFile++;
				String[] line = lineStr.trim().split("\t");
				List<String> uniqueLineID = new ArrayList<String>();
				for (int i=0; i<line.length; i++) {
					if (removeRedundantTSVRows.columns.contains(
						header.get(i))) {
						uniqueLineID.add(line[i]);
					}
				}
				if (uniqueLineIDs.contains(uniqueLineID) == false) {
					linesInOutputFile++;
					output.println(lineStr);
					uniqueLineIDs.add(uniqueLineID);
				}
			}
			// flush the output stream and report result
			output.flush();
			System.out.println(String.format(
				"\n------------------\nJob completed.\nLines in input file: %d\n" +
				"Lines in output file: %d",
				linesInInputFile, linesInOutputFile));


		} catch (Throwable error) {
			thrownError = error;
		} finally {
			if (input != null) try {
				input.close();
			} catch (Throwable error) {}
			if (output != null) try {
				output.close();
			} catch (Throwable error) {}
			if (thrownError != null) {
				die(null, thrownError);
			}
		}

	}

	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each merge operation.
	 */
	private static class RemoveRedundantTSVRowsOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File inputFile;
		private File outputFile;
		private List<String> columns;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public RemoveRedundantTSVRowsOperation(
			File inputFile, File outputFile, List<String> columns
		) throws IOException {
			// validate input file
			if (inputFile == null)
				throw new NullPointerException(
					"Input file cannot be null.");
			else if (inputFile.isFile() == false)
				throw new IllegalArgumentException(
					String.format("Input file \"%s\" must be a file.",
						inputFile.getAbsolutePath()));
			else if (inputFile.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input file \"%s\" must be readable.",
						inputFile.getAbsolutePath()));
			this.inputFile = inputFile;
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
			// set header columns
			this.columns = columns;
		}
	}

	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static RemoveRedundantTSVRowsOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File inputFile = null;
		File outputFile = null;
		List<String> columns = null;
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
					outputFile = new File(value);
				else if (argument.equals("-columns")) {
					columns = new ArrayList<String>(
						Arrays.asList(value.trim().split(",")));
					for (int j=0; j<columns.size(); j++) {
						columns.set(j, columns.get(j).trim());
					}
				}
				else return null;
			}
		}
		try {
			return new RemoveRedundantTSVRowsOperation(inputFile, outputFile, columns);
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
			message = "There was an error removing redundant TSV rows";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
