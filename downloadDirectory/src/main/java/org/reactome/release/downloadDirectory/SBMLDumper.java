package org.reactome.release.downloadDirectory;

import java.util.Collection;
import java.util.HashMap;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class SBMLDumper {

	public static void execute(MySQLAdaptor dba) throws Exception {
		GKInstance human = dba.fetchInstance(48887L);
		Collection<GKInstance> reactionInstances = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, ReactomeJavaConstants.species, "=", human);
		for (GKInstance reactionInst : reactionInstances) {
			SBMLDumper.dissectEvent(reactionInst);
		}
	}
	
	public static void dissectEvent(GKInstance reactionInst) throws InvalidAttributeException, Exception {
		
		boolean validReaction = false;
		for (GKInstance inputInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(ReactomeJavaConstants.input)) {
			if (inputInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
				validReaction = true;
			}
		}
		for (GKInstance outputInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(ReactomeJavaConstants.output)) {
			if (outputInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
				validReaction = true;
			}
		}
		String id = SBMLDumper.getId(reactionInst);
		HashMap<String,HashMap<String,String>> reactions = new HashMap<String,HashMap<String,String>>();
		if (validReaction) {
			
		}
	}
	
	public static String getId(GKInstance reactionInst) throws InvalidAttributeException, Exception {
		
		String id = null;
		if (reactionInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.stableIdentifier) && reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier) != null) {
			id = ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants._displayName).toString();
		} else {
			id = reactionInst.getDBID().toString();
		}
		return id;
		
	}
}
