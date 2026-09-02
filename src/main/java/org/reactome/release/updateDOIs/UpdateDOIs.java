package org.reactome.release.updateDOIs;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;
import org.reactome.curation.model.SimpleInstance;

public class UpdateDOIs {

	private static final Logger logger = LogManager.getLogger();
	private static final String REACTOME_DOI_PREFIX = "10.3180";

	private MySQLAdaptor releaseDBA;
	private CuratorToolWSAPI curatorToolWSAPI;

	private int releaseNumber;
	private long personId;
	private boolean testMode;

	public UpdateDOIs(PropertyManager propertyManager) {
		this.releaseDBA = propertyManager.getReleaseDbAdaptor();
		this.curatorToolWSAPI = new CuratorToolWSAPI(
			propertyManager.getCuratorHostURL(),
			propertyManager.getCuratorUserName(),
			propertyManager.getCuratorPassword()
		);

		this.releaseNumber = propertyManager.getReleaseNumber();
		this.personId = propertyManager.getPersonId();
		this.testMode = propertyManager.getTestMode();
	}

	@SuppressWarnings("unchecked")
	public void findAndUpdateDOIs(String pathToReport) throws Exception {
		logger.info("Starting UpdateDOIs");
		List<ExpectedDOI> expectedDOIsToUpdate = !isTestMode() ? getExpectedDOIs(pathToReport) : new ArrayList<>();
		List<GKInstance> actualPathwaysNeedingDOIs = getPathwaysRequiringDOIUpdate(getReleaseDBA());

		if (isTestMode()) {
			logger.info("Test mode is active. Outputting DOIs that can be updated");
			Files.deleteIfExists(getDOIsToBeUpdatedFilePath());
			Files.createFile(getDOIsToBeUpdatedFilePath());
		}

		GKInstance releaseInstanceEdit = getReleaseDBInstanceEdit();
		for (GKInstance pathwayNeedingDOI : actualPathwaysNeedingDOIs) {
			if (!isTestMode()) {
				if (!isPathwayWithExpectedDOI(pathwayNeedingDOI, expectedDOIsToUpdate)) {
					continue;
				}

				pathwayNeedingDOI.getAttributeValuesList(ReactomeJavaConstants.modified);
				pathwayNeedingDOI.addAttributeValue(ReactomeJavaConstants.modified, releaseInstanceEdit);
				pathwayNeedingDOI.setAttributeValue(ReactomeJavaConstants.doi, getUpdatedDOI(pathwayNeedingDOI));
				getReleaseDBA().updateInstanceAttribute(pathwayNeedingDOI, ReactomeJavaConstants.modified);
				getReleaseDBA().updateInstanceAttribute(pathwayNeedingDOI, ReactomeJavaConstants.doi);

				SimpleInstance gkCentralPathwayNeedingDOI = fetchAndVerifyGKCentralPathway(pathwayNeedingDOI);
				if (gkCentralPathwayNeedingDOI != null) {
					gkCentralPathwayNeedingDOI.setAttribute(
						ReactomeJavaConstants.doi, getUpdatedDOI(gkCentralPathwayNeedingDOI)
					);

					getCuratorToolWSAPI().commit(gkCentralPathwayNeedingDOI);
				}
				logger.info("Updated DOI: " + getUpdatedDOI(pathwayNeedingDOI) + " for " +
					pathwayNeedingDOI.getDisplayName());
			}

			if (isTestMode()) {
				Files.write(
					getDOIsToBeUpdatedFilePath(),
					getDOIWithDisplayName(pathwayNeedingDOI).concat(System.lineSeparator()).getBytes(),
					StandardOpenOption.APPEND
				);
			}
		}

		logger.info("Finished run of UpdateDOIs");
	}

	private List<ExpectedDOI> getExpectedDOIs(String pathToReport) throws IOException {
		return Files.lines(Paths.get(pathToReport))
			.map(ExpectedDOI::new)
			.collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	private List<GKInstance> getPathwaysRequiringDOIUpdate(MySQLAdaptor dba) throws Exception {
		List<GKInstance> pathwaysNeedingDOI =
			(List<GKInstance>) dba.fetchInstancesByClass(ReactomeJavaConstants.Pathway)
				.stream()
				.filter(pathway -> needsDOI((GKInstance) pathway))
				.collect(Collectors.toList());

		logger.info("Found " + pathwaysNeedingDOI.size() + " pathway instances that need a DOI");

		return pathwaysNeedingDOI;
	}

	private String getUpdatedDOI(GKInstance pathway) {
		String stableIdFromDb;
		try {
			stableIdFromDb = ((GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getDisplayName();
		} catch (Exception e) {
			throw new RuntimeException("Unable to obtain stable id from pathway", e);
		}
		return REACTOME_DOI_PREFIX + "/" + stableIdFromDb;
	}

	private String getUpdatedDOI(SimpleInstance pathway) {
		String stableIdFromDb = ((SimpleInstance) pathway.getAttribute(ReactomeJavaConstants.stableIdentifier)).getDisplayName();

		return REACTOME_DOI_PREFIX + "/" + stableIdFromDb;
	}

	private boolean isPathwayWithExpectedDOI(GKInstance pathwayNeedingDOI, List<ExpectedDOI> expectedDOIsToUpdate) {
		return expectedDOIsToUpdate.stream().anyMatch(
			expectedDOI -> expectedDOI.getDOI().equals(getUpdatedDOI(pathwayNeedingDOI)) &&
				expectedDOI.getPathwayDisplayName().equals(pathwayNeedingDOI.getDisplayName())
		);
	}

	private SimpleInstance fetchAndVerifyGKCentralPathway(GKInstance releasePathway) {
		SimpleInstance gkCentralPathway = getCuratorToolWSAPI().findByDbId(releasePathway.getDBID());

		boolean verified = ReportTests.verifyDOIMatches(releasePathway, gkCentralPathway, getUpdatedDOI(releasePathway));
		if (!verified) {
			return null;
		}

		gkCentralPathway.setDefaultPersonId(getPersonId());

		return gkCentralPathway;
	}

	private String getDOIWithDisplayName(GKInstance releasePathway) {
		return getUpdatedDOI(releasePathway) + "," + releasePathway.getDisplayName();
	}

	private GKInstance getReleaseDBInstanceEdit() throws Exception {
		return getInstanceEdit(getReleaseDBA());
	}

	private GKInstance getInstanceEdit(MySQLAdaptor dba) throws Exception {
		GKInstance defaultPerson = dba.fetchInstance(getPersonId());
		if (defaultPerson == null) {
			throw new Exception("Could not fetch Person entity with ID " + getPersonId()
				+ ". Please check that a Person entity exists in the database with this ID.");
		}
		GKInstance newIE = createDefaultInstanceEdit(defaultPerson);
		newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
		newIE.addAttributeValue(ReactomeJavaConstants.note, "org.reactome.release.updateDOIs.Main");
		InstanceDisplayNameGenerator.setDisplayName(newIE);

		dba.storeInstance(newIE);

		return newIE;
	}

	private GKInstance createDefaultInstanceEdit(GKInstance person) {
		GKInstance instanceEdit = new GKInstance();
		PersistenceAdaptor adaptor = person.getDbAdaptor();
		instanceEdit.setDbAdaptor(adaptor);
		SchemaClass cls = adaptor.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit);
		instanceEdit.setSchemaClass(cls);

		try {
			instanceEdit.addAttributeValue(ReactomeJavaConstants.author, person);
		} catch (InvalidAttributeException | InvalidAttributeValueException e) {
			throw new RuntimeException(e);
		}

		return instanceEdit;
	}

	private boolean needsDOI(GKInstance pathway) {
		try {
			String doi = (String) pathway.getAttributeValue(ReactomeJavaConstants.doi);
			return doi != null && !doi.startsWith(REACTOME_DOI_PREFIX);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Path getDOIsToBeUpdatedFilePath() {
		return Paths.get("doisToBeUpdated-v" + getReleaseNumber() + ".txt");
	}

	private MySQLAdaptor getReleaseDBA() {
		return this.releaseDBA;
	}

	private CuratorToolWSAPI getCuratorToolWSAPI() {
		return this.curatorToolWSAPI;
	}

	private int getReleaseNumber() {
		return this.releaseNumber;
	}

	private long getPersonId() {
		return this.personId;
	}

	private boolean isTestMode() {
		return this.testMode;
	}

	private class ExpectedDOI {
		private String doi;
		private String pathwayDisplayName;

		public ExpectedDOI(String reportLine) {
			String[] reportLineColumns = reportLine.split(",");
			this.doi = reportLineColumns[0];
			this.pathwayDisplayName = reportLineColumns[1];
		}

		public String getDOI() {
			return this.doi;
		}

		public String getPathwayDisplayName() {
			return this.pathwayDisplayName;
		}

		public String getStableIdentifier() {
			return getStableIdentifierWithVersion().substring(
				0, getIndexOfStableIdentifierDotSeparator()
			);
		}

		public int getStableIdentifierVersion() {
			return Integer.parseInt(
				getStableIdentifierWithVersion().substring(getIndexOfStableIdentifierDotSeparator() + 1)
			);
		}

		private int getIndexOfStableIdentifierDotSeparator() {
			return getStableIdentifierWithVersion().lastIndexOf(".");
		}

		private String getStableIdentifierWithVersion() {
			return getDOI().replace(REACTOME_DOI_PREFIX + "/", "");
		}
	}
}
