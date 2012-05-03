package edu.ucsd.workflow;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Stack;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.FilenameUtils;

public class XTandemResultParser
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool XTandemResultParser" +
		"\n\t-input <XTandemResultDirectory>" +
		"\n\t-output <ParsedResultFile>";
	private static final String HEADER = "SpectrumFile\tIndex\tPeptide\t" +
		"Protein\tCharge\tPrecursor_MH\tPeptide_MH\tDelta_MH\tE_Value\t" +
		"Hyperscore";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		XTandemResult parser = extractArguments(args);
		if (parser == null)
			die(USAGE);
		// parse XML result files, write the content of each to the output file
		PrintWriter output = null;
		XMLEventReader reader = null;
		try {
			// set up output file writer
			output = new PrintWriter(parser.parsedOutput);
			// write tab-delimited header line to output file
			output.println(HEADER);
			// set up XML result file parsers
			XMLInputFactory factory = XMLInputFactory.newInstance();
			// iterate over XML result files
			File[] results = parser.resultDirectory.listFiles();
			if (results == null || results.length < 1)
				die("No X!Tandem result files were found in directory \"" +
					parser.resultDirectory.getAbsolutePath() + "\"");
			for (File result : results) {
				if (result == null)
					die("A bad X!Tandem result file was found in directory \"" +
						parser.resultDirectory.getAbsolutePath() + "\"");
				else if (result.canRead() == false)
					die("X!Tandem result file \"" + result.getAbsolutePath() +
						"\" could not be read.");
				// parse this XML result file
				reader = factory.createXMLEventReader(new FileReader(result));
				// set up objects for keeping track of result hit data
				Stack<XMLEvent> groups = new Stack<XMLEvent>();
				String filename = null;
				XTandemResultHit hit = null;
				// read through XML file, handle each event properly
				while (reader.hasNext()) {
					XMLEvent event = reader.nextEvent();
					int type = event.getEventType();
					if (type == XMLStreamConstants.START_ELEMENT) {
						// get basic element information
						StartElement element = event.asStartElement();
						String name = element.getName().getLocalPart();
						// if this is the beginning of the file,
						// get the input spectrum file name
						if (name.equals("bioml")) {
							filename = getInputFilename(
								getAttribute(element, "label"));
							if (filename == null)
								die("No input spectrum filename could be " +
									"found in X!Tandem output file \"" +
									result.getAbsolutePath() + "\"");
						} else if (name.equals("group")) {
							groups.push(event);
							// if this is the only group currently in the stack,
							// then this is a top-level <group> element
							if (groups.size() == 1) {
								// some top-level <group> elements do not
								// correspond to X!Tandem search "hits" --
								// this one will correspond to a hit only if
								// its "type" attribute has a value of "model"
								String group = getAttribute(element, "type");
								if (group != null && group.equals("model")) {
									hit = new XTandemResultHit(filename);
									hit.index = Integer.parseInt(
										getAttribute(element, "id"));
									hit.protein =
										getAttribute(element, "label");
									hit.charge = Integer.parseInt(
										getAttribute(element, "z"));
									hit.precursorMH = Double.parseDouble(
										getAttribute(element, "mh"));
								}
							}
						}
						// <domain> elements correspond to the final set of
						// data needed to complete an X!Tandem result "hit" --
						// it's possible for a top-level <group> element to
						// contain more than one descendant <domain> element,
						// so we will only parse the first one
						else if (name.equals("domain") && hit != null) {
							hit.peptide = getAttribute(element, "seq");
							hit.peptideMH = Double.parseDouble(
								getAttribute(element, "mh"));
							hit.deltaMH = Double.parseDouble(
								getAttribute(element, "delta"));
							hit.eValue = Double.parseDouble(
								getAttribute(element, "expect"));
							hit.hyperscore = Double.parseDouble(
								getAttribute(element, "hyperscore"));
							if (writeHit(output, hit) == false)
								die("There was a problem writing a row " +
									"to the result file");
							hit = null;
						}
					} else if (type == XMLStreamConstants.END_ELEMENT) {
						EndElement element = event.asEndElement();
						if (element.getName().getLocalPart().equals("group")) {
							groups.pop();
							if (groups.size() < 1 && hit != null)
								die("An X!Tandem result hit was not " +
									"completely annotated within the scope " +
									"of a single top-level <group> element " +
									"in X!Tandem result file \"" +
									result.getAbsolutePath() + "\"");
						}
					}
				}
				reader.close();
			}
		} catch (Throwable error) {
			die(null, error);
		} finally {
			if (output != null) try {
				output.close();
			} catch (Throwable error) {}
			if (reader != null) try {
				reader.close();
			} catch (Throwable error) {}
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each X!Tandem result file parsing
	 * operation.
	 */
	private static class XTandemResult {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File resultDirectory;
		private File parsedOutput;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public XTandemResult(File resultDirectory, File parsedOutput)
		throws IOException {
			// validate result directory
			if (resultDirectory == null)
				throw new NullPointerException(
					"Result directory cannot be null.");
			else if (resultDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format(
						"Result directory \"%s\" must be a directory.",
						resultDirectory.getAbsolutePath()));
			else if (resultDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Result directory \"%s\" must be readable.",
						resultDirectory.getAbsolutePath()));
			this.resultDirectory = resultDirectory;
			// attempt to create output file and test its writeability
			if (parsedOutput == null)
				throw new NullPointerException("Output file cannot be null.");
			else if (parsedOutput.createNewFile() == false ||
				parsedOutput.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be writable.",
						parsedOutput.getAbsolutePath()));
			this.parsedOutput = parsedOutput;
		}
	}
	
	/**
	 * Struct to maintain context data for each result "hit" in an X!Tandem
	 * XML result file.
	 */
	private static class XTandemResultHit {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private String resultFile;
		private Integer index;
		private String protein;
		private String peptide;
		private Integer charge;
		private Double precursorMH;
		private Double peptideMH;
		private Double deltaMH;
		private Double eValue;
		private Double hyperscore;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public XTandemResultHit(String resultFile) {
			this.resultFile = resultFile;
			index = null;
			protein = null;
			peptide = null;
			charge = null;
			precursorMH = null;
			peptideMH = null;
			deltaMH = null;
			eValue = null;
			hyperscore = null;
		}
		
		/*====================================================================
		 * Public interface methods
		 *====================================================================*/
		public final boolean isValid() {
			return (index != null && resultFile != null && protein != null &&
				peptide != null && charge != null && precursorMH != null &&
				peptideMH != null && deltaMH != null && eValue != null &&
				hyperscore != null);
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static XTandemResult extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File resultDirectory = null;
		File parsedOutput = null;
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
					resultDirectory = new File(value);
				else if (argument.equals("-output"))
					parsedOutput = new File(value);
				else return null;
			}
		}
		try {
			return new XTandemResult(resultDirectory, parsedOutput);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static String getAttribute(StartElement element, String name) {
		if (element == null || name == null)
			return null;
		Attribute attribute = element.getAttributeByName(new QName(name));
		if (attribute == null)
			return null;
		else return attribute.getValue();
	}
	
	private static String getInputFilename(String label) {
		if (label == null || label.length() < 15)
			return null;
		else {
			// cut out the fixed-length fluff added by X!Tandem
			label = label.substring(13, label.length() - 1);
			// return only the last part of the file path
			return FilenameUtils.getName(label);
		}
	}
	
	private static boolean writeHit(PrintWriter output, XTandemResultHit hit) {
		if (output == null || hit == null || hit.isValid() == false)
			return false;
		output.print(hit.resultFile);
		output.print("\t");
		output.print(hit.index);
		output.print("\t");
		output.print(hit.peptide);
		output.print("\t");
		output.print(hit.protein);
		output.print("\t");
		output.print(hit.charge);
		output.print("\t");
		output.print(hit.precursorMH);
		output.print("\t");
		output.print(hit.peptideMH);
		output.print("\t");
		output.print(hit.deltaMH);
		output.print("\t");
		output.print(hit.eValue);
		output.print("\t");
		output.print(hit.hyperscore);
		output.println();
		return true;
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error parsing X!Tandem result files";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
