package org.reactome.release.downloadDirectory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapOldStableIds {
	private static final Logger logger = LogManager.getLogger();
	private static final String filename = "reactome_stable_ids.txt";
	
	public static void execute(String username, String password, String host, String releaseNumber) throws Exception {
		
		logger.info("Running MapOldStableIds step");
		// Need to use mysql driver to access stable_identifiers db
		logger.info("Connecting to stable_identifers db...");
		Class.forName("com.mysql.jdbc.Driver");
		Connection connect = DriverManager.getConnection("jdbc:mysql://" + host + "/stable_identifiers?" + "user=" + username + "&password=" + password);
		Statement statement = connect.createStatement();
		ResultSet resultSet = statement.executeQuery("SELECT identifier,instanceId FROM StableIdentifier");
		
		// Iterate through returned results of DB IDs and stable IDs 
		logger.info("Mapping Old Stable IDs to Current Stable IDs...");
		Map<String,List<String>> dbIdToStableIds = new HashMap<>();
		List<String> dbIds = new ArrayList<String>();
		while (resultSet.next()) 
		{
			String stableId = resultSet.getString(1);
			String dbId = resultSet.getString(2);
			
			if (dbIdToStableIds.get(dbId) != null) 
			{
				dbIdToStableIds.get(dbId).add(stableId);
			} else {
				ArrayList<String> stableIds = new ArrayList<>();
				stableIds.add(stableId);
				dbIdToStableIds.put(dbId, stableIds);
				dbIds.add(dbId);
			}
		}
		Collections.sort(dbIds);
		
		// Iterate through array of stable IDs associated with DB ID, splitting into human and non-human groups
		List<List<Object>> hsaIds = new ArrayList<>();
		List<List<Object>> nonHsaIds = new ArrayList<>();
		for (String dbId : dbIds) 
		{
			List<String> stableIds = dbIdToStableIds.get(dbId);
			Collections.sort(stableIds);
			
			// After sorting the first stable ID in the array is considered the primary ID.
			// An Array of Arrays is used here, with each interior array's first element being 
			// the primaryId string and the second element being an array of the remaining stable IDs.
			// Example: [R-HSA-1006169, [REACT_118604]], [R-HSA-1006173, [REACT_119254]]]
			if (!(stableIds.size() < 2) || (stableIds.get(0).matches("^R-.*"))) 
			{
				String primaryId = stableIds.get(0);
				stableIds.remove(0);
				ArrayList<Object> organizedIds = new ArrayList<>();
				if (primaryId.matches("R-HSA.*")) 
				{
					organizedIds.add(primaryId);
					organizedIds.add(stableIds);
					hsaIds.add(organizedIds);
				} else {
					organizedIds.add(primaryId);
					organizedIds.add(stableIds);
					nonHsaIds.add(organizedIds);
				}
			}
		}

		// Reorder the data so that the interior arrays that have only 1 element are going to be output first
		List<List<Object>> combinedIds = new ArrayList<>();
		combinedIds.addAll(hsaIds);
		combinedIds.addAll(nonHsaIds);
		List<List<Object>> preferredIds = new ArrayList<>();
		List<List<Object>> deferredIds = new ArrayList<>();
		for (List<Object> stableIdsArray : combinedIds) 
		{
			@SuppressWarnings("unchecked")
			List<String> secondaryIds = (List<String>) stableIdsArray.get(1);
			if (secondaryIds.size() > 1) 
			{
				deferredIds.add(stableIdsArray);
			} else {
				preferredIds.add(stableIdsArray);
			}
		}
		preferredIds.addAll(deferredIds);
		
		// Write to file
		File oldIdMappingFile = new File(filename);
		if (oldIdMappingFile.exists()) {
			oldIdMappingFile.delete();
		}
		oldIdMappingFile.createNewFile();
		String header = "# Reactome stable IDs for release " + releaseNumber + "\n" + "Stable_ID\told_identifier(s)\n";
		Files.write(Paths.get(filename), header.getBytes(), StandardOpenOption.APPEND);
		for (List<Object> stableIdsArray : preferredIds) 
		{
			String primaryId = (String) stableIdsArray.get(0);
			@SuppressWarnings("unchecked")
			List<String> secondaryIds = (ArrayList<String>) stableIdsArray.get(1);
			String line = primaryId + "\t" + String.join(",", secondaryIds) + "\n";
			Files.write(Paths.get(filename), line.getBytes(), StandardOpenOption.APPEND);
		}
		String outpathName = releaseNumber + "/" + filename;
		Files.move(Paths.get(filename), Paths.get(outpathName), StandardCopyOption.REPLACE_EXISTING);
		
		logger.info("MapOldStableIds finished");
	}
}
