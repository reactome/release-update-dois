package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class Main {

	public static void main(String[] args) throws Exception {
		
		String pathToConfig = "src/main/resources/config.properties";
		String pathToSpeciesConfig = "src/main/resources/Species.json";
		
		if (args.length > 0 && !args[0].equals(""))
		{
			pathToConfig = args[0];
		}
		
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
		
		InferEvents.eventInferrer(props, pathToConfig, pathToSpeciesConfig);
	}

}
