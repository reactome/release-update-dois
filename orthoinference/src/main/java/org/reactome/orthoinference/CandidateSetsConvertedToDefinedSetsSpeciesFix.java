package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.reactome.release.common.database.InstanceEditUtils;

/*
 * This data fix was written to correct a mistake in the first version of the Java Orthoinference code.
 * 
 * During orthoinference, when CandidateSet instances contain multiple instances in the 'hasMember' attribute slot the inferred instance is changed
 * from a CandidateSet to DefinedSet instance. At the location of the code in question (OrthologousEntity.createInfEntitySet in Java Orthoinference),
 * the new instance is produced by creating a new GKInstance from a DefinedSet SchemaClass, rather than using the InstanceUtilities.newInferredGKInstance
 * method that would normally be used (using that method would create a new CandidateSet instance, based on the source instance).
 * 
 * The mistake was that the 'species' and 'created' slots that are populated in 'newInferredGKInstance' were not populated when the new DefinedSet was created.
 * This code fixes that mistake (Justin Cook November 2018).
 * 
 */

public class CandidateSetsConvertedToDefinedSetsSpeciesFix {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		Properties props = new Properties();
		props.load(new FileInputStream("src/main/resources/config.properties"));
		// Set up DB adaptor using config.properties file
		String username = props.getProperty("username");
		String password = props.getProperty("password");
//		String database = props.getProperty("database");
		String preFixDatabase = "4unfixed_test_reactome_67";
//		String postFixDatabase = "2OI_test_reactome_67";
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));

		MySQLAdaptor dbaPre = new MySQLAdaptor(host, preFixDatabase, username, password, port);
		MySQLAdaptor dbaPost = new MySQLAdaptor("reactomerelease.oicr.on.ca", "test_reactome_67", "jcook", "C4ct0s33", port);
//		GKInstance instanceEdit = InstanceEditUtils.createDefaultIE(dbAdaptor, 8948690L, true, "Due to a bug with CandidateSets that are converted to DefinedSets during Orthoinference, the 'species' and 'created' slots needed to be filled");
//		Collection<GKInstance> physicalEntityInstances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("PhysicalEntity", ReactomeJavaConstants.stableIdentifier, "IS NULL", null);
		
//		for (GKInstance peInst : physicalEntityInstances) {
//			int memberCount = peInst.getAttributeValuesList(ReactomeJavaConstants.hasMember).size();
			
//			Collection<GKInstance> memberInstances = peInst.getAttributeValuesList(ReactomeJavaConstants.hasMember);
//			for (GKInstance memberInst : memberInstances) {
//			Check that there is only one species instance per member instance
//			if (memberInst.getAttributeValuesList(ReactomeJavaConstants.species).size() < 1) {
//				System.out.println("bad");
//			}
//			GKInstance speciesInst = (GKInstance) memberInst.getAttributeValue(ReactomeJavaConstants.species);
//			speciesIds.add(speciesInst.getDBID());
//		}
//		
//			Check that between members there is only one species instance
//			if (speciesIds.size() > 1) {
//			System.out.println("bad");
//		}
			
//			if (memberCount > 0) {
//				GKInstance memberInst = (GKInstance) peInst.getAttributeValue(ReactomeJavaConstants.hasMember);
//				GKInstance speciesInst = (GKInstance) memberInst.getAttributeValue(ReactomeJavaConstants.species);
//				GKInstance createdInst = (GKInstance) memberInst.getAttributeValue(ReactomeJavaConstants.created);
//				peInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
//				peInst.addAttributeValue(ReactomeJavaConstants.created, createdInst);
//				peInst.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
//				dbAdaptor.updateInstance(peInst);
//			} else {
//				System.out.println(peInst + "\tNo members found -- investigate!");
//			}
//		}
		
		
		
		
//		GKInstance instanceEdit = InstanceEditUtils.createDefaultIE(dbaPost, 8948690L, true, "CandidateSets that are changed to DefinedSets during orthoinference experienced a bug that caused some attributes (compartment, inferredFrom, and name) to be removed during a prior 'fix'.");
//		Collection<GKInstance> physicalEntityInstances = (Collection<GKInstance>) dbaPost.fetchInstanceByAttribute("DefinedSet", "name", "IS NULL", null);
//		int count = 0;
//		for (GKInstance peInst : physicalEntityInstances) {
//			int updatedAttributesCount = 0;
//			GKInstance prePEInst = dbaPre.fetchInstance(peInst.getDBID());
//			Collection<GKSchemaAttribute> definedSetAttributes = peInst.getSchemaAttributes();
//			String output = peInst.getDBID().toString();
//			for (GKSchemaAttribute attribute : definedSetAttributes) {
//				Collection<Object> attributeValuePE = peInst.getAttributeValuesList(attribute.getName());
//				Collection<Object> attributeValuePrePE = prePEInst.getAttributeValuesList(attribute.getName());
//				
//				if (attribute.getName().matches("created|modified|species|hasMember|stableIdentifier|DB_ID|_displayName")) {
//
//				} else {
//					if (attributeValuePrePE.size() > 0 && attributeValuePE.size() == 0) {
//						updatedAttributesCount++;
//						output += "\t" + attribute.getName();
//						for (Object attributeValue : attributeValuePrePE) {
//							peInst.addAttributeValueNoCheck(attribute.getName(), attributeValue);
//						}
//						dbaPost.updateInstanceAttribute(peInst, attribute.getName());
//					} else if (attributeValuePE.size() > 0) {
//						System.out.println("PE attribute size filled, but PrePE attribute not filled:\t" + attribute.getName());
//					} else {
//						// Null values don't need to be populated
//					}
//				}
//			}
//			if (updatedAttributesCount > 0) {
//				count++;
//				System.out.println(output);
//				peInst.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
//				dbaPost.updateInstanceAttribute(peInst, ReactomeJavaConstants.modified);
//			}
//		}
//		System.out.println("Updated instances: " + count);
		
		
		GKInstance instanceEdit = InstanceEditUtils.createDefaultIE(dbaPost, 8948690L, true, "During the orthologousEvent fix during release 67, some G. gallus Pathways' orthologousEvent attribute contained manually inferred G. gallus Pathway instances rather than computationally inferred ones.");
		Collection<GKInstance> pathwayInstances = (Collection<GKInstance>) dbaPost.fetchInstanceByAttribute("Pathway", "stableIdentifier", "IS NULL", null);
		for (GKInstance pathwayInst : pathwayInstances) {
			GKInstance inferredFrom = (GKInstance) pathwayInst.getAttributeValue(ReactomeJavaConstants.inferredFrom);
			Collection<GKInstance> orthologousEvents = inferredFrom.getAttributeValuesList(ReactomeJavaConstants.orthologousEvent);
			List<GKInstance> newOrthologousEvents = (List<GKInstance>) orthologousEvents;
			int orthoIndex = 0;
			int gallusIndex = 0;
			for (GKInstance orthoEvent : orthologousEvents) {
				String species = ((GKInstance) orthoEvent.getAttributeValue(ReactomeJavaConstants.species)).getDisplayName();
				if (species.matches("Gallus gallus")) {
					gallusIndex = orthoIndex;
				}
				orthoIndex++;
			}
			newOrthologousEvents.set(gallusIndex, pathwayInst);
			inferredFrom.setAttributeValue(ReactomeJavaConstants.orthologousEvent, newOrthologousEvents);
			dbaPost.updateInstanceAttribute(inferredFrom, ReactomeJavaConstants.orthologousEvent);
			inferredFrom.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
			dbaPost.updateInstanceAttribute(inferredFrom, ReactomeJavaConstants.modified);
		}
		
	}

}
