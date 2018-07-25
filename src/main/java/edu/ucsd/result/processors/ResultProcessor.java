package edu.ucsd.result.processors;

import edu.ucsd.result.parsers.Result;
import edu.ucsd.result.parsers.ResultHit;

public interface ResultProcessor
{
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public void processHit(ResultHit hit, Result result);
}
