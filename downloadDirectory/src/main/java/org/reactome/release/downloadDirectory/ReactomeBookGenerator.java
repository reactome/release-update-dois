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
		String pdfBookCommand = "perl " + releaseDir + "/genbook_pdf.pl -depth 100 -db " + database + " -host " + host + " -user " + username + " -pass " + password + " -port " + port + " -stdout -react_rep 2";
		String rtfBookCommand = "perl " + releaseDir + "/genbook_rtf.pl -depth 100 -db " + database + " -host " + host + " -user " + username + " -pass " + password + " -port " + port + " -split -react_rep 2";
		
		if (runPDF) {
			logger.info("Generating PDF Reactome Book ...");
			Process generatePDFBook = Runtime.getRuntime().exec(pdfBookCommand);
			generatePDFBook.waitFor();
			
			Process zipPDFBook = Runtime.getRuntime().exec("zip TheReactomeBook.pdf.zip TheReactomeBook.pdf");
			zipPDFBook.waitFor();
			
			Files.move(Paths.get("TheReactomeBook.pdf.zip"), Paths.get(releaseNumber + "/TheReactomeBook.pdf.zip"), StandardCopyOption.REPLACE_EXISTING); 
			Files.delete(Paths.get("TheReactomeBook.pdf"));
			logger.info("Finished generating PDF Reactome Book");
		}

		if (runRTF) {
			logger.info("Generating RTF Reactome Book ...");
			Process generateRTFBook = Runtime.getRuntime().exec(rtfBookCommand);
			generateRTFBook.waitFor();
			
			Process zipRTFBook = Runtime.getRuntime().exec("zip TheReactomeBook.rtf.zip TheReactomeBook");
			zipRTFBook.waitFor();
			
			Files.move(Paths.get("TheReactomeBook.rtf.zip"), Paths.get(releaseNumber + "/TheReactomeBook.rtf.zip"), StandardCopyOption.REPLACE_EXISTING); 
			FileUtils.deleteDirectory(new File("TheReactomeBook"));
			logger.info("Finished generating RTF Reactome Book");
		}
	}
}
