package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.server.graph.config.Neo4jConfig;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.graph.utils.ReactomeGraphCore;

public class NCBI {
    private static final Logger logger = LogManager.getLogger();

    public static void main( String[] args ) {
        logger.info("Beginning NCBI step...");

        /*
        String pathToResources = args.length > 0 ? args[0] : "src/main/resources/config.properties";
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(pathToResources));
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        ReactomeGraphCore.initialise(
             "192.168.99.100",
             "7474",
             "test",
             "test",
             Neo4jConfig.class
        );

        GeneralService genericService = ReactomeGraphCore.getService(GeneralService.class);
        //DBInfo dbInfo = genericService.getDBInfo();
        //System.out.println(dbInfo.getVersion());

        logger.info("Finished NCBI step");
    }
}
