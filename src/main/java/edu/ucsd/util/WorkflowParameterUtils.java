package edu.ucsd.util;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WorkflowParameterUtils
{
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static final Map<String, Collection<String>> extractParameters(
		Document parameters
	) {
		if (parameters == null)
			return null;
		Map<String, Collection<String>> extracted =
			new TreeMap<String, Collection<String>>();
		try {
			NodeList nodes = XPathAPI.selectNodeList(parameters, "//parameter");
			for (int i=0; i<nodes.getLength(); i++) {
				Node node = nodes.item(i);
				addParameterValue(extracted,
					getAttribute(node, "name"), node.getTextContent());
			}
		} catch (Throwable error) {
			return null;
		}
		if (extracted.isEmpty())
			return null;
		else return extracted;
	}
	
	public static final String getParameter(
		Map<String, Collection<String>> parameters, String parameter
	) {
		if (parameters == null || parameter == null)
			return null;
		Collection<String> values = parameters.get(parameter);
		if (values == null || values.isEmpty())
			return null;
		else return values.toArray(new String[values.size()])[0];
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static boolean addParameterValue(
		Map<String, Collection<String>> parameters,
		String parameter, String value
	) {
		if (parameters == null || parameter == null || value == null)
			return false;
		Collection<String> values = parameters.get(parameter);
		if (values == null)
			values = new Vector<String>();
		values.add(value);
		parameters.put(parameter, values);
		return true;
	}
	
	private static String getAttribute(Node node, String attribute) {
		if (node == null || attribute == null)
			return null;
		try {
			return node.getAttributes().getNamedItem(attribute).getNodeValue();
		} catch (Throwable error) {
			return null;
		}
	}
}
