package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;

public class MergeRectangular
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE =
		"java -cp CCMSWorkflowUtils.jar edu.ucsd.workflow.MergeRectangular" +
		"\n\t-input <InputDirectory>" +
		"\n\t-output <OutputFile>";
	// TODO: either allow this to be configurable or attempt
	// to determine it automatically, so that other common
	// delimiters (e.g. comma) can be supported
	private static final char DELIMITER = '\t';
	private static final String ESCAPED_DELIMITER =
		StringEscapeUtils.escapeJava(Character.toString(DELIMITER));
	private static final String MISSING_COLUMN_VALUE = "N/A";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	@SuppressWarnings("resource")
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
			// first read through all input files, building the complete header
			System.out.println(String.format(
				"Normalizing column headers from input directory [%s]:",
				merge.inputDirectory.getAbsolutePath()));
			System.out.println("----------");
			Set<String> finalHeader = new LinkedHashSet<String>();
			for (File inputFile : merge.inputDirectory.listFiles()) {
				if (inputFile.isDirectory())
					continue;
				else if (inputFile.canRead() == false)
					throw new IOException(String.format(
						"Input file [%s] is not readable.",
						inputFile.getAbsolutePath()));
				input = new BufferedReader(new FileReader(inputFile));
				// read the header line and note all of its columns
				String line = input.readLine();
				if (line == null || line.trim().isEmpty())
					throw new IllegalArgumentException(String.format(
						"Error merging input file [%s]: the file must contain " +
						"a valid header line consisting of one or more non-empty " +
						"field names.", inputFile.getAbsolutePath()));
				for (String column : line.split(ESCAPED_DELIMITER))
					finalHeader.add(column);
				input.close();
				continue;
			}
			// write the complete header to the output file
			String normalizedHeader = serializeRow(
				finalHeader.toArray(new String[finalHeader.size()]));
			System.out.println(normalizedHeader);
			System.out.println("----------");
			output.println(normalizedHeader);
			// build index map of all found columns
			Map<String, Integer> columns =
				new LinkedHashMap<String, Integer>(finalHeader.size());
			int index = 0;
			for (String column : finalHeader) {
				columns.put(column, index);
				index++;
			}
			// read through each input file again, reorganizing it into
			// rectangular rows with empty cells for any columns missing
			// in that file, merging all such normalized results
			System.out.println(String.format(
				"Merging the normalized contents of input directory [%s]\n" +
				"into output file [%s]:",
				merge.inputDirectory.getAbsolutePath(),
				merge.outputFile.getAbsolutePath()));
			System.out.println("----------");
			int filesMerged = 0;
			long totalFileSize = 0L;
			for (File inputFile : merge.inputDirectory.listFiles()) {
				if (inputFile.isDirectory())
					continue;
				else if (inputFile.canRead() == false)
					throw new IOException(String.format(
						"Input file [%s] is not readable.",
						inputFile.getAbsolutePath()));
				System.out.print(String.format(
					"\t%3d. Input file [%s] - size %,d bytes...",
					(filesMerged + 1), inputFile.getName(),
					inputFile.length()));
				input = new BufferedReader(new FileReader(inputFile));
				// read the first line, record it as this file's header
				String line = input.readLine();
				if (line == null || line.trim().isEmpty())
					throw new IllegalArgumentException(String.format(
						"Error merging input file [%s]: the file must contain " +
						"a valid header line consisting of one or more non-empty " +
						"field names.", inputFile.getAbsolutePath()));
				String[] header = line.split(ESCAPED_DELIMITER);
				// read the remaining lines, normalize them,
				// and write them to the output file
				int lineNumber = 2;
				while (true) {
					line = input.readLine();
					if (line == null)
						break;
					String[] row = line.split(ESCAPED_DELIMITER);
					if (row == null || row.length != header.length)
						throw new IllegalArgumentException(String.format(
							"Error merging input file [%s]: line %d contains a " +
							"different number of elements (%d) than the file's " +
							"header line (%d).", inputFile.getAbsolutePath(),
							lineNumber, row != null ? row.length : 0,
							header.length));
					output.println(normalizeRow(row, header, columns));
					lineNumber++;
				}
				// close this input file
				input.close();
				// report success
				filesMerged++;
				totalFileSize += inputFile.length();
				System.out.println("merged.");
			}
			// flush the output stream and report the result of the merge
			output.flush();
			StringBuilder report = new StringBuilder("Merged ");
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
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public MergeOperation(
			File inputDirectory, File outputFile
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
					outputFile = new File(value);
				else return null;
			}
		}
		try {
			return new MergeOperation(inputDirectory, outputFile);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static String serializeRow(String[] row) {
		if (row == null || row.length < 1)
			return null;
		StringBuilder serialized = new StringBuilder();
		for (String cell : row)
			serialized.append(cell).append(DELIMITER);
		// chomp trailing delimiter
		if (serialized.length() > 0 &&
			serialized.charAt(serialized.length() - 1) == DELIMITER)
			serialized.setLength(serialized.length() - 1);
		return serialized.toString();
	}
	
	private static String normalizeRow(
		String[] row, String[] header, Map<String, Integer> columns
	) {
		if (row == null || row.length < 1)
			return null;
		else if (header == null || header.length < row.length)
			throw new IllegalArgumentException(String.format(
				"A header name must be provided for each element in the " +
				"argument row (length %d) in order to normalize the row's " +
				"columns.", row.length));
		else if (columns == null || columns.size() < header.length)
			throw new IllegalArgumentException(String.format(
				"An index mapping must be provided for each column in the " +
				"argument header (length %d) in order to normalize a row's " +
				"columns.", header.length));
		// create a new array for the normalized row
		String[] normalized = new String[columns.size()];
		// initialize all elements of the array with the placeholder string
		// value indicating that the column is missing from the source row
		Arrays.fill(normalized, MISSING_COLUMN_VALUE);
		// for each column actually present in this header, fill its row
		// value into the proper position in the normalized row
		for (int i=0; i<row.length; i++) {
			Integer index = columns.get(header[i]);
			if (index == null)
				throw new IllegalArgumentException(String.format(
					"No index mapping was found for column [%s] found in " +
					"position %d of the argument header.", header[i], i));
			normalized[index] = row[i];
		}
		// serialize and return the normalized row
		return serializeRow(normalized);
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
