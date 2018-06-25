package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class SkipTests {
	
	private static MySQLAdaptor dba;
	private static ArrayList<String> skipList = new ArrayList<String>();
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	public static void getSkipList(String skipListFilename) throws NumberFormatException, Exception
	{
		String[] pathwayIdsToSkip = {"162906","168254","977225"};
		for (String pathwayId : pathwayIdsToSkip) 
		{
			GKInstance pathwayInst = dba.fetchInstance(Long.valueOf(pathwayId));
			if (pathwayInst != null)
			{
				List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
				classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Pathway, new String[]{ReactomeJavaConstants.hasEvent}, new String[]{}));
				String[] outClasses = new String[] {ReactomeJavaConstants.ReactionlikeEvent};
				@SuppressWarnings("unchecked")
				Collection<GKInstance> followedInstances = InstanceUtilities.followInstanceAttributes(pathwayInst, classesToFollow, outClasses);
				
				for (GKInstance entity : followedInstances)
				{
					skipList.add(entity.getDBID().toString());
				}
			}
		}
		String skipListFilePath = "src/main/resources/" + skipListFilename; 
		FileReader fr = new FileReader(skipListFilePath);
		BufferedReader br = new BufferedReader(fr);
		String currentLine;
		while ((currentLine = br.readLine()) != null)
		{
			skipList.add(currentLine.trim());
		}
		br.close();
		fr.close();
	}
	
	public static void checkIfSkip(GKInstance reactionInst) throws NumberFormatException, Exception
	{
		return;
	}
}
