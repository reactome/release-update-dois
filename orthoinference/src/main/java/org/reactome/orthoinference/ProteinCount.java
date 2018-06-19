package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;

public class ProteinCount {

	public static void countDistinctProteins (GKInstance instanceToBeInferred) throws Exception
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

	}
}
