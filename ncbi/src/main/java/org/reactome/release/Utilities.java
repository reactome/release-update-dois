package org.reactome.release;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Utilities {

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
}
