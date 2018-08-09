package org.reactome.release.updateDOIs;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class ReportTests {
	
	final static Logger logger = Logger.getLogger(FindNewDOIsAndUpdate.class);
	
	public static void expectedUpdatesTests( HashMap<String,HashMap<String,String>> expectedUpdatedDOIs, ArrayList<String> updatedDOIs, ArrayList<String> updated, ArrayList<String> notUpdated, int fetchHits ) {
		// Checking if provided list matched updated instances
		if (notUpdated.size() > 0)
		{
//			for (String updatedDOI : updatedDOIs)
//			{
//				expectedUpdatedDOIs.remove(updatedDOI);
//			}
			for (String missed : notUpdated) {
				String missedDoi = missed.split(":")[0];
				String missedName = missed.split(":")[1];
				String missedClean = missedDoi.replace("10.3180/", "");
				String missedStableId = missedClean.split("\\.")[0];
				String missedStableIdVersion = missedClean.split("\\.")[1];
				for (String key : expectedUpdatedDOIs.keySet()) {
					if (expectedUpdatedDOIs.get(key).get("stableId").equals(missedStableId)) {
						if (!expectedUpdatedDOIs.get(key).get("stableIdVersion").equals(missedStableIdVersion)) {
							logger.warn("[" + key + "] StableID 'version' in DB different from expected: [DB] " + missedDoi + "* [Expected] " + key + "*");
							continue;
						} else if (!expectedUpdatedDOIs.get(key).get("displayName").equals(missedName)) {
							logger.warn("[" + key + "] 'Display name' in DB different from expected: [DB] " + missedName + " [Expected] " + expectedUpdatedDOIs.get(key).get("displayName"));
							continue;
						}
					} else if (expectedUpdatedDOIs.get(key).get("displayName").equals(missedName)) {
						logger.warn("[" + key + "] 'DB ID' from DB different from expected, but found matching display name: [DB] " + missed + " [Expected] " + key + ":" + missedName);
						continue;
					}
				}
			}
			// TODO: Message/logic for those that didn't get found from this
			
				
				
				
				
				
				
//			logger.warn("The following DOIs from the provided list were not updated: ");
//			logger.warn("  " + notUpdated);
//			logger.warn("  " + expectedUpdatedDOIs.keySet());
		} else if (expectedUpdatedDOIs.size() != 0 && fetchHits > expectedUpdatedDOIs.size()) {
			logger.warn("The following DOIs were unexpectedly updated: ");
			for (Object updatedDOI : updatedDOIs)
			{
				if (expectedUpdatedDOIs.get(updatedDOI) == null)
				{
					logger.warn("  " + updatedDOI);
				}
			}
		} else if (expectedUpdatedDOIs.size() != 0) {

			logger.info("All expected DOIs updated");
		}
	}

}
