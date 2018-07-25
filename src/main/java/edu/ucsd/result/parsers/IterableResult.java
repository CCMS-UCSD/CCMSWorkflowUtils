package edu.ucsd.result.parsers;

import java.util.Iterator;
import java.util.List;

import edu.ucsd.result.processors.ResultProcessor;

/**
 * Sub-interface for results representing ordered lists of result elements.
 * 
 * @author Jeremy Carver
 */
public interface IterableResult
extends Result, Iterator<ResultHit>, Iterable<ResultHit>
{
	/*========================================================================
	 * Property accessor methods
	 *========================================================================*/
	public List<String> getFieldNames();
	
	public List<String> getAttributeNames();
	
	public void addAttributeName(String name);
	
	public String getHeaderLine();
	
	/*========================================================================
	 * Processor methods
	 *========================================================================*/
	public List<ResultProcessor> getProcessors();
	
	public void addProcessor(ResultProcessor processor);
}
