package edu.ucsd.result;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResultViewXMLUtils
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	@SuppressWarnings("unused")
	private static final Logger logger =
		LoggerFactory.getLogger(ResultViewXMLUtils.class);
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static Element getDownloadSpecification(Document document) {
		if (document == null)
			return null;
		// parse the document to retrieve its download specification
		Element downloadSpec = null;
		try {
			NodeList download = document.getElementsByTagName("download");
			for (int i=0; i<download.getLength(); i++) {
				Node downloadNode = download.item(i);
				// there should only be one download element,
				// so return the first one found
				if (downloadNode instanceof Element == false)
					continue;
				else {
					downloadSpec = (Element)downloadNode;
					break;
				}
			}
		} catch (Throwable error) {
			return null;
		}
		return downloadSpec;
	}
	
	public static Element getViewSpecification(Document document, String view) {
		if (document == null || view == null)
			return null;
		// parse the document to retrieve the proper specification for this view
		Element viewSpec = null;
		try {
			NodeList views = document.getElementsByTagName("view");
			for (int i=0; i<views.getLength(); i++) {
				Node viewNode = views.item(i);
				if (viewNode instanceof Element == false)
					continue;
				Node id = viewNode.getAttributes().getNamedItem("id");
				if (id != null && id.getNodeValue().equals(view)) {
					viewSpec = (Element)viewNode;
					break;
				}
			}
		} catch (Throwable error) {
			return null;
		}
		return viewSpec;
	}
	
	public static List<Element> getViewSpecifications(Document document) {
		if (document == null)
			return null;
		// parse the document to retrieve all of its view specifications
		List<Element> viewSpecs = null;
		try {
			NodeList views = document.getElementsByTagName("view");
			viewSpecs = new Vector<Element>(views.getLength());
			for (int i=0; i<views.getLength(); i++) {
				Node viewNode = views.item(i);
				if (viewNode instanceof Element == false)
					continue;
				else viewSpecs.add((Element)viewNode);
			}
		} catch (Throwable error) {
			return null;
		}
		if (viewSpecs == null || viewSpecs.isEmpty())
			return null;
		else return viewSpecs;
	}
	
	public static Element getBlockSpecification(
		Document document, String block
	) {
		if (document == null || block == null)
			return null;
		// parse the document to retrieve the
		// proper specification for this block
		Element blockSpec = null;
		try {
			NodeList blocks = document.getElementsByTagName("block");
			for (int i=0; i<blocks.getLength(); i++) {
				Node blockNode = blocks.item(i);
				if (blockNode instanceof Element == false)
					continue;
				Node id = blockNode.getAttributes().getNamedItem("id");
				if (id != null && id.getNodeValue().equals(block)) {
					blockSpec = (Element)blockNode;
					break;
				}
			}
		} catch (Throwable error) {
			return null;
		}
		return blockSpec;
	}
	
	public static List<Element> getBlockSpecifications(Document document) {
		if (document == null)
			return null;
		// parse the document to retrieve all of its block specifications
		List<Element> viewSpecs = null;
		try {
			NodeList views = document.getElementsByTagName("block");
			viewSpecs = new Vector<Element>(views.getLength());
			for (int i=0; i<views.getLength(); i++) {
				Node viewNode = views.item(i);
				if (viewNode instanceof Element == false)
					continue;
				else viewSpecs.add((Element)viewNode);
			}
		} catch (Throwable error) {
			return null;
		}
		if (viewSpecs == null || viewSpecs.isEmpty())
			return null;
		else return viewSpecs;
	}
	
	public static Element getDataSpecification(Element block) {
		if (block == null)
			return null;
		// parse the block element to retrieve the
		// proper specification for its data source
		Element dataSpec = null;
		try {
			NodeList data = block.getElementsByTagName("data");
			for (int i=0; i<data.getLength(); i++) {
				Node dataNode = data.item(i);
				// there should only be one data element,
				// so return the first one found
				if (dataNode instanceof Element == false)
					continue;
				else {
					dataSpec = (Element)dataNode;
					break;
				}
			}
		} catch (Throwable error) {
			return null;
		}
		return dataSpec;
	}
	
	public static Element getSourceSpecification(Element dataSpec) {
		return getFirstChildElement(dataSpec, "source");
	}
	
	public static List<Element> getParameterSpecifications(Element sourceSpec) {
		return getChildElements(sourceSpec, "parameter");
	}
	
	public static List<Element> getParserSpecifications(Element dataSpec) {
		List<Element> parsers = getChildElements(
			getFirstChildElement(dataSpec, "parsers"), "parser");
		if (parsers == null || parsers.isEmpty())
			return null;
		else return parsers;
	}
	
	public static List<Element> getProcessorSpecifications(Element parserSpec) {
		List<Element> processors = getChildElements(parserSpec, "processor");
		if (processors == null || processors.isEmpty())
			return null;
		else return processors;
	}
	
	public static List<Element> getChildElements(Element element, String type) {
		if (element == null || type == null)
			return null;
		List<Element> children = null;
		try {
			NodeList childNodes = element.getElementsByTagName(type);
			if (childNodes != null && childNodes.getLength() > 0) {
				children = new Vector<Element>(childNodes.getLength());
				for (int i=0; i<childNodes.getLength(); i++) {
					Node processorNode = childNodes.item(i);
					if (processorNode instanceof Element == false)
						continue;
					else children.add((Element)processorNode);
				}
			}
		} catch (Throwable error) {
			return null;
		}
		if (children == null || children.isEmpty())
			return null;
		else return children;
	}
	
	public static Element getFirstChildElement(Element element, String type) {
		List<Element> children = getChildElements(element, type);
		if (children == null || children.isEmpty())
			return null;
		else return children.get(0);
	}
	
	public static Map<String, String> getAttributes(Element element) {
		if (element == null)
			return null;
		NamedNodeMap attributeMap = element.getAttributes();
		Map<String, String> attributes =
			new HashMap<String, String>(attributeMap.getLength());
		for (int i=0; i<attributeMap.getLength(); i++) {
			Node attribute = attributeMap.item(i);
			attributes.put(attribute.getNodeName(), attribute.getNodeValue());
		}
		if (attributes.isEmpty())
			return null;
		else return attributes;
	}
}
