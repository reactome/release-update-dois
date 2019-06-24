package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class EWASInferrer {

	private static final Logger logger = LogManager.getLogger();
	private static MySQLAdaptor dba;
	static boolean altRefDbExists = false;
	private static String altRefDbId;
	private static GKInstance instanceEditInst;
	private static GKInstance ensgDbInst;
	private static GKInstance enspDbInst;
	private static GKInstance alternateDbInst;
	private static GKInstance uniprotDbInst;
	private static GKInstance speciesInst;
	private static Map<String, String[]> homologueMappings = new HashMap<>();
	private static Map<String, List<String>> ensgMappings = new HashMap<>();
	private static Map<String, GKInstance> referenceGeneProductIdenticals = new HashMap<>();
	private static Map<String,GKInstance> ewasIdenticals = new HashMap<>();
	private static Map<String,GKInstance> residueIdenticals = new HashMap<>();

	// Creates an array of inferred EWAS instances from the homologue mappings file (hsap_species_mapping.txt).
	@SuppressWarnings("unchecked")
	public static List<GKInstance> inferEWAS(GKInstance ewasInst) throws InvalidAttributeException, Exception
	{
		List<GKInstance> infEWASInstances = new ArrayList<>();
		String referenceEntityId = ((GKInstance) ewasInst.getAttributeValue(referenceEntity)).getAttributeValue(identifier).toString();
		if (homologueMappings.get(referenceEntityId) != null)
		{
			// Iterate through the array of homologue mappings, attempting to infer EWAS instances for each.
			logger.info("EWAS homologue(s): " + Arrays.toString(homologueMappings.get(referenceEntityId)));
			for (String homologue : homologueMappings.get(referenceEntityId)) {
				logger.info("Homologue:" + homologue + "  Source:" + referenceEntityId);
				// Handles homologues formatted as either DB:ID or just ID
				String homologueSource = homologue.contains(":") ? homologue.split(":")[0] : "";
				String homologueId = homologue.contains(":") ? homologue.split(":")[1] : homologue;

				if (checkValidSpeciesProtein(homologueId)) {
					GKInstance infReferenceGeneProductInst;
					if (referenceGeneProductIdenticals.get(homologueId) == null) {
						logger.info("Creating ReferenceGeneProduct for " + homologue);
						infReferenceGeneProductInst = InstanceUtilities.createNewInferredGKInstance((GKInstance) ewasInst.getAttributeValue(referenceEntity));
						infReferenceGeneProductInst.addAttributeValue(identifier, homologueId);
						// Reference DB can differ between homologue mappings, but can be differentiated by the 'homologueSource' found in each mapping.
						// With PANTHER data, the Protein IDs are exclusively UniProt
						GKInstance referenceDatabaseInst = homologueSource.equals("ENSP") ? enspDbInst : uniprotDbInst;
						infReferenceGeneProductInst.addAttributeValue(referenceDatabase, referenceDatabaseInst);

						// Creates ReferenceDNASequence instance from ReferenceEntity
						List<GKInstance> inferredReferenceDNAInstances = createReferenceDNASequence(homologueId);
						infReferenceGeneProductInst.addAttributeValue(referenceGene, inferredReferenceDNAInstances);

						infReferenceGeneProductInst.addAttributeValue(species, speciesInst);
						String referenceGeneProductSource = homologueSource.equals("ENSP") ? "ENSEMBL:" : "UniProt:";
						infReferenceGeneProductInst.setAttributeValue(_displayName, referenceGeneProductSource + homologueId);
						logger.info("ReferenceGeneProduct instance created");
						infReferenceGeneProductInst = InstanceUtilities.checkForIdenticalInstances(infReferenceGeneProductInst, null);
						referenceGeneProductIdenticals.put(homologueId, infReferenceGeneProductInst);
					} else {
						logger.info("Orthologous ReferenceGeneProduct already exists");
						infReferenceGeneProductInst = referenceGeneProductIdenticals.get(homologueId);
					}
					// Creating inferred EWAS
					GKInstance infEWASInst = InstanceUtilities.createNewInferredGKInstance(ewasInst);
					infEWASInst.addAttributeValue(referenceEntity, infReferenceGeneProductInst);

					// Method for adding start/end coordinates. It is convoluted due to a quirk with assigning the name differently based on coordinate value (see infer_events.pl lines 1190-1192).
					// The name of the entity needs to be at the front of the 'name' array if the coordinate is over 1, and rearranging arrays in Java for this was a bit tricky.
					for (int startCoord : (Collection<Integer>) ewasInst.getAttributeValuesList(startCoordinate)) {
						infEWASInst.addAttributeValue(startCoordinate, startCoord);
					}
					for (int endCoord : (Collection<Integer>) ewasInst.getAttributeValuesList(endCoordinate)) {
						infEWASInst.addAttributeValue(endCoordinate, endCoord);
					}
					if (infEWASInst.getAttributeValue(startCoordinate) != null && (int) infEWASInst.getAttributeValue(startCoordinate) > 1 || infEWASInst.getAttributeValue(endCoordinate) != null && (int) infEWASInst.getAttributeValue(endCoordinate) > 1) {
						List<String> infEWASInstNames = (ArrayList<String>) (ewasInst).getAttributeValuesList(name);
						infEWASInst.addAttributeValue(name, infEWASInstNames.get(0));
						infEWASInst.addAttributeValue(name, homologueId);
					} else {
						infEWASInst.addAttributeValue(name, homologueId);
					}

					String ewasDisplayName = infEWASInst.getAttributeValue(name) + " [" + ((GKInstance) ewasInst.getAttributeValue(compartment)).getDisplayName() + "]";
					infEWASInst.setAttributeValue(_displayName, ewasDisplayName);

					// Infer residue modifications. This was another step where the name of an EWAS can change.
					// For this, it is based on the existence of the string 'phospho' in the name of the psiMod attribute.
					// If true, 'phospho-' is prepended to the EWAS' name attribute.
					List<GKInstance> infModifiedResidueInstances = new ArrayList<>();
					boolean phosFlag = true;
					for (GKInstance modifiedResidueInst : (Collection<GKInstance>) ewasInst.getAttributeValuesList(hasModifiedResidue)) {
						logger.info("Inferring ModifiedResidue: " + modifiedResidueInst);
						String infModifiedResidueDisplayName = "";
						GKInstance infModifiedResidueInst = InstanceUtilities.createNewInferredGKInstance(modifiedResidueInst);
						infModifiedResidueInst.addAttributeValue(referenceSequence, infReferenceGeneProductInst);
						infModifiedResidueDisplayName += infReferenceGeneProductInst.getDisplayName();
						for (int coordinateValue : (Collection<Integer>) modifiedResidueInst.getAttributeValuesList(coordinate)) {
							infModifiedResidueInst.addAttributeValue(coordinate, coordinateValue);
						}
						if (infModifiedResidueInst.getSchemClass().isValidAttribute(modification)) {
							for (GKInstance modifiedInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList(modification)) {
								infModifiedResidueInst.addAttributeValue(modification, modifiedInst);
							}
							if (infModifiedResidueInst.getAttributeValue(modification) != null) {
								infModifiedResidueDisplayName += " " + ((GKInstance) infModifiedResidueInst.getAttributeValue(modification)).getDisplayName();
							}
						}
						// Update name depending on the presence of 'phospho' in the Psimod's name attribute
						GKInstance firstPsiModInst = (GKInstance) modifiedResidueInst.getAttributeValue(psiMod);
						if (phosFlag && firstPsiModInst.getAttributeValue(name).toString().contains("phospho")) {
							String phosphoName = "phospho-" + infEWASInst.getAttributeValue(name);
							List<String> ewasNames = (ArrayList<String>) infEWASInst.getAttributeValuesList(name);
							String originalName = ewasNames.get(0);
							ewasNames.remove(0);
							infEWASInst.setAttributeValue(name, phosphoName);
							// In the Perl version, this code block modifies the 'name' attribute to include 'phosopho-', but in the process it drops the other names contained. I believe this is unintentional.
							// This would mean attributes without the 'phospho- ' addition would retain their array of names, while attributes containing 'phospho-' would only contain a single name attribute.
							// I've assumed this is incorrect for the rewrite -- Instances that modify the name attribute to prepend 'phospho-' retain their name array. (Justin Cook 2018)
							infEWASInst.addAttributeValue(name, ewasNames);
							String phosphoDisplayName = phosphoName + " [" + ((GKInstance) ewasInst.getAttributeValue(compartment)).getDisplayName() + "]";
							infEWASInst.setAttributeValue(_displayName, phosphoDisplayName);
							// This flag ensures the 'phospho-' is only prepended once.
							logger.info("Updated EWAS name to reflect phosphorylation. Original: " + originalName + ". Updated: " + phosphoName);
							phosFlag = false;
						}
						for (GKInstance psiModInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList(psiMod)) {
							infModifiedResidueInst.addAttributeValue(psiMod, psiModInst);
						}
						if (infModifiedResidueInst.getAttributeValue(psiMod) != null) {
							infModifiedResidueDisplayName += " " + ((GKInstance) infModifiedResidueInst.getAttributeValue(psiMod)).getDisplayName();
						}
						infModifiedResidueInst.setAttributeValue(_displayName, modifiedResidueInst.getAttributeValue(_displayName));
						// Update name to reflect that coordinate values are taken from humans. This takes place after cache retrieval, since the name from DB won't contain updated name.
						if (modifiedResidueInst.getAttributeValue(coordinate) != null) {
							String newModifiedResidueDisplayName = modifiedResidueInst.getAttributeValue(_displayName).toString() + " (in Homo sapiens)";
							infModifiedResidueInst.setAttributeValue(_displayName, newModifiedResidueDisplayName);

						} else {
							if (infModifiedResidueInst.getSchemClass().isa(InterChainCrosslinkedResidue)) {
								infModifiedResidueInst.setDisplayName(infModifiedResidueDisplayName);
							}
						}
						// Database-checker gave errors related to missing 'secondReferenceSequence' and 'equivalentTo' attributes in InterChainCrosslinkedResidues
						// This was because they were never populated. This block is the fix.
						if (infModifiedResidueInst.getSchemClass().isa(InterChainCrosslinkedResidue)) {
							if (modifiedResidueInst.getAttributeValue(secondReferenceSequence) != null) {
								for (GKInstance secondRefSequenceInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList(secondReferenceSequence)) {
									infModifiedResidueInst.addAttributeValue(secondReferenceSequence, secondRefSequenceInst);
								}
							}
							if (modifiedResidueInst.getAttributeValue("equivalentTo") != null) {
								for (GKInstance equivalentToInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList("equivalentTo")) {
									infModifiedResidueInst.addAttributeValue("equivalentTo", equivalentToInst);
								}
							}
						}
						// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which slows things.
						String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infModifiedResidueInst.getSchemClass(), infModifiedResidueInst);
						if (residueIdenticals.get(cacheKey) != null) {
							infModifiedResidueInst = residueIdenticals.get(cacheKey);
						} else {
							infModifiedResidueInst = InstanceUtilities.checkForIdenticalInstances(infModifiedResidueInst, null);
							residueIdenticals.put(cacheKey, infModifiedResidueInst);
						}
						infModifiedResidueInstances.add(infModifiedResidueInst);
						logger.info("Successfully inferred ModifiedResidue");
					}
					infEWASInst.addAttributeValue(hasModifiedResidue, infModifiedResidueInstances);
					// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which slows things.
					String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infEWASInst.getSchemClass(), infEWASInst);
					if (ewasIdenticals.get(cacheKey) != null) {
						infEWASInst = ewasIdenticals.get(cacheKey);
					} else {
						infEWASInst = InstanceUtilities.checkForIdenticalInstances(infEWASInst, ewasInst);
						ewasIdenticals.put(cacheKey, infEWASInst);
					}

					infEWASInst = InstanceUtilities.addAttributeValueIfNecessary(infEWASInst, ewasInst, inferredFrom);
					dba.updateInstanceAttribute(infEWASInst, inferredFrom);
					ewasInst = InstanceUtilities.addAttributeValueIfNecessary(ewasInst, infEWASInst, inferredTo);
					dba.updateInstanceAttribute(ewasInst, inferredTo);
					logger.info("Successfully inferred EWAS instance for " + homologue + " homologue");
					infEWASInstances.add(infEWASInst);
				} else {
					logger.info("Gene ID corresponding to " + homologue + " not found in gene_protein_mapping file -- skipping EWAS inference");
				}
			}
		} else {
			logger.info("Could not infer EWAS, unable to find homologue for " + referenceEntityId);
        }
		logger.info("Total orthologous EWAS' created: " + infEWASInstances.size());
		return infEWASInstances;
	}

	// Homologous Protein IDs can exist in ${source}_${target}_mapping.txt but the corresponding Gene ID might not exist in ${target}_gene_protein_mapping.txt.
	// This is different from when we built Orthopairs files using Compara, since the homology mapping file was generated using IDs from the gene-protein file.
	// This function prevents a Null Exception from killing the entire Reaction's inference, rather than just the EWAS inference.
	private static boolean checkValidSpeciesProtein(String homologueId) {
		return ensgMappings.containsKey(homologueId);
	}

	// Creates ReferenceGeneSequence instance based on ENSG identifier mapped to protein. Creates an instance for the primary database and an alternate, if it exists.
	private static List<GKInstance> createReferenceDNASequence(String homologueId) throws Exception
	{
		List<GKInstance> referenceDNAInstances = new ArrayList<>();
		List<String> ensgIds = ensgMappings.get(homologueId);
		logger.info("Gene ID(s): " + ensgIds);
		for (String ensgId : ensgIds)
		{
			logger.info("Creating ReferenceDNASequence for " + ensgId);
			SchemaClass referenceDNAClass = dba.getSchema().getClassByName(ReferenceDNASequence);
			GKInstance referenceDNAInst = new GKInstance(referenceDNAClass);
			referenceDNAInst.setDbAdaptor(dba);
			referenceDNAInst.addAttributeValue(created, instanceEditInst);
			referenceDNAInst.addAttributeValue(identifier, ensgId);
			referenceDNAInst.addAttributeValue(referenceDatabase, ensgDbInst);
			referenceDNAInst.addAttributeValue(species, speciesInst);
			referenceDNAInst.setAttributeValue(_displayName, "ENSEMBL:" + ensgId);
			referenceDNAInst = InstanceUtilities.checkForIdenticalInstances(referenceDNAInst, null);
			referenceDNAInstances.add(referenceDNAInst);
			if (altRefDbExists)
			{
				logger.info("Creating ReferenceDNASequence for " + ensgId + " using alternate reference database");
				GKInstance alternateRefDNAInst = new GKInstance(referenceDNAClass);
				alternateRefDNAInst.setDbAdaptor(dba);
				String altDbIdentifier = ensgId;
				if (altRefDbId != null)
				{
					altDbIdentifier = altDbIdentifier.replaceAll(altRefDbId, "");
				}
				alternateRefDNAInst.addAttributeValue(created, instanceEditInst);
				alternateRefDNAInst.addAttributeValue(identifier, altDbIdentifier);
				alternateRefDNAInst.addAttributeValue(referenceDatabase, alternateDbInst);
				alternateRefDNAInst.addAttributeValue(species, speciesInst);
				alternateRefDNAInst.setAttributeValue(_displayName, alternateDbInst.getAttributeValue(name) + ":" + ensgId);
				alternateRefDNAInst = InstanceUtilities.checkForIdenticalInstances(alternateRefDNAInst, null);
				referenceDNAInstances.add(alternateRefDNAInst);
			}
		}
		logger.info("Total ReferenceDNASequence instance(s) created: " + referenceDNAInstances.size());
		return referenceDNAInstances;
	}

	// These are setup functions called at the beginning of the 'inferEvent' script
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}

	public static void setInstanceEdit(GKInstance instanceEditCopy)
	{
		instanceEditInst = instanceEditCopy;
	}

	// Sets the HashMap of species-specific homologue-identifier mappings
	public static void setHomologueMappingFile(Map<String, String[]> homologueMappingsCopy) { homologueMappings = homologueMappingsCopy; }

	// Read the species-specific ENSG gene-protein mappings, and create a Hashmap with the contents
	public static void readENSGMappingFile(String toSpecies, String pathToOrthopairs) throws IOException
	{
		String mappingFileName = toSpecies + "_gene_protein_mapping.txt";
		String mappingFilePath = pathToOrthopairs + mappingFileName;
		logger.info("Reading in " + mappingFilePath);
		FileReader fr = new FileReader(mappingFilePath);
		BufferedReader br = new BufferedReader(fr);

		String currentLine;
		while ((currentLine = br.readLine()) != null)
		{
			String[] tabSplit = currentLine.split("\t");
			String ensgKey = tabSplit[0];
			String[] proteins = tabSplit[1].split(" ");
			for (String protein : proteins)
			{
				String proteinId = protein.contains(":") ? protein.split(":")[1] : protein;

				if (ensgMappings.get(proteinId) == null)
				{
					List<String> singleArray = new ArrayList<>();
					singleArray.add(ensgKey);
					ensgMappings.put(proteinId, singleArray);
				} else {
					ensgMappings.get(proteinId).add(ensgKey);
				}
			}
		}
		br.close();
		fr.close();
	}

	// Fetches Uniprot DB instance
	@SuppressWarnings("unchecked")
	public static void fetchAndSetUniprotDbInstance() throws Exception
	{
		Collection<GKInstance> uniprotDbInstances = (Collection<GKInstance>) dba.fetchInstanceByAttribute(ReferenceDatabase, name, "=", "UniProt");
		uniprotDbInst = uniprotDbInstances.iterator().next();
	}

	// Creates instance pertaining to the species Ensembl Protein DB
	public static void createEnsemblProteinDbInstance(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnspAccessUrl) throws Exception
	{
		String enspSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_PROTEIN";
		enspDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		enspDbInst.setDbAdaptor(dba);
		enspDbInst.addAttributeValue(created, instanceEditInst);
		enspDbInst.addAttributeValue(name, "Ensembl");
		enspDbInst.addAttributeValue(name, enspSpeciesDb);
		enspDbInst.addAttributeValue(url, toSpeciesReferenceDbUrl);
		enspDbInst.addAttributeValue(accessUrl, toSpeciesEnspAccessUrl);
		enspDbInst.setAttributeValue(_displayName, "Ensembl");
		dba.storeInstance(enspDbInst);
	}

	// Creates instance pertaining to the species Ensembl Gene DB
	public static void createEnsemblGeneDBInstance(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnsgAccessUrl) throws Exception
	{
		String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_GENE";
		ensgDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		ensgDbInst.setDbAdaptor(dba);
		ensgDbInst.addAttributeValue(created, instanceEditInst);
		ensgDbInst.addAttributeValue(name, "ENSEMBL");
		ensgDbInst.addAttributeValue(name, ensgSpeciesDb);
		ensgDbInst.addAttributeValue(url, toSpeciesReferenceDbUrl);
		ensgDbInst.addAttributeValue(accessUrl, toSpeciesEnsgAccessUrl);
		ensgDbInst.setAttributeValue(_displayName, "ENSEMBL");
		dba.storeInstance(ensgDbInst);
	}

	// Create instance pertaining to any alternative reference DB for the species
	public static void createAlternateReferenceDBInstance(JSONObject altRefDbJSON) throws Exception
	{
		alternateDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		alternateDbInst.setDbAdaptor(dba);
		alternateDbInst.addAttributeValue(created, instanceEditInst);
		alternateDbInst.addAttributeValue(name, ((JSONArray) altRefDbJSON.get("dbname")).get(0));
		alternateDbInst.addAttributeValue(url, altRefDbJSON.get("url"));
		alternateDbInst.addAttributeValue(accessUrl, altRefDbJSON.get("access"));
		alternateDbInst.setAttributeValue(_displayName, ((JSONArray) altRefDbJSON.get("dbname")).get(0));
		alternateDbInst = InstanceUtilities.checkForIdenticalInstances(alternateDbInst, null);
		if (altRefDbJSON.get("alt_id") != null)
		{
			altRefDbId = (String) altRefDbJSON.get("alt_id");
		}
		altRefDbExists = true;
	}

	public static void setAltRefDbToFalse()
	{
		altRefDbExists = false;
	}

	// Sets the species instance for inferEWAS to use
	public static void setSpeciesInstance(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
}
