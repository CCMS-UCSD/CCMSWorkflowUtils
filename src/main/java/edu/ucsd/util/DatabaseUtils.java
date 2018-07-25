package edu.ucsd.util;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class DatabaseUtils
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String DB_CONFIG_FILE = "massive.properties";
	private static final String DB_DRIVER = "com.mysql.jdbc.Driver";
	private static final String DB_PROTOCOL = "jdbc:mysql";
	
	/*========================================================================
	 * Static properties
	 *========================================================================*/
	private static Properties dbConfig = null;
	private static String dbURL = null;
	static {
		dbConfig = loadDatabaseConfiguration();
		if (dbConfig != null) try {
			Class.forName(DB_DRIVER);
			dbURL = DB_PROTOCOL + "://";
			dbURL += dbConfig.getProperty("db.host");
			dbURL += "/" + dbConfig.getProperty("db.database");
			dbURL += "?user=" +
				URLEncoder.encode(dbConfig.getProperty("db.user"), "UTF-8");
			dbURL += "&password=" +
				URLEncoder.encode(dbConfig.getProperty("db.password"), "UTF-8");
			// set "autoReconnect" to true, since there may be long waits
			// between database file inserts due to delays in copying
			dbURL += "&autoReconnect=true";
		} catch (Throwable error) {
			System.err.println(
				"There was an error generating the database URL.");
			error.printStackTrace();
			dbURL = null;
		}
	}
	private static Map<String, Map<String, String>> taskUploadsMap =
		new HashMap<String, Map<String, String>>();
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static Connection getConnection() {
		if (dbURL == null)
			return null;
		else try {
			return DriverManager.getConnection(dbURL);
		} catch (Throwable error) {
			System.err.println(
				"There was an error obtaining a database connection.");
			error.printStackTrace();
			return null;
		}
	}
	
	public static String getOriginalName(
		String mangledFilename, String taskID
	) {
		if (mangledFilename == null || taskID == null)
			return null;
		// get this task's upload mappings
		Map<String, String> uploadMappings = taskUploadsMap.get(taskID);
		// if they haven't been looked up yet, lazy load them now
		if (uploadMappings == null) {
			// prepare non-null uploads map
			uploadMappings = new LinkedHashMap<String, String>();
			// query the database for this task's upload mappings
			Connection connection = null;
			PreparedStatement statement = null;
			ResultSet result = null;
			try {
				connection = getConnection();
				if (connection == null)
					throw new NullPointerException(
						"Could not connect to the ProteoSAFe database server.");
				statement = connection.prepareStatement(
					"SELECT * FROM uploads " +
					"WHERE task_id=? " +
					"ORDER BY saved_as ASC");
				statement.setString(1, taskID);
				result = statement.executeQuery();
				while (result.next())
					uploadMappings.put(result.getString("saved_as"),
						result.getString("original_name"));
			} catch (RuntimeException error) {
				throw error;
			} catch (Throwable error) {
				throw new RuntimeException(error);
			} finally {
				try { connection.close(); } catch (Throwable error) {}
			}
			// save the map, even if it's empty, to prevent further lookups
			taskUploadsMap.put(taskID, uploadMappings);
		}
		// return this mangled filename's original upload path
		return uploadMappings.get(mangledFilename);
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static Properties loadDatabaseConfiguration() {
		Properties properties = new Properties();
		try {
			File appRoot = new File(DatabaseUtils.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI()).getParentFile();
			properties.load(
				new FileInputStream(new File(appRoot, DB_CONFIG_FILE)));
		} catch (Throwable error) {
			System.err.println(
				"There was an error loading the database configuration.");
			error.printStackTrace();
			return null;
		}
		if (properties.isEmpty())
			return null;
		else return properties;
	}
}
