package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.util.Properties;

public class Main {

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
			System.out.println("Please include a 4-letter species code as the first argument (eg: mmus)");
//			System.exit(0);
		}
		
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
		EventsInferrer.eventInferrer(props, pathToConfig, speciesCode);
	}

}
