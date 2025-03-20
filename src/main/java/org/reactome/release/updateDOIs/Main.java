package org.reactome.release.updateDOIs;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.server.service.persistence.Neo4JAdaptor;

public class Main {

	private static final Logger logger = LogManager.getLogger();
	private static final String RESOURCES_DIR = Paths.get("src", "main", "resources").toString();

	public static void main( String[] args ) throws IOException, SQLException {
		// Default locations of properties and pre-set report files
		// Will override if arguments are provided
		String pathToConfig = args.length > 0 && !args[0].isEmpty() ?
			args[0] :
			Paths.get(RESOURCES_DIR,"config.properties").toString();

		Path pathToReport = Paths.get(RESOURCES_DIR,"UpdateDOIs.report");
		boolean testMode = true;
		if (args.length > 1 && !args[1].isEmpty()) {
		  pathToReport = Paths.get(args[1]);
		  testMode = false;
		}

		// Properties file should contain information needed to access current Test Reactome and
		// GKCentral databases
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));

		long personId = Long.parseLong(props.getProperty("personId"));
		int releaseNumber = Integer.parseInt(props.getProperty("releaseNumber"));
		//if (props.getProperty("testMode") != null) {
		//  testMode = Boolean.valueOf(props.getProperty("testMode"));
		//}

		// Set up db connections.
		Neo4JAdaptor dbaTestReactome = getReleaseDbAdaptor(props);
		Neo4JAdaptor dbaGkCentral = getGKCentralDbAdaptor(props);

		UpdateDOIs.setAdaptors(dbaTestReactome, dbaGkCentral);
		logger.info("Starting UpdateDOIs");
		UpdateDOIs.findAndUpdateDOIs(personId, pathToReport, releaseNumber, testMode);
		if (!testMode) {
			logger.info("UpdateDOIs Complete");
		} else {
			logger.info("Finished test run of UpdateDOIs");
		}
	}

	private static Neo4JAdaptor getReleaseDbAdaptor(Properties props) {
		String userTR = props.getProperty("release.database.user");
		String passwordTR = props.getProperty("release.database.password");
		String hostTR = props.getProperty("release.database.host");
		int portTR = Integer.parseInt(props.getProperty("release.database.port"));

		String uri = String.format("bolt://%s:%d", hostTR, portTR);
		return new Neo4JAdaptor(uri, userTR, passwordTR);
	}

	private static Neo4JAdaptor getGKCentralDbAdaptor(Properties props) {
		String userGK = props.getProperty("curator.database.user");
		String passwordGK = props.getProperty("curator.database.password");
		String hostGK = props.getProperty("curator.database.host");
		int portGK = Integer.parseInt(props.getProperty("curator.database.port"));

		String uri = String.format("bolt://%s:%d", hostGK, portGK);
		return new Neo4JAdaptor(uri, userGK, passwordGK);
	}
}
