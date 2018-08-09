package org.reactome.release.updateDOIs;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class ReportTests {
	
	final static Logger logger = Logger.getLogger(FindNewDOIsAndUpdate.class);
	
	public static void expectedUpdatesTests( HashMap<String,HashMap<String,String>> reportContents, ArrayList<String> updatedDOIs, int reportHits, int fetchHits ) {
		// Checking if provided list matched updated instances
		if (reportContents.size() != 0 && reportHits < reportContents.size())
		{
			for (Object updatedDOI : updatedDOIs)
			{
				reportContents.remove(updatedDOI);
			}
			logger.warn("The following DOIs from the provided list were not updated: ");
			logger.warn("  " + reportContents.keySet());
		} else if (reportContents.size() != 0 && fetchHits > reportContents.size()) {
			logger.warn("The following DOIs were unexpectedly updated: ");
			for (Object updatedDOI : updatedDOIs)
			{
				if (reportContents.get(updatedDOI) == null)
				{
					logger.warn("  " + updatedDOI);
				}
			}
		} else if (reportContents.size() != 0) {

			logger.info("All expected DOIs updated");
		}
	}

}
