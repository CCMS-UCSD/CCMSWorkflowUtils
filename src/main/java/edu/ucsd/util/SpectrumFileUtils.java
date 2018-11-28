package edu.ucsd.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;

public class SpectrumFileUtils
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	/**
	 * Enumeration of all supported/recognized spectrum file types.
	 * @author jjcarver
	 */
	public static enum SpectrumFileType {
		/*====================================================================
		 * Recognized enumeration values
		 *====================================================================*/
		MGF, MZXML, MZML, MZML_GZ;
		
		/*====================================================================
		 * Public interface methods
		 *====================================================================*/
		@Override
		public String toString() {
			String name = name();
			if (name == null || name.trim().isEmpty())
				throw new IllegalStateException();
			else if (name.equals(MGF.name()))
				return "MGF";
			else if (name.equals(MZXML.name()))
				return "mzXML";
			else if (name.equals(MZML.name()))
				return "mzML";
			else if (name.equals(MZML_GZ.name()))
				return "mzML.gz";
			else throw new UnsupportedOperationException();
		}
		
		public String getMS2SpectrumPattern() {
			String name = name();
			if (name == null || name.trim().isEmpty())
				throw new IllegalStateException();
			else if (name.equals(MGF.name()))
				return "BEGIN IONS";
			else if (name.equals(MZXML.name()))
				return "msLevel=\"2\"";
			else if (name.equals(MZML.name()) || name.equals(MZML_GZ.name()))
				return "name=\"ms level\" value=\"2\"";
			else throw new UnsupportedOperationException();
		}
		
		public String getMS2SpectrumXPath() {
			String name = name();
			if (name == null || name.trim().isEmpty())
				throw new IllegalStateException();
			else if (name.equals(MGF.name()))
				throw new UnsupportedOperationException();
			else if (name.equals(MZXML.name()))
				return "//scan[@msLevel='2']";
			else if (name.equals(MZML.name()) || name.equals(MZML_GZ.name()))
				return "//spectrum/cvParam[@name='ms level' and @value='2']";
			else throw new UnsupportedOperationException();
		}
		
		public boolean isMS2SpectrumElement(
			StartElement element, XMLEventReader reader
		) throws XMLStreamException {
			String name = name();
			if (name == null || name.trim().isEmpty())
				throw new IllegalStateException();
			else if (name.equals(MGF.name()))
				throw new UnsupportedOperationException();
			if (element == null)
				return false;
			else if (name.equals(MZXML.name())) {
				// mzXML MS2 spectra are easy to verify - "scan[@msLevel='2']"
				if (element.getName().getLocalPart().equals("scan")) {
					Attribute msLevel =
						element.getAttributeByName(new QName("msLevel"));
					if (msLevel != null &&
						msLevel.getValue().trim().equals("2"))
						return true;
				}
				return false;
			} else if (name.equals(MZML.name()) ||
				name.equals(MZML_GZ.name())) {
				// verifying mzML MS2 spectra requires examining two elements -
				// "spectrum" / "cvParam[@name='ms level' @value='2']",
				// so the streaming XML reader is necessary to answer this
				// since we need to move through each spectrum's children
				if (reader == null)
					return false;
				else if (element.getName().getLocalPart().equals("spectrum")) {
					// find this spectrum's "ms level" cvParam
					while (reader.hasNext()) {
						XMLEvent event = reader.nextEvent();
						if (event.isStartElement()) {
							StartElement next = event.asStartElement();
							if (next.getName().getLocalPart().equals(
								"cvParam")) {
								Attribute cvName =
									next.getAttributeByName(new QName("name"));
								Attribute value =
									next.getAttributeByName(new QName("value"));
								if (cvName != null && value != null &&
									cvName.getValue().trim().equals(
										"ms level") &&
									value.getValue().trim().equals("2"))
									return true;
							}
						}
						// stop looking when we get to the end of this spectrum
						else if (event.isEndElement()) {
							EndElement end = event.asEndElement();
							if (end.getName().getLocalPart().equals("spectrum"))
								break;
						}
					}
				}
				return false;
			}
			else throw new UnsupportedOperationException();
		}
	}
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static SpectrumFileType getSpectrumFileType(File file) {
		if (file == null)
			return null;
		// check filename extension
		String filename = file.getName();
		String extension = FilenameUtils.getExtension(filename);
		if (extension == null)
			return null;
		// handle mzML.gz properly
		else if (extension.trim().equalsIgnoreCase("gz")) {
			extension =
				FilenameUtils.getExtension(FilenameUtils.getBaseName(filename));
			if (extension != null && extension.trim().equalsIgnoreCase("mzML"))
				return SpectrumFileType.MZML_GZ;
			else return null;
		}
		// all other spectrum file types should have an extension that
		// corresponds directly to the SpectrumFileType enum constant
		else try {
			return SpectrumFileType.valueOf(extension.toUpperCase());
		} catch (Throwable error) {
			return null;
		}
	}
	
	public static Integer countMS2Spectra(File file)
	throws IOException, XMLStreamException {
		return countMS2Spectra(file, false, true);
	}
	
	public static Integer countMS2Spectra(
		File file, boolean grep, boolean stream
	) throws IOException, XMLStreamException {
		if (file == null || file.isFile() == false || file.canRead() == false)
			return null;
		// search file for the proper MS2 spectrum pattern based on type
		SpectrumFileType type = getSpectrumFileType(file);
		if (type == null)
			return 0;
		// simple search (grep -c)
		// TODO: address potential issues if target mzML cvParam
		// attributes do not occur in the file exactly as searched
		if (grep) {
			String pattern = type.getMS2SpectrumPattern();
			switch (type) {
				case MGF:
				case MZXML:
				case MZML:
					return FileIOUtils.countStringOccurrencesInFile(
						file, pattern);
				// read mzML.gz files into gzip stream, count from there
				case MZML_GZ:
					BufferedReader reader = new BufferedReader(
						new InputStreamReader(new GZIPInputStream(
						new FileInputStream(file))));
					try {
						return FileIOUtils.countStringOccurrencesInStream(
							reader, pattern);
					} finally {
						try { reader.close(); }
						catch (Throwable error) {}
					}
			}
		}
		// streaming search (slower, but uses less memory)
		else if (stream) {
			// get the proper stream for this file type
			InputStream inputStream = null;
			switch (type) {
				case MGF:
				case MZXML:
				case MZML:
					inputStream = new FileInputStream(file);
					break;
				case MZML_GZ:
					inputStream =
						new GZIPInputStream(new FileInputStream(file));
					break;
			}
			// stream through the file to look for the proper elements
			switch (type) {
				case MGF:
					String pattern = type.getMS2SpectrumPattern();
					BufferedReader fileReader =
						new BufferedReader(new InputStreamReader(inputStream));
					try {
						return FileIOUtils.countStringOccurrencesInStream(
							fileReader, pattern);
					} finally {
						try { fileReader.close(); }
						catch (Throwable error) {}
					}
				case MZXML:
				case MZML:
				case MZML_GZ:
					int count = 0;
					XMLEventReader xmlReader =
						XMLInputFactory.newInstance().createXMLEventReader(
							inputStream);
					while (xmlReader.hasNext()) {
						XMLEvent event = xmlReader.nextEvent();
						if (event.isStartElement() &&
							type.isMS2SpectrumElement(
								event.asStartElement(), xmlReader))
							count++;
					}
					return count;
			}
		}
		// in-memory search (faster, but uses more memory)
		else {
			switch (type) {
				case MGF:
					return StringUtils.countMatches(FileIOUtils.readFile(file),
						type.getMS2SpectrumPattern());
				case MZXML:
				case MZML:
					return FileIOUtils.countNodeOccurrencesInXML(
						FileIOUtils.parseXML(file), type.getMS2SpectrumXPath());
				case MZML_GZ:
					return FileIOUtils.countNodeOccurrencesInXML(
						FileIOUtils.parseXML(FileIOUtils.readGZIPFile(file)),
						type.getMS2SpectrumXPath());
			}
		}
		return null;
	}
}
