package org.reactome.release.chebiupdate;

import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;
import org.reactome.release.common.ReleaseStep;

public class ChebiUpdateStep extends ReleaseStep
{
	@Override
	public void executeStep(Properties props) throws Exception
	{
		MySQLAdaptor adaptor = getMySQLAdaptorFromProperties(props);
		this.loadTestModeFromProperties(props);
		long personID = new Long(props.getProperty("person.id"));
		boolean useCache = Boolean.parseBoolean(props.getProperty("useCache", "false"));
		ChebiUpdater chebiUpdater = new ChebiUpdater(adaptor, this.testMode, personID, useCache);
		
		logger.info("Pre-update duplicate check:");
		chebiUpdater.checkForDuplicates();
		chebiUpdater.updateChebiReferenceMolecules();
		logger.info("Post-update duplicate check:");
		chebiUpdater.checkForDuplicates();
	}

}
