package edu.ucsd.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class FileIOUtils
{
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
			throw new IOException(String.format("Error reading file \"%s\"",
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
			throw new IOException(String.format("Error writing to file \"%s\"",
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
					"Error copying contents of file \"%s\" to file \"%s\"",
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
}
