package edu.ucsd.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.xpath.XPathAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unix4j.Unix4j;
import org.unix4j.unix.Grep;
import org.unix4j.unix.cut.CutOption;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.ucsd.saint.commons.Helpers;

public class FileIOUtils
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final Logger logger =
		LoggerFactory.getLogger(FileIOUtils.class);
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static final String readFile(File file)
	throws IOException {
		if (file == null)
			return null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			StringBuffer contents = new StringBuffer();
			String line = null;
			while ((line = reader.readLine()) != null) {
				contents.append(line);
				contents.append("\n");
			}
			return contents.toString();
		} catch (IOException error) {
			throw new IOException(String.format("Error reading file [%s]",
				file.getAbsolutePath()), error);
		} finally {
			if (reader != null) try {
				reader.close();
			} catch (IOException error) {}
		}
	}
	public static final String readGZIPFile(File file)
	throws IOException {
		if (file == null)
			return null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(new FileInputStream(file))));
			StringBuffer contents = new StringBuffer();
			String line = null;
			while ((line = reader.readLine()) != null) {
				contents.append(line);
				contents.append("\n");
			}
			return contents.toString();
		} catch (IOException error) {
			throw new IOException(String.format(
				"Error reading gzipped file [%s]",
				file.getAbsolutePath()), error);
		} finally {
			if (reader != null) try {
				reader.close();
			} catch (IOException error) {}
		}
	}
	
	public static final boolean writeFile(
		File file, String contents, boolean append
	) throws IOException {
		if (file == null || contents == null)
			return false;
		// write document to file
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(file, append));
			writer.write(contents);
			return true;
		} catch (IOException error) {
			throw new IOException(String.format("Error writing to file [%s]",
				file.getAbsolutePath()), error);
		} finally {
			if (writer != null) try {
				writer.close();
			} catch (IOException error) {}
		}
	}
	
	public static final boolean copyFile(File source, File destination)
	throws IOException {
		if (source == null || destination == null)
			return false;
		BufferedReader reader = null;
		PrintWriter writer = null;
		try {
			reader = new BufferedReader(new FileReader(source));
			writer = new PrintWriter(
				new BufferedWriter(new FileWriter(destination, false)));
			String line = null;
			while ((line = reader.readLine()) != null)
				writer.println(line);
			return true;
		} catch (IOException error) {
			throw new IOException(String.format(
					"Error copying contents of file [%s] to file [%s]",
					source.getAbsolutePath(), destination.getAbsolutePath()),
				error);
		} finally {
			if (reader != null) try {
				reader.close();
			} catch (Throwable error) {}
			if (writer != null) try {
				writer.close();
			} catch (Throwable error) {}
		}
	}
	
	public static final String getMD5Hash(File file)
	throws IOException {
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
		} catch (NoSuchAlgorithmException error) {
			return null;
		} finally {
			try { input.close(); }
			catch (Throwable error) {}
		}
	}
	
	public static final Document parseXML(File file)
	throws IOException {
		if (file == null || file.canRead() == false)
			return null;
		else {
			// read XML file contents into string
			String contents = readFile(file);
			// build XML document from parameters file
			return parseXML(contents);
		}
	}
	
	public static final Document parseXML(String contents)
	throws IOException {
		if (contents == null)
			return null;
		else {
			// get document builder
			DocumentBuilderFactory factory =
				DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = null;
			try {
				builder = factory.newDocumentBuilder();
			} catch (ParserConfigurationException error) {
				throw new IOException(
					"Error instantiating XML DocumentBuilder", error);
			}
			// parse XML string into document
			Document document = null;
			try {
				document = builder.parse(
					new ByteArrayInputStream(contents.getBytes()));
			} catch (IOException error) {
				throw new IOException("Error parsing XML document", error);
			} catch (SAXException error) {
				throw new IOException("Error parsing XML document", error);
			}
			return document;
		}
	}
	
	public static final String printXML(Document document)
	throws IOException {
		if (document == null)
			return null;
		else {
			Transformer transformer = null;
			try {
				transformer =
					TransformerFactory.newInstance().newTransformer();
			} catch (TransformerConfigurationException error) {
				throw new IOException(
					"Error instantiating XML Transformer", error);
			}
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			StreamResult result = new StreamResult(new StringWriter());
			DOMSource source = new DOMSource(document);
			try {
				transformer.transform(source, result);
			} catch (TransformerException error) {
				throw new IOException("Error transforming XML", error);
			}
			return result.getWriter().toString();
		}
	}
	
	public static final NodeList parseXMLNodes(
		Document document, String xPath
	) throws IOException {
		if (document == null || xPath == null || xPath.isEmpty())
			return null;
		try {
			return XPathAPI.selectNodeList(document, xPath);
		} catch (TransformerException error) {
			throw new IOException("Error transforming XML", error);
		}
	}
	
	public static final Integer countNodeOccurrencesInXML(
		Document document, String xPath
	) throws IOException {
		if (document == null)
			return null;
		else if (xPath == null || xPath.isEmpty())
			return 0;
		NodeList nodes = parseXMLNodes(document, xPath);
		if (nodes == null)
			return 0;
		else return nodes.getLength();
	}
	
	public static final Integer countStringOccurrencesInFile(
		File file, String pattern
	) throws IOException {
		if (file == null || file.isFile() == false || file.canRead() == false)
			return null;
		else if (pattern == null || pattern.isEmpty())
			return 0;
		String result = Unix4j.grep(Grep.Options.c, pattern, file)
			.cut(CutOption.fields, ":", 1).toStringResult();
		try {
			return Integer.parseInt(result);
		} catch (NumberFormatException error) {
			throw new IOException(String.format(
				"Could not parse grep -c output [%s] as an integer.", result),
				error);
		}
	}
	
	public static final Integer countStringOccurrencesInStream(
		BufferedReader reader, String pattern
	) throws IOException {
		if (reader == null)
			return null;
		else if (pattern == null || pattern.isEmpty())
			return 0;
		int count = 0;
		String line = null;
		while ((line = reader.readLine()) != null)
			count += StringUtils.countMatches(line, pattern);
		return count;
	}
	
	public static final String getMappedPath(
		String filename, Map<String, String> filenames
	) {
		if (filename == null)
			return null;
		else if (filenames == null || filenames.isEmpty())
			return filename;
		// first try to find a literal match for the filename in the map
		String path = filenames.get(filename);
		// if no literal match was found, compare filename bases
		if (path == null) {
			String baseFilename = FilenameUtils.getBaseName(filename);
			String extension = FilenameUtils.getExtension(filename);
			for (String mapped : filenames.keySet()) {
				if (baseFilename.equals(FilenameUtils.getBaseName(mapped))) {
					path = changeExtension(filenames.get(mapped), extension);
					break;
				}
			}
		}
		// if no good match was found, return the original filename
		if (path == null)
			return filename;
		else return path;
	}
	
	public static final String changeExtension(
		String filename, String extension
	) {
		if (filename == null)
			return null;
		// if the new extension is null, then just remove the extension
		else if (extension == null)
			return String.format("%s%s", FilenameUtils.getPath(filename),
				FilenameUtils.getBaseName(filename));
		// otherwise change the old extension to the new one
		else return String.format("%s%s.%s", FilenameUtils.getPath(filename),
			FilenameUtils.getBaseName(filename), extension);
	}
	
	public static final void sortTSVFile(
		File inputFile, File outputFile,
		int sortColumn, boolean header, boolean numericSort, boolean descending
	) {
		// validate input TSV file
		if (inputFile == null)
			throw new NullPointerException(
				"Argument TSV file to be sorted is null.");
		else if (inputFile.canRead() == false)
			throw new IllegalArgumentException(String.format(
				"Argument TSV file to be sorted [%s] must be readable.",
				inputFile.getAbsolutePath()));
		// validate output sorted file
		if (outputFile == null)
			throw new NullPointerException(
				"Output sorted file cannot be null.");
		else if (outputFile.isDirectory())
			throw new IllegalArgumentException(String.format(
				"Output sorted file [%s] must be a normal " +
				"(non-directory) file.", outputFile.getAbsolutePath()));
		// TODO: validate sort column index
		try {
			// ensure output file exists; create blank output file if not
			if (outputFile.exists() == false &&
				outputFile.createNewFile() == false)
				throw new RuntimeException(String.format(
					"Could not create output sorted file [%s].",
					outputFile.getAbsolutePath()));
			// build Unix sort command line
			StringBuilder command = new StringBuilder();
			String inputPath = inputFile.getAbsolutePath();
			if (header)
				command.append("(head -n 1 \"").append(inputPath)
					.append("\" && tail -n +2 \"").append(inputPath);
			else command.append("(cat \"").append(inputPath);
			command.append("\" | sort -t$'\\t' -k").append(sortColumn)
				.append(",").append(sortColumn);
			if (numericSort || descending) {
				command.append(" -");
				if (numericSort)
					command.append("g");
				if (descending)
					command.append("r");
			}
			command.append(") > \"").append(outputFile.getAbsolutePath())
				.append("\"");
			logger.info(String.format(
				"Calling Unix sort: [%s]", command.toString()));
			ProcessBuilder builder = new ProcessBuilder();
			builder.command("bash", "-c", command.toString());
			// set up sort process
			builder.redirectErrorStream(true);
			Process process = builder.start();
			// run and clean up process
			Helpers.runSimpleProcess(process);
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw new RuntimeException(error);
		}
	}
}
