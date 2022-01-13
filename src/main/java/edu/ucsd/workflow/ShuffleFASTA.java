package edu.ucsd.workflow;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import edu.ucsd.util.FileIOUtils;

public class ShuffleFASTA
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool shuffleFasta" +
		"\n\t-input <FastaFile>" +
		"\n\t-output <OutputFile>";
	private static final String DECOY_PREFIX = "XXX";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		FASTAShuffle shuffle = extractArguments(args);
		if (shuffle == null)
			die(USAGE);
		// read input file, reverse FASTA sequences and write to output file
		BufferedReader input = null;
		PrintWriter output = null;
		try {
			// first copy the contents of the input file to the output file
			if (FileIOUtils.copyFile(shuffle.input, shuffle.output) == false)
				die("Error copying contents of input FASTA file");
			// set up input file reader
			input = new BufferedReader(new FileReader(shuffle.input));
			// set up output file writer
			output = new PrintWriter(
				new BufferedWriter(new FileWriter(shuffle.output, true)));
			// add a line break to separate unshuffled content from shuffled
			output.println();
			// read and reverse sequences one by one from input file
			String line = null;
			StringBuffer sequence = null;
			// If sequence is already reversed, don't write the reversed
			// version of that
			boolean prevProteinIsDecoy = false;
			while (true) {
				line = input.readLine();
				if (line == null) {
					// if a previous sequence is still being processed,
					// reverse it and write it to the output file
					if (
						sequence != null && sequence.length() > 0 &&
						(!prevProteinIsDecoy)
					) {
						writeReversedSequence(sequence, output);
					}
					break;
				} else if (line.startsWith(">")) {
					// process the FASTA comment line to
					// insert the proper decoy prefix
					line = line.substring(1).trim();
					// if a previous sequence is still being processed,
					// reverse it and write it to the output file
					if (
						sequence != null && sequence.length() > 0 &&
						(!prevProteinIsDecoy)
					) {
						writeReversedSequence(sequence, output);
					}
					prevProteinIsDecoy = line.startsWith(DECOY_PREFIX);
					line = ">" + DECOY_PREFIX + "_" + line;
					// start a new sequence
					sequence = new StringBuffer();
					// write new comment line to output file
					if (!prevProteinIsDecoy) {
						output.println(line);
					}
				} else if (line.trim().equals("") == false) {
					if (sequence == null)
						die("Bad FASTA file: sequence text encountered " +
							"before a valid comment line");
					else sequence.append(line.trim());
				}
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
	 * Struct to maintain context data for each FASTA sequence shuffling
	 * operation.
	 */
	private static class FASTAShuffle {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File input;
		private File output;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public FASTAShuffle(File input, File output)
		throws IOException {
			// validate input file
			if (input == null)
				throw new NullPointerException(
					"Input file cannot be null.");
			else if (input.isFile() == false)
				throw new IllegalArgumentException(
					String.format(
						"Input file \"%s\" must be a regular file.",
						input.getAbsolutePath()));
			else if (input.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Input file \"%s\" must be readable.",
						input.getAbsolutePath()));
			this.input = input;
			// validate output file
			if (output == null)
				throw new NullPointerException(
					"Output file cannot be null.");
			// attempt to create output file and test its writeability
			if (output.createNewFile() == false || output.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be writable.",
						output.getAbsolutePath()));
			this.output = output;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static FASTAShuffle extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File input = null;
		File output = null;
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
				else return null;
			}
		}
		try {
			return new FASTAShuffle(input, output);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static void writeReversedSequence(
		StringBuffer sequence, PrintWriter output
	) {
		if (sequence == null || output == null)
			return;
		sequence = sequence.reverse();
		output.println(sequence.toString());
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error reversing FASTA sequences";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
