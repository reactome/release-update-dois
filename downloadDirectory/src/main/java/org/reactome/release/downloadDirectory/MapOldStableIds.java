package org.reactome.release.downloadDirectory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

public class MapOldStableIds {
	private static final Logger logger = LogManager.getLogger();
	
	private static Connection connect = null;
	private static Statement statement = null;
	private PreparedStatement preparedStatement = null;
	private static ResultSet resultSet = null;
	
	public static void execute(String username, String password, String host, String releaseNumber) throws Exception {
		logger.info("Running MapOldStableIds");
		// Need to use mysql driver to access stable_identifiers db
		logger.info("Connecting to stable_identifers db...");
		Class.forName("com.mysql.jdbc.Driver");
		connect = DriverManager.getConnection("jdbc:mysql://" + host + "/stable_identifiers?" + "user=" + username + "&password=" + password);
		statement = connect.createStatement();
		resultSet = statement.executeQuery("SELECT identifier,instanceId FROM StableIdentifier");
		
		// Iterate through returned results of DB IDs and stable IDs 
		logger.info("Mapping Old Stable IDs to Current Stable IDs...");
		HashMap<String,ArrayList<String>> dbIdToStableIds = new HashMap<String,ArrayList<String>>();
		ArrayList<String> dbIds = new ArrayList<String>();
		while (resultSet.next()) {
			String stableId = resultSet.getString(1);
			String dbId = resultSet.getString(2);
			
			if (dbIdToStableIds.get(dbId) != null) {
				dbIdToStableIds.get(dbId).add(stableId);
			} else {
				ArrayList<String> stableIds = new ArrayList<String>(Arrays.asList(stableId));
				dbIdToStableIds.put(dbId, stableIds);
				dbIds.add(dbId);
			}
		}
		Collections.sort(dbIds);
		
		// Iterate through array of stable IDs associated with DB ID, splitting into human and non-human groups
		ArrayList<ArrayList<Object>> hsaIds = new ArrayList<ArrayList<Object>>();
		ArrayList<ArrayList<Object>> nonHsaIds = new ArrayList<ArrayList<Object>>();
		for (String dbId : dbIds) {
			ArrayList<String> stableIds = dbIdToStableIds.get(dbId);
			Collections.sort(stableIds);

			if (stableIds.size() < 2 || !stableIds.get(0).matches("^R-.*")) {
				continue;
			}
			
			// After sorting the first stable ID in the array is considered the primary ID.
			// An Array of Arrays is used here, with each interior array's first element being 
			// the primaryId string and the second element being an array of the remaining stable IDs.
			String primaryId = stableIds.get(0);
			stableIds.remove(0);
			ArrayList<Object> organizedIds = new ArrayList<Object>();
			if (primaryId.matches("R-HSA.*")) {
				organizedIds.add(primaryId);
				organizedIds.add(stableIds);
				hsaIds.add(organizedIds);
			} else {
				organizedIds.add(primaryId);
				organizedIds.add(stableIds);
				nonHsaIds.add(organizedIds);
			}
		}

		// Reorder the data so that the interior arrays that have only 1 element are going to be output first
		ArrayList<ArrayList<Object>> combinedIds = new ArrayList<ArrayList<Object>>(hsaIds);
		combinedIds.addAll(nonHsaIds);
		ArrayList<ArrayList<Object>> preferredIds = new ArrayList<ArrayList<Object>>();
		ArrayList<ArrayList<Object>> deferredIds = new ArrayList<ArrayList<Object>>();
		for (ArrayList<Object> stableIdsArray : combinedIds) {
			String primaryId = (String) stableIdsArray.get(0);
			ArrayList<String> secondaryIds = (ArrayList<String>) stableIdsArray.get(1);
			if (secondaryIds.size() > 1) {
				deferredIds.add(stableIdsArray);
			} else {
				preferredIds.add(stableIdsArray);
			}
		}
		preferredIds.addAll(deferredIds);
		
		// Write to file
		PrintWriter oldIdMappingFile = new PrintWriter("reactome_stable_ids.txt");
		oldIdMappingFile.close();
		String header = "# Reactome stable IDs for release " + releaseNumber + "\n" + "Stable_ID\told_identifier(s)\n";
		Files.write(Paths.get("reactome_stable_ids.txt"), header.getBytes(), StandardOpenOption.APPEND);
		for (ArrayList<Object> stableIdsArray : preferredIds) {
			String primaryId = (String) stableIdsArray.get(0);
			ArrayList<String> secondaryIds = (ArrayList<String>) stableIdsArray.get(1);
			String line = primaryId + "\t" + String.join(",", secondaryIds) + "\n";
			Files.write(Paths.get("reactome_stable_ids.txt"), line.getBytes(), StandardOpenOption.APPEND);
		}
		String outpathName = releaseNumber + "/reactome_stable_ids.txt";
		Files.move(Paths.get("reactome_stable_ids.txt"), Paths.get(outpathName), StandardCopyOption.REPLACE_EXISTING); 
		
		logger.info("MapOldStableIds finished");
	}
}
