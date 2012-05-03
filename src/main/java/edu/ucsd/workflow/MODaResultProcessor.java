package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;

@Deprecated
public class MODaResultProcessor
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool modaResultProcessor" +
		"\n\t-input <MODaResultFile>" +
		"\n\t-output <ProcessedResultFile>" +
		"\n\t-spectrum <SpectrumDirectory>";
	private static final String MODA_HEADER = "spectrum_file" +
		"\tspectrum_name" +
		"\tobserved_MW" +
		"\tcharge_state" +
		"\tcalculated_MW" +
		"\tdelta_mass" +
		"\tscore" +
		"\tprobability" +
		"\tpeptide" +
		"\tprotein" +
		"\tpeptide_position";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		MODaResultProcess process = extractArguments(args);
		if (process == null)
			die(USAGE);
		// add appropriate information to input file, write to output file
		RandomAccessFile input = null;
		PrintWriter output = null;
		try {
			// set up input file reader
			input = new RandomAccessFile(process.input, "r");
			// set up output file writer
			output = new PrintWriter(process.output);
			// write header line to output file
			output.println(MODA_HEADER);
			// read input file, and add spectrum file name to each line
			String line = null;
			while (true) {
				line = input.readLine();
				if (line == null)
					break;
				else output.println(process.spectrum + "\t" + line);
			}
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
	 * Struct to maintain context data for each MODa result file processing
	 * operation.
	 */
	private static class MODaResultProcess {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File input;
		private File output;
		private String spectrum;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public MODaResultProcess(
			File input, File output, File spectrumDirectory
		) throws IOException {
			// validate input result file
			if (input == null)
				throw new NullPointerException(
					"Input result file cannot be null.");
			else if (input.isFile() == false)
				throw new IllegalArgumentException(
					String.format(
						"Input result file \"%s\" must be a regular file.",
						input.getAbsolutePath()));
			else if (input.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input result file \"%s\" must be readable.",
						input.getAbsolutePath()));
			this.input = input;
			// validate output file
			if (output == null)
				throw new NullPointerException("Output file cannot be null.");
			else if (output.isDirectory())
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" " +
						"must be a normal (non-directory) file.",
						output.getAbsolutePath()));
			this.output = output;
			// attempt to create output file and test its writeability
			if (output.createNewFile() == false || output.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be writable.",
						output.getAbsolutePath()));
			// validate spectrum directory
			if (spectrumDirectory == null)
				throw new NullPointerException(
					"Spectrum directory cannot be null.");
			else if (spectrumDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format(
						"Spectrum directory \"%s\" must be a directory.",
						spectrumDirectory.getAbsolutePath()));
			else if (spectrumDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Spectrum directory \"%s\" must be readable.",
						spectrumDirectory.getAbsolutePath()));
			// get the first spectrum file from the spectrum directory
			File[] files = spectrumDirectory.listFiles();
			if (files == null || files.length < 1)
				throw new IllegalArgumentException(
					String.format("Spectrum directory \"%s\" must contain at " +
						"least one readable spectrum file.",
						spectrumDirectory.getAbsolutePath()));
			File spectrumFile = files[0];
			// validate spectrum file
			if (spectrumFile == null)
				throw new NullPointerException(
					"Spectrum file cannot be null.");
			else if (spectrumFile.isFile() == false)
				throw new IllegalArgumentException(
					String.format(
						"Spectrum file \"%s\" must be a regular file.",
						spectrumFile.getAbsolutePath()));
			else if (spectrumFile.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Spectrum file file \"%s\" must be readable.",
						spectrumFile.getAbsolutePath()));
			this.spectrum = spectrumFile.getName();
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static MODaResultProcess extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File input = null;
		File output = null;
		File spectrumDirectory = null;
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
					input = new File(value);
				else if (argument.equals("-output"))
					output = new File(value);
				else if (argument.equals("-spectrum"))
					spectrumDirectory = new File(value);
				else return null;
			}
		}
		try {
			return new MODaResultProcess(input, output, spectrumDirectory);
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
			message = "There was an error processing a MODa result file";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
