package org.reactome.release.updateDOIs;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
	private static final Logger logger = LogManager.getLogger();
	private static final String RESOURCES_DIR = Paths.get("src", "main", "resources").toString();

	@Parameter(names = "-config", description = "Path to the configuration file", required = true)
	private String configFilePath;

	@Parameter(names = "-report", description = "Path to report from Reactome curators of expected DOIs to update")
	private String reportFilePath = RESOURCES_DIR + "/UpdateDOIs.report";

	public static void main(String[] args) throws Exception {
		Main main = new Main();
		JCommander.newBuilder()
			.addObject(main)
			.build()
			.parse(args);

		main.run();
	}

	public void run() throws Exception {
		PropertyManager propertyManager = new PropertyManager(getConfigProperties());

		UpdateDOIs updateDOIs = new UpdateDOIs(propertyManager);
		updateDOIs.findAndUpdateDOIs(getReportFilePath());
	}

	private Properties getConfigProperties() throws IOException {
		Properties props = new Properties();
		props.load(new FileInputStream(getPathToConfigFile()));
		return props;
	}

	private String getPathToConfigFile() {
		return this.configFilePath;
	}

	private String getReportFilePath() {
		return this.reportFilePath;
	}
}
