package org.reactome.orthoinference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;

public class ProteinCount {
	
	private static HashMap<String, String[]> homologueMappings = new HashMap<String,String[]>();
	
	public static void setHomologueMappingFile(HashMap<String, String[]> homologueMappingsCopy) throws IOException
	{
		homologueMappings = homologueMappingsCopy;
	}

	public static void countDistinctProteins (GKInstance instanceToBeInferred) throws InvalidAttributeException, Exception
	{
		List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntityWithAccessionedSequence, new String[]{ReactomeJavaConstants.referenceEntity}, new String[]{}));
		
		String[] outClasses = new String[] {ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.EntitySet};
		@SuppressWarnings("unchecked")
		Collection<GKInstance> followedInstances = InstanceUtilities.followInstanceAttributes(instanceToBeInferred, classesToFollow, outClasses);		

		int total = 0;
		int inferred = 0;
		int max = 0;
		
		for (GKInstance entity : followedInstances)
		{
			if (entity.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct))
			{
				int count = 0;
				String identifier = entity.getAttributeValue(ReactomeJavaConstants.identifier).toString();
			}
		}
	}
}
