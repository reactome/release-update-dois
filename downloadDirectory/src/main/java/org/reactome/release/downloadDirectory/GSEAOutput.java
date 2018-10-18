package org.reactome.release.downloadDirectory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.reactome.gsea.ReactomeToMsigDBExport;

public class GSEAOutput {

	public static void execute(String username, String password, String host, int port, String database, int releaseNumber) {
		System.out.println("Running GSEAOutput...");
		String outFilename = "ReactomePathways.gmt";
		ReactomeToMsigDBExport.main(new String[] {host, "test_reactome_66_final", username, password, Integer.toString(port), outFilename});
		
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
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
