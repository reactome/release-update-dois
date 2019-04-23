package org.reactome.orthodiagrams;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;
import org.gk.pathwaylayout.PredictedPathwayDiagramGeneratorFromDB;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidClassException;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

public class Main {

    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) throws SQLException, InvalidClassException, IOException {


        logger.info("Beginning orthodiagrams step");

        String pathToConfig = "";
        if (args.length > 0) {
            pathToConfig = args[0];
        } else {
            pathToConfig = "src/main/resources/config.properties";
        }
        Properties props = new Properties();
        props.load(new FileInputStream(pathToConfig));

        String username = props.getProperty("username");
        String password = props.getProperty("password");
        String currentDatabase = props.getProperty("currentDatabase");
        String previousDatabase = props.getProperty("previousDatabase");
        String host = props.getProperty("host");
        int port = Integer.valueOf(props.getProperty("port"));
        String personId = props.getProperty("personId");

        MySQLAdaptor dbaCurrent = new MySQLAdaptor(host, currentDatabase, username, password, port);
        MySQLAdaptor dbaPrevious = new MySQLAdaptor(host, previousDatabase, username, password, port);

        // Call PredictedPathwayDiagramGeneratorFromDB from CuratorTool project. This generates PathwayDiagram instances for
        // each pathway that was inferred during orthoinference.
        PredictedPathwayDiagramGeneratorFromDB webELVTool = new PredictedPathwayDiagramGeneratorFromDB();
        webELVTool.main(new String[]{host, currentDatabase, username, password, String.valueOf(port), personId});

        // Simple check for PathwayDiagram count discrepancies between releases. If it has gone down, this should be looked into.
        long currentReleasePathwayDiagramCount = dbaCurrent.getClassInstanceCount(ReactomeJavaConstants.PathwayDiagram);
        long prevReleasePathwayDiagramCount = dbaPrevious.getClassInstanceCount(ReactomeJavaConstants.PathwayDiagram);

        if (currentReleasePathwayDiagramCount < prevReleasePathwayDiagramCount) {
            logger.warn("PathwayDiagram count has gone down between releases. Current: " + currentReleasePathwayDiagramCount + ", Previous: " + prevReleasePathwayDiagramCount);
        }

        logger.info("Orthodiagrams has finished");

    }
}
