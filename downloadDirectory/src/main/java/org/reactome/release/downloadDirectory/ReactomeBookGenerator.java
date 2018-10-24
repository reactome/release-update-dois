package org.reactome.release.downloadDirectory;

import java.io.IOException;

import org.gk.persistence.MySQLAdaptor;

public class ReactomeBookGenerator {

	static public void execute(String username, String password, String host, int port, String database, int releaseNumber) throws IOException, InterruptedException {
		System.out.println("Generating Reactome Books...");
		String releaseDir = "/release/scripts/release/download_directory";
		String pdfBookCommand = "perl " + releaseDir + "/genbook_pdf.pl -depth 100 -db " + database + " -host " + host + " -user " + username + " -pass " + password + " -port " + port + " -stdout -react_rep 2 > TheReactomeBook.pdf";
		String rtfBookCommand = "perl " + releaseDir + "/genbook_rtf.pl -depth 100 -db " + database + " -host " + host + " -user " + username + " -pass " + password + " -port " + port + " -split -react_rep 2";
		
		System.out.println("\tGenerating PDF Reactome Book ...");
		Process generatePDFBook = Runtime.getRuntime().exec(pdfBookCommand);
		generatePDFBook.waitFor();
		
		Process zipPDFBook = Runtime.getRuntime().exec("zip TheReactomeBook.pdf.zip TheReactomeBook.pdf");
		zipPDFBook.waitFor();
		Runtime.getRuntime().exec("mv TheReactomeBook.pdf.zip " + releaseNumber);
		Runtime.getRuntime().exec("rm TheReactomeBook.pdf");
		//TODO: rm
		System.out.println("\tGenerating RTF Reactome Book ...");
		Process generateRTFBook = Runtime.getRuntime().exec(rtfBookCommand);
		generateRTFBook.waitFor();
		
		Process zipRTFBook = Runtime.getRuntime().exec("zip TheReactomeBook.rtf.zip TheReactomeBook");
		zipRTFBook.waitFor();
		Runtime.getRuntime().exec("mv TheReactomeBook.rtf.zip " + releaseNumber);
		Runtime.getRuntime().exec("rm -rf TheReactomeBook");
		
	}
}
