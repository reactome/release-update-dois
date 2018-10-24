package org.reactome.release.downloadDirectory;

import java.io.IOException;

import org.gk.persistence.MySQLAdaptor;

public class ReactomeBookGenerator {

	static public void execute(String username, String password, String host, int port, String database, int releaseNumber) throws IOException, InterruptedException {
		
		String releaseDir = "/release/scripts/release/download_directory";
		String pdfBookCommand = "perl " + releaseDir + "/genbook_pdf.pl -depth 100 -db " + database + " -host " + host + " -user " + username + " -pass " + password + " -port " + port + " -stdout -react_rep 2 > TheReactomeBook.pdf";
		String rtfBookCommand = "perl " + releaseDir + "/genbook_rtf.pl -depth 100 -db " + database + " -host " + host + " -user " + username + " -pass " + password + " -port " + port + " -split -react_rep 2";
		
		Process generatePDFBook = Runtime.getRuntime().exec(pdfBookCommand);
		generatePDFBook.waitFor();
		
		Process generateRTFBook = Runtime.getRuntime().exec(rtfBookCommand);
		generateRTFBook.waitFor();
		
		
	}
}
