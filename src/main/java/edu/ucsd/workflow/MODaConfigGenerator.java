package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.apache.commons.io.FilenameUtils;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import edu.ucsd.util.AminoAcidUtils;
import edu.ucsd.util.AminoAcidUtils.PTMType;
import edu.ucsd.util.FileIOUtils;
import edu.ucsd.util.WorkflowParameterUtils;

public class MODaConfigGenerator
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool modaConfig" +
		"\n\t-spectrum <SpectrumFile>" +
		"\n\t-fasta <DatabaseFile>" +
		"\n\t-param <ParameterFile>" +
		"\n\t-output <OutputDirectory>";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		MODaConfig config = extractArguments(args);
		if (config == null)
			die(USAGE);
		// read input files, write the relevant data to the config file
		PrintWriter output = null;
		try {
			// set up XML parameter file reader
			Document param = FileIOUtils.parseXML(config.param);
			// set up output file writer
			output = new PrintWriter(config.output);
			
			// write spectrum file parameter
			output.print("Spectra=");
			output.println(config.spectrum);
			
			// write instrument parameter
			String instrument = getInstrument(param);
			if (instrument == null)
				die("Could not retrieve \"instrument\" parameter.");
			else {
				output.print("Instrument=");
				output.println(instrument);
			}
			
			// write database file parameter
			output.print("Fasta=");
			output.println(config.fasta);
			
			// write parent mass tolerance parameter
			String pmTolerance = getParameter(param, "tolerance.PM_tolerance");
			if (pmTolerance == null)
				die("Could not retrieve \"parent mass tolerance\" parameter.");
			else {
				output.print("PeptTolerance=");
				output.println(pmTolerance);
			}
			
			// write parent mass correction parameter
			output.println("ParentMassCorrection=1");
			
			// write ion tolerance parameter
			String ionTolerance =
				getParameter(param, "tolerance.Ion_tolerance");
			if (ionTolerance == null)
				die("Could not retrieve \"fragment ion tolerance\" parameter.");
			else {
				output.print("FragTolerance=");
				output.println(ionTolerance);
			}
			
			// write blind mode parameter
			String blindMode = getParameter(param, "moda.blindmode");
			if (blindMode == null)
				die("Could not retrieve \"blind mode\" parameter.");
			else {
				output.print("BlindMode=");
				output.println(blindMode);
			}
			
			// write minimum and maximum modification size parameters
			String minModMass = getParameter(param, "msalign.minmodmass");
			if (minModMass == null)
				die("Could not retrieve \"minimum modification mass\" " +
					"parameter.");
			else {
				output.print("MinModSize=");
				output.println(minModMass);
			}
			String maxModMass = getParameter(param, "msalign.maxmodmass");
			if (maxModMass == null)
				die("Could not retrieve \"maximum modification mass\" " +
					"parameter.");
			else {
				output.print("MaxModSize=");
				output.println(maxModMass);
			}
			
			// write enzyme parameter
			String enzyme = getEnzyme(param);
			if (enzyme == null)
				die("Could not retrieve \"enzyme\" parameter.");
			else {
				output.print("Enzyme=");
				output.println(enzyme);
			}
			
			// write missed cleavage parameter
			String missedCleavage = getParameter(param, "c13_nnet.nnet");
			if (missedCleavage == null)
				die("Could not retrieve \"missed cleavage\" parameter.");
			else {
				output.print("MissedCleavage=");
				output.println(missedCleavage);
			}
			
			// extract PTM map from parameter document
			Map<String, Collection<String>> parameters =
				WorkflowParameterUtils.extractParameters(param);
			
			// write all fixed "*" N-Term PTMs
			Map<String, Collection<Double>> offsets =
				AminoAcidUtils.getMassOffsets(
					parameters, PTMType.FIXED_N_TERMINAL);
			if (offsets != null && offsets.isEmpty() == false) {
				for (String residue : offsets.keySet()) {
					if (residue.equals("*")) {
						Collection<Double> masses = offsets.get(residue);
						if (masses != null && masses.isEmpty() == false) {
							for (Double mass : masses) {
								output.print("ADD=NTerm, ");
								output.println(mass);
							}
						}
					}
				}
			}
			
			// write all fixed PTMs
			offsets = AminoAcidUtils.getMassOffsets(parameters, PTMType.FIXED);
			if (offsets != null && offsets.isEmpty() == false) {
				for (String residue : offsets.keySet()) {
					Collection<Double> masses = offsets.get(residue);
					if (masses != null && masses.isEmpty() == false) {
						for (Double mass : masses) {
							output.print("ADD=");
							output.print(residue);
							output.print(", ");
							output.println(mass);
						}
					}
				}
			}
		} catch (Throwable error) {
			die(null, error);
		} finally {
			if (output != null) try {
				output.close();
			} catch (Throwable error) {}
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each MODa config file generation
	 * operation.
	 */
	private static class MODaConfig {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private String spectrum;
		private String fasta;
		private File param;
		private File output;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public MODaConfig(
			String spectrum, String fasta, File param, File outputDirectory
		) throws IOException {
			// validate spectrum file
			File spectrumFile = new File(spectrum);
			if (spectrumFile.isFile() == false)
				throw new IllegalArgumentException(
					String.format(
						"Spectrum file \"%s\" must be a regular file.",
						spectrumFile.getAbsolutePath()));
			else if (spectrumFile.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Spectrum file \"%s\" must be readable.",
						spectrumFile.getAbsolutePath()));
			this.spectrum = spectrum;
			// validate database file
			File fastaFile = new File(fasta);
			if (fastaFile.isFile() == false)
				throw new IllegalArgumentException(
					String.format(
						"Database file \"%s\" must be a regular file.",
						fastaFile.getAbsolutePath()));
			else if (fastaFile.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Database file \"%s\" must be readable.",
						fastaFile.getAbsolutePath()));
			this.fasta = fasta;
			// validate parameter file
			if (param == null)
				throw new NullPointerException(
					"Parameter file cannot be null.");
			else if (param.isFile() == false)
				throw new IllegalArgumentException(
					String.format("Parameter file \"%s\" " +
						"must be a normal (non-directory) file.",
						param.getAbsolutePath()));
			else if (param.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Parameter file \"%s\" must be readable.",
						param.getAbsolutePath()));
			this.param = param;
			// validate output directory
			if (outputDirectory == null)
				throw new NullPointerException(
					"Output directory cannot be null.");
			else if (outputDirectory.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format(
						"Output directory \"%s\" must be a directory.",
						outputDirectory.getAbsolutePath()));
			else if (outputDirectory.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Output directory \"%s\" must be readable.",
						outputDirectory.getAbsolutePath()));
			// attempt to create output file and test its writeability
			String outputFilename =
				FilenameUtils.getBaseName(spectrum) + ".config";
			output = new File(outputDirectory, outputFilename);
			if (output.createNewFile() == false || output.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Output file \"%s\" must be writable.",
						output.getAbsolutePath()));
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static MODaConfig extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		String spectrum = null;
		String fasta = null;
		File param = null;
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
				if (argument.equals("-spectrum"))
					spectrum = value;
				else if (argument.equals("-fasta"))
					fasta = value;
				else if (argument.equals("-param"))
					param = new File(value);
				else if (argument.equals("-output"))
					output = new File(value);
				else return null;
			}
		}
		try {
			return new MODaConfig(spectrum, fasta, param, output);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static String getParameter(Document document, String parameter)
	throws TransformerException {
		if (document == null || parameter == null)
			return null;
		Node node = XPathAPI.selectSingleNode(document,
			String.format("//parameter[@name='%s']", parameter));
		if (node == null)
			return null;
		else return node.getFirstChild().getNodeValue();
	}
	
	private static String getInstrument(Document document)
	throws TransformerException {
		if (document == null)
			return null;
		String instrument = getParameter(document, "instrument.instrument");
		if (instrument == null)
			return null;
		else if (instrument.equals("ESI-ION-TRAP"))
			return "ESI-TRAP";
		else if (instrument.equals("QTOF"))
			return "ESI-QTOF";
		else throw new IllegalArgumentException(
			String.format(
				"Inappropriate \"instrument\" value for MODa: \"%s\"",
				instrument));
	}
	
	private static String getEnzyme(Document document)
	throws TransformerException {
		if (document == null)
			return null;
		String enzyme =
			getParameter(document, "cysteine_protease.protease");
		if (enzyme == null)
			return null;
		else if (enzyme.equals("Trypsin"))
			return "Trypsin, KR/C";
		else if (enzyme.equals("Chymotrypsin"))
			return "Chymotrypsin, FYWL/C";
		else if (enzyme.equals("Lys-C"))
			return "LysC, K/C";
		else if (enzyme.equals("Lys-N"))
			return "LysN, K/N";
		else if (enzyme.equals("None"))
			return "";
		else throw new IllegalArgumentException(
			String.format(
				"Inappropriate \"enzyme\" value for MODa: \"%s\"",
				enzyme));
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error generating a MODa config file";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
