package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoinTables
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool joinTables" +
		"\n\t-input <InputFile1> <InputFile2>" +
		"\n\t-output <OutputFile>" +
		"\n\t-keys <CommaSeparatedHeaderList>" +
		"\n\t[-separator <DelimiterString>] (default tab)";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		JoinOperation join = extractArguments(args);
		if (join == null)
			die(USAGE);
		// set up file I/O objects
		BufferedReader input1 = null;
		BufferedReader input2 = null;
		PrintWriter output = null;
		try {
			// analyze input files
			input1 = new BufferedReader(new FileReader(join.inputFile1));
			input2 = new BufferedReader(new FileReader(join.inputFile2));
			// capture column headers
			List<String> fields1 = Arrays.asList(
				input1.readLine().split(join.delimiter));
			List<String> fields2 = Arrays.asList(
				input2.readLine().split(join.delimiter));
			// combine headers into a single merged header line
			List<String> fields = new ArrayList<String>(fields1);
			for (String field : fields2)
				if (fields.contains(field) == false)
					fields.add(field);
			// write the merged header line to the output file
			output = new PrintWriter(join.outputFile);
			output.println(printRow(fields, join.delimiter));
			// determine the column indices of the specified header keys
			List<Integer> keys1 = new ArrayList<Integer>(join.keys.length);
			List<Integer> keys2 = new ArrayList<Integer>(join.keys.length);
			for (String key : join.keys) {
				// find the index of this key in the first field list
				int index1 = fields1.indexOf(key);
				if (index1 < 0)
					throw new IllegalArgumentException(
						String.format("Key \"%s\" could not be found " +
							"as a column header in file \"%s\".",
							key, join.inputFile1.getAbsolutePath()));
				else keys1.add(index1);
				// find the index of this key in the second field list
				int index2 = fields2.indexOf(key);
				if (index2 < 0)
					throw new IllegalArgumentException(
						String.format("Key \"%s\" could not be found " +
							"as a column header in file \"%s\".",
							key, join.inputFile2.getAbsolutePath()));
				else keys2.add(index2);
			}
			// read the rows of the first input file
			Map<List<String>, List<String>> rows =
				new HashMap<List<String>, List<String>>();
			String line = null;
			int i = 1;
			while (true) {
				line = input1.readLine();
				if (line == null)
					break;
				List<String> id = new ArrayList<String>(keys1.size());
				List<String> row = new ArrayList<String>(
					Arrays.asList(line.split(join.delimiter)));
				if (row.size() != fields1.size())
					throw new IllegalArgumentException(
						String.format("Row %d of file \"%s\" does not have " +
							"the same number of columns as the file's header " +
							"line (expected %d, found %d).",
							i, join.inputFile1.getAbsolutePath(),
							fields1.size(), row.size()));
				for (Integer key : keys1)
					id.add(row.get(key));
				if (rows.containsKey(id))
					throw new IllegalArgumentException(
						String.format("Key \"%s\" appeared in " +
							"two separate rows in file \"%s\".",
							id, join.inputFile1.getAbsolutePath()));
				else rows.put(id, row);
				i++;
			}
			// read the rows of the second input file, merge them
			i = 1;
			while (true) {
				line = input2.readLine();
				if (line == null)
					break;
				List<String> id = new ArrayList<String>(keys2.size());
				List<String> row2 = Arrays.asList(line.split(join.delimiter));
				if (row2.size() != fields2.size())
					throw new IllegalArgumentException(
						String.format("Row %d of file \"%s\" does not have " +
							"the same number of columns as the file's header " +
							"line (expected %d, found %d).",
							i, join.inputFile2.getAbsolutePath(),
							fields2.size(), row2.size()));
				for (Integer key : keys2)
					id.add(row2.get(key));
				if (rows.containsKey(id) == false)
					throw new IllegalArgumentException(
						String.format("Key \"%s\" appeared in row %d of file " +
							"\"%s\", but did not appear in file \"%s\".",
							id, i, join.inputFile2.getAbsolutePath(),
							join.inputFile1.getAbsoluteFile()));
				// verify the consistency of all shared columns, and
				// merge new columns into the existing row object
				List<String> row = rows.get(id);
				for (String field : fields2) {
					String value2 = row2.get(fields2.indexOf(field));
					if (fields1.contains(field)) {
						String value1 = row.get(fields1.indexOf(field));
						if (value1.equals(value2) == false)
							throw new IllegalArgumentException(
								String.format("The value of column \"%s\" " +
								"was not consistent between rows sharing key " +
								"\"%s\" (found \"%s\" in file \"%s\", and " +
								"\"%s\" in file \"%s\").", field, id,
								value1, join.inputFile1.getAbsolutePath(),
								value2, join.inputFile2.getAbsolutePath()));
					} else row.add(value2);
				}
				// write the merged row to the output file
				output.println(printRow(row, join.delimiter));
				// remove just-printed row from the first file's rows map
				rows.remove(id);
				i++;
			}
			// verify that no rows are left over from the
			// first file that weren't in the second file
			if (rows.size() > 0) {
				List<String> id = rows.keySet().iterator().next();
				throw new IllegalArgumentException(
					String.format("Key \"%s\" appeared in file " +
						"\"%s\", but did not appear in file \"%s\".",
						id, join.inputFile1.getAbsolutePath(),
						join.inputFile2.getAbsoluteFile()));
			}
		} catch (Throwable error) {
			die(null, error);
		} finally {
			if (input1 != null) try {
				input1.close();
			} catch (Throwable error) {}
			if (input2 != null) try {
				input2.close();
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
	 * Struct to maintain context data for each join operation.
	 */
	private static class JoinOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File inputFile1;
		private File inputFile2;
		private File outputFile;
		private String[] keys;
		private String delimiter;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public JoinOperation(
			File inputFile1, File inputFile2, File outputFile,
			String keys, String delimiter
		) throws IOException {
			// validate input files
			if (inputFile1 == null)
				throw new NullPointerException("Input file cannot be null.");
			else if (inputFile1.isFile() == false)
				throw new IllegalArgumentException(
					String.format("Input file \"%s\" must be a regular file.",
						inputFile1.getAbsolutePath()));
			else if (inputFile1.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input file \"%s\" must be readable.",
						inputFile1.getAbsolutePath()));
			this.inputFile1 = inputFile1;
			if (inputFile2 == null)
				throw new NullPointerException("Input file cannot be null.");
			else if (inputFile2.isFile() == false)
				throw new IllegalArgumentException(
					String.format("Input file \"%s\" must be a regular file.",
						inputFile2.getAbsolutePath()));
			else if (inputFile2.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input file \"%s\" must be readable.",
						inputFile2.getAbsolutePath()));
			this.inputFile2 = inputFile2;
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
			// parse header keys
			if (keys == null)
				throw new NullPointerException("Key list cannot be null.");
			else if (keys.trim().equals(""))
				throw new IllegalArgumentException("Key list cannot be empty.");
			this.keys = keys.split(",");
			// set delimiter string (default tab)
			if (delimiter == null)
				this.delimiter = "\t";
			else this.delimiter = delimiter;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static JoinOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File inputFile1 = null;
		File inputFile2 = null;
		File outputFile = null;
		String keys = null;
		String delimiter = null;
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			else {
				i++;
				if (i >= args.length)
					return null;
				String value = args[i];
				if (argument.equals("-input")) {
					inputFile1 = new File(value);
					i++;
					if (i >= args.length)
						return null;
					inputFile2 = new File(args[i]);
				} else if (argument.equals("-output"))
					outputFile = new File(value);
				else if (argument.equals("-keys"))
					keys = value;
				else if (argument.equals("-separator"))
					delimiter = value;
				else return null;
			}
		}
		try {
			return new JoinOperation(
				inputFile1, inputFile2, outputFile, keys, delimiter);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static String printRow(List<String> row, String delimiter) {
		if (row == null || row.isEmpty() || delimiter == null)
			return null;
		StringBuffer line = new StringBuffer();
		for (String column : row) {
			line.append(column);
			line.append(delimiter);
		}
		line.setLength(line.length() - delimiter.length());
		return line.toString();
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error joining tabular files";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
