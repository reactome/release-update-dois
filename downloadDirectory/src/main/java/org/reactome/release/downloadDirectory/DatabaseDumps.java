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
	
	public static void execute(String releaseNumber, String username, String password, String host, int port, String testReactomeDatabase) throws IOException, InterruptedException {
		// Take mysqldumps of 'stable_identifiers' and 'test_reactome_##' and compress them using gzip.
		
		//TODO: Refactor
		
		logger.info("Running DatabaseDumps step");
		// Create databases folder that will hold the DB dumps
		Files.createDirectories(Paths.get(releaseNumber + "/databases"));
		// Generate stable_identifiers DB dump
		dumpDatabaseAndGzip("stable_identifiers", username, password, host, port, releaseNumber + "/databases/gk_stable_ids.sql");
		// Generate test_reactome dump
		dumpDatabaseAndGzip(testReactomeDatabase, username, password, host, port, releaseNumber + "/databases/gk_current.sql");
		
		logger.info("Finished DatabaseDumps");
	}
	
	private static void dumpDatabaseAndGzip(String databaseName, String username, String password, String host, int port, String dumpFile)
	    throws IOException, InterruptedException{
		    
	    dumpDatabase(databaseName, username, password, host, port, dumpFile);
	    gzip(dumpFile);
	    Files.delete(Paths.get(dumpFile));
	}

	private static void dumpDatabase(String databaseName, String username, String password, String host, int port, String dumpFile) 
	    throws IOException, InterruptedException {
	    logger.info("Dumping " + databaseName + " to " + dumpFile);
	    File gkCurrentFile = new File(dumpFile);

	    String[] dumpCommand = new String[]{"mysqldump", "-h" + host,"-u" + username, "-p" + password, "-P" + port, databaseName};
	    ProcessBuilder dumpProcessBuilder = new ProcessBuilder(Arrays.asList(dumpCommand));
	    dumpProcessBuilder.redirectError(Redirect.INHERIT);
	    dumpProcessBuilder.redirectOutput(Redirect.to(gkCurrentFile));

	    Process dumpProcess = dumpProcessBuilder.start();
	    dumpProcess.waitFor();
	}

	private static void gzip(String dumpFile) throws IOException {
	    GZIPOutputStream gzosGkCurrent = new GZIPOutputStream(new FileOutputStream(dumpFile + ".gz"));
	    FileInputStream fisGkCurrent = new FileInputStream(dumpFile);
	    byte[] buffer = new byte[1024];
	    int lengthGk;
	    while ((lengthGk = fisGkCurrent.read(buffer)) > 0) {
	        gzosGkCurrent.write(buffer, 0, lengthGk);
	    }
	    fisGkCurrent.close();
	    gzosGkCurrent.finish();
	    gzosGkCurrent.close();
	}
}
