package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReactomeBookGenerator {
	private static final Logger logger = LogManager.getLogger();
	
	static public void execute(String username, String password, String host, int port, String database, String releaseNumber, String releaseDir, boolean runPDF, boolean runRTF) throws IOException, InterruptedException {
		logger.info("Running Reactome Book steps");
		String args = "-depth 100 -db " + database + " -host " + host + " -user " + username + " -pass " + password + " -port " + port + " -react_rep 2";
		String pdfBookCommand = "perl " + releaseDir + "/genbook_pdf.pl " + args + " -stdout";
		String rtfBookCommand = "perl " + releaseDir + "/genbook_rtf.pl " + args + " -split";
		
		if (runPDF) {
		    generateReactomeBook("pdf", pdfBookCommand, releaseNumber);
		}

		if (runRTF) {
		    generateReactomeBook("rtf", rtfBookCommand, releaseNumber);
		}
	}


private static void generateReactomeBook(String bookType, String bookCommand, String releaseNumber) throws IOException, InterruptedException {

	    logger.info("Generating " + bookType.toUpperCase() + " Reactome Book ...");
	    Process generateBook = Runtime.getRuntime().exec(bookCommand);
	    generateBook.waitFor();

	    String bookFile = "TheReactomeBook." + bookType;
	    String bookZipFile = bookFile + ".zip";
	    Process zipBook = Runtime.getRuntime().exec("zip " + bookZipFile + " " + bookFile);
	    zipBook.waitFor();

	    Files.move(Paths.get(bookZipFile), Paths.get(releaseNumber + "/" + bookZipFile), StandardCopyOption.REPLACE_EXISTING);
	    if (bookType.equals("pdf")) {
	        Files.delete(Paths.get(bookFile));
	    } else if (bookType.equals("rtf")) {
	        FileUtils.deleteDirectory(new File("TheReactomeBook"));
	    }
	    logger.info("Finished generating " + bookType + " Reactome Book");
	}
}
