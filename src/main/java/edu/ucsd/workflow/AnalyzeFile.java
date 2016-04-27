package edu.ucsd.workflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.MessageDigest;

import org.apache.commons.io.FileUtils;

public class AnalyzeFile
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -cp CCMSWorkflowUtils.jar " +
		"edu.ucsd.workflow.AnalyzeFile <File> [-verbose]";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		FileAnalysis stats = extractArgument(args);
		if (stats == null)
			die(USAGE);
		// indent each level of output with two spaces
		stats.prefix = "  ";
		try {
			String analysis = analyze(stats, stats.file, stats.prefix);
			if (stats.verbose)
				System.out.println(String.format("%s\n", analysis));
			System.out.println(String.format(
				"Symbolic links analyzed: %d", stats.linkCount));
			System.out.println(String.format(
				"Directories analyzed: %d", stats.folderCount));
			System.out.println(String.format(
				"Files analyzed: %d", stats.fileCount));
			System.out.println(String.format(
				"Total file size: %d (%s)", stats.fileSize,
				byteCountToDisplaySize(stats.fileSize, 2)));
		} catch (IOException error) {
			die(null, error);
		}
	}
	
	/*========================================================================
	 * Convenience class
	 *========================================================================*/
	/**
	 * Struct to maintain context data for a file analysis operation.
	 */
	private static class FileAnalysis {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		protected File    file = null;
		protected String  prefix = null;
		protected int     linkCount = 0;
		protected int     folderCount = 0;
		protected int     fileCount = 0;
		protected long    fileSize = 0;
		protected boolean verbose = false;
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static FileAnalysis extractArgument(String[] args) {
		if (args == null || args.length < 1)
			die(String.format(
				"Please provide an argument file to analyze:\n%s", USAGE));
		else if (args.length > 2)
			die(String.format("Expected at most two arguments:\n%s", USAGE));
		// process arguments into analysis parameters
		FileAnalysis stats = new FileAnalysis();
		for (int i=0; i<args.length; i++) {
			String argument = args[i];
			if (argument == null)
				return null;
			if (argument.equals("-verbose"))
				stats.verbose = true;
			else stats.file = new File(argument);
		}
		// verify specified file
		if (stats.file == null)
			die(String.format(
				"Please provide an argument file to analyze:\n%s", USAGE));
		else if (stats.file.exists() == false)
			die(String.format("Argument file [%s] could not be found.",
				stats.file.getAbsolutePath()));
		else if (stats.file.canRead() == false)
			die(String.format("Argument file [%s] is not readable.",
				stats.file.getAbsolutePath()));
		return stats;
	}
	
	private static String analyze(
		FileAnalysis stats, File file, String prefix
	) throws IOException {
		if (file == null) {
			die("Analyzing argument file: file is null!");
			return null;
		} else if (FileUtils.isSymlink(file)) {
			stats.linkCount++;
			return String.format("%s -> %s",
				file.getPath(), file.getCanonicalPath());
		} else if (file.isFile()) {
			long size = file.length();
			String fileDetails = String.format(
				"%s : size = %d bytes", file.getPath(), size);
			stats.fileCount++;
			stats.fileSize += size;
			if (stats.verbose) {
				long checksum = FileUtils.checksumCRC32(file);
				String hash = getMD5Hash(file);
				return String.format("%s, CRC32 checksum = %d, MD5 hash = %s",
					fileDetails, checksum, hash);
			} else return fileDetails;
		} else if (file.isDirectory()) {
			StringBuilder contents = new StringBuilder(file.getPath());
			// render down arrow for directories, to be nifty
			contents.append(" ").append(Character.toChars(8628)).append("\n");
			// be sure to indent properly for each level
			String thisPrefix = "", nextPrefix = null;
			if (prefix != null && stats.prefix != null) {
				thisPrefix = prefix;
				nextPrefix = prefix + stats.prefix;
			}
			File[] children = file.listFiles();
			if (children != null && children.length > 0) {
				for (File child : file.listFiles()) {
					contents.append(thisPrefix);
					contents.append(analyze(stats, child, nextPrefix));
					contents.append("\n");
				}
			}
			// chomp trailing newline
			if (contents.toString().endsWith("\n"))
				contents.setLength(contents.length() - 1);
			stats.folderCount++;
			return contents.toString();
		} else {
			die(String.format(
				"Analyzing argument file [%s]: unrecognized file type.",
				file.getAbsolutePath()));
			return null;
		}
	}
	
	private static String getMD5Hash(File file) {
		if (file == null || file.canRead() == false)
			return null;
		FileInputStream input = null;
		try {
			// read file, compute MD5 digest bytes
			input = new FileInputStream(file);
			MessageDigest digest = MessageDigest.getInstance("MD5");
			byte[] data = new byte[1024];
			int bytesRead = 0;
			while ((bytesRead = input.read(data)) != -1)
				digest.update(data, 0, bytesRead);
			byte[] digestData = digest.digest();
			// convert digest bytes to hex string format
			StringBuffer result = new StringBuffer();
			for (int i=0; i<digestData.length; i++) {
				String hex = Integer.toHexString(0xff & digestData[i]);
	    		if (hex.length() == 1)
	   	     		result.append("0");
	   	     	result.append(hex);
	   	     }
			return result.toString();
		} catch (Throwable error) {
			return null;
		} finally {
			try { input.close(); }
			catch (Throwable error) {}
		}
	}
	
	private static String byteCountToDisplaySize(long size, int precision) {
		BigInteger size_bi = BigInteger.valueOf(size);
		// precision must be >= 0
		if (precision < 0) 
			precision = 0;
		String unit = null;
		String value = null;
		BigDecimal size_bd = new BigDecimal(size);
		// determine order of magnitude by integer dividing by scale constants
		if (size_bi.divide(FileUtils.ONE_EB_BI)
				.compareTo(BigInteger.ZERO) > 0) {
			unit = "EiB";
			value = size_bd.divide(new BigDecimal(FileUtils.ONE_EB_BI),
				precision, RoundingMode.FLOOR).toString();
		} else if (size_bi.divide(FileUtils.ONE_PB_BI)
				.compareTo(BigInteger.ZERO) > 0) {
			unit = "PiB";
			value = size_bd.divide(new BigDecimal(FileUtils.ONE_PB_BI),
				precision, RoundingMode.FLOOR).toString();
		} else if (size_bi.divide(FileUtils.ONE_TB_BI)
				.compareTo(BigInteger.ZERO) > 0) {
			unit = "TiB";
			value = size_bd.divide(new BigDecimal(FileUtils.ONE_TB_BI),
				precision, RoundingMode.FLOOR).toString();
		} else if (size_bi.divide(FileUtils.ONE_GB_BI)
				.compareTo(BigInteger.ZERO) > 0) {
			unit = "GiB";
			value = size_bd.divide(new BigDecimal(FileUtils.ONE_GB_BI),
				precision, RoundingMode.FLOOR).toString();
		} else if (size_bi.divide(FileUtils.ONE_MB_BI)
				.compareTo(BigInteger.ZERO) > 0) {
			unit = "MiB";
			value = size_bd.divide(new BigDecimal(FileUtils.ONE_MB_BI),
				precision, RoundingMode.FLOOR).toString();
		} else if (size_bi.divide(FileUtils.ONE_KB_BI)
				.compareTo(BigInteger.ZERO) > 0) {
			unit = "KiB";
			value = size_bd.divide(new BigDecimal(FileUtils.ONE_KB_BI),
				precision, RoundingMode.FLOOR).toString();
		} else {
			unit = "bytes";
			value = size_bi.toString();
		}
		return String.format("%s %s", value, unit);
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error analyzing argument file";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
