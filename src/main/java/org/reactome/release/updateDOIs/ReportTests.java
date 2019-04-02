package org.reactome.release.updateDOIs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;

public class ReportTests {
	
	private static final Logger logger = LogManager.getLogger();
	private static final Logger warningsLog = LogManager.getLogger("warningsLog");
	private static final String REACTOME_DOI_PREFIX = "10.3180";
	
	// Compares the DB IDs and display names of the instances to be updated from Test Reactome and GK Central
	public static boolean verifyDOIMatches( GKInstance trDOI, GKInstance gkDOI, String newDOI ) {
		if (trDOI.getDBID().equals(gkDOI.getDBID()) && trDOI.getDisplayName().equals(gkDOI.getDisplayName())) {
			return true;
		} else if (trDOI.getDBID().equals(gkDOI.getDBID()) && !trDOI.getDisplayName().equals(gkDOI.getDisplayName())) {
			warningsLog.warn("[" + newDOI + "] Display names do not match: [Test Reactome]: " + trDOI.getDisplayName() + " ~ [GK Central]: " + gkDOI.getDisplayName());
			return false;
		} else if (!trDOI.getDBID().equals(gkDOI.getDBID()) && trDOI.getDisplayName().equals(gkDOI.getDisplayName())) {
			warningsLog.warn("[" + newDOI + "] DB IDs do not match: [Test Reactome]: " + trDOI.getDBID() + " ~ [GK Central]: " + gkDOI.getDBID());
			return false;
		} else {
			warningsLog.warn("DB ID and display name do not match: [Test Reactome]: " + trDOI + " ~ [GK Central]: " + gkDOI);
			return false;
		}
		
	}
	
	public static void expectedUpdatesTests(Map<String,Map<String,String>> expectedUpdatedDOIs, List<String> updated, List<String> notUpdated, int expectedNumberOfUpdatedDOIs ) {
		// Checking if provided list matched updated instances. Any that don't, it attempts to determine why they might not of been updated.
		// This entails comparing the DB ID, display name and the stable ID version of the provided list (UpdateDOIs.report) with the actual updated instances
		if (notUpdated.size() > 0)
		{
			warningsLog.warn("Some DOIs from UpdateDOIs.report were not updated");
			List<String> unresolvedDOIs = new ArrayList<>();
			for (String missed : notUpdated) 
			{
				String missedDoi = missed.split(":")[0];
				String missedName = missed.split(":")[1];
				String missedClean = missedDoi.replace(REACTOME_DOI_PREFIX + "/", "");
				String missedStableId = missedClean.split("\\.")[0];
				String missedStableIdVersion = missedClean.split("\\.")[1];
				int resolved = 0;
				// Iterate through each of the DOI's provided in UpdateDOIs.report, trying to find a match for the DOI that wasn't updated. 
				// If it finds a match of either DB ID, stable ID version, or the display name, it will try to determine which of those 3 fields don't match.
				// Once it finds the field that doesn't match, it logs it and then ends the current iteration.
				for (String key : expectedUpdatedDOIs.keySet()) 
				{
					if (expectedUpdatedDOIs.get(key).get("stableId").equals(missedStableId)) 
					{
						if (!expectedUpdatedDOIs.get(key).get("stableIdVersion").equals(missedStableIdVersion)) 
						{
							warningsLog.warn("[" + key + "] StableID 'version' in DB different from expected: [DB] " + missedDoi + "* ~ [Expected] " + key + "*");
							resolved++;
							continue;
						} else if (!expectedUpdatedDOIs.get(key).get("displayName").equals(missedName)) {
							warningsLog.warn("[" + key + "] 'Display name' in DB different from expected: [DB] " + missedName + " ~ [Expected] " + expectedUpdatedDOIs.get(key).get("displayName"));
							resolved++;
							continue;
						}
					} else if (expectedUpdatedDOIs.get(key).get("displayName").equals(missedName)) {
						warningsLog.warn("[" + key + "] 'DB ID' from DB different from expected, but found matching display name: ~ [DB] " + missed + " [Expected] " + key + ":" + missedName);
						resolved++;
						continue;
					}
				}
				if (resolved == 0) 
				{
					unresolvedDOIs.add(missedDoi);
				}
			}
			if (unresolvedDOIs.size() > 0) 
			{
				for (String unresolvedDOI : unresolvedDOIs) 
				{
					warningsLog.warn("[" + unresolvedDOI + "]" + "DOI does not match any DOIs expected to be updated -- Could not match display name or DB ID");
				}
			}
		} else if (expectedUpdatedDOIs.size() != 0 && expectedNumberOfUpdatedDOIs > expectedUpdatedDOIs.size()) {
			warningsLog.warn("The following DOIs were unexpectedly updated: ");
			for (Object updatedDOI : updated)
			{
				if (expectedUpdatedDOIs.get(updatedDOI) == null)
				{
					warningsLog.warn("  " + updatedDOI);
				}
			}
		} else if (expectedUpdatedDOIs.size() != 0) {

			warningsLog.info("All expected DOIs updated");
		}
	}

}
