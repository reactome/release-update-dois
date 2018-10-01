package org.reactome.release.downloadDirectory;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class PathwaySummationMappingFile {

	public static void execute(MySQLAdaptor dba) throws Exception {
		
		Collection<GKInstance> pathwayInstances = dba.fetchInstancesByClass(ReactomeJavaConstants.Pathway);
		HashSet<String> rowHash = new HashSet<String>();
		PrintWriter pathwaySummationFilename = new PrintWriter("pathway2summation.txt");
		for (GKInstance pathwayInst : pathwayInstances) {
			boolean containsHumanEntry = false;
			for (GKInstance speciesInst: (Collection<GKInstance>) pathwayInst.getAttributeValuesList(ReactomeJavaConstants.species)) {
				if (speciesInst.getDisplayName().equals("Homo sapiens")) {
					containsHumanEntry = true;
				}
			}
			if (containsHumanEntry) {
				String name = pathwayInst.getAttributeValue(ReactomeJavaConstants.name).toString();
				String stableId = ((GKInstance) pathwayInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
				for (GKInstance summationInst : (Collection<GKInstance>) pathwayInst.getAttributeValuesList(ReactomeJavaConstants.summation)) {
					String text = summationInst.getAttributeValue(ReactomeJavaConstants.text).toString();
					text = text.replaceAll(" +", " ");
					text = text.replaceAll("\n", "");
					String row = stableId + "\t" + name + "\t" + text + "\n";
					if (!rowHash.contains(row)) {
						Files.write(Paths.get("pathway2summation.txt"), row.getBytes(), StandardOpenOption.APPEND);
						rowHash.add(row);
					}
				}
			}
		}
	}
}
