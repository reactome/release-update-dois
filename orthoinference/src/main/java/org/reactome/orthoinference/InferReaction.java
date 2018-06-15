package org.reactome.orthoinference;

import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class InferReaction {

	private static MySQLAdaptor dba;
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	// This function mimics the Perl version of InferEvent, inferring attribute instances of input, output, catalyst activity, and regulations
	public static void inferEvent(GKInstance reactionInst) throws InvalidAttributeException, Exception
	{
		String dbId = reactionInst.getAttributeValue("DB_ID").toString();
		String stableId = reactionInst.getAttributeValue("name").toString();
		System.out.println("Reaction: [" + dbId + "] " + stableId);	
		
		// TODO: Release date/instance edit; skip_event; %inferred_event; check for identical instances (summation/evidence type); Global variables for summation/evidence type
		// Creates inferred instance of reactionInst
		GKInstance inferredReaction = GenerateInstance.newInferredGKInstance(reactionInst);
		inferredReaction.addAttributeValue(ReactomeJavaConstants.name, reactionInst.getAttributeValue(ReactomeJavaConstants.name));
		inferredReaction.addAttributeValue(ReactomeJavaConstants.goBiologicalProcess, reactionInst.getAttributeValue(ReactomeJavaConstants.goBiologicalProcess));
		//TODO: $75% needs to be a variable (??)
		GKInstance summationInst = new GKInstance(dba.getSchema().getClassByName(ReactomeJavaConstants.Summation));
		summationInst.addAttributeValue(ReactomeJavaConstants.text, "This event has been computationally inferred from an event that has been demonstrated in another species.<p>The inference is based on the homology mapping in Ensembl Compara. Briefly, reactions for which all involved PhysicalEntities (in input, output and catalyst) have a mapped orthologue/paralogue (for complexes at least 75% of components must have a mapping) are inferred to the other species. High level events are also inferred for these events to allow for easier navigation.<p><a href='/electronic_inference_compara.html' target = 'NEW'>More details and caveats of the event inference in Reactome.</a> For details on the Ensembl Compara system see also: <a href='http://www.ensembl.org/info/docs/compara/homology_method.html' target='NEW'>Gene orthology/paralogy prediction method.</a>");
		inferredReaction.addAttributeValue(ReactomeJavaConstants.summation, summationInst);

		GKInstance evidenceTypeInst = new GKInstance(dba.getSchema().getClassByName(ReactomeJavaConstants.EvidenceType));
		evidenceTypeInst.addAttributeValue(ReactomeJavaConstants.name, "inferred by electronic annotation");
		evidenceTypeInst.addAttributeValue(ReactomeJavaConstants.name, "IEA");
		inferredReaction.addAttributeValue(ReactomeJavaConstants.evidenceType, evidenceTypeInst);
		
		//TODO: count_distinct_proteins; write eligible reactions to external file and keep count;
		
		//TODO: Success measure; 
		InferReaction.inferAttributes(reactionInst, inferredReaction, "input");
//		InferReaction.inferAttributes(reactionInst, inferredReaction, "output");
//		InferReaction.inferCatalyst(reactionInst, inferredReaction);
	}
	
	// Function used to create inferred instances related to either 'input' or 'output'
	@SuppressWarnings("unchecked")
	public static void inferAttributes(GKInstance reactionInst, Instance inferredReaction, String attribute) throws InvalidAttributeException, Exception
	{
		//TODO: Put ortho'd entity in array and add to inferredReaction
		for (GKInstance attributeInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(attribute))
		{
			System.out.println("   " + attributeInst.getAttributeValue("DB_ID"));
			OrthologousEntity.createOrthoEntity(attributeInst, false);
		}
	}
	
	// Function used to created inferred catalysts
	@SuppressWarnings("unchecked")
	public static void inferCatalyst(GKInstance reactionInst, Instance inferredReaction) throws InvalidAttributeException, Exception
	{
		for (@SuppressWarnings("unused") GKInstance attributeInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList("catalystActivity"))
		{
//			System.out.println("  CatalystActivity");
		}
	}
}
