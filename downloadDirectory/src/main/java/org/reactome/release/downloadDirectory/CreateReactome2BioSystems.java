package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.biosystems.ReactomeToBioSystemsConverter;

public class CreateReactome2BioSystems {
	private static final Logger logger = LogManager.getLogger();
	public static void execute(String host, String database, String username, String password, int port, String releaseNumber) throws IOException {
		
		logger.info("Running CreateReactome2BioSystems...");
		// The last argument, 'BioSystems', specifies the output directory of the ReactomeToBioSystems.zip file. 
		// The script removes all files within the named directory, so only change the output directory to an empty or non-existent one
		String bioSystemsDirectory = "BioSystems";
		ReactomeToBioSystemsConverter.main(new String[] {host, database, username, password, Integer.toString(port), bioSystemsDirectory});
		String bioSystemsoutFileName = releaseNumber + "/ReactomeToBioSystems.zip";
		Files.move(Paths.get( bioSystemsDirectory + "/ReactomeToBioSystems.zip"), Paths.get(bioSystemsoutFileName), StandardCopyOption.REPLACE_EXISTING); 
		FileUtils.deleteDirectory(new File(bioSystemsDirectory));
		logger.info("Finished CreateReactome2BioSystems");
	}
}
