package org.reactome.release.downloadDirectory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.reactome.gsea.ReactomeToMsigDBExport;

public class GSEAOutput {

	public static void execute(String username, String password, String host, int port, String database, int releaseNumber) {
		System.out.println("Running GSEAOutput...");
		String outFilename = "ReactomePathways.gmt";
		ReactomeToMsigDBExport.main(new String[] {host, "test_reactome_66_final", username, password, Integer.toString(port), outFilename});
		
		// Initial output file needs to have a column inserted that contains 'Reactome Pathway'
		try {
			FileReader fr = new FileReader(outFilename);
			BufferedReader br = new BufferedReader(fr);
			StringBuffer inputBuffer = new StringBuffer();
			
			String line;
			while ((line = br.readLine()) != null) {
				String[] tabSplit = line.split("\t");
				ArrayList<String> updatedLine = new ArrayList<String>();
				for (int i=0; i < tabSplit.length; i++) {
					if (i == 2) {
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
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		// Zip the output and then remove it
		try {
			FileOutputStream fos = new FileOutputStream(outFilename + ".zip");
			ZipOutputStream zos = new ZipOutputStream(fos);
			ZipEntry ze = new ZipEntry(outFilename);
			zos.putNextEntry(ze);
			FileInputStream inputStream = new FileInputStream(outFilename);
			
			byte[] bytes = new byte[1024];
			int length;
			
			while ((length = inputStream.read(bytes)) > 0) {
				zos.write(bytes, 0, length);
			}
			
			inputStream.close();
			zos.closeEntry();
			zos.close();
			Runtime.getRuntime().exec("rm " + outFilename);
			Runtime.getRuntime().exec("mv " + outFilename + ".zip " + releaseNumber);
			
			//TODO: Insert 'Reactome Pathway' in third column of file
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
