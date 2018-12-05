package org.reactome.release.update_stable_ids;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.TransactionsNotSupportedException;

public class UpdateStableIds {

	public static void stableIdUpdater(MySQLAdaptor dbaSlice, MySQLAdaptor dbaGkCentral) throws Exception {
		
//		dbaSlice.startTransaction();
		dbaGkCentral.startTransaction();
		
		List<String> acceptedClassesWithStableIdentifiers = Arrays.asList("Pathway","SimpleEntity","OtherEntity","DefinedSet","Complex","EntityWithAccessionedSequence","GenomeEncodedEntity","Reaction","BlackBoxEvent","CandidateSet","Polymer","Depolymerisation","Polymerisation","Drug","FailedReaction","EntitySet");
		List<String> excludedClassesWithStableIdentifiers = Arrays.asList("PositiveGeneExpressionRegulation","PositiveRegulation","NegativeRegulation","Requirement","NegativeGeneExpressionRegulation");
		ResultSet classesWithStableIdentifiers = dbaGkCentral.executeQuery("select distinct _class from DatabaseObject where StableIdentifier is not null", null);

		while (classesWithStableIdentifiers.next()) {
			String className = classesWithStableIdentifiers.getString(1);
			if (acceptedClassesWithStableIdentifiers.contains(className)) {
				Collection<GKInstance> instancesFromClass = dbaSlice.fetchInstancesByClass(className);
				for (GKInstance instance : instancesFromClass) {
					GKInstance stableIdentifierInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
				}
			} else if (excludedClassesWithStableIdentifiers.contains(className)) {
				System.out.println("'" + className + "' class is excluded from StableIdentifier update");
			} else {
				System.out.println("Unknown class found that contains StableIdentifier attribute: '" + className + "'");
			}
		}
		
	}

}
