package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
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
		} else if (args.length == 1 && args[0].length() == 4){
			speciesCode = args[0];
		} else {
			logger.warn("Please include a 4-letter species code as the first argument (eg: mmus)");
//			System.exit(0);
		}
		
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
//        List<String> speciesCodes = Arrays.asList("pfal", "spom", "scer", "ddis", "cele", "sscr", "btau", "cfam", "mmus", "rnor", "ggal", "tgut", "xtro", "drer", "dmel", "atha", "osat");
		List<String> speciesCodes = Arrays.asList("spom");
		for (String speciesCode1 : speciesCodes) {
                EventsInferrer.inferEvents(props, pathToConfig, speciesCode1);
        }
	}

}
