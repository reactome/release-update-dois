package org.reactome.release.downloadDirectory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class PathwaySummationMappingFile {
	private static final Logger logger = LogManager.getLogger();
	private static final String pathwaySummationFilename = "pathway2summation.txt";

	@SuppressWarnings("unchecked")
	public static void execute(MySQLAdaptor dba, String releaseNumber) throws Exception {
		logger.info("Running PathwaySummationMappingFile step");
		// Get all Pathway instances
		Collection<GKInstance> pathwayInstances = dba.fetchInstancesByClass(Pathway);
		Set<String> rowHash = new HashSet<>();
		//Create file
		logger.info("Generating " + pathwaySummationFilename + " file...");
		File pathwaySummationFile = new File(pathwaySummationFilename);
		pathwaySummationFile.delete();
		pathwaySummationFile.createNewFile();
		for (GKInstance pathwayInst : pathwayInstances)
		{
			if (containsHumanEntry(pathwayInst))
			{
				// Build line of file
				String nameString = pathwayInst.getAttributeValue(name).toString();
				String stableId = ((GKInstance) pathwayInst.getAttributeValue(stableIdentifier)).getAttributeValue(identifier).toString();
				for (GKInstance summationInst : (Collection<GKInstance>) pathwayInst.getAttributeValuesList(summation))
				{
					String textString = summationInst.getAttributeValue(text).toString().replaceAll("\\s+", " ");

					String row = stableId + "\t" + nameString + "\t" + textString + "\n";
					// Filter duplicates
					if (!rowHash.contains(row))
					{
						Files.write(Paths.get(pathwaySummationFilename), row.getBytes(), StandardOpenOption.APPEND);
						rowHash.add(row);
					}
				}
			}
		}
		String outpathName = releaseNumber + "/" + pathwaySummationFilename;
		Files.move(Paths.get(pathwaySummationFilename), Paths.get(outpathName), StandardCopyOption.REPLACE_EXISTING);

		logger.info("Finished PathwaySummationMappingFile");
	}

	// Check that at least one of the Species instances is for Homo sapiens
	private static boolean containsHumanEntry(GKInstance pathwayInst) throws InvalidAttributeException, Exception {
		@SuppressWarnings("unchecked")
		Collection<GKInstance> speciesInstances = pathwayInst.getAttributeValuesList(species);

		return speciesInstances.stream().anyMatch(species -> species.getDisplayName().equals("Homo sapiens"));
	}
}
