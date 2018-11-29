package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.release.common.database.InstanceEditUtils;

/*
 * This data fix was written to correct a mistake in the first version of the Java Orthoinferene code.
 * 
 * At the end of a species' orthoinference process, all involved human pathways are supposed to have their 'orthologousEvent' attribute updated 
 * to connect them with the newly inferred pathway. This attribute is multi-value, and as such is meant to be an array of pathways that were inferred.
 * The issue was that after orthoinference had completed, the orthologousEvent attribute was only populated with the most recently inferred pathway. 
 * 
 * The problem manifested when the code was changed from a 'single run of all species' to 'one run per species'. At time of writing, it is believed that
 * the orthologusEvent attribute was not properly loaded in the latter scenario, and resulted in the orthologousEvent slot not being updated properly per run. 
 * 
 * This correction iterates through all human pathways, and correctly populates the orthologusEvent attribute by checking all referrals of the 'inferredFrom' 
 * attribute. The below code clears the orthologusEvent slot before adding each species pathway in the same order as the orthoinference process. (Justin Cook November 2018)
 */

public class OrthologousEventsFix {
	static MySQLAdaptor dbAdaptor = null;
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
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		GKInstance instanceEdit = InstanceEditUtils.createDefaultIE(dbAdaptor, 8948690L, true, "The orthologousEvent attribute was updated to include all inferred events");
		// Species array that matches orthoinference order
		List<String> speciesNames = Arrays.asList("PFA", "SPO", "SCE", "DDI", "CEL", "SSC", "BTA", "CFA", "MMU", "RNO", "GGA", "TGU", "XTR", "DRE", "DME", "ATH", "OSA");
		Collection<GKInstance> sourceSpeciesInst = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("Species", "name", "=", "Homo sapiens");
		String dbId = sourceSpeciesInst.iterator().next().getDBID().toString();
		Collection<GKInstance> pathwayInstances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("Pathway", "species", "=", dbId);
		int count = 0;
		for (GKInstance pathwayInst : pathwayInstances) {
//			System.out.println(pathwayInst);
			Collection<GKInstance> pathwayReferrers = pathwayInst.getReferers("inferredFrom");
			Map<String,GKInstance> speciesReferrals = new HashMap<String,GKInstance>();
			if (pathwayReferrers == null) {
				count++;
				System.out.println("NOT\t" + pathwayInst);
				continue;
			}
			for (GKInstance referrer : pathwayReferrers) {
				String species = (String) ((GKInstance) referrer.getAttributeValue(ReactomeJavaConstants.species)).getAttributeValue("abbreviation");
				speciesReferrals.put(species, referrer);
			}
			pathwayInst.setAttributeValue(ReactomeJavaConstants.orthologousEvent, null);
			for (String name : speciesNames) {
				GKInstance speciesRef = speciesReferrals.get(name);
				if (speciesRef != null) {
					//System.out.println("Added: " + name);
					pathwayInst.addAttributeValue(ReactomeJavaConstants.orthologousEvent, speciesRef);
//				} else {
//					System.out.println("Not added: " + name);
				}
			}
			dbAdaptor.updateInstanceAttribute(pathwayInst, ReactomeJavaConstants.orthologousEvent);
			pathwayInst.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
			dbAdaptor.updateInstanceAttribute(pathwayInst, ReactomeJavaConstants.modified);
		}
		System.out.println("No update: " + count);
	}

}
