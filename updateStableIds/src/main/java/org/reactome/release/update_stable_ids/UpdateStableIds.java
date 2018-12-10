package org.reactome.release.update_stable_ids;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.TransactionsNotSupportedException;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.reactome.release.common.database.InstanceEditUtils;

public class UpdateStableIds {

	public static void stableIdUpdater(MySQLAdaptor dbaSlice, MySQLAdaptor dbaPrevSlice, MySQLAdaptor dbaGkCentral, Long personId) throws Exception {
		
		// Instance Edits for test_slice and gk_central
		String creatorName = "org.reactome.release.update_stable_ids";
		GKInstance sliceIE = InstanceEditUtils.createInstanceEdit(dbaSlice, personId, creatorName);
		GKInstance gkCentralIE = InstanceEditUtils.createInstanceEdit(dbaGkCentral, personId, creatorName);

		// At time of writing (December 2018), test_slice is a non-transactional database. This check has been put in place as a safety net in case that changes.
		if (dbaSlice.supportsTransactions()) {
			dbaSlice.startTransaction();
		}
		dbaGkCentral.startTransaction();
		
		//TODO:Should legacy regulation instances be updated?
		//TODO:Ensure that re-run handling is implemented (backing up DB)
		
		// These two Lists were originally used to determine what classes' instances should be updated.
		// Since all 'accepted' classes turned out to encompass all PhysicalEntity and Event classes, and all
		// 'excluded' classes were Regulation classes, the filter was not needed anymore.
//		final List<String> acceptedClassesWithStableIdentifiers = Arrays.asList("Pathway","SimpleEntity","OtherEntity","DefinedSet","Complex","EntityWithAccessionedSequence","GenomeEncodedEntity","Reaction","BlackBoxEvent","CandidateSet","Polymer","Depolymerisation","Polymerisation","Drug","FailedReaction","EntitySet");
//		List<String> excludedClassesWithStableIdentifiers = Arrays.asList("PositiveGeneExpressionRegulation","PositiveRegulation","NegativeRegulation","Requirement","NegativeGeneExpressionRegulation");
//		ResultSet classesWithStableIdentifiers = dbaGkCentral.executeQuery("SELECT DISTINCT _class FROM DatabaseObject WHERE StableIdentifier IS NOT NULL", null);		

		// Get all Event and PhysicalEntity instances and combine them into one large List
		Collection<GKInstance> eventInstances = dbaSlice.fetchInstancesByClass(Event);
		Collection<GKInstance> physicalEntityInstances = dbaSlice.fetchInstancesByClass(PhysicalEntity);
		List<GKInstance> sliceInstances = new ArrayList<GKInstance>();
		sliceInstances.addAll(eventInstances);
		sliceInstances.addAll(physicalEntityInstances);
//		
		for (GKInstance sliceInstance : sliceInstances) {
			
			GKInstance gkCentralInstance = (GKInstance) dbaGkCentral.fetchInstance(sliceInstance.getDBID());
			GKInstance prevSliceInstance = (GKInstance) dbaPrevSlice.fetchInstance(sliceInstance.getDBID());
			// Check if instance is new and that it exists on gkCentral (they could be deleted)
			if (prevSliceInstance != null && gkCentralInstance != null) {
				
				// Compare number of 'Modified' instances between slices
				Collection<GKInstance> sliceInstanceModified = sliceInstance.getAttributeValuesList(modified);
				Collection<GKInstance> prevSliceInstanceModified = prevSliceInstance.getAttributeValuesList(modified);
				if (sliceInstanceModified.size() > prevSliceInstanceModified.size()) {
					// Make sure StableIdentifier instance exists
					if (sliceInstance.getAttributeValue(stableIdentifier) != null && gkCentralInstance.getAttributeValue(stableIdentifier) != null) {
						incrementStableIdentifier(sliceInstance, dbaSlice, sliceIE);
						incrementStableIdentifier(gkCentralInstance, dbaGkCentral, gkCentralIE);
						System.out.println("Stable Id updated: " + sliceInstance.getAttributeValue(stableIdentifier));
					}
				} else {
					if (sliceInstanceModified.size() == prevSliceInstanceModified.size()) {
//						System.out.println("No change between releases");
					} else {
						System.out.println("Unexpected: Number of 'Modified' instances in [" + sliceInstance.getDBID() + "] is fewer than in previous release");
					}
				}
				
				// Instances that have been updated already during the current release will have their 'releaseStatus' attribute equal to 'UPDATED'.
				// This will make sure that StableIDs are only updated once per release.
				if (isUpdated(sliceInstance, prevSliceInstance, dbaPrevSlice)) {
					String releaseStatusString = (String) sliceInstance.getAttributeValue(releaseStatus);
					String updated = "UPDATED";
					if (releaseStatus != updated) {
						sliceInstance.addAttributeValue(releaseStatus, updated);
						sliceInstance.addAttributeValue(modified, sliceIE);
						dbaSlice.updateInstanceAttribute(sliceInstance, releaseStatus);
						dbaSlice.updateInstanceAttribute(sliceInstance, modified);
					} else {
						System.out.println("StableIdentifer has already been updated during this release");
					}
				}
			}
		}

		if (dbaSlice.supportsTransactions()) {
			dbaSlice.commit();
		}
		dbaGkCentral.commit();		
	}

	// Increments the identifierVersion attribute and updates the StableIdentifier displayName accordingly.
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
					if (prevEventInst != null && isUpdated(eventInst, prevEventInst, dbaPrevSlice)) {
						return true;
					}
				}
			}		
		}
		return false;
	}
}
