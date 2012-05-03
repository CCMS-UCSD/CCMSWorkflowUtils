package edu.ucsd.workflow;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.ucsd.util.AminoAcidUtils;
import edu.ucsd.util.AminoAcidUtils.PTMType;
import edu.ucsd.util.FileIOUtils;
import edu.ucsd.util.WorkflowParameterUtils;

public class XTandemConfigGenerator
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool XTandemConfig" +
		"\n\t-spectra <SpectrumDirectory>" +
		"\n\t-fasta <FastaDirectory>" +
		"\n\t-param <ParameterFile>" +
		"\n\t-default <DefaultInputFile>" +
		"\n\t-taxonomy <TaxonomyFile>" +
		"\n\t-config <ConfigDirectory>" +
		"\n\t-output <OutputDirectory>";
	private static final String TAXON = "ProteoSAFe_fasta_databases";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		XTandemConfig config = extractArguments(args);
		if (config == null)
			die(USAGE);
		// create X!Tandem taxonomy file
		PrintWriter output = null;
		try {
			// build taxonomy XML document
			Document taxonomyDocument =
				FileIOUtils.parseXML("<?xml version=\"1.0\"?>\n" +
					"<bioml label=\"x! taxon-to-file matching list\">\n" +
					"\t<taxon label=\"ProteoSAFe_fasta_databases\">\n" +
					"\t</taxon>\n" +
					"</bioml>\n");
			// iterate over fasta files, adding each to the taxonomy file
			for (File fasta : config.fasta.listFiles()) {
				// create new file node
				Element fileNode = taxonomyDocument.createElement("file");
				fileNode.setAttribute("format", "peptide");
				fileNode.setAttribute("URL", getFilePath(fasta));
				// append the file node to the <taxon> element, which
				// is the second child of the root element (index = 1)
				taxonomyDocument.getDocumentElement().getChildNodes().item(1)
					.appendChild(fileNode);
			}
			// write document to file
			output = new PrintWriter(config.taxonomy);
			output.print(FileIOUtils.printXML(taxonomyDocument));
		} catch (Throwable error) {
			die(null, error);
		} finally {
			if (output != null) try {
				output.close();
			} catch (Throwable error) {}
		}
		
		// read ProteoSAFe parameter file
		Document params = null;
		try {
			params = FileIOUtils.parseXML(config.param);
		} catch (Throwable error) {
			die(null, error);
		}
		
		// create common portion of X!Tandem config files
		String xml = "<?xml version=\"1.0\"?>\n<bioml>\n</bioml>\n";
		Document inputDocument = null;
		Element root = null;
		try {
			inputDocument = FileIOUtils.parseXML(xml);
			root = inputDocument.getDocumentElement();
		} catch (Throwable error) {
			die(null, error);
		}
		
		// add default input file parameter
		Element node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label", "list path, default parameters");
		node.setTextContent(config.defaults.getAbsolutePath());
		root.appendChild(node);
		
		// add taxonomy file parameter
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label", "list path, taxonomy information");
		node.setTextContent(getFilePath(config.taxonomy));
		root.appendChild(node);
		
		// add taxon parameter
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label", "protein, taxon");
		node.setTextContent(TAXON);
		root.appendChild(node);
		
		// extract parameters from XML document
		Map<String, Collection<String>> parameters =
			WorkflowParameterUtils.extractParameters(params);
		
		// add fragmentation method parameters
		String value = getFragmentationMethod(parameters);
		if (value == null)
			die("Could not retrieve \"fragmentation\" parameter.");
		// only modify the defaults if the fragmentation method is ETD
		else if (value.equals("ETD")) {
			// add b-ions parameter (set to "no")
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label", "scoring, b ions");
			node.setTextContent("no");
			root.appendChild(node);
			// add y-ions parameter (set to "no")
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label", "scoring, y ions");
			node.setTextContent("no");
			root.appendChild(node);
			// add c-ions parameter (set to "yes")
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label", "scoring, c ions");
			node.setTextContent("yes");
			root.appendChild(node);
			// add z-ions parameter (set to "yes")
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label", "scoring, z ions");
			node.setTextContent("yes");
			root.appendChild(node);
		}
		
		// add fixed PTM mass offset parameter
		value = getMassOffsets(parameters, PTMType.FIXED);
		// value may be null, if no relevant PTM
		// parameters were selected by the user
		if (value != null) {
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label", "residue, modification mass");
			node.setTextContent(value);
			root.appendChild(node);
		}
		
		// add optional PTM mass offset parameter
		value = getMassOffsets(parameters, PTMType.OPTIONAL);
		// value may be null, if no relevant PTM
		// parameters were selected by the user
		if (value != null) {
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label",
				"residue, potential modification mass");
			node.setTextContent(value);
			root.appendChild(node);
		}
		
		// add protease parameter
		value = getProtease(parameters);
		if (value == null)
			die("Could not retrieve \"protease\" parameter.");
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label", "protein, cleavage site");
		node.setTextContent(value);
		root.appendChild(node);
		// if enzyme was set to "None", then another
		// scoring parameter must be updated properly
		if (value.equals("[X]|[X]")) {
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label",
				"scoring, maximum missed cleavage sites");
			node.setTextContent("50");
			root.appendChild(node);
		}
		
		// add parent mass tolerance parameters
		value = WorkflowParameterUtils.getParameter(
			parameters, "tolerance.PM_tolerance");
		if (value == null)
			die("Could not retrieve \"parent mass tolerance\" parameter.");
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label",
			"spectrum, parent monoisotopic mass error plus");
		node.setTextContent(value);
		root.appendChild(node);
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label",
			"spectrum, parent monoisotopic mass error minus");
		node.setTextContent(value);
		root.appendChild(node);
		
		// add parent mass tolerance unit parameter
		value = getToleranceUnits(parameters, "PM");
		if (value == null)
			die("Could not retrieve \"parent mass tolerance unit\" parameter.");
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label",
			"spectrum, parent monoisotopic mass error units");
		node.setTextContent(value);
		root.appendChild(node);
		
		// add ion tolerance parameter
		value = WorkflowParameterUtils.getParameter(
			parameters, "tolerance.Ion_tolerance");
		if (value == null)
			die("Could not retrieve \"ion tolerance\" parameter.");
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label",
			"spectrum, fragment monoisotopic mass error");
		node.setTextContent(value);
		root.appendChild(node);
		
		// add ion tolerance unit parameter
		value = getToleranceUnits(parameters, "Ion");
		if (value == null)
			die("Could not retrieve \"ion tolerance unit\" parameter.");
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label",
			"spectrum, fragment monoisotopic mass error units");
		node.setTextContent(value);
		root.appendChild(node);
		
		// add C13 parameter
		value = getC13(parameters);
		if (value == null)
			die("Could not retrieve \"number of allowed 13C\" parameter.");
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label",
			"spectrum, parent monoisotopic mass isotope error");
		node.setTextContent(value);
		root.appendChild(node);
		
		// disable refinement search
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label", "refine");
		node.setTextContent("no");
		root.appendChild(node);
		
		// add output sorting parameter (set to "spectrum")
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label", "output, sort results by");
		node.setTextContent("spectrum");
		root.appendChild(node);
		
		// add output results parameter (set to "all")
		node = inputDocument.createElement("note");
		node.setAttribute("type", "input");
		node.setAttribute("label", "output, results");
		node.setTextContent("all");
		root.appendChild(node);
		
		// write common XML to string for reuse
		try {
			xml = FileIOUtils.printXML(inputDocument);
		} catch (Throwable error) {
			die(null, error);
		}
		
		// create X!Tandem input files
		int index = 0;
		for (File spectrum : config.spectra.listFiles()) {
			// set up relevant file paths for this input file
			String id = String.format("%05d", index);
			File configFile = new File(config.config, "input_" + id + ".xml");
			File outputFile = new File(config.output, "output_" + id + ".xml");
			
			// build new document
			try {
				inputDocument = FileIOUtils.parseXML(xml);
				root = inputDocument.getDocumentElement();
			} catch (Throwable error) {
				die(null, error);
			}
			
			// add input spectrum file parameter
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label", "spectrum, path");
			node.setTextContent(getFilePath(spectrum));
			root.appendChild(node);
			
			// add output file parameter
			node = inputDocument.createElement("note");
			node.setAttribute("type", "input");
			node.setAttribute("label", "output, path");
			node.setTextContent(getFilePath(outputFile));
			root.appendChild(node);
			
			// write the input file
			output = null;
			try {
				output = new PrintWriter(configFile);
				output.print(FileIOUtils.printXML(inputDocument));
			} catch (Throwable error) {
				die(null, error);
			} finally {
				if (output != null) try {
					output.close();
				} catch (Throwable error) {}
			}
			index++;
		}
	}
	
	/*========================================================================
	 * Convenience classes
	 *========================================================================*/
	/**
	 * Struct to maintain context data for each X!Tandem config file generation
	 * operation.
	 */
	private static class XTandemConfig {
		/*====================================================================
		 * Properties
		 *====================================================================*/
		private File spectra;
		private File fasta;
		private File param;
		private File defaults;
		private File taxonomy;
		private File config;
		private File output;
		
		/*====================================================================
		 * Constructors
		 *====================================================================*/
		public XTandemConfig(
			File spectra, File fasta, File param, File defaults,
			File taxonomy, File config, File output
		) throws IOException {
			// validate spectrum directory
			if (spectra == null)
				throw new NullPointerException(
					"Spectrum directory cannot be null.");
			else if (spectra.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format(
						"Spectrum directory \"%s\" must be a directory.",
						spectra.getAbsolutePath()));
			else if (spectra.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Spectrum directory \"%s\" must be readable.",
						spectra.getAbsolutePath()));
			this.spectra = spectra;
			// validate fasta directory
			if (fasta == null)
				throw new NullPointerException(
					"Fasta directory cannot be null.");
			// if a single file was given, process its parent directory
			else if (fasta.isDirectory() == false)
				fasta = fasta.getParentFile();
			if (fasta.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format(
						"Fasta directory \"%s\" must be a directory.",
						fasta.getAbsolutePath()));
			else if (fasta.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Fasta directory \"%s\" must be readable.",
						fasta.getAbsolutePath()));
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
			// validate default input file
			if (defaults == null)
				throw new NullPointerException(
					"Default input file cannot be null.");
			else if (defaults.isFile() == false)
				throw new IllegalArgumentException(
					String.format("Default input file \"%s\" " +
						"must be a normal (non-directory) file.",
						defaults.getAbsolutePath()));
			else if (defaults.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Default input file \"%s\" must be readable.",
						defaults.getAbsolutePath()));
			this.defaults = defaults;
			// validate taxonomy file
			if (taxonomy == null)
				throw new NullPointerException(
					"Taxonomy file cannot be null.");
			// attempt to create taxonomy file and test its writeability
			if (taxonomy.createNewFile() == false ||
				taxonomy.canWrite() == false)
				throw new IllegalArgumentException(
					String.format("Taxonomy file \"%s\" must be writable.",
						taxonomy.getAbsolutePath()));
			this.taxonomy = taxonomy;
			// validate config directory
			if (config == null)
				throw new NullPointerException(
					"Config directory cannot be null.");
			else if (config.isDirectory() == false)
				throw new IllegalArgumentException(
					String.format(
						"Config directory \"%s\" must be a directory.",
						config.getAbsolutePath()));
			else if (config.canRead() == false)
				throw new IllegalArgumentException(
					String.format("Config directory \"%s\" must be readable.",
							config.getAbsolutePath()));
			this.config = config;
			// validate output directory
			if (output == null)
				throw new NullPointerException(
					"Output directory cannot be null.");
			// this config tool will not actually be writing any output files,
			// it just needs to know where they will go, so no explicit
			// validation of the output directory is necessary
			this.output = output;
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static XTandemConfig extractArguments(String[] args) {
		if (args == null || args.length < 1)
			return null;
		File spectra = null;
		File fasta = null;
		File param = null;
		File defaults = null;
		File taxonomy = null;
		File config = null;
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
				if (argument.equals("-spectra"))
					spectra = new File(value);
				else if (argument.equals("-fasta"))
					fasta = new File(value);
				else if (argument.equals("-param"))
					param = new File(value);
				else if (argument.equals("-default"))
					defaults = new File(value);
				else if (argument.equals("-taxonomy"))
					taxonomy = new File(value);
				else if (argument.equals("-config"))
					config = new File(value);
				else if (argument.equals("-output"))
					output = new File(value);
				else return null;
			}
		}
		try {
			return new XTandemConfig(spectra, fasta, param, defaults,
				taxonomy, config, output);
		} catch (Throwable error) {
			System.err.println(error.getMessage());
			return null;
		}
	}
	
	private static String getFilePath(File file) {
		if (file == null)
			return null;
		File parentDirectory = file.getParentFile();
		if (parentDirectory == null)
			return null;
		String separator = System.getProperty("file.separator");
		if (separator == null)
			separator = "/";
		return parentDirectory.getName() + separator + file.getName();
	}
	
	private static String getFragmentationMethod(
		Map <String, Collection<String>> parameters
	) {
		if (parameters == null)
			return null;
		String fragmentation = WorkflowParameterUtils.getParameter(
			parameters, "fragmentation.fragmentation");
		if (fragmentation == null)
			return null;
		else if (fragmentation.equals("CID/HCD/QTOF"))
			return "CID";
		else if (fragmentation.equals("ETD"))
			return "ETD";
		else throw new IllegalArgumentException(
			String.format(
				"Inappropriate \"fragmentation\" value for X!Tandem: \"%s\"",
				fragmentation));
	}
	
	private static String getMassOffsets(
		Map<String, Collection<String>> parameters, PTMType type
	) {
		if (parameters == null || type == null)
			return null;
		// get amino acid mass offsets for PTMs of this type, and write them
		// into a proper parameter string of the format expected by X!Tandem
		StringBuffer mods = new StringBuffer();
		Map<String, Collection<Double>> massOffsets =
			AminoAcidUtils.getMassOffsets(parameters, type);
		if (massOffsets == null || massOffsets.size() < 1)
			return null;
		for (String residue : massOffsets.keySet()) {
			Collection<Double> masses = massOffsets.get(residue);
			if (masses != null && masses.isEmpty() == false) {
				for (Double mass : masses) {
					// only add this mod into the parameter string if
					// it's actually a real non-zero mass offset
					if (mass == null || mass.equals(Double.valueOf(0.0)))
						continue;
					mods.append(mass);
					mods.append("@");
					mods.append(residue);
					mods.append(",");
				}
			}
		}
		if (mods.length() < 2)
			return null;
		// trim the trailing comma before returning the parameter string
		else return mods.substring(0, mods.length() - 1);
	}
	
	private static String getProtease(
		Map<String, Collection<String>> parameters
	) {
		if (parameters == null)
			return null;
		String protease = WorkflowParameterUtils.getParameter(
			parameters, "cysteine_protease.protease");
		if (protease == null)
			return null;
		else if (protease.equals("Trypsin"))
			return "[KR]|{P}";
		else if (protease.equals("Chymotrypsin"))
			return "[FYWL]|{P}";
		else if (protease.equals("Lys-N"))
			return "[X]|[K]";
		else if (protease.equals("Lys-C"))
			return "[K]|{P}";
		else if (protease.equals("Asp-N"))
			return "[X]|[DE]";
		else if (protease.equals("Arg-C"))
			return "[R]|[X]";
		else if (protease.equals("Glu-C"))
			return "[DE]|[X]";
		else if (protease.equals("None"))
			return "[X]|[X]";
		else throw new IllegalArgumentException(
			String.format(
				"Inappropriate \"protease\" value for X!Tandem: \"%s\"",
				protease));
	}
	
	private static String getToleranceUnits(
		Map<String, Collection<String>> parameters, String type
	) {
		if (parameters == null || type == null)
			return null;
		else if (type.equals("PM") == false && type.equals("Ion") == false)
			return null;
		String tolerance = WorkflowParameterUtils.getParameter(
			parameters, "tolerance_unit." + type + "_unit");
		if (tolerance == null)
			return null;
		else if (tolerance.equals("Da"))
			return "Daltons";
		else if (tolerance.equals("ppm"))
			return "ppm";
		else throw new IllegalArgumentException(
			String.format("Inappropriate \"mass tolerance units\" " +
				"value for X!Tandem: \"%s\"", tolerance));
	}
	
	private static String getC13(
		Map<String, Collection<String>> parameters
	) {
		if (parameters == null)
			return null;
		String c13 = WorkflowParameterUtils.getParameter(
			parameters, "c13_nnet.c13");
		if (c13 == null)
			return null;
		else if (c13.equals("0"))
			return "no";
		else if (c13.equals("1"))
			return "yes";
		else throw new IllegalArgumentException(
			String.format("Inappropriate \"number of allowed 13C\" " +
				"value for X!Tandem: \"%s\"", c13));
	}
	
	private static void die(String message) {
		die(message, null);
	}
	
	private static void die(String message, Throwable error) {
		if (message == null)
			message = "There was an error generating an X!Tandem config file";
		if (error == null)
			message += ".";
		else message += ":";
		System.err.println(message);
		if (error != null)
			error.printStackTrace();
		System.exit(1);
	}
}
