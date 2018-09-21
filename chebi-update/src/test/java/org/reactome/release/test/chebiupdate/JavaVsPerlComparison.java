package org.reactome.release.test.chebiupdate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;
import org.reactome.util.compare.DBObjectComparer;

public class JavaVsPerlComparison
{

	public static void main(String[] args) throws Exception
	{
		MySQLAdaptor javaUpdatedDB = new MySQLAdaptor("localhost", "gk_central_R66_before_chebi_update.sql", "root", "root", 3307);
		MySQLAdaptor perlUpdatedDB = new MySQLAdaptor("localhost", "gk_central_R66_before_chebi_update.sql", "root", "root", 3308);
		int diffCount = 0;
		int sameCount = 0;

		@SuppressWarnings("unchecked")
		String chebiRefDBID = (new ArrayList<GKInstance>( javaUpdatedDB.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", "ChEBI"))).get(0).getDBID().toString();

		@SuppressWarnings("unchecked")
		Collection<GKInstance> javaRefMolecules = (Collection<GKInstance>) javaUpdatedDB.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceMolecule, ReactomeJavaConstants.referenceDatabase, "=", chebiRefDBID);

//		@SuppressWarnings("unchecked")
//		Collection<GKInstance> perlRefMolecules = (Collection<GKInstance>) perlUpdatedDB.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", chebiRefDBID);

		for (GKInstance javaInstance : javaRefMolecules)
		{
			StringBuilder sb = new StringBuilder();
			GKInstance perlInstance = perlUpdatedDB.fetchInstance(javaInstance.getDBID());
			int currentDiff = DBObjectComparer.compareInstances(javaInstance, perlInstance, sb, 2, a -> {
				return !(new HashSet<String>(Arrays.asList("DB_ID", "dateTime", "modified", "created",
								"input", "output", "crossReference", "compartment", "stableIdentifier",
								"termProvider", "vertex", "hasMember", "referenceDatabase", "representedInstance"))).contains(a.getName())
						// This condition below is to avoid recursing from the SimpleEntity back the ReferenceMolecule via SimpleEntity's referenceEntity attribute.
						// The maxRecursionDepth would prevent things from getting too out of hand, but you'll still get a lot of redundant diffs if you don't do this.
						&& !( ( new ArrayList<SchemaClass>(a.getSchemaClass())).get(0).getName().equals(ReactomeJavaConstants.SimpleEntity) && a.getName().equals(ReactomeJavaConstants.referenceEntity));
				}, true);
			 
			if (currentDiff > 0)
			{
				System.out.println("===\nDiffs on instance: "+javaInstance.toString());
				System.out.println(sb.toString());
			}
			else
			{
				sameCount++;
			}
			
			diffCount += currentDiff;
		}
		
		System.out.println("Number of ReferenceMolecule diffs: "+diffCount + " Number of sames: "+sameCount);
	}
}
