package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
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
		String database = props.getProperty("database");
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));
		MySQLAdaptor dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		GKInstance instanceEdit = InstanceEditUtils.createDefaultIE(dbAdaptor, 8948690L, true, "Due to a bug with CandidateSets that are converted to DefinedSets during Orthoinference, the 'species' and 'created' slots needed to be filled");
		Collection<GKInstance> physicalEntityInstances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("PhysicalEntity", ReactomeJavaConstants.stableIdentifier, "IS NULL", null);
		
		for (GKInstance peInst : physicalEntityInstances) {
			int memberCount = peInst.getAttributeValuesList(ReactomeJavaConstants.hasMember).size();
			
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
			
			if (memberCount > 0) {
				GKInstance memberInst = (GKInstance) peInst.getAttributeValue(ReactomeJavaConstants.hasMember);
				GKInstance speciesInst = (GKInstance) memberInst.getAttributeValue(ReactomeJavaConstants.species);
				GKInstance createdInst = (GKInstance) memberInst.getAttributeValue(ReactomeJavaConstants.created);
				peInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
				peInst.addAttributeValue(ReactomeJavaConstants.created, createdInst);
				peInst.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
				dbAdaptor.updateInstance(peInst);
			} else {
				System.out.println(peInst + "\tNo members found -- investigate!");
			}
		}
		
		
	}

}
