package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.commons.io.FilenameUtils;

public class ConvertTSVToSQLite
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool convertTSVToSQLite" +
		"\n\t-input  <TSVFile>" +
		"\n\t-output <SQLiteDBFile>";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		ConvertTSVToSQLiteOperation conversion = extractArguments(args);
		if (conversion == null)
			die(USAGE);
		// first, parse the input TSV file into an SQL commands file
		BufferedReader reader = null;
		PrintWriter writer = null;
		try {
			reader = new BufferedReader(new FileReader(conversion.tsvFile));
			writer = new PrintWriter(conversion.sqlCommandsFile);
			// read the header line to extract the column names
			int chunk = 0;
			String line = reader.readLine();
			if (line == null)
				throw new IllegalArgumentException(String.format(
					"File [%s] does not contain a valid header line.",
					conversion.tsvFile.getAbsolutePath()));
			String[] columns = line.split("\\t");
			if (columns == null || columns.length < 1)
				throw new IllegalArgumentException(String.format(
					"Header line [%s] from file [%s] could not be parsed " +
					"to properly extract the file's column names.", line,
					conversion.tsvFile.getAbsolutePath()));
			// read the first row to to determine column types from the values
			line = reader.readLine();
			if (line == null)
				throw new IllegalArgumentException(String.format(
					"File [%s] contains no valid data rows.",
					conversion.tsvFile.getAbsolutePath()));
			String[] values = line.split("\\t");
			if (values == null || values.length != columns.length)
				throw new IllegalArgumentException(String.format(
					"First data row [%s] from file [%s] could not be parsed " +
					"into a valid array of values, whose length matches that " +
					"of the parsed header line (%d).", line,
					conversion.tsvFile.getAbsolutePath(), columns.length));
			// start with static table creation statement
			StringBuffer tableCreation =
				new StringBuffer("CREATE TABLE Result\n(");
			// add columns found in the header line
			for (int i=0; i<columns.length; i++)
				tableCreation.append(String.format("%s %s,\n",
					cleanColumnName(columns[i]), getColumnType(values[i])));
			// chomp trailing comma and newline
			if (tableCreation.toString().endsWith(",\n"))
				tableCreation.setLength(tableCreation.length() - 2);
			// close the table creation statement
			tableCreation.append(");");
			writer.println(tableCreation.toString());
			// write static insert statement before the first chunk of rows
			writer.println("INSERT INTO Result SELECT");
			// write the first row
			writer.print(getRowInsertion(values));
			chunk++;
			// write the rest of the rows
			int row = 2;
			while ((line = reader.readLine()) != null) {
				// parse the row into values
				values = line.split("\\t");
				if (values == null || values.length != columns.length)
					throw new IllegalArgumentException(String.format(
						"Data row %d [%s] from file [%s] could not be " +
						"parsed into a valid array of values, whose length " +
						"matches that of the parsed header line (%d).",
						row, line, conversion.tsvFile.getAbsolutePath(),
						columns.length));
				// SQLite will only tolerate insert statements with a
				// maximum of 500 rows each, so if we've reached the
				// end of this chunk, close it out and start a new one
				if (chunk >= 500) {
					writer.println(";");
					writer.println("INSERT INTO Result SELECT");
					chunk = 0;
				} else writer.println(" UNION ALL SELECT");
				// write this row
				writer.print(getRowInsertion(values));
				row++;
				chunk++;
			}
			// close the full insert statement
			writer.println(";");
		} catch (Throwable error) {
			die("Could not write parsed TSV file to " +
				"SQL database creation commands file.", error);
		} finally {
			try { reader.close(); } catch (Throwable error) {}
			try { writer.close(); } catch (Throwable error) {}
		}
		// then, create the SQLite database and
		// populate it with the SQL commands file
		Process process = null;
		Integer exitValue = null;
		String output = null;
		try {
			ProcessBuilder builder = new ProcessBuilder();
			builder.command(
				"sqlite3", "-batch", "-init",
				conversion.sqlCommandsFile.getAbsolutePath(),
				conversion.sqliteDBFile.getAbsolutePath(), ".exit"
			);
			builder.redirectErrorStream(true);
			process = builder.start();
			exitValue = process.waitFor();
			output = getConsoleOutput(process);
		} catch (Throwable error) {
			die("Could not run SQL database creation commands " +
				"file to generate SQLite result database.", error);
		} finally {
			try { process.getInputStream().close(); } catch (Throwable error) {}
			try { process.getOutputStream().close(); } catch (Throwable error) {}
			try { process.getErrorStream().close(); } catch (Throwable error) {}
			try { process.destroy(); } catch (Throwable error) {}
		}
		// verify that the SQLite database was successfully created
		if (conversion.sqliteDBFile.canRead() &&
			databaseCreationSucceeded(exitValue, output) == false)
			die(String.format("Failed to generate SQLite result database [%s].",
				conversion.sqliteDBFile.getAbsolutePath()));
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each
	 * TSV to SQLite conversion operation.
	 */
	private static class ConvertTSVToSQLiteOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File tsvFile;
		private File sqlCommandsFile;
		private File sqliteDBFile;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public ConvertTSVToSQLiteOperation(
			File tsvFile, File sqliteDBFile
		) throws IOException {
			// validate input TSV file
			if (tsvFile == null)
				throw new NullPointerException(
					"Input TSV file cannot be null.");
			else if (tsvFile.isFile() == false)
				throw new IllegalArgumentException(String.format(
					"Input TSV file [%s] must be a regular file.",
					tsvFile.getAbsolutePath()));
			else if (tsvFile.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input file [%s] must be readable.",
						tsvFile.getAbsolutePath()));
			this.tsvFile = tsvFile;
			String baseFilename = FilenameUtils.getBaseName(tsvFile.getName());
			// set up and validate SQL commands file
			File working = new File(System.getProperty("user.dir"));
			if (working.isDirectory() == false || working.canWrite() == false)
				throw new IllegalArgumentException(String.format(
					"Cannot write temporary SQL commands text file " +
					"to current working directory [%s].",
					working.getAbsolutePath()));
			sqlCommandsFile = new File(working, baseFilename + ".sql");
			// attempt to create SQL commands file and test its writeability
			if (sqlCommandsFile.createNewFile() == false ||
				sqlCommandsFile.canWrite() == false)
				throw new IllegalArgumentException(String.format(
					"Temporary SQL commands text file [%s] must be writable.",
					sqlCommandsFile.getAbsolutePath()));
			// validate output SQLite database file
			if (sqliteDBFile == null)
				throw new NullPointerException(
					"Output SQLite database file cannot be null.");
			// attempt to create database file and test its writeability
			if (sqliteDBFile.createNewFile() == false ||
				sqliteDBFile.canWrite() == false)
				throw new IllegalArgumentException(String.format(
					"Output SQLite database file [%s] must be writable.",
					sqliteDBFile.getAbsolutePath()));
			this.sqliteDBFile = sqliteDBFile;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static ConvertTSVToSQLiteOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File tsvFile = null;
		File sqliteDBFile = null;
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
					tsvFile = new File(value);
				else if (argument.equals("-output"))
					sqliteDBFile = new File(value);
				else return null;
			}
		}
		try {
			return new ConvertTSVToSQLiteOperation(tsvFile, sqliteDBFile);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static String cleanColumnName(String columnName) {
		if (columnName == null)
			return null;
		columnName.replaceAll("'", "_");
		return String.format("'%s'", columnName);
	}
	
	private static String cleanColumnValue(String columnValue) {
		if (columnValue == null)
			return null;
		return columnValue.replaceAll("'", "_");
	}
	
	private static String getColumnType(String columnValue) {
		// first, try to parse it as an integer
		try {
			Integer.parseInt(columnValue);
			return "INTEGER";
		} catch (Throwable error) {}
		// then, try to parse it as a float
		try {
			Double.parseDouble(columnValue);
			return "REAL";
		} catch (Throwable error) {}
		// otherwise, it's a text column
		return "TEXT";
	}
	
	private static String getRowInsertion(String[] values) {
		if (values == null)
			return "";
		StringBuffer insertion = new StringBuffer();
		// add a value for each column
		for (String value : values)
			insertion.append(String.format("'%s',", cleanColumnValue(value)));
		// chomp trailing comma
		if (insertion.length() > 0 &&
			insertion.charAt(insertion.length() - 1) == ',')
			insertion.setLength(insertion.length() - 1);
		return insertion.toString();
	}
	
	private static String getConsoleOutput(Process process) {
		if (process == null)
			return null;
		BufferedReader reader =
			new BufferedReader(new InputStreamReader(process.getInputStream()));
		StringBuilder message = new StringBuilder();
		String line = null;
		try {
			while ((line = reader.readLine()) != null)
				message.append(line)
					.append(System.getProperty("line.separator"));
		} catch (Throwable error) {
			return null;
		}
		if (message.length() <= 0)
			return null;
		else return message.toString();
	}
	
	private static boolean databaseCreationSucceeded(
		Integer exitValue, String output
	) {
		if (exitValue == null)
			return false;
		// apparently the sqlite3 command line can
		// return an exit code of "2" on success
		else if (exitValue != 0 && exitValue != 2)
			return false;
		// there should be no console output from sqlite3
		// if the database file was successfully created
		else if (output != null && output.trim().equals("") == false)
			return false;
		else return true;
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error converting a tab-separated " +
				"result file to an SQLite database representation.";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
