package org.reactome.release.downloadDirectory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.gsea.ReactomeToMsigDBExport;

public class GSEAOutput {
	private static final Logger logger = LogManager.getLogger();
	private static final String outFilename = "ReactomePathways.gmt";
	
	public static void execute(MySQLAdaptor dba, String releaseNumber) throws Exception {
		logger.info("Running GSEAOutput step");
		
		// Generate the ReactomePathways.gmt file
		ReactomeToMsigDBExport exporter = new ReactomeToMsigDBExport();
		exporter.setIsForGMT(true);
		exporter.setDBA(dba);
		exporter.export(outFilename);
		
		logger.info("Updating ReactomePathways.gmt with 'Reactome Pathway' column...");
		updateGSEAFile();
		
		logger.info("Zipping ReactomePathways.gmt...");
		zipGSEAFile(releaseNumber);
		
		logger.info("Finished GSEAOutput");
	}

	// Adds the string 'Reactome Pathway' to the third column of the output file
	private static void updateGSEAFile() throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(outFilename));
		StringBuffer inputBuffer = new StringBuffer();
		
		String line;
		while ((line = br.readLine()) != null) 
		{
			String[] tabSplit = line.split("\t");
			List<String> updatedLine = new ArrayList<String>();
			for (int i=0; i < tabSplit.length; i++)
			{
				if (i == 2)
				{
					updatedLine.add("Reactome Pathway");
				}
				updatedLine.add(tabSplit[i]);
			}
			inputBuffer.append(String.join("\t",updatedLine));
			inputBuffer.append("\n");
		}
		String updatedLines = inputBuffer.toString();
		br.close();
		FileOutputStream fileOut = new FileOutputStream(outFilename);
		fileOut.write(updatedLines.getBytes());
		fileOut.close();
	}
	
	// Zips the GSEA file and then removes the original
	private static void zipGSEAFile(String releaseNumber) throws IOException {
		FileOutputStream fos = new FileOutputStream(outFilename + ".zip");
		ZipOutputStream zos = new ZipOutputStream(fos);
		ZipEntry ze = new ZipEntry(outFilename);
		zos.putNextEntry(ze);
		FileInputStream inputStream = new FileInputStream(outFilename);
		
		byte[] bytes = new byte[1024];
		int length;
		
		while ((length = inputStream.read(bytes)) > 0) 
		{
			zos.write(bytes, 0, length);
		}
		
		inputStream.close();
		zos.closeEntry();
		zos.close();
		Files.delete(Paths.get(outFilename));
		String outpathName = releaseNumber + "/" + outFilename + ".zip";
		Files.move(Paths.get(outFilename + ".zip"), Paths.get(outpathName), StandardCopyOption.REPLACE_EXISTING); 
	}
}
