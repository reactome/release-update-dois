package org.reactome.release.chebiupdate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault_Exception;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

/**
 * An instance of this class can be used to retrieve data from ChEBI.
 * @author sshorser
 *
 */
class ChebiDataRetriever
{
	// Optional TODO: Make this user-configurable. Not really a high priority, but might prove useful some day in the distant future...
	private static final String CHEBI_CACHE_FILE_NAME = "chebi-cache";

	private ChebiWebServiceClient chebiClient = new ChebiWebServiceClient();
	
	private static final Logger logger = LogManager.getLogger();
	
	private boolean useCache;

	/**
	 * Creates a new ChEBI Data Retriever. 
	 * @param useCache - should the cache be used? If <em>true</em> the file "chebi-cache" will be used and no live connections to ChEBI will be attempted.
	 */
	public ChebiDataRetriever(boolean useCache)
	{
		this.useCache = useCache;
	}
	
	/**
	 * This looks weird, I know. I needed to be able to set the Formulae on an Entity 
	 * and the Entity class provided by ChEBI does not have a setter for that. It has setters for
	 * other members, just not all of them. So I added a setter, hence the "accessible" name.
	 * @author sshorser
	 *
	 */
	class AccessibleEntity extends Entity
	{
		public void setFormulae(List<DataItem> formulae)
		{
			this.formulae = formulae;
		}
	}
	
	
	/**
	 * Makes calls to the ChEBI web service to get info for specified ChEBI
	 * identifiers.
	 * 
	 * @param updator
	 * @param refMolecules - a list of ReferenceMolecules. The Identifier of each of these molecules will be sent to ChEBI to get up-to-date information for that Identifier.
	 * @param failedEntitiesList - A list of ReferenceMolecules for which no information was returned by ChEBI. Will be updated by this method.
	 * @return A ReferenceMolecule DB_ID-to-ChEBI Entity map.
	 * @throws IOException 
	 */
	public Map<Long, Entity> retrieveUpdatesFromChebi(Collection<GKInstance> refMolecules, Map<GKInstance, String> failedEntitiesList) throws IOException
	{
		Map<Long, Entity> entityMap = Collections.synchronizedMap(new HashMap<Long, Entity>());
		final Map<String,List<String>> chebiCache = loadCacheFromFile();
		
		// BufferedWriter is supposed to be thread-safe.
		try(FileWriter fileWriter = new FileWriter(CHEBI_CACHE_FILE_NAME, true);
			BufferedWriter bw = new BufferedWriter(fileWriter);)
		{
			AtomicInteger counter = new AtomicInteger(0);
			// The web service calls are a bit slow to respond, so do them in parallel.
			refMolecules.parallelStream().forEach(molecule ->
			{
				String identifier = null;
				try
				{
					identifier = (String) molecule.getAttributeValue("identifier");
					if (identifier != null && !identifier.trim().equals(""))
					{
						Entity entity = null;
						// first, try to get from cache. NULL will be returned if identifier is not in cache.
						if (this.useCache)
						{
							entity = this.extractChEBIEntityFromCache(chebiCache, identifier);
						}
						// if the data we want is not in the cache OR we don't want to use the cache, try the web service. This will be done even if this.useCache == true
						if (entity == null || !this.useCache)
						{
							entity = this.getChEBIDataFromWebService(failedEntitiesList, chebiCache, bw, molecule);
						}
						// Add entity to map (if it's non-null)
						if (entity != null)
						{
							entityMap.put(molecule.getDBID(), entity);
						}
					}
					else
					{
						logger.error("ERROR: Instance \"{}\" has an empty/null identifier. This should not be allowed.", molecule.toString());
						failedEntitiesList.put(molecule, molecule.toString() + " has an empty/NULL identifier.");
					}
					int i = counter.getAndIncrement();
					if (i % 250 == 0)
					{
						logger.debug("{} ChEBI identifiers checked", i);
					}
				}
				catch (ChebiWebServiceFault_Exception e)
				{
					ChebiDataRetriever.handleWSException(failedEntitiesList, molecule, identifier, e);
				}
				catch (InvalidAttributeException e)
				{
					logger.error("InvalidAttributeException caught while trying to get the \"identifier\" attribute on " + molecule.toString());
					// stack trace should be printed, but I don't think this should break execution, though the only way I can think
					// of this happening is if the data model changes - otherwise, this exception would probably never be caught.
					e.printStackTrace();
				}
				catch (Exception e)
				{
					// general exceptions - print stack trace but keep going.
					logger.error("Exception was caught: {}", e.getMessage());
					e.printStackTrace();
				}
			});
		}
		return entityMap;
	}

	/**
	 * Handles exceptions from the ChEBI web service. Some of them are ok, as in "invalid ChEBI identifier",
	 * and "entity is deleted, obsolete, not released", but anything else
	 * will cause a runtime exception to be thrown. It's probably not safe to continue with unrecognized exceptions.
	 * IF you encounter *new* exceptions that are not listed here, but are not too serious, feel free to update this code
	 * to write the appropriate message for those exceptions, rather than failing with a RuntimeException.
	 * @param failedEntitiesList
	 * @param molecule
	 * @param identifier
	 * @param e
	 */
	private static void handleWSException(Map<GKInstance, String> failedEntitiesList, GKInstance molecule, String identifier, ChebiWebServiceFault_Exception e)
	{
		// "invalid ChEBI identifier" probably shouldn't break execution but should be logged for further investigation.
		if (e.getMessage().contains("invalid ChEBI identifier"))
		{
			String errMsg = "ChEBI Identifier \""+identifier+"\" is not formatted correctly.";
			logger.error(errMsg);
			failedEntitiesList.put(molecule, errMsg);
		}
		// Log this identifier, but don't fail.
		else if (e.getMessage().contains("the entity in question is deleted, obsolete, or not yet released"))
		{
			String errMsg = "ChEBI Identifier \""+identifier+"\" is deleted, obsolete, or not yet released.";
			logger.error(errMsg);
			failedEntitiesList.put(molecule, errMsg);
		}
		else
		{
			// Other Webservice errors should probably break execution - if one fails, they will all probably fail.
			// This is *not* a general principle, but is based on my experience with the ChEBI webservice specifically -
			// it's a pretty stable service so it's unlikely that if one service call fails, the others will succeed.
			logger.error("WebService error occurred! Message is: {}", e.getMessage());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/**
	 * Get data from the ChEBI web service.
	 * @param failedEntitiesList - a list of failed entities, this may be updated if the query to ChEBI fails.
	 * @param chebiCache - the cache of ChEBI identifiers, this will be updated if the query is successful.
	 * @param bw - writer for the cache.
	 * @param molecule - the Molecule to query for.
	 * @return - The Entity, from ChEBI.
	 * @throws ChebiWebServiceFault_Exception
	 * @throws IOException
	 * @throws Exception
	 */
	private Entity getChEBIDataFromWebService(Map<GKInstance, String> failedEntitiesList, final Map<String, List<String>> chebiCache, BufferedWriter bw, GKInstance molecule) throws ChebiWebServiceFault_Exception, IOException, Exception
	{
		String identifier = (String) molecule.getAttributeValue(ReactomeJavaConstants.identifier);
		// If there's a valid cache and we're supposed to use it, log a message here indicating
		// that there must have been a cache miss.
		if (this.useCache && chebiCache.size() > 0)
		{
			logger.trace("Cache miss for CHEBI:{}", identifier);
		}
		Entity entity = this.chebiClient.getCompleteEntity(identifier);
		// IF there is valid entity AND we are supposed to use a cache, then write the entity to the cache file.
		if (entity != null && this.useCache)
		{
			bw.write("CHEBI:"+identifier+"\t"+entity.getChebiId()+"\t"+entity.getChebiAsciiName()+"\t"+ (entity.getFormulae().size() > 0 ? entity.getFormulae().get(0).getData() : "") + "\t" + LocalDateTime.now().toString() + "\n");
			bw.flush();
		}
		// IF the entity was null, then log a message that the webservice response was null!
		if (entity == null)
		{
			failedEntitiesList.put(molecule, "ChEBI WebService response was NULL.");
		}
		
		return entity;
	}

	/**
	 * Returns the data from the cache file (if it exists). If it doesn't exit, then an empty map will be returned.
	 * @return A mapping of ChEBI ID to a list with items in this order: ChEBI (might be different if ChEBI has changed identifiers), ChEBI Name, Formula.
	 * @throws IOException
	 */
	private Map<String,List<String>> loadCacheFromFile() throws IOException
	{
		Map<String,List<String>> chebiCache = Collections.synchronizedMap(new HashMap<String, List<String>>());
		if (this.useCache)
		{
			logger.info("useCache is TRUE - chebi-cache file will be read, and populated. Identifiers not in the cache will be queried from ChEBI.");
			// if the cache exists, load it.
			if (Files.exists(Paths.get(CHEBI_CACHE_FILE_NAME)))
			{
				Files.readAllLines(Paths.get(CHEBI_CACHE_FILE_NAME)).parallelStream().forEach( line -> {
					String[] parts = line.split("\t");
					String oldChebiID = parts[0];
					String newChebiID = parts[1];
					String name = parts[2];
					String formula = parts.length > 2 ? parts[3] : "";
					chebiCache.put(oldChebiID, Arrays.asList(newChebiID, name, formula));
				});
			}
			logger.debug("{} entries in the chebi-cache", chebiCache.size());
		}
		else
		{
			logger.info("useCache is FALSE - chebi-cache will NOT be read. ChEBI will be queried for ALL identifiers.");
		}
		return chebiCache;
	}

	/**
	 * Extract a ChEBI entity from the cache, based on the identifier.
	 * @param chebiCache - The cache from a file.
	 * @param identifier - The chebi Identifier.
	 * @return A ChEBI Entity that will be built from the data in the cache. It will only have: an Identifier, ChEBI Name, Formula.
	 * NULL will be returned if <code>identifier</code> is not in the cache.
	 */
	private Entity extractChEBIEntityFromCache(Map<String, List<String>> chebiCache, String identifier)
	{
		Entity entity = null;
		if (chebiCache.containsKey("CHEBI:"+identifier))
		{
			// Use the AccessibleEntity to set the formula.
			entity = new AccessibleEntity();
			entity.setChebiId(chebiCache.get("CHEBI:"+identifier).get(0));
			entity.setChebiAsciiName(chebiCache.get("CHEBI:"+identifier).get(1) );
			DataItem formula = new DataItem();
			formula.setData(chebiCache.get("CHEBI:"+identifier).get(2));
			((AccessibleEntity)entity).setFormulae(Arrays.asList(formula));
		}
		return entity;
	}
}
