package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class DatabaseDumps {
	private static final Logger logger = LogManager.getLogger();
	
	public static void execute(MySQLAdaptor dba, String releaseNumber, String username, String password, String host, int port, String testReactomeDatabase) throws IOException, InterruptedException {
		// Take mysqldumps of 'stable_identifiers' and 'test_reactome_##' and compress them using gzip.
		
		//TODO: Refactor
		
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
		
		byte[] buffer = new byte[1024];
		GZIPOutputStream gzosStableIds = new GZIPOutputStream(new FileOutputStream(releaseNumber + "/databases/gk_stable_ids.sql.gz"));
		FileInputStream fisStableIds = new FileInputStream(releaseNumber + "/databases/gk_stable_ids.sql");
		int lengthStId;
		while ((lengthStId = fisStableIds.read(buffer)) > 0) {
			gzosStableIds.write(buffer, 0, lengthStId);
		}
		fisStableIds.close();
		gzosStableIds.finish();
		gzosStableIds.close();
		Files.delete(Paths.get(releaseNumber + "/databases/gk_stable_ids.sql"));
		// Now test_reactome dump
		logger.info("Dumping test_reactome_" + releaseNumber + " to gk_current.sql");
		File gkCurrentFile = new File(releaseNumber + "/databases/gk_current.sql");
		
		String[] gkCurrentCommand = new String[]{"mysqldump", "-h" + host,"-u" + username, "-p" + password, "-P" + port, testReactomeDatabase};
		ProcessBuilder gkCurrentProcessBuilder = new ProcessBuilder(Arrays.asList(gkCurrentCommand));
		gkCurrentProcessBuilder.redirectError(Redirect.INHERIT);
		gkCurrentProcessBuilder.redirectOutput(Redirect.to(gkCurrentFile));
		
		Process gkCurrentProcess = gkCurrentProcessBuilder.start();
		gkCurrentProcess.waitFor();
		
		GZIPOutputStream gzosGkCurrent = new GZIPOutputStream(new FileOutputStream(releaseNumber + "/databases/gk_current.sql.gz"));
		FileInputStream fisGkCurrent = new FileInputStream(releaseNumber + "/databases/gk_current.sql");
		int lengthGk;
		while ((lengthGk = fisGkCurrent.read(buffer)) > 0) {
			gzosGkCurrent.write(buffer, 0, lengthGk);
		}
		fisGkCurrent.close();
		gzosGkCurrent.finish();
		gzosGkCurrent.close();
		
		Files.delete(Paths.get(releaseNumber + "/databases/gk_current.sql"));
		
		logger.info("Finished DatabaseDumps");
	}
}
