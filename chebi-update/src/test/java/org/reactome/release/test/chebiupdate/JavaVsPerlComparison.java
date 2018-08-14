package org.reactome.release.test.chebiupdate;

import java.util.ArrayList;
import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.util.compare.DBObjectComparer;

public class JavaVsPerlComparison
{

	public static void main(String[] args) throws Exception
	{
		MySQLAdaptor javaUpdatedDB = new MySQLAdaptor("localhost", "gk_central.reactomecurator.20180803.dump.sql", "root", "root", 3307);
		MySQLAdaptor perlUpdatedDB = new MySQLAdaptor("localhost", "gk_central.reactomecurator.20180803.dump.sql", "root", "root", 3308);
		int diffCount = 0;
		int sameCount = 0;

		@SuppressWarnings("unchecked")
		String chebiRefDBID = (new ArrayList<GKInstance>( javaUpdatedDB.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI"))).get(0).getDBID().toString();

		@SuppressWarnings("unchecked")
		Collection<GKInstance> javaRefMolecules = (Collection<GKInstance>) javaUpdatedDB.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", chebiRefDBID);

//		@SuppressWarnings("unchecked")
//		Collection<GKInstance> perlRefMolecules = (Collection<GKInstance>) perlUpdatedDB.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", chebiRefDBID);

		
		for (GKInstance javaInstance : javaRefMolecules)
		{
			StringBuilder sb = new StringBuilder();
			GKInstance perlInstance = perlUpdatedDB.fetchInstance(javaInstance.getDBID());
			int currentDiff = DBObjectComparer.compareInstances(javaInstance, perlInstance, sb, 2);
			
			if (currentDiff > 0)
			{
				System.out.println("Diffs on instance: "+javaInstance.getDisplayName());
				System.out.println(sb.toString());
			}
			else
			{
				sameCount++;
			}
			
			diffCount += currentDiff;
		}
		
		System.out.println("Number of diffs: "+diffCount + " Number of sames: "+sameCount);
	}

}
