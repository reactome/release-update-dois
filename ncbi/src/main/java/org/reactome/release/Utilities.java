package org.reactome.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * General Utilities class.  This class contains methods needed for general manipulation of data or common operations
 * used by the data export process.
 * @author jweiser
 */
public class Utilities {
	/**
	 * Takes a set of Strings and splits it into a list of the requested number of sub-sets.  If an ordered set, order
	 * is preserved.  If the set does not split evenly into the number of set-sets requested, the remainder elements
	 * will be added one element each to the beginning sets of the returned list.
	 * @param set Set of Strings to split
	 * @param numOfSubSets Number of sub-sets to return
	 * @return List of String sub-sets
	 */
	public static List<Set<String>> splitSet(Set<String> set, int numOfSubSets) {
		int subSetSize = set.size() / numOfSubSets ;
		int numberOfExtraKeys = set.size() % numOfSubSets;
		if (numberOfExtraKeys > 0) {
			subSetSize += 1;
		}

		List<Set<String>> splitSets = new ArrayList<>();

		Set<String> subSet = new LinkedHashSet<>();
		int keyCount = 0;
		for(String ncbiGeneXMLNodeString : set) {
			subSet.add(ncbiGeneXMLNodeString);
			keyCount += 1;

			// Sub set is "full" and the next sub set should be populated
			if (keyCount == subSetSize) {
				splitSets.add(subSet);
				subSet = new LinkedHashSet<>();
				keyCount = 0;

				if (numberOfExtraKeys > 0) {
					numberOfExtraKeys--;

					if (numberOfExtraKeys == 0) {
						subSetSize--;
					}
				}
			}
		}

		return splitSets;
	}

	/**
	 * Appends a String value to a file, specified by path, with a new line character (determined by OS)
	 * @param lineToAppend Line to append to file
	 * @param filePath Path of file to which to append line
	 * @throws IOException Thrown if unable to append to file at path provided
	 */
	public static void appendWithNewLine(String lineToAppend, Path filePath) throws IOException {
		Files.write(
			filePath,
			lineToAppend.concat(System.lineSeparator()).getBytes(),
			StandardOpenOption.APPEND
		);
	}


	/**
	 * Appends a List of String values to a file, specified by path, with a new line character (determined by OS)
	 * added for each String value in the list
	 * @param linesToAppend Lines to append to file
	 * @param filePath Path of file to which to append line
	 * @throws IOException Thrown if unable to append to file at path provided
	 */
	public static void appendWithNewLine(Iterable<String> linesToAppend, Path filePath) throws IOException {
		for (String lineToAppend : linesToAppend) {
			appendWithNewLine(lineToAppend, filePath);
		}
	}
}