package edu.ucsd.result;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import edu.ucsd.result.parsers.EmptyResult;
import edu.ucsd.result.parsers.IterableResult;
import edu.ucsd.result.parsers.Result;
import edu.ucsd.result.processors.ResultProcessor;

public class ResultFactory
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final Logger logger =
		LoggerFactory.getLogger(ResultFactory.class);

	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static final Result createResult(
		Element dataSpec, File resultFile, File tempDirectory,
		File outputDirectory, String taskID, String block,
		Map<String, String> parameters
	) {
		if (dataSpec == null)
			return null;
		Element source = ResultViewXMLUtils.getSourceSpecification(dataSpec);
		Result result = null;
		// if there is no result file, it's not necessarily an error
		if (resultFile == null) {
			result = new EmptyResult();
			String value = getSourceValue(source, parameters);
			if (value != null)
				((EmptyResult)result).setData(value);
		} else {
			// run result file through parser chain
			List<Element> parsers =
				ResultViewXMLUtils.getParserSpecifications(dataSpec);
			result = parseResultFile(
				parsers, resultFile, tempDirectory, outputDirectory,
				taskID, block, parameters);
			// retrieve and handle global processor specifications;
			// these processors will be run on the final parser in the chain
			List<Element> processors =
				ResultViewXMLUtils.getProcessorSpecifications(
					ResultViewXMLUtils.getFirstChildElement(
						dataSpec, "processors"));
			result = processResult(result, processors, parameters);
		}
		if (result == null) {
			logger.error("Error creating and initializing result.");
			return null;
		}
		// attempt to load the result file
		try {
			result.load();
		} catch (Throwable error) {
			logger.error("Error loading result", error);
			return null;
		}
		return result;
	}

	public static final String getSourceValue(
		Element source, Map<String, String> parameters
	) {
		if (source == null)
			return null;
		// extract required attributes
		Map<String, String> attributes =
			ResultViewXMLUtils.getAttributes(source);
		if (attributes == null)
			return null;
		// get source type
		String type = resolveParameters(attributes.get("type"), parameters);
		if (type == null) {
			logger.error("Error retrieving task result file: \"type\" is a " +
				"required attribute of element <source> in the result view " +
				"specification.");
			return null;
		}
		// get value string
		else if (type.toUpperCase().equals("VALUE") == false)
			return null;
		else return resolveParameters(attributes.get("value"), parameters);
	}

	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static Result parseResultFile(
		List<Element> parsers, File file, File tempDirectory,
		File outputDirectory, String taskID, String block,
		Map<String, String> parameters
	) {
		if (parsers == null || parsers.isEmpty())
			return null;
		// verify file
		if (file == null || file.canRead() == false) {
			logger.error("Error instantiating task result: a valid data " +
				"source file could not be retrieved.");
			return null;
		}
		// instantiate all parsers and run them to retrieve a final result
		Result result = null;
		for (int i=0; i<parsers.size(); i++) {
			Element parser = parsers.get(i);
			File directory = null;
			// if this is the last parser in the chain, use the
			// final output directory for the produced file
			if (i == (parsers.size() - 1))
				directory = outputDirectory;
			// otherwise use the temp directory since the file is intermediate
			else directory = tempDirectory;
			result = parseResultFile(
				parser, result, file, directory, taskID, block, parameters);
			if (result == null)
				return null;
		}
		return result;
	}

	private static Result parseResultFile(
		Element parser, Result previous, File file, File outputDirectory,
		String taskID, String block, Map<String, String> parameters
	) {
		if (parser == null)
			return null;
		// extract required attributes
		Map<String, String> attributes =
			ResultViewXMLUtils.getAttributes(parser);
		if (attributes == null)
			return null;
		// get result type
		String type = resolveParameters(attributes.get("type"), parameters);
		if (type == null) {
			logger.error("Error instantiating task result: \"type\" is a " +
				"required attribute of element <parser> in the result view " +
				"specification.");
			return null;
		} else attributes.remove("type");
		// get result instance
		Result result = null;
		if (previous == null)
			result = getResultInstance(
				type, taskID, block, file, outputDirectory);
		else result = getResultInstance(type, block, previous, outputDirectory);
		if (result == null) {
			logger.error("Error obtaining result instance.");
			return null;
		}
		// set properties of the result from attributes
		for (String attribute : attributes.keySet()) {
			String value = resolveParameters(
				attributes.get(attribute), parameters);
			if (setObjectProperty(result, attribute, value, parameters)
				== false) {
				logger.error("Error setting result property \"" +
					attribute + "\".");
				return null;
			}
		}
		// get parameters for this result
		List<Element> parameterSpecs =
			ResultViewXMLUtils.getParameterSpecifications(parser);
		// set properties of the result from parameters
		if (parameterSpecs != null) {
			for (Element parameterSpec : parameterSpecs) {
				Map<String, String> parameter =
					ResultViewXMLUtils.getAttributes(parameterSpec);
				if (parameter == null)
					continue;
				String name =
					resolveParameters(parameter.get("name"), parameters);
				String value =
					resolveParameters(parameter.get("value"), parameters);
				if (name == null || value == null)
					continue;
				else if (setObjectProperty(result, name, value, parameters)
					== false) {
					logger.error(String.format(
						"Error setting result property \"%s\".", name));
					return null;
				}
			}
		}
		// get processors for this result
		List<Element> processors =
			ResultViewXMLUtils.getProcessorSpecifications(parser);
		result = processResult(result, processors, parameters);
		return result;
	}

	private static Result processResult(
		Result result, List<Element> processorSpecs,
		Map<String, String> parameters
	) {
		if (result == null)
			return null;
		else if (processorSpecs == null || processorSpecs.isEmpty())
			return result;
		else if (result instanceof IterableResult == false) {
			logger.error("Error initializing task result processors: " +
				"results of type \"" + result.getClass().getSimpleName() +
				"\" cannot have child <processor> elements in the result " +
				"view specification. <processor> elements are only " +
				"meaningful for iterable result types.");
			return null;
		}
		for (Element processorSpec : processorSpecs) {
			// extract required attributes
			Map<String, String> attributes =
				ResultViewXMLUtils.getAttributes(processorSpec);
			// get processor type
			String type = resolveParameters(attributes.get("type"), parameters);
			if (type == null) {
				logger.error("Error initializing task result processors: " +
					"\"type\" is a required attribute of element " +
					"<processor> in the result view specification.");
				return null;
			} else attributes.remove("type");
			// get processor instance
			ResultProcessor processor = getProcessorInstance(type);
			if (processor == null) {
				logger.error("Error obtaining result processor instance.");
				return null;
			}
			// set all remaining properties of the processor
			for (String attribute : attributes.keySet()) {
				String value = resolveParameters(
					attributes.get(attribute), parameters);
				if (setObjectProperty(
					processor, attribute, value, parameters) == false) {
					logger.error(
						"Error setting result processor property \"" +
						attribute + "\".");
					return null;
				}
			}
			// add the processor to the result
			((IterableResult)result).addProcessor(processor);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static Class<Result> getResultClass(String type) {
		if (type == null)
			return null;
		String className = "edu.ucsd.result.parsers." +
			Character.toUpperCase(type.charAt(0)) + type.substring(1) +
			"Result";
		Class<Result> resultClass = null;
		try {
			resultClass = (Class<Result>)Class.forName(className);
		} catch (ClassNotFoundException error) {
			logger.error("Error loading result class", error);
			return null;
		}
		return resultClass;
	}

	private static Result getResultInstance(
		String type, String taskID, String block,
		File file, File outputDirectory
	) {
		if (file == null)
			return null;
		// get result class
		Class<Result> resultClass = getResultClass(type);
		if (resultClass == null)
			return null;
		// instantiate result class
		Result result = null;
		try {
			result = resultClass.getConstructor(
				File.class, File.class, String.class, String.class).newInstance(
				file, outputDirectory, taskID, block);
		} catch (Throwable error) {
			logger.error("Error instantiating result class", error);
			return null;
		}
		return result;
	}

	private static Result getResultInstance(
		String type, String block, Result previous, File outputDirectory
	) {
		if (previous == null)
			return null;
		// get result class
		Class<Result> resultClass = getResultClass(type);
		if (resultClass == null)
			return null;
		// instantiate result class
		Result result = null;
		try {
			result = resultClass.getConstructor(
				Result.class, File.class, String.class).newInstance(
				previous, outputDirectory, block);
		} catch (Throwable error) {
			logger.error("Error instantiating result class", error);
			return null;
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static ResultProcessor getProcessorInstance(String type) {
		if (type == null)
			return null;
		String className = "edu.ucsd.result.processors." +
			Character.toUpperCase(type.charAt(0)) + type.substring(1) +
			"Processor";
		Class<ResultProcessor> processorClass = null;
		try {
			processorClass = (Class<ResultProcessor>)Class.forName(className);
		} catch (ClassNotFoundException error) {
			logger.error("Error loading result processor class", error);
			return null;
		}
		ResultProcessor processor = null;
		try {
			processor = processorClass.getConstructor().newInstance();
		} catch (Throwable error) {
			logger.error("Error instantiating result class", error);
			return null;
		}
		return processor;
	}

	private static boolean setObjectProperty(
		Object object, String property, String value,
		Map<String, String> parameters
	) {
		if (object == null || property == null || value == null)
			return false;
		// convert the first character of the property to upper case
		property = Character.toUpperCase(property.charAt(0)) +
			property.substring(1);
		// get the property setter method
		Method method = null;
		try {
			method = object.getClass().getMethod(
				"set" + property, String.class);
		} catch (Throwable error) {
			logger.error("Error retrieving setter method for property \"" +
				property + "\" of class \"" +
				object.getClass().getName() + "\"", error);
		} finally {
			if (method == null)
				return false;
		}
		// invoke the property setter method
		try {
			method.invoke(object, value);
		} catch (Throwable error) {
			logger.error("Error invoking setter method for property \"" +
				property + "\" of class \"" +
				object.getClass().getName() + "\"", error);
			return false;
		}
		return true;
	}

	private static String resolveParameters(
		String value, Map<String, String> parameters
	) {
		if (value == null)
			return null;
		// parse out all parameter references
		StringBuffer copy = new StringBuffer(value);
		while (true) {
			// parse out parameter name
			String parameterName = null;
			int start = copy.indexOf("{");
			if (start < 0)
				break;
			int end = copy.indexOf("}");
			if (end <= start)
				break;
			else if (end == start + 1)
				parameterName = "";
			else parameterName = copy.substring(start + 1, end);
			// retrieve parameter value
			String parameterValue = null;
			if (parameters != null)
				parameterValue = parameters.get(parameterName);
			if (parameterValue == null)
				parameterValue = parameterName;
			int paramStart = parameterValue.indexOf("{");
			int paramEnd = parameterValue.indexOf("}");
			if (paramStart >= 0 && paramEnd > paramStart)
				throw new IllegalArgumentException(
					"Request parameter values should not themselves " +
					"be references to other parameters. You run the risk " +
					"of entering an infinite loop!");
			copy.delete(start, end + 1);
			copy.insert(start, parameterValue);
		}
		return copy.toString();
	}
}
