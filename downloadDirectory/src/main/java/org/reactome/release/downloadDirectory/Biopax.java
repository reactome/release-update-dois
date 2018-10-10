package org.reactome.release.downloadDirectory;

import org.gk.persistence.MySQLAdaptor;
import org.reactome.biopax.SpeciesAllPathwaysConverter;

import org.jdom.*;

public class Biopax {

	public static void execute(MySQLAdaptor dba) throws Exception {
		//TODO: Change to exporter directory and remove any folders of current release
		
		//TODO: Try it with an end-of-release db
		SpeciesAllPathwaysConverter.main(new String[]{"localhost", "test_reactome_65", "jcook", "R34ct0m3", "3306", "65"});
	}
}
