package org.reactome.orthoinference.cache;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;

/**
 * This cache is based on the hashmaps that are created at the begining of the infer_events.pl script.
 * @author sshorser
 *
 */
public class OrthoinferenceCache
{

	public static Map<String,List<GKInstance>> getHomologues()
	{
		//TODO: Return an actual cache (and also write code to populate it. 
		return null;
	}
}
