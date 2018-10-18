package org.reactome.release.downloadDirectory;

import java.io.IOException;

import org.gk.biosystems.ReactomeToBioSystemsConverter;

public class CreateReactome2BioSystems {

	public static void execute(String host, String database, String username, String password, int port, int releaseNumber) throws IOException {
		
		System.out.println("Running CreateReactome2BioSystems...");
		// The last argument, 'BioSystems', specifies the output directory of the ReactomeToBioSystems.zip file. 
		// The script removes all files within the directory, so only change the output directory to an empty or non-existent one
		ReactomeToBioSystemsConverter.main(new String[] {host, database, username, password, Integer.toString(port), "BioSystems" });
		Runtime.getRuntime().exec("mv BioSystems/ReactomeToBioSystems.zip " + releaseNumber);
		Runtime.getRuntime().exec("rm -r BioSystems");
	}
}
