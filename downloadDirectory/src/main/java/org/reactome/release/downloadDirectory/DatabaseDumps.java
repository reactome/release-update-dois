package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class DatabaseDumps {
	private static final Logger logger = LogManager.getLogger();
	
	public static void execute(MySQLAdaptor dba, String releaseNumber, String username, String password, String host, int port, String testReactomeDatabase) throws IOException, InterruptedException {
		// Take mysqldumps of 'stable_identifiers' and 'test_reactome_##' and compress them using gzip.
		
		//TODO: The gzipped and non-gzipped files might be getting moved 
		
		logger.info("Generating DatabaseDumps");
		// Create databases folder that will hold the DB dumps
		File databaseDir = new File(releaseNumber + "/databases");
		if (!databaseDir.exists()) {
			databaseDir.mkdir();
		}
		// Start with stable_identifiers dump
		//TODO: Sticking to GK naming conventions?
		logger.info("Dumping stable_identifiers to gk_stable_ids.sql...");
		File stableIdsFile = new File(releaseNumber + "/databases/gk_stable_ids.sql");
		String[] stableIdsCommand = new String[]{"mysqldump", "-h" + host,"-u" + username, "-p" + password, "-P" + port, "stable_identifiers"};
		ProcessBuilder stableIdsprocessBuilder = new ProcessBuilder(Arrays.asList(stableIdsCommand));
		stableIdsprocessBuilder.redirectError(Redirect.INHERIT);
		stableIdsprocessBuilder.redirectOutput(Redirect.to(stableIdsFile));
		Process stableIdsProcess = stableIdsprocessBuilder.start();
		stableIdsProcess.waitFor();
		
		Process gzipStableIds  = Runtime.getRuntime().exec("gzip " + releaseNumber + "/databases/gk_stable_ids.sql");
		gzipStableIds.waitFor();

		// Now test_reactome dump
		logger.info("Dumping test_reactome_" + releaseNumber + " to gk_current.sql");
		File gkCurrentFile = new File(releaseNumber + "/databases/gk_current.sql");
		
		String[] gkCurrentCommand = new String[]{"mysqldump", "-h" + host,"-u" + username, "-p" + password, "-P" + port, testReactomeDatabase};
		ProcessBuilder gkCurrentProcessBuilder = new ProcessBuilder(Arrays.asList(gkCurrentCommand));
		gkCurrentProcessBuilder.redirectError(Redirect.INHERIT);
		gkCurrentProcessBuilder.redirectOutput(Redirect.to(gkCurrentFile));
		
		Process gkCurrentProcess = gkCurrentProcessBuilder.start();
		gkCurrentProcess.waitFor();
		
		Process gzipGkCurrent = Runtime.getRuntime().exec("gzip " + releaseNumber + "/databases/gk_current.sql");
		gzipGkCurrent.waitFor();
		
		logger.info("Finished DatabaseDumps");
	}
}
