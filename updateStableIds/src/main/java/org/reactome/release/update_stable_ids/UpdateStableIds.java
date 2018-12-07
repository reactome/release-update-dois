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
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.TransactionsNotSupportedException;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.reactome.release.common.database.InstanceEditUtils;

public class UpdateStableIds {

	public static void stableIdUpdater(MySQLAdaptor dbaSlice, MySQLAdaptor dbaPrevSlice, MySQLAdaptor dbaGkCentral, Long personId) throws Exception {
		
		GKInstance sliceIE = InstanceEditUtils.createInstanceEdit(dbaSlice, personId, "org.reactome.release.update_stable_ids");
		GKInstance gkCentralIE = InstanceEditUtils.createInstanceEdit(dbaGkCentral, personId, "org.reactome.release.update_stable_ids");

		if (dbaSlice.supportsTransactions()) {
			dbaSlice.startTransaction();
		}
		dbaGkCentral.startTransaction();
		
		//Should legacy regulation instances be updated?
		final List<String> acceptedClassesWithStableIdentifiers = Arrays.asList("Pathway","SimpleEntity","OtherEntity","DefinedSet","Complex","EntityWithAccessionedSequence","GenomeEncodedEntity","Reaction","BlackBoxEvent","CandidateSet","Polymer","Depolymerisation","Polymerisation","Drug","FailedReaction","EntitySet");
		List<String> excludedClassesWithStableIdentifiers = Arrays.asList("PositiveGeneExpressionRegulation","PositiveRegulation","NegativeRegulation","Requirement","NegativeGeneExpressionRegulation");
//		ResultSet classesWithStableIdentifiers = dbaGkCentral.executeQuery("SELECT DISTINCT _class FROM DatabaseObject WHERE StableIdentifier IS NOT NULL", null);		
//		Object classString = new String("Pathway");
//		String test = classString;
		GKSchemaClass gkClassString = new GKSchemaClass("Pathway");
		System.out.println(gkClassString.getAttributes());
//		Collection<GKInstance> instancesWithStableIdentifiers = 
//				(Collection<GKInstance>) dbaSlice.fetchInstanceByAttribute(DatabaseObject, stableIdentifier, "IS NOT NULL", null)
//		.stream()
//		.filter(i -> {
//			for (String stIdClass : acceptedClassesWithStableIdentifiers) {
//				if (((GKInstance) i).getSchemClass().(stIdClass)) {
//					return true;
//				};
//			}
//			return false;
//		})
//		.collect(Collectors.toList());
//		Collection<GKInstance> stIds = dbaSlice.fetchInstancesByClass(StableIdentifier);
//		System.out.println(instancesWithStableIdentifiers.size());
//		while (classesWithStableIdentifiers.next()) {
//			String className = classesWithStableIdentifiers.getString(1);
//			int count = 0;
//			if (acceptedClassesWithStableIdentifiers.contains(className)) {
//				System.out.print("Updating '" + className + "' instance Stable Identifiers\t");
//				Collection<GKInstance> sliceInstancesFromClass = dbaSlice.fetchInstanceByAttribute(DatabaseObject, "_class", "=", Pathway);
//				
////				System.out.println(dbaSlice.getClassInstanceCount(className));
////				System.out.println(count += sliceInstancesFromClass.size());
//				for (GKInstance sliceInstance : sliceInstancesFromClass) {
////					if (sliceInstance.getDBID().toString().matches("442745|9609507")) {
////						System.out.println(sliceInstance);
////					}
//					// Should be incremented?
//					
//					GKInstance gkCentralInstance = (GKInstance) dbaGkCentral.fetchInstance(sliceInstance.getDBID());
//					GKInstance prevSliceInstance = (GKInstance) dbaPrevSlice.fetchInstance(sliceInstance.getDBID());
//					// Check if instance is new
//					if (prevSliceInstance != null) {
//						
//						// Compare number of 'Modified' instances between slices
//						Collection<GKInstance> sliceInstanceModified = sliceInstance.getAttributeValuesList(modified);
//						Collection<GKInstance> prevSliceInstanceModified = prevSliceInstance.getAttributeValuesList(modified);
//						if (sliceInstanceModified.size() > prevSliceInstanceModified.size()) {
//							if (sliceInstance.getAttributeValue(stableIdentifier) != null && gkCentralInstance.getAttributeValue(stableIdentifier) != null) {
//								incrementStableIdentifier(sliceInstance, dbaSlice, sliceIE);
//								incrementStableIdentifier(gkCentralInstance, dbaGkCentral, gkCentralIE);
//							}
//						} else {
//							if (sliceInstanceModified.size() == prevSliceInstanceModified.size()) {
////								System.out.println("No change between releases");
//							} else {
//								System.out.println("Weird");
//							}
//						}
//						
//						if (isUpdated(sliceInstance, prevSliceInstance, dbaPrevSlice)) {
//							String releaseStatus = (String) sliceInstance.getAttributeValue(releaseStatus);
//							String updated = "UPDATED";
//							
//							if (releaseStatus != updated) {
//								sliceInstance.addAttributeValue(releaseStatus, updated);
//								sliceInstance.addAttributeValue(modified, sliceIE);
//								dbaSlice.updateInstanceAttribute(sliceInstance, releaseStatus);
//								dbaSlice.updateInstanceAttribute(sliceInstance, modified);
//								count++;
//							} else {
//								System.out.println("AHAH!!!!");
//								System.exit(0);
//							}
//						}
//					}
//				}
//			} else if (excludedClassesWithStableIdentifiers.contains(className)) {
//				System.out.println("'" + className + "' class is excluded from StableIdentifier update");
//			} else {
//				System.out.println("Unknown class found that contains StableIdentifier attribute: '" + className + "'");
//			}
//			System.out.println("\t" + count + " stableIdentifiers updated in class " + className);
//		}
		System.exit(0);
//		System.out.println(count);
		if (dbaSlice.supportsTransactions()) {
			dbaSlice.commit();
		}
		dbaGkCentral.commit();		
	}

	private static void incrementStableIdentifier(GKInstance instance, MySQLAdaptor dba, GKInstance instanceEdit) throws InvalidAttributeException, Exception {
		GKInstance stableIdentifierInst = (GKInstance) instance.getAttributeValue(stableIdentifier);
		String id = (String) stableIdentifierInst.getAttributeValue(identifier);
		int idVersion = Integer.valueOf((String) stableIdentifierInst.getAttributeValue(identifierVersion));
		int newIdentifierVersion = idVersion + 1;

		stableIdentifierInst.addAttributeValue(identifierVersion, String.valueOf(newIdentifierVersion));
		stableIdentifierInst.setDisplayName(id + "." + newIdentifierVersion);
		stableIdentifierInst.addAttributeValue(modified, instanceEdit);
//		dba.updateInstanceAttribute(stableIdentifierInst, identifierVersion);
//		dba.updateInstanceAttribute(stableIdentifierInst, _displayName);
//		dba.updateInstanceAttribute(stableIdentifierInst, modified);
	}
	
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
