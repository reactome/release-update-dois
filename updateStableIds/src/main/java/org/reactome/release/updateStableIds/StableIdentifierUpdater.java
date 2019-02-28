package org.reactome.release.updateStableIds;

import static org.gk.model.ReactomeJavaConstants.*;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.release.common.database.InstanceEditUtils;

public class StableIdentifierUpdater {

	private static final Logger logger = LogManager.getLogger();

	@SuppressWarnings("unchecked")
	public static void updateStableIdentifiers(MySQLAdaptor dbaSlice, MySQLAdaptor dbaPrevSlice, MySQLAdaptor dbaGkCentral, Long personId) throws Exception {
		
		logger.info("Generating InstanceEdits for " + dbaSlice.getDBName() + " and " + dbaGkCentral.getDBName());
		// Instance Edits for test_slice and gk_central
		String creatorName = "org.reactome.release.updateStableIds";
		GKInstance sliceIE = InstanceEditUtils.createInstanceEdit(dbaSlice, personId, creatorName);
		GKInstance gkCentralIE = InstanceEditUtils.createInstanceEdit(dbaGkCentral, personId, creatorName);

		// At time of writing (December 2018), test_slice is a non-transactional database. This check has been put in place as a safety net in case that changes.
		if (dbaSlice.supportsTransactions()) {
			dbaSlice.startTransaction();
		}
		dbaGkCentral.startTransaction();

		//TODO: Perl wrapper will create a 'snapshot' of the previous slice -- once the wrapper is retired this needs to be done

		logger.info("Fetching all Event and PhysicalEntity instances");
		// Get all Event and PhysicalEntity instances and combine them into one large List
		Collection<GKInstance> eventInstances = dbaSlice.fetchInstancesByClass(Event);
		Collection<GKInstance> physicalEntityInstances = dbaSlice.fetchInstancesByClass(PhysicalEntity);
		List<GKInstance> sliceInstances = new ArrayList<GKInstance>();
		sliceInstances.addAll(eventInstances);
		sliceInstances.addAll(physicalEntityInstances);

		int incrementedCount = 0;
		int notIncrementedCount = 0;
		logger.info("Total instances to check: " + sliceInstances.size());

		for (GKInstance sliceInstance : sliceInstances) {
			logger.info("Checking " + sliceInstance);
			GKInstance gkCentralInstance = dbaGkCentral.fetchInstance(sliceInstance.getDBID());
			GKInstance prevSliceInstance = dbaPrevSlice.fetchInstance(sliceInstance.getDBID());
			// Check if instance is new and that it exists on gkCentral (they could be deleted)
			if (prevSliceInstance != null && gkCentralInstance != null) {
				
				// Compare number of 'Modified' instances between slices
				Collection<GKInstance> sliceInstanceModified = sliceInstance.getAttributeValuesList(modified);
				Collection<GKInstance> prevSliceInstanceModified = prevSliceInstance.getAttributeValuesList(modified);
				if (sliceInstanceModified.size() > prevSliceInstanceModified.size()) {
					// Make sure StableIdentifier instance exists
					if (sliceInstance.getAttributeValue(stableIdentifier) != null && gkCentralInstance.getAttributeValue(stableIdentifier) != null) {
						logger.info("\tIncrementing " + sliceInstance.getAttributeValue(stableIdentifier));
						incrementStableIdentifier(sliceInstance, dbaSlice, sliceIE);
						incrementStableIdentifier(gkCentralInstance, dbaGkCentral, gkCentralIE);
						incrementedCount++;
					} else if (sliceInstance.getAttributeValue(stableIdentifier) == null){
						logger.warn(sliceInstance + ": could not locate StableIdentifier instance");
					} else {
						logger.warn(prevSliceInstance + ": Instance from previous slice did not have StableIdentifier instance");
					}
				} else {
					if (sliceInstanceModified.size() < prevSliceInstanceModified.size()) {
						logger.fatal(sliceInstance + " in current release has less modification instances than previous release");
						throw new IllegalStateException("Found instance with less modification instances than in previous release -- terminating");
					}
					notIncrementedCount++;
				}
				// Instances that have been updated already during the current release will have their 'releaseStatus' attribute equal to 'UPDATED'.
				// This will make sure that StableIDs are only updated once per release.
				try {
					if (isUpdated(sliceInstance, prevSliceInstance, dbaPrevSlice)) {
						logger.info("Checking if " + sliceInstance + " needs to be updated");
						String releaseStatusString = (String) sliceInstance.getAttributeValue(releaseStatus);
						String updated = "UPDATED";

						if (releaseStatusString == null || !releaseStatusString.equals(updated)) {
							logger.info("Updating release status for " + sliceInstance);
							sliceInstance.addAttributeValue(releaseStatus, updated);
							sliceInstance.addAttributeValue(modified, sliceIE);
							dbaSlice.updateInstanceAttribute(sliceInstance, releaseStatus);
							dbaSlice.updateInstanceAttribute(sliceInstance, modified);
						} else {
							logger.info("StableIdentifer has already been updated during this release");
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (gkCentralInstance == null) {
				logger.warn(sliceInstance + " -- Instance not found in gkCentral");
			}
		}

		// TODO: Update test_slice after gkCentral has been successfully updated
		if (dbaSlice.supportsTransactions()) {
			dbaSlice.commit();
		}
		logger.info("Commiting all changes in " + dbaGkCentral.getDBName());
		dbaGkCentral.commit();
		logger.info(incrementedCount + " Stable Identifiers were updated");
		logger.info(notIncrementedCount + " were not updated");
		logger.info("UpdateStableIdentifiers step has finished");
	}

	// Increments the identifierVersion attribute and updates the StableIdentifier displayName accordingly.
	// TODO: Integration Testing of increment function
	private static void incrementStableIdentifier(GKInstance instance, MySQLAdaptor dba, GKInstance instanceEdit) throws InvalidAttributeException, Exception {
		GKInstance stableIdentifierInst = (GKInstance) instance.getAttributeValue(stableIdentifier);
		String id = (String) stableIdentifierInst.getAttributeValue(identifier);
		int idVersion = Integer.valueOf((String) stableIdentifierInst.getAttributeValue(identifierVersion));
		int newIdentifierVersion = idVersion + 1;

		stableIdentifierInst.addAttributeValue(identifierVersion, String.valueOf(newIdentifierVersion));
		stableIdentifierInst.setDisplayName(id + "." + newIdentifierVersion);
		stableIdentifierInst.addAttributeValue(modified, instanceEdit);
		dba.updateInstanceAttribute(stableIdentifierInst, identifierVersion);
		dba.updateInstanceAttribute(stableIdentifierInst, _displayName);
		dba.updateInstanceAttribute(stableIdentifierInst, modified);
	}
	
	// Checks via the 'releaseStatus', 'revised', and 'reviewed' attributes if this instance has been updated since last release.
	// Also goes through any child 'hasEvent' instances and recursively checks as well.
	private static boolean isUpdated(GKInstance sliceInstance, GKInstance prevSliceInstance, MySQLAdaptor dbaPrevSlice) throws InvalidAttributeException, Exception {
		
		if (sliceInstance.getSchemClass().isa(Event)) {
			if (sliceInstance.getAttributeValue(releaseStatus) != null) {
				return true;
			}

			Collection<GKInstance> revisedInstances = sliceInstance.getAttributeValuesList(revised);
			Collection<GKInstance> prevRevisedInstances = prevSliceInstance.getAttributeValuesList(revised);
			
			if (revisedInstances.size() > prevRevisedInstances.size()) {
				return true;
			}
			
			Collection<GKInstance> reviewedInstances = sliceInstance.getAttributeValuesList(reviewed);
			Collection<GKInstance> prevReviewedInstances = prevSliceInstance.getAttributeValuesList(reviewed);
			if (reviewedInstances.size() > prevReviewedInstances.size()) {
				return true;
			}
			
			if (sliceInstance.getSchemClass().isValidAttribute(hasEvent)) {
				Collection<GKInstance> eventInstances = sliceInstance.getAttributeValuesList(hasEvent);
				for (GKInstance eventInst : eventInstances) {
					GKInstance prevEventInst = dbaPrevSlice.fetchInstance(eventInst.getDBID());

					try {
						if (prevEventInst != null && isUpdated(eventInst, prevEventInst, dbaPrevSlice)) {
							return true;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}		
		}
		return false;
	}
}
