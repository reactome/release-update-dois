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

import org.gk.util.GKApplicationUtilities;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.reactome.server.service.model.GKInstance;
import org.reactome.server.service.model.InstanceDisplayNameGenerator;
import org.reactome.server.service.model.PersistenceAdaptor;
import org.reactome.server.service.model.ReactomeJavaConstants;
import org.reactome.server.service.persistence.Neo4JAdaptor;
import org.reactome.server.service.schema.InvalidAttributeException;
import org.reactome.server.service.schema.InvalidAttributeValueException;
import org.reactome.server.service.schema.SchemaClass;

public class UpdateDOIs {

	private static final Logger logger = LogManager.getLogger();
	private static final String REACTOME_DOI_PREFIX = "10.3180";

	private Neo4JAdaptor releaseDBA;
	private Neo4JAdaptor curatorDBA;

	private int releaseNumber;
	private long personId;
	private boolean testMode;

	public UpdateDOIs(PropertyManager propertyManager) {
		this.releaseDBA = propertyManager.getReleaseDbAdaptor();
		this.curatorDBA = propertyManager.getGKCentralDbAdaptor();

		this.releaseNumber = propertyManager.getReleaseNumber();
		this.personId = propertyManager.getPersonId();
		this.testMode = propertyManager.getTestMode();
	}

	@SuppressWarnings("unchecked")
	public void findAndUpdateDOIs(String pathToReport) throws Exception {
		logger.info("Starting UpdateDOIs");
		List<ExpectedDOI> expectedDOIsToUpdate = getExpectedDOIs(pathToReport);
		List<GKInstance> pathwaysNeedingDOIs = getPathwaysRequiringDOIUpdate(getReleaseDBA());

		if (isTestMode()) {
			logger.info("Test mode is active. Outputting DOIs that can be updated");
			Files.deleteIfExists(getDOIsToBeUpdatedFilePath());
			Files.createFile(getDOIsToBeUpdatedFilePath());
		}

		try (
			Session releaseSession = getReleaseDBA().getConnection().session(
				SessionConfig.forDatabase(getReleaseDBA().getDBName()));
			Session gkCentralSession = getCuratorDBA().getConnection().session(
				SessionConfig.forDatabase(getCuratorDBA().getDBName()));
			Transaction releaseTransaction = releaseSession.beginTransaction();
			Transaction gkCentralTransaction = gkCentralSession.beginTransaction()
		) {

			GKInstance releaseInstanceEdit = getReleaseDBInstanceEdit(releaseTransaction);
			GKInstance curatorInstanceEdit = getGKCentralDBInstanceEdit(gkCentralTransaction);
			for (GKInstance pathwayNeedingDOI : pathwaysNeedingDOIs) {
				if (!isPathwayWithExpectedDOI(pathwayNeedingDOI, expectedDOIsToUpdate)) {
					continue;
				}

				pathwayNeedingDOI.getAttributeValuesList(ReactomeJavaConstants.modified);
				pathwayNeedingDOI.addAttributeValue(ReactomeJavaConstants.modified, releaseInstanceEdit);
				pathwayNeedingDOI.setAttributeValue(ReactomeJavaConstants.doi, getUpdatedDOI(pathwayNeedingDOI));
				getReleaseDBA().updateInstanceAttribute(pathwayNeedingDOI, ReactomeJavaConstants.modified, releaseTransaction);
				getReleaseDBA().updateInstanceAttribute(pathwayNeedingDOI, ReactomeJavaConstants.doi, releaseTransaction);

				GKInstance gkCentralPathwayNeedingDOI = fetchAndVerifyGKCentralPathway(pathwayNeedingDOI);
				if (gkCentralPathwayNeedingDOI != null) {
					gkCentralPathwayNeedingDOI.getAttributeValuesList(ReactomeJavaConstants.modified);
					gkCentralPathwayNeedingDOI.addAttributeValue(ReactomeJavaConstants.modified, curatorInstanceEdit);
					gkCentralPathwayNeedingDOI.setAttributeValue(
						ReactomeJavaConstants.doi, getUpdatedDOI(gkCentralPathwayNeedingDOI)
					);
					getCuratorDBA().updateInstanceAttribute(
						gkCentralPathwayNeedingDOI, ReactomeJavaConstants.modified, gkCentralTransaction
					);
					getCuratorDBA().updateInstanceAttribute(
						gkCentralPathwayNeedingDOI, ReactomeJavaConstants.doi, gkCentralTransaction
					);
				}
				logger.info("Updated DOI: " + getUpdatedDOI(pathwayNeedingDOI) + " for " +
					pathwayNeedingDOI.getDisplayName());

				if (isTestMode()) {
					Files.write(
						getDOIsToBeUpdatedFilePath(),
						getDOIWithDisplayName(pathwayNeedingDOI).concat(System.lineSeparator()).getBytes(),
						StandardOpenOption.APPEND
					);
				}
			}

			if (isTestMode()) {
				releaseTransaction.rollback();
				gkCentralTransaction.rollback();
			} else {
				releaseTransaction.commit();
				gkCentralTransaction.commit();
			}
		} catch (Exception e) {
			logger.error("Problem with session transaction(s)", e);
		}
		logger.info("Finished run of UpdateDOIs");
	}

	private List<ExpectedDOI> getExpectedDOIs(String pathToReport) throws IOException {
		return Files.lines(Paths.get(pathToReport))
			.map(ExpectedDOI::new)
			.collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	private List<GKInstance> getPathwaysRequiringDOIUpdate(Neo4JAdaptor dba) throws Exception {
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

	private boolean isPathwayWithExpectedDOI(GKInstance pathwayNeedingDOI, List<ExpectedDOI> expectedDOIsToUpdate) {
		return expectedDOIsToUpdate.stream().anyMatch(
			expectedDOI -> expectedDOI.getDOI().equals(getUpdatedDOI(pathwayNeedingDOI)) &&
				expectedDOI.getPathwayDisplayName().equals(pathwayNeedingDOI.getDisplayName())
		);
	}

	private GKInstance fetchAndVerifyGKCentralPathway(GKInstance releasePathway) throws Exception {
		GKInstance gkCentralPathway = getCuratorDBA().fetchInstance(releasePathway.getDBID());

		boolean verified = ReportTests.verifyDOIMatches(releasePathway, gkCentralPathway, getUpdatedDOI(releasePathway));
		if (!verified) {
			return null;
		}

		return gkCentralPathway;
	}

	private String getDOIWithDisplayName(GKInstance releasePathway) {
		return getUpdatedDOI(releasePathway) + "," + releasePathway.getDisplayName();
	}

	private GKInstance getReleaseDBInstanceEdit(Transaction tx) throws Exception {
		return getInstanceEdit(getReleaseDBA(), tx);
	}

	private GKInstance getGKCentralDBInstanceEdit(Transaction tx) throws Exception {
		return getInstanceEdit(getCuratorDBA(), tx);
	}

	private GKInstance getInstanceEdit(Neo4JAdaptor dba, Transaction tx) throws Exception {
		GKInstance defaultPerson = dba.fetchInstance(getPersonId());
		if (defaultPerson == null) {
			throw new Exception("Could not fetch Person entity with ID " + getPersonId()
				+ ". Please check that a Person entity exists in the database with this ID.");
		}
		GKInstance newIE = createDefaultInstanceEdit(defaultPerson);
		newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
		newIE.addAttributeValue(ReactomeJavaConstants.note, "org.reactome.release.updateDOIs.Main");
		InstanceDisplayNameGenerator.setDisplayName(newIE);

		dba.storeInstance(newIE, tx);

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
			e.printStackTrace();
			// throw this back up the stack - no way to recover from in here.
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

	private Neo4JAdaptor getReleaseDBA() {
		return this.releaseDBA;
	}

	private Neo4JAdaptor getCuratorDBA() {
		return this.curatorDBA;
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
