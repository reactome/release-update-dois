package org.reactome.release.updateDOIs;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 11/26/2024
 */
public class Verifier {
    @Parameter(names ={"--releaseNumber", "--r"}, required = true)
    private int releaseNumber;

    @Parameter(names ={"--curatorUser", "--cu"}, required = true)
    private String curatorUserName;

    @Parameter(names ={"--curatorPassword", "--cp"}, required = true)
    private String curatorPassword;

    @Parameter(names ={"--curatorHost", "--ch"})
    private String curatorHost = "localhost";

    @Parameter(names ={"--curatorDbName", "--cd"})
    private String curatorDatabaseName = "gk_central";

    @Parameter(names ={"--curatorPort", "--cP"})
    private int curatorPort = 3306;

    @Parameter(names ={"--releaseUser", "--ru"}, required = true)
    private String releaseUserName;

    @Parameter(names ={"--releasePassword", "--rp"}, required = true)
    private String releasePassword;

    @Parameter(names ={"--releaseHost", "--rh"})
    private String releaseHost = "localhost";

    @Parameter(names ={"--releaseDbName", "--rd"})
    private String releaseDatabaseName = "release_current";

    @Parameter(names ={"--releasePort", "--rP"})
    private int releasePort = 3306;

    @Parameter(names ={"--output", "--o"})
    private String outputFolder = ".";

    public static void main(String[] args) throws Exception {
        Verifier verifier = new Verifier();
        JCommander.newBuilder()
            .addObject(verifier)
            .build()
            .parse(args);

        verifier.run();
    }

    public void run() throws Exception {
        List<String> errorMessages = getErrorMessages();
        if (errorMessages.isEmpty()) {
            System.out.println("Update DOIs has run correctly!");
        } else {
            errorMessages.forEach(System.err::println);
            System.exit(1);
        }
    }

    private List<String> getErrorMessages() throws Exception {
        List<String> errorMessages = new ArrayList<>();

        MySQLAdaptor curatorDBA = getCuratorDBA();
        MySQLAdaptor releaseDBA = getReleaseDBA();

        errorMessages.addAll(checkReleaseDBAForPathwaysWithUnassignedDOIs(releaseDBA));
        errorMessages.addAll(checkCuratorDBAForPathwaysRequiringDOIs(curatorDBA));

        for (MySQLAdaptor dba : Arrays.asList(curatorDBA, releaseDBA)) {
            errorMessages.addAll(checkDBAForPathwaysWithUnexpectedDOIs(dba));
        }

        return errorMessages;
    }

    private List<String> checkReleaseDBAForPathwaysWithUnassignedDOIs(MySQLAdaptor releaseDBA) throws Exception {
        List<String> errorMessages = new ArrayList<>();

        List<GKInstance> releasedPathwaysNeedingDOIs = getPathwaysNeedingDOI(releaseDBA);
        if (!releasedPathwaysNeedingDOIs.isEmpty()) {
            errorMessages.add("The following pathways in the release database still require DOIs:");
            for (GKInstance releasedPathwayNeedingDOI : releasedPathwaysNeedingDOIs) {
                errorMessages.add(releasedPathwayNeedingDOI.toString());
            }
        }

        return errorMessages;
    }

    private List<String> checkCuratorDBAForPathwaysRequiringDOIs(MySQLAdaptor curatorDBA) throws Exception {
        List<String> errorMessages = new ArrayList<>();

        List<GKInstance> curatedPathwaysNeedingDOIs = getPathwaysNeedingDOI(curatorDBA);
        if (!curatedPathwaysNeedingDOIs.isEmpty()) {
            List<GKInstance> curatedPathwaysExpectedToHaveDOIs = matchingExpectedDOIs(curatedPathwaysNeedingDOIs);
            if (!curatedPathwaysExpectedToHaveDOIs.isEmpty()) {
                errorMessages.add("The following pathways in the curated database still require DOIs:");
                for (GKInstance curatedPathwayExpectedToHaveDOI : curatedPathwaysExpectedToHaveDOIs) {
                    errorMessages.add(curatedPathwayExpectedToHaveDOI.toString());
                }
            }
        }

        return errorMessages;
    }

    private List<String> checkDBAForPathwaysWithUnexpectedDOIs(MySQLAdaptor dba) throws Exception {
        List<String> errorMessages = new ArrayList<>();

        for (String expectedDOI : getExpectedDOIs()) {
            GKInstance pathway = getPathwayByDOI(expectedDOI, dba);
            if (!hasExpectedDOI(pathway, expectedDOI)) {
                errorMessages.add(String.format("Pathway %s in %s database has '%s' as its doi (expected %s)",
                    pathway, dba.getDBName(), getCorrectDOI(pathway), expectedDOI));
            }
        }

        return errorMessages;
    }

    private List<GKInstance> getPathwaysNeedingDOI(MySQLAdaptor dba) throws Exception {
        return ((Collection<GKInstance>) dba.fetchInstancesByClass(ReactomeJavaConstants.Pathway))
            .stream()
            .filter(this::needsDOI)
            .collect(Collectors.toList());
    }

    private List<GKInstance> matchingExpectedDOIs(List<GKInstance> pathwaysNeedingDOIs) throws IOException {
        List<GKInstance> pathwaysExpectedToHaveDOIs = new ArrayList<>();

        List<String> expectedDOIs = getExpectedDOIs();
        for (String expectedDOI : expectedDOIs) {
            Optional<GKInstance> pathwayExpectedToHaveDOI =
                pathwaysNeedingDOIs.stream().filter(pathway -> getCorrectDOI(pathway).equals(expectedDOI)).findFirst();
            pathwayExpectedToHaveDOI.ifPresent(pathwaysExpectedToHaveDOIs::add);
        }
        return pathwaysExpectedToHaveDOIs;
    }

    private GKInstance getPathwayByDOI(String doi, MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> pathwaysWithDOI =
            dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway, ReactomeJavaConstants.doi, "=", doi);
        if (pathwaysWithDOI.isEmpty()) {
            throw new RuntimeException("Unable to find pathway with doi " + doi + " in database " + dba.getDBName());
        }

        if (pathwaysWithDOI.size() > 1) {
            throw new RuntimeException("More than one pathway, " + pathwaysWithDOI + ", found with doi " + doi +
                " in database " + dba.getDBName());
        }

        return pathwaysWithDOI.iterator().next();
    }

    private boolean hasExpectedDOI(GKInstance pathway, String expectedDOI) throws Exception {
        String actualDOI = (String) pathway.getAttributeValue(ReactomeJavaConstants.doi);
        return actualDOI.equals(expectedDOI);
    }

    private String getCorrectDOI(GKInstance pathway) {
        final String reactomeDOIPrefix = "10.3180/";

        try {
            GKInstance pathwayStableIdInstance = (GKInstance)
                pathway.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            String pathwayStableId = pathwayStableIdInstance.getDisplayName();

            return reactomeDOIPrefix + pathwayStableId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean needsDOI(GKInstance pathway) {
        String doiValue;
        try {
            doiValue = (String) pathway.getAttributeValue(ReactomeJavaConstants.doi);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get doi value from pathway " + pathway, e);
        }
        return doiValue != null && doiValue.equals("needs DOI");
    }

    private List<String> getExpectedDOIs() throws IOException {
        return Files.lines(getExpectedDOIFile())
            .map(this::getDOIFromFileLine)
            .collect(Collectors.toList());
    }

    private Path getExpectedDOIFile() {
        return Paths.get(getOutputFolder(), String.format("doisToBeUpdated-v%d.txt", this.releaseNumber));
    }

    private String getDOIFromFileLine(String line) {
        return line.split(",")[0];
    }

    private MySQLAdaptor getCuratorDBA() throws SQLException {
        return getDbAdaptor(
            this.curatorHost,
            this.curatorDatabaseName,
            this.curatorUserName,
            this.curatorPassword,
            this.curatorPort
        );
    }

    private MySQLAdaptor getReleaseDBA() throws SQLException {
        return getDbAdaptor(
            this.releaseHost,
            this.releaseDatabaseName,
            this.releaseUserName,
            this.releasePassword,
            this.releasePort
        );
    }

    private MySQLAdaptor getDbAdaptor(String host, String dbName, String userName, String password, int port)
        throws SQLException {

        return new MySQLAdaptor(
            host,
            dbName,
            userName,
            password,
            port
        );
    }

    private String getOutputFolder() {
        return this.outputFolder;
    }
}
