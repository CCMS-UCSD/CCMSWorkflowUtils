package edu.ucsd.result.parsers;

import java.util.List;

/**
 * Interface representing generic hits parsed out of workflow result files
 * for display in the CCMS ProteoSAFe web application result view.
 * 
 * @author Jeremy Carver
 */
public interface ResultHit
{
	/*========================================================================
	 * Property accessor methods
	 *========================================================================*/
	public List<String> getFieldNames();
	
	public String getFieldValue(String name);
	
	public List<String> getFieldValues(String name);
	
	public String getFirstFieldValue(String name);
	
	public void setFieldValue(String name, String value);
	
	public char getDelimiter();
	
	public void setDelimiter(char delimiter);
	
	public List<String> getAttributeNames();
	
	public String getAttribute(String name);
	
	public void setAttribute(String name, String value);
	
	public String toJSON();
}
