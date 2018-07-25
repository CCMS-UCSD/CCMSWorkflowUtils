package edu.ucsd.result.parsers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ucsd.saint.commons.IOUtils;
import edu.ucsd.util.OnDemandLoader;

public class SQLiteResult
extends TabularResult
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final Logger logger =
		LoggerFactory.getLogger(SQLiteResult.class);
	public static final String RESULT_DATABASE_PRIMARY_KEY = "ccms_row_id";
	
	/*========================================================================
	 * Properties
	 *========================================================================*/
	protected File sqlCommands;
	protected File tempSqlDB;
	protected File sqlDB;
	
	/*========================================================================
	 * Constructors
	 *========================================================================*/
	public SQLiteResult(
		File resultFile, File outputDirectory, String taskID, String block
	) throws NullPointerException, IllegalArgumentException, IOException {
		super(resultFile, outputDirectory, taskID, block);
	}
	
	public SQLiteResult(Result result, File outputDirectory, String block)
	throws NullPointerException, IllegalArgumentException, IOException {
		super(result, outputDirectory, block);
	}
	
	@Override
	protected void init(
		File resultFile, File outputDirectory, String taskID, String block
	) throws NullPointerException, IllegalArgumentException, IOException {
		super.init(resultFile, outputDirectory, taskID, block);
		// get temp directory
		File tempDir = new File("temp");
		tempDir.mkdirs();
		// determine base filename of both SQL commands file
		// and final SQLite database file
		String baseResultFilename = getBaseFilename(resultFile.getName());
		String blockPrefix = block + "_";
		if (baseResultFilename.startsWith(blockPrefix) == false)
			baseResultFilename = blockPrefix + baseResultFilename;
		sqlCommands = new File(tempDir, baseResultFilename + ".sql");
		// use temporary directory to write initial sqlite database file
		tempSqlDB = new File(tempDir, baseResultFilename + ".db");
		// determine final SQLite database file path
		sqlDB = new File(outputDirectory, baseResultFilename + ".db");
	}
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	@Override
	public void load()
	throws IOException, IllegalArgumentException {
		super.load();
		// write SQLite database file in advance
		if (OnDemandLoader.load(this) == false)
			throw new IOException(String.format("Could not parse TSV file " +
				"[%s] into an SQLite database representation.",
				resultFile.getAbsolutePath()));
	}
	
	/*========================================================================
	 * Property accessor methods
	 *========================================================================*/
	/**
	 * Stub implementation of Result.getData() that always returns null,
	 * since this implementation of the ProteoSAFe Result infrastructure
	 * is not intended to be queried, but rather simply to be invoked
	 * for pre-building result files only.
	 * 
	 * @return	null
	 */
	@Override
	public String getData() {
		return null;
	}
	
	/**
	 * Stub implementation of Result.getSize() that always returns 0,
	 * since this implementation of the ProteoSAFe Result infrastructure
	 * is not intended to be queried, but rather simply to be invoked
	 * for pre-building result files only.
	 * 
	 * @return	0
	 */
	@Override
	public Long getSize() {
		return 0L;
	}
	
	/*========================================================================
	 * OnDemandOperation methods
	 *========================================================================*/
	@Override
	public boolean execute() {
		// first, parse the input TSV file into an SQL commands file
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(sqlCommands);
			if (isLoaded() == false)
				load();
			// the table structure cannot be written until the first row has
			// been generated, since the generation of a row necessarily
			// determines any processing attributes that need to be added to
			// each hit, and therefore also to the set of table columns
			List<String> attributeNames = null;
			int id = 1;
			int chunk = 0;
			if (hasNext()) {
				ResultHit hit = next();
				// start with static table creation statement
				StringBuffer tableCreation =
					new StringBuffer("CREATE TABLE Result\n(");
				tableCreation.append(
					cleanColumnName(RESULT_DATABASE_PRIMARY_KEY));
				tableCreation.append(" INTEGER PRIMARY KEY ASC,\n");
				// add columns for the result file fields first
				List<String> fieldNames = hit.getFieldNames();
				if (fieldNames != null)
					for (String fieldName : fieldNames)
						tableCreation.append(String.format("%s %s,\n",
							cleanColumnName(fieldName),
							getColumnType(hit.getFieldValue(fieldName))));
				// then add columns for any attributes that may
				// have been generated during processing
				attributeNames = hit.getAttributeNames();
				if (attributeNames != null)
					for (String attributeName : attributeNames)
						tableCreation.append(String.format("%s %s,\n",
							cleanColumnName(attributeName),
							getColumnType(hit.getAttribute(attributeName))));
				// chomp trailing comma and newline
				if (tableCreation.toString().endsWith(",\n"))
					tableCreation.setLength(tableCreation.length() - 2);
				// close the table creation statement
				tableCreation.append(");");
				writer.println(tableCreation.toString());
				// write static insert statement before the first chunk of rows
				writer.println("INSERT INTO Result SELECT");
				// write the first row
				writer.print(getRowInsertion(hit, id, attributeNames));
				id++;
				chunk++;
			}
			// write the rest of the rows
			while (hasNext()) {
				// SQLite will only tolerate insert statements with a
				// maximum of 500 rows each, so if we've reached the
				// end of this chunk, close it out and start a new one
				if (chunk >= 500) {
					writer.println(";");
					writer.println("INSERT INTO Result SELECT");
					chunk = 0;
				} else writer.println(" UNION ALL SELECT");
				// write this row
				writer.print(getRowInsertion(next(), id, attributeNames));
				id++;
				chunk++;
			}
			// close the full insert statement
			writer.println(";");
		} catch (Throwable error) {
			logger.error("Could not write parsed mzTab to " +
				"SQL database creation commands file.", error);
			return false;
		} finally {
			close();
			try { writer.close(); }
			catch (Throwable error) {}
		}
		// then, create the SQLite database and
		// populate it with the SQL commands file
		Process process = null;
		Integer exitValue = null;
		String output = null;
		try {
			// first, be sure to explicitly delete the output file,
			// if it's already there, to prevent concurrency issues
			if (sqlDB.exists() && sqlDB.delete() == false)
				throw new IOException(String.format(
					"SQLite database file [%s] is already present, and " +
					"could not be deleted to accommodate being rebuilt.",
					sqlDB.getAbsolutePath()));
			if (tempSqlDB.exists() && tempSqlDB.delete() == false)
				throw new IOException(String.format(
					"Temporary SQLite database file [%s] is already present, " +
					"and could not be deleted to accommodate being rebuilt.",
					tempSqlDB.getAbsolutePath()));
			ProcessBuilder builder = new ProcessBuilder();
			builder.command("bash", "-c",
				String.format("cat \"%s\" | sqlite3 \"%s\"",
					sqlCommands.getAbsolutePath(),
					tempSqlDB.getAbsolutePath()));
			builder.redirectErrorStream(true);
			process = builder.start();
			exitValue = process.waitFor();
			output = getConsoleOutput(process);
			// copy temporary output file to final destination
			IOUtils.copyFileSecurely(tempSqlDB, sqlDB, null);
			// if we got this far without failing,
			// clean up the temporary files
			sqlCommands.delete();
			tempSqlDB.delete();
		} catch (Throwable error) {
			logger.error("Could not run SQL database creation commands " +
				"file to generate SQLite result database.", error);
			return false;
		} finally {
			try { process.getInputStream().close(); } catch (Throwable error) {}
			try { process.getOutputStream().close(); } catch (Throwable error) {}
			try { process.getErrorStream().close(); } catch (Throwable error) {}
			try { process.destroy(); } catch (Throwable error) {}
		}
		// verify that the SQLite database was successfully created
		return resourceExists() && databaseCreationSucceeded(exitValue, output);
	}
	
	@Override
	public boolean resourceExists() {
		if (sqlDB == null)
			return false;
		else return sqlDB.exists();
	}
	
	@Override
	public boolean resourceDated() {
		if (sqlDB == null || sqlDB.exists() == false ||
			resultFile == null || resultFile.exists() == false)
			return false;
		else return sqlDB.lastModified() < resultFile.lastModified(); 
	}
	
	@Override
	public String getResourceName() {
		if (sqlDB == null)
			return null;
		else return sqlDB.getAbsolutePath();
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private String cleanColumnName(String columnName) {
		if (columnName == null)
			return null;
		columnName.replaceAll("'", "_");
		return String.format("'%s'", columnName);
	}
	
	private String cleanColumnValue(String columnValue) {
		if (columnValue == null)
			return null;
		return columnValue.replaceAll("'", "_");
	}
	
	private String getColumnType(String columnValue) {
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
	
	private String getRowInsertion(
		ResultHit hit, int id, List<String> attributeNames
	) {
		if (hit == null)
			return "";
		StringBuffer insertion = new StringBuffer();
		// add row primary key first
		insertion.append(id).append(",");
		// then add columns for the result file fields
		List<String> fieldNames = hit.getFieldNames();
		if (fieldNames != null)
			for (String fieldName : fieldNames)
				insertion.append(String.format("'%s',",
					cleanColumnValue(hit.getFieldValue(fieldName))));
		// then add columns for any attributes that may have been generated
		// during processing; use the argument list since we want order to be
		// preserved with respect to the declared column list at the beginning
		if (attributeNames != null)
			for (String attributeName : attributeNames)
				insertion.append(String.format("'%s',",
					cleanColumnValue(hit.getAttribute(attributeName))));
		// chomp trailing comma
		if (insertion.length() > 0 &&
			insertion.charAt(insertion.length() - 1) == ',')
			insertion.setLength(insertion.length() - 1);
		return insertion.toString();
	}
	
	private String getConsoleOutput(Process process) {
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
	
	private boolean databaseCreationSucceeded(
		Integer exitValue, String output
	) {
		if (exitValue == null)
			return false;
		// apparently the sqlite3 command line can
		// return an exit code of "2" on success
		else if (exitValue != 0 && exitValue != 2) {
			logger.error(String.format(
				"sqlite3 command returned an exit code of %d, " +
				"with the following console output:\n%s", exitValue, output));
			return false;
		}
		// there should be no console output from sqlite3
		// if the database file was successfully created
		else if (output != null && output.trim().equals("") == false) {
			logger.error(String.format(
				"sqlite3 command produced non-empty console output:\n%s.",
				output));
			return false;
		} else return true;
	}
	
	/*
	 * Method to iteratively strip off filename extensions, for files with
	 * multiple dot-separated name sections, e.g. "abcd.tar.gz" -> "abcd"
	 */
	private String getBaseFilename(String filename) {
		if (filename == null)
			return null;
		String base = filename;
		String updated = FilenameUtils.getBaseName(base);
		while (updated != null && updated.equals(base) == false) {
			base = updated;
			updated = FilenameUtils.getBaseName(base);
		}
		return base;
	}
}
