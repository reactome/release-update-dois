package org.reactome.release.updateDOIs;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.curation.model.SimpleInstance;

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

    @Parameter(names ={"--curatorHostURL", "--cHU"})
    private String curatorHostURL = "http://localhost:9090/api/";;

    @Parameter(names ={"--curatorUser", "--cu"}, required = true)
    private String curatorUser;

    @Parameter(names ={"--curatorPassword", "--cp"}, required = true)
    private String curatorPassword;

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

        CuratorToolWSAPI curatorToolWSAPI = new CuratorToolWSAPI(
            this.curatorHostURL, this.curatorUser, this.curatorPassword);
        MySQLAdaptor releaseDBA = getReleaseDBA();

        errorMessages.addAll(checkReleaseDBAForPathwaysWithUnassignedDOIs(releaseDBA));
        errorMessages.addAll(checkCuratorGraphDBForPathwaysRequiringDOIs(curatorToolWSAPI));

        errorMessages.addAll(checkReleaseDBAForPathwaysWithUnexpectedDOIs(releaseDBA));
        errorMessages.addAll(checkCuratedGraphDBForPathwaysWithUnexpectedDOIs(curatorToolWSAPI));

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

    private List<String> checkCuratorGraphDBForPathwaysRequiringDOIs(CuratorToolWSAPI curatorToolWSAPI) throws Exception {
        List<String> errorMessages = new ArrayList<>();

        List<SimpleInstance> curatedPathwaysNeedingDOIs = getPathwaysNeedingDOI(curatorToolWSAPI);
        if (!curatedPathwaysNeedingDOIs.isEmpty()) {
            List<SimpleInstance> curatedPathwaysExpectedToHaveDOIs = matchingExpectedDOIs(curatedPathwaysNeedingDOIs);
            if (!curatedPathwaysExpectedToHaveDOIs.isEmpty()) {
                errorMessages.add("The following pathways in the curated graph database still require DOIs:");
                for (SimpleInstance curatedPathwayExpectedToHaveDOI : curatedPathwaysExpectedToHaveDOIs) {
                    errorMessages.add(curatedPathwayExpectedToHaveDOI.toString());
                }
            }
        }

        return errorMessages;
    }

    private List<String> checkReleaseDBAForPathwaysWithUnexpectedDOIs(MySQLAdaptor dba) throws Exception {
        List<String> errorMessages = new ArrayList<>();

        for (String expectedDOI : getExpectedDOIs()) {
            GKInstance pathway = getPathwayByDOI(expectedDOI, dba);
            if (pathway == null) {
                errorMessages.add(String.format("No pathway with '%s' could be found in %s", expectedDOI, dba));
            } else if (!hasExpectedDOI(pathway, expectedDOI)) {
                errorMessages.add(String.format("Pathway %s in %s database has '%s' as its doi (expected %s)",
                    pathway, dba.getDBName(), getCorrectDOI(pathway), expectedDOI));
            }
        }

        return errorMessages;
    }

    private List<String> checkCuratedGraphDBForPathwaysWithUnexpectedDOIs(CuratorToolWSAPI curatorToolWSAPI) throws IOException {
        List<String> errorMessages = new ArrayList<>();

        for (String expectedDOI : getExpectedDOIs()) {
            SimpleInstance pathway = curatorToolWSAPI.getPathwayWithDOI(expectedDOI);

            if (pathway == null) {
                errorMessages.add(
                    String.format("No pathway with doi '%s' could be found in the curated graph database", expectedDOI)
                );
            } else if (!hasExpectedDOI(pathway, expectedDOI)) {
                errorMessages.add(
                    String.format("Pathway %s in curated graph database has '%s' as its doi (expected %s)",
                    pathway.getDisplayName(), getCorrectDOI(pathway), expectedDOI)
                );
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

    private List<SimpleInstance> getPathwaysNeedingDOI(CuratorToolWSAPI curatorToolWSAPI) {
        return curatorToolWSAPI.getPathwaysWithoutDOIs();
    }

    private List<SimpleInstance> matchingExpectedDOIs(List<SimpleInstance> pathwaysNeedingDOIs) throws IOException {
        List<SimpleInstance> pathwaysExpectedToHaveDOIs = new ArrayList<>();

        List<String> expectedDOIs = getExpectedDOIs();
        for (String expectedDOI : expectedDOIs) {
            Optional<SimpleInstance> pathwayExpectedToHaveDOI =
                pathwaysNeedingDOIs.stream().filter(pathway -> getCorrectDOI(pathway).equals(expectedDOI)).findFirst();
            pathwayExpectedToHaveDOI.ifPresent(pathwaysExpectedToHaveDOIs::add);
        }
        return pathwaysExpectedToHaveDOIs;
    }

    private GKInstance getPathwayByDOI(String doi, MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> pathwaysWithDOI =
            dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway, ReactomeJavaConstants.doi, "=", doi);
        if (pathwaysWithDOI.isEmpty()) {
            return null;
        }

        if (pathwaysWithDOI.size() > 1) {
            throw new IllegalStateException("More than one pathway, " + pathwaysWithDOI + ", found with doi " + doi +
                " in database " + dba.getDBName());
        }

        return pathwaysWithDOI.iterator().next();
    }

    private boolean hasExpectedDOI(GKInstance pathway, String expectedDOI) throws Exception {
        String actualDOI = (String) pathway.getAttributeValue(ReactomeJavaConstants.doi);
        return actualDOI.equals(expectedDOI);
    }

    private boolean hasExpectedDOI(SimpleInstance pathway, String expectedDOI) {
        String actualDOI = (String) pathway.getAttribute(ReactomeJavaConstants.doi);
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

    private String getCorrectDOI(SimpleInstance pathway) {
        final String reactomeDOIPrefix = "10.3180/";

        try {
            SimpleInstance pathwayStableIdInstance = (SimpleInstance)
                pathway.getAttribute(ReactomeJavaConstants.stableIdentifier);
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
        return Paths.get(String.format("doisToBeUpdated-v%d.txt", this.releaseNumber));
    }

    private String getDOIFromFileLine(String line) {
        return line.split(",")[0];
    }

    private MySQLAdaptor getReleaseDBA() {
        try {
            return new MySQLAdaptor(
                this.releaseHost,
                this.releaseDatabaseName,
                this.releaseUserName,
                this.releasePassword,
                this.releasePort
            );
        } catch (SQLException e) {
            throw new RuntimeException(
                "Unable to create MySQLAdaptor for " + this.releaseDatabaseName + "@" + this.releaseHost, e);
        }
    }
}
