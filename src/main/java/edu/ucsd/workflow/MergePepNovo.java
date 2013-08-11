package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

public class MergePepNovo
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool   mergePepNovo" +
		"\n\t-config <ConfigFileDirectory>" +
		"\n\t-result <RawPepNovoResultDirectory>" +
		"\n\t-output <OutputFile>";
	private static final Pattern PEPNOVO_HEADER_PATTERN = Pattern.compile(
		">>\\s+([+-]?\\d+)\\s+([+-]?\\d+)\\s+([+-]?\\d+)\\s+(.*)");
	public static final String[] PEPNOVO_NATIVE_FIELDS = new String[]{
		"#Index", "RnkScr", "PnvScr", "N-Gap",
		"C-Gap", "[M+H]", "Charge", "Sequence"
	};
	public static final String[] PEPNOVO_COMPUTED_FIELDS = new String[]{
		"SpectrumFile", "Index", "Scan", "Title"
	};
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		MergePepNovoOperation merge = extractArguments(args);
		if (merge == null)
			die(USAGE);
		PrintWriter output = null;
		try {
			// write global header line to output file
			output = new PrintWriter(merge.outputFile);
			// prepare native PepNovo header line separately, since it
			// will be needed later to verify each block's field sub-header
			StringBuffer nativeHeader = new StringBuffer();
			for (String field : PEPNOVO_NATIVE_FIELDS) {
				nativeHeader.append(field);
				nativeHeader.append("\t");
			}
			// chomp trailing tab character
			nativeHeader.setLength(nativeHeader.length() - 1);
			// prepare complete header line, and write it to output file
			StringBuffer header = new StringBuffer();
			for (String field : PEPNOVO_COMPUTED_FIELDS) {
				header.append(field);
				header.append("\t");
			}
			header.append(nativeHeader.toString());
			output.println(header.toString());
			// iterate over all the raw PepNovo result files, first extracting
			// the input spectrum filename from the matching PepNovo config file
			// and then parsing the result file into the merged output file
			for (File result : merge.resultDirectory.listFiles()) {
				// retrieve this result file's matching config file;
				// by convention, PepNovo config files are named
				// "<index>.cfg", and PepNovo result files have
				// the same base name as their input config file
				File config = new File(merge.configDirectory, String.format(
					"%s.cfg", FilenameUtils.getBaseName(result.getName())));
				if (config == null || config.canRead() == false)
					die(String.format("No matching input config file " +
						"could be found for PepNovo result file \"%s\"",
						result.getAbsolutePath()));
				// read config file, extract spectrum filename
				BufferedReader reader = null;
				String spectrumFilename = null;
				try {
					reader = new BufferedReader(new FileReader(config));
					String line = null;
					while ((line = reader.readLine()) != null) {
						// if this is the config file's spectrum file line,
						// extract the filename; by convention, it is
						// prefixed with the string "spectra,"
						if (line.startsWith("spectra,")) {
							spectrumFilename =
								FilenameUtils.getName(line.substring(8));
							break;
						}
					}
				} catch (Throwable error) {
					die(String.format(
						"Could not read PepNovo config file \"%s\"",
						config.getAbsolutePath()), error);
				} finally {
					try { reader.close(); } catch (Throwable error) {}
				}
				if (spectrumFilename == null)
					die(String.format("Could not extract spectrum " +
						"filename from PepNovo config file \"%s\"",
						config.getAbsolutePath()));
				// read PepNovo result file, parse out ID blocks
				try {
					reader = new BufferedReader(new FileReader(result));
					String line = null;
					int linesRead = 0;
					while ((line = reader.readLine()) != null) {
						linesRead++;
						// search for the beginning of the next valid block
						if (line.trim().equals(""))
							continue;
						Matcher matcher = PEPNOVO_HEADER_PATTERN.matcher(line);
						if (matcher.matches() == false) {
							System.out.println(String.format("Warning [" +
								"PepNovo result file \"%s\", line %d]: " +
								"expected a block header, but found " +
								"line \"%s\" instead.",
								result.getAbsolutePath(), linesRead, line));
							continue;
						}
						// parse out header properties
						String index = matcher.group(2);
						String scan = matcher.group(3);
						String title = matcher.group(4);
						// parse out second header line
						line = reader.readLine();
						if (line == null)
							break;
						linesRead++;
						// the second header should be a comment, starting
						// with "#", and it should match the native header
						// string; otherwise, it's an invalid block
						if (line.startsWith("#") == false ||
							line.equals(nativeHeader.toString()) == false) {
							System.out.println(String.format("Warning [" +
								"PepNovo result file \"%s\", line %d]: " +
								"expected a valid block field sub-header " +
								"(\"%s\"), but found line \"%s\" instead.",
								result.getAbsolutePath(), linesRead,
								nativeHeader.toString(), line));
							continue;
						}
						// parse out each PSM line from this block
						while ((line = reader.readLine()) != null) {
							linesRead++;
							if (line.trim().equals(""))
								break;
							String[] row = line.split("\\t");
							// verify this line
							if (row.length != PEPNOVO_NATIVE_FIELDS.length)
								die(String.format("Error [PepNovo result " +
									"file \"%s\", line %d]: expected ID row " +
									"\"%s\" to contain %d fields, but found " +
									"%d instead",
									result.getAbsolutePath(), linesRead, line,
									PEPNOVO_NATIVE_FIELDS.length, row.length));
							// build tab-separated ID row for this PSM
							StringBuffer hit =
								new StringBuffer(spectrumFilename);
							hit.append("\t");
							hit.append(index);
							hit.append("\t");
							hit.append(scan);
							hit.append("\t");
							hit.append(title);
							for (String value : row) {
								hit.append("\t");
								hit.append(value);
							}
							// write row to output file
							output.println(hit.toString());
						}
					}
				} catch (Throwable error) {
					die(String.format(
						"Could not parse PepNovo result file \"%s\"",
						result.getAbsolutePath()), error);
				} finally {
					try { reader.close(); } catch (Throwable error) {}
				}
			}
		} catch (Throwable error) {
			die(null, error);
		} finally {
			try { output.close(); } catch (Throwable error) {}
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each merge operation.
	 */
	private static class MergePepNovoOperation {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File configDirectory;
		private File resultDirectory;
		private File outputFile;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public MergePepNovoOperation(
			File configDirectory, File resultDirectory, File outputFile
		) throws IOException {
			// validate config file directory
			if (configDirectory == null)
				throw new NullPointerException(
					"Config file directory cannot be null.");
			else if (configDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format("Config file directory \"%s\" must be " +
						"a directory.", configDirectory.getAbsolutePath()));
			else if (configDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Config file directory \"%s\" must be " +
						"readable.", configDirectory.getAbsolutePath()));
			this.configDirectory = configDirectory;
			// validate PepNovo result file directory
			if (resultDirectory == null)
				throw new NullPointerException(
					"PepNovo result directory cannot be null.");
			else if (resultDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format("PepNovo result directory \"%s\" must be " +
						"a directory.", resultDirectory.getAbsolutePath()));
			else if (resultDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("PepNovo result directory \"%s\" must be " +
						"readable.", resultDirectory.getAbsolutePath()));
			this.resultDirectory = resultDirectory;
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
	private static MergePepNovoOperation extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File configDirectory = null;
		File resultDirectory = null;
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
				if (argument.equals("-config"))
					configDirectory = new File(value);
				else if (argument.equals("-result"))
					resultDirectory = new File(value);
				else if (argument.equals("-output"))
					outputFile = new File(value);
				else return null;
			}
		}
		try {
			return new MergePepNovoOperation(
				configDirectory, resultDirectory, outputFile);
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
			message = "There was an error merging PepNovo result files";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
