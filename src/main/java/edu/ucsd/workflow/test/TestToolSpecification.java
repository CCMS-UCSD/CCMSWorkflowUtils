package edu.ucsd.workflow.test;

import java.io.File;
import java.io.PrintWriter;

public class TestToolSpecification
{
	/*========================================================================
	 * Constants
	 *========================================================================*/
	private static final String OUTPUT_FILE = "output.txt";
	
	/*========================================================================
	 * Public interface methods
	 *========================================================================*/
	public static void main(String[] args) {
		PrintWriter output = null;
		try {
			output = new PrintWriter(new File(OUTPUT_FILE));
			println("Analyzing arguments...", output);
			for (String arg : args) {
				String report = analyzeArgument(arg);
				if (report != null)
					println(report, output);
			}
		} catch (Throwable error) {
			error.printStackTrace();
		} finally {
			if (output != null) try {
				output.close();
			} catch (Throwable ignored) {}
		}
	}
	
	/*========================================================================
	 * Convenience methods
	 *========================================================================*/
	private static String analyzeArgument(String arg) {
		if (arg == null)
			return null;
		StringBuffer report = new StringBuffer("\"");
		report.append(arg);
		report.append("\":");
		// first, check to see if the argument is empty or some form of the
		// string "null"; if so, then this argument is probably broken
		if (arg.isEmpty()) {
			report.append("\n  is empty, probably indicating a ");
			report.append("command line generation error");
		} else if (arg.trim().isEmpty()) {
			report.append("\n  is just whitespace, probably indicating a ");
			report.append("command line generation error");
		} else if (arg.equalsIgnoreCase("null")) {
			report.append("\n  is equivalent to the string \"null\", ");
			report.append("probably indicating a command line generation ");
			report.append("error");
		}
		// check to see if this argument starts with a dash ("-");
		// if so, then it's probably a parameter specifier
		else if (arg.startsWith("-")) {
			report.append("\n  starts with a dash (\"-\"), so ");
			report.append("is probably a parameter specifier");
		}
		// report this argument's file status
		else {
			boolean isFile = false;
			File file = new File(arg);
			if (file.isDirectory()) {
				report.append("\n  is a directory");
				isFile = true;
			} else if (file.isFile()) {
				report.append("\n  is a file");
				isFile = true;
			}
			if (isFile) {
				if (file.canRead())
					report.append("\n  is readable");
				else report.append("\n  is NOT readable");
				if (file.canWrite())
					report.append("\n  is writable");
				else report.append("\n  is NOT writable");
				if (file.canExecute())
					report.append("\n  is executable");
				else report.append("\n  is NOT executable");
			} else report.append("\n  appears to be an ordinary scalar input");
		}
		return report.toString();
	}
	
	private static void println(String message, PrintWriter output) {
		if (message == null)
			return;
		System.out.println(message);
		if (output != null)
			output.println(message);
	}
}
