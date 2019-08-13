package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

	private static final Logger logger = LogManager.getLogger();
	
	public static void main(String[] args) throws Exception {

		String pathToConfig = "src/main/resources/config.properties";
		String speciesCode = "";
		if (args.length > 0 && args[0].matches("config.properties"))
		{
			pathToConfig = args[0];
			speciesCode = args[1];
		} else if (args.length == 1 && args[0].length() == 4) {
			speciesCode = args[0];
		} else {
			logger.fatal("Please include a 4-letter species code as the first argument (eg: mmus)");
			System.exit(0);
		}

		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
        EventsInferrer.inferEvents(props, speciesCode);

        // Link report file to report_ortho_inference.txt in website_files_update directory
        Path pathtoWebsiteFilesUpdateWithReportFile = Paths.get(props.getProperty("pathToWebsiteFilesUpdateFolder") + "report_ortho_inference.txt");
        Path reportFilename = Paths.get("report_ortho_inference_test_reactome_" + props.getProperty("releaseNumber") + ".txt");
        Files.deleteIfExists(pathtoWebsiteFilesUpdateWithReportFile);
        Files.createLink(pathtoWebsiteFilesUpdateWithReportFile, reportFilename);
	}

}
