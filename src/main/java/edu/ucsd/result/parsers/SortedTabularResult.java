package edu.ucsd.result.parsers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ucsd.util.CommonUtils;
import edu.ucsd.util.FileIOUtils;
import edu.ucsd.util.OnDemandLoader;

public class SortedTabularResult
extends TabularResult
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final Logger logger =
		LoggerFactory.getLogger(SortedTabularResult.class);
	
	/*========================================================================
	 * Properties
	 *========================================================================*/
	protected File    source;
	protected File    sorted;
	protected String  sortBy;
	protected Boolean ascending;
	protected Boolean numeric;
	
	/*========================================================================
	 * Constructors
	 *========================================================================*/
	public SortedTabularResult(
		File resultFile, File outputDirectory, String taskID, String block
	) throws NullPointerException, IllegalArgumentException, IOException {
		super(resultFile, outputDirectory, taskID, block);
	}
	
	public SortedTabularResult(
		Result result, File outputDirectory, String block
	) throws NullPointerException, IllegalArgumentException, IOException {
		super(result, outputDirectory, block);
	}
	
	@Override
	protected void init(
		File resultFile, File outputDirectory, String taskID, String block
	) throws NullPointerException, IllegalArgumentException, IOException {
		super.init(resultFile, outputDirectory, taskID, block);
		// set source file to original result file, before sorting
		source = resultFile;
		// set default sort field (null)
		setSortBy(null);
		// set default sort operator (ascending)
		setOperator("ascending");
		// set default sort type (null)
		setNumeric(null);
	}
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	@Override
	public void load()
	throws IOException, IllegalArgumentException {
		// the sorted result file must be written in advance
		if (OnDemandLoader.load(this))
			resultFile = sorted;
		else throw new IOException("A valid result file sorted by " +
			"the specified sort field could not be generated.");
		// once the proper sorted file is assigned, load normally
		super.load();
	}
	
	/*========================================================================
	 * Property accessor methods
	 *========================================================================*/
	@Override
	public File getFile() {
		return sorted;
	}
	
	public String getSortBy() {
		return sortBy;
	}
	
	public void setSortBy(String sortBy) {
		this.sortBy = sortBy;
		// if no sort field was specified, then no parsing is required
		if (sortBy == null)
			sorted = source;
		// otherwise, set target for sorted result file to indicate sort field
		else {
			// determine base filename of sorted file
			String sortedBase = FilenameUtils.getBaseName(source.getName());
			String sortedPrefix = block + "_";
			if (sortedBase.startsWith(sortedPrefix) == false)
				sortedBase = sortedPrefix + sortedBase;
			// determine proper suffix for this file, to
			// indicate that it has been sorted
			String sortedSuffix = "." + sortBy + "_";
			if (ascending)
				sortedSuffix += "ascending";
			else sortedSuffix += "descending";
			// only append the suffix if the source file has not
			// already been sorted with the specified parameters
			if (sortedBase.endsWith(sortedSuffix) == false)
				sortedBase = sortedBase + sortedSuffix;
			sorted = new File(
				outputDirectory, String.format("%s.tsv", sortedBase));
		}
	}
	
	public String getOperator() {
		if (ascending)
			return "ascending";
		else return "descending";
	}
	
	public void setOperator(String operator)
	throws NullPointerException, IllegalArgumentException {
		if (operator == null)
			throw new NullPointerException("Sort method cannot be null.");
		else if (operator.toLowerCase().equals("ascending"))
			ascending = true;
		else if (operator.toLowerCase().equals("descending"))
			ascending = false;
		else throw new IllegalArgumentException("Sort method must be either " +
			"\"ascending\" or \"descending\".");
	}
	
	public Boolean isNumeric() {
		return numeric;
	}
	
	public String getNumeric() {
		if (numeric == null)
			return null;
		else return Boolean.toString(numeric);
	}
	
	public void setNumeric(String numeric)
	throws IllegalArgumentException {
		if (numeric == null)
			this.numeric = null;
		else {
			Boolean parsed = CommonUtils.parseBooleanColumn(numeric);
			if (parsed == null)
				throw new IllegalArgumentException(
					"Numeric sort flag must be a boolean string value.");
			else this.numeric = parsed;
		}
	}
	
	/*========================================================================
	 * OnDemandOperation methods
	 *========================================================================*/
	@Override
	public boolean execute() {
		if (source == null)
			return false;
		// if no sort field is specified, then no parsing is required
		else if (sortBy == null)
			return resourceExists();
		// parse source file to determine sorting properties
		Integer sortIndex = null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(source));
			// read header line to determine sort field index
			String header = reader.readLine();
			if (header == null)
				throw new IllegalArgumentException();
			String[] fields = header.split(getEscapedDelimiter());
			if (fields == null || fields.length < 1)
				throw new IllegalArgumentException();
			for (int i=0; i<fields.length; i++) {
				if (fields[i].trim().equals(sortBy)) {
					sortIndex = i;
					break;
				}
			}
			if (sortIndex == null)
				throw new IllegalArgumentException();
			// if sort type was specified in result.xml, use that;
			// otherwise read first data row to determine sort type
			if (isNumeric() == null) {
				String firstRow = reader.readLine();
				// default to non-numeric sort, though it won't really matter
				// in this case since there are no data rows to actually sort
				if (firstRow == null)
					setNumeric("false");
				else {
					fields = firstRow.split(getEscapedDelimiter());
					if (sortIndex >= fields.length)
						throw new IllegalArgumentException();
					String firstSortableElement = fields[sortIndex];
					try {
						Double.parseDouble(firstSortableElement);
						setNumeric("true");
					} catch (Throwable error) {
						setNumeric("false");
					}
				}
			}
			// key index given to Unix sort must be 1-based
			sortIndex = sortIndex + 1;
		} catch (Throwable error) {
			logger.error(String.format(
				"There was an error reading the first line of " +
				"result file [%s] to determine index of sort column [%s].",
				source.getAbsolutePath(), sortBy), error);
			return false;
		} finally {
			try { reader.close(); } catch (Throwable error) {}
		}
		// otherwise, call the OS's sort utility to sort the file efficiently
		try {
			FileIOUtils.sortTSVFile(
				source, sorted, sortIndex, true, isNumeric(), !ascending);
			return true;
		} catch (Throwable error) {
			logger.error(String.format(
				"There was an error sorting result file [%s] by field [%s].",
				source.getAbsolutePath(), sortBy), error);
			return false;
		}
	}
	
	@Override
	public boolean resourceExists() {
		if (sorted == null)
			return false;
		else return sorted.exists();
	}
	
	@Override
	public boolean resourceDated() {
		if (sorted == null || sorted.exists() == false ||
			source == null || source.exists() == false)
			return false;
		else return sorted.lastModified() < source.lastModified(); 
	}
	
	@Override
	public String getResourceName() {
		return sorted.getAbsolutePath();
	}
}
