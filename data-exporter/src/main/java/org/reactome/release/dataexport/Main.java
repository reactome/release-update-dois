package org.reactome.release.dataexport;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Generates post-release export files for NCBI, UCSC and Europe PMC.
 * @author jweiser
 */
public class Main {

	/**
	 * Main method to process configuration file and run the executeStep method of the DataExporterStep class
	 * @param args Command line arguments for the post-release data files export (currently the only argument is,
	 *     optionally, the configuration file
	 * @throws IOException Thrown if unable to read configuration file, create output directory or write files
	 * @throws Exception Thrown if execution of executeStep method in DataExporterStep fails
	 */
	public static void main( String[] args ) throws IOException, Exception {
		String pathToResources =
			args.length > 0 ?
			args[0] :
			Objects.requireNonNull(
				Main.class.getClassLoader().getResource("sample_config.properties")
			).getPath();

		Properties props = new Properties();
		props.load(new FileInputStream(pathToResources));

		DataExporterStep dataExporterStep = new DataExporterStep();
		dataExporterStep.executeStep(props);
	}
}