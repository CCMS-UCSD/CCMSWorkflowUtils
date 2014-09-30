package edu.ucsd.workflow;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class WorkflowUtils
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String USAGE = "java -jar CCMSWorkflowUtils.jar" +
		"\n\t-tool <ToolName>" +
		"\n\t[<tool parameters>]";
	private static final String BAD_TOOL =
		"Invalid or unrecognized tool name: \"%s\".";
	private static final String NO_TOOL =
		"Please indicate a valid tool to invoke.";
	public static enum Tool {
		MERGE, JOINTABLES, COPYCOLLECTION, COPYCOLLECTIONTOUSERSPACE,
		FILTERCOLLECTION, SELECTFIRST, SHUFFLEFASTA, MERGEPEPNOVO;
		public String toString() {
			return this.name().toLowerCase();
		}
	}
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		// convert argument array to list, so that it can be mutable
		List<String> arguments = new Vector<String>(Arrays.asList(args));
		Tool tool = determineTool(arguments);
		if (tool == null) {
			System.err.println(NO_TOOL);
			die();
		}
		// restore argument list to array, so it can be passed to other mains
		args = arguments.toArray(new String[arguments.size()]);
		switch (tool) {
			case MERGE:
				Merge.main(args);
				break;
			case JOINTABLES:
				JoinTables.main(args);
				break;
			case COPYCOLLECTION:
				CopyCollection.main(args);
				break;
			case COPYCOLLECTIONTOUSERSPACE:
				CopyCollectionToUserSpace.main(args);
				break;
			case FILTERCOLLECTION:
				FilterCollection.main(args);
				break;
			case SELECTFIRST:
				SelectFirst.main(args);
				break;
			case SHUFFLEFASTA:
				ShuffleFASTA.main(args);
				break;
			case MERGEPEPNOVO:
				MergePepNovo.main(args);
				break;
			default:
				System.err.println(String.format(BAD_TOOL, tool.toString()));
				die();
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static Tool determineTool(List<String> arguments) {
		if (arguments == null || arguments.isEmpty())
			return null;
		for (int i=0; i<arguments.size(); i++) {
			String argument = arguments.get(i);
			if (argument == null)
				return null;
			else if (argument.equals("-tool")) {
				i++;
				if (i >= arguments.size())
					return null;
				else {
					String value = arguments.get(i);
					try {
						// this will throw an exception if
						// the indicated tool is invalid
						Tool tool = Tool.valueOf(value.toUpperCase());
						// remove the tool arguments from the argument list,
						// so that the remaining arguments can be passed
						// directly to the indicated tool
						arguments.remove(i);
						arguments.remove(i - 1);
						return tool;
					} catch (Throwable error) {
						System.err.println(String.format(BAD_TOOL, value));
						return null;
					}
				}
			}
		}
		return null;
	}
	
	private static void die() {
		System.err.println(USAGE);
		System.exit(1);
	}
}
