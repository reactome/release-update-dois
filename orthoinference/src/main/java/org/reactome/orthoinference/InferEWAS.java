package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;

public class InferEWAS {

	private static MySQLAdaptor dba;
	//TODO: Value of homologueMappings and ensgMappings differs
	private static HashMap<String, String[]> homologueMappings = new HashMap<String,String[]>();
	private static HashMap<String, ArrayList<String>> ensgMappings = new HashMap<String,ArrayList<String>>();
	private static HashMap<String, GKInstance> seenRPS = new HashMap<String,GKInstance>();
	private static GKInstance ensgDbInst = null;
	private static GKInstance enspDbInst = null;
	private static GKInstance alternateDbInst = null;
	private static GKInstance uniprotDbInst = null;
	//TODO: Remove static value when scaling up species total
	static boolean refDb = false;
	private static GKInstance speciesInst = null;

/// Creates an array inferred EWAS instances from the homologue mappings file (hsap_species_mapping.txt)
	public static ArrayList<GKInstance> inferEWAS(GKInstance ewasInst) throws InvalidAttributeException, Exception
	{
		ArrayList<GKInstance> infEWASInstances = new ArrayList<GKInstance>();
		String referenceEntityId = ((GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
		//TODO: opt_filt?
		if (homologueMappings.get(referenceEntityId) != null)
			{
				// Iterate through the array of homologue mappings, creating inferred EWAS instances
				for (Object homologue : homologueMappings.get(referenceEntityId))
				{
					String[] splitHomologue = homologue.toString().split(":");
					String homologueSource = splitHomologue[0];
					String homologueId = splitHomologue[1];
					GKInstance infReferenceGeneProduct = null;
					if (seenRPS.get(homologueId) == null)
					{
						infReferenceGeneProduct = GenerateInstance.newInferredGKInstance((GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.referenceEntity));
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.identifier, homologueId);
						GKInstance referenceDb = null;
						if (homologueSource.equals("ENSP"))
						{
							referenceDb = enspDbInst;
						} else {
							referenceDb = uniprotDbInst;
						}
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.referenceDatabase,  referenceDb);
						// Equivalent of create_ReferenceDNASequence function in infer_events.pl
						ArrayList<GKInstance> inferredReferenceDNAInstances = InferEWAS.createReferenceDNASequence(homologueId);
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.referenceGene, inferredReferenceDNAInstances);
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
						infReferenceGeneProduct = GenerateInstance.checkForIdenticalInstances(infReferenceGeneProduct);
						seenRPS.put(homologueId, infReferenceGeneProduct);
					} else {
						infReferenceGeneProduct = seenRPS.get(homologueId);
					}

					// Creating inferred EWAS
					GKInstance infEWAS = GenerateInstance.newInferredGKInstance(ewasInst);
					infEWAS.addAttributeValue(ReactomeJavaConstants.referenceEntity, infReferenceGeneProduct);
					
					// Convoluted method for adding start/end coordinates. It is convoluted due to a quirk with assigning the name differently based on coordinate value. 
					// The name of the entity needs to be at the front of the 'name' array if the coordinate is over 1, and rearranging arrays in Java was not trivial.
					for (Object startCoordinate : ewasInst.getAttributeValuesList(ReactomeJavaConstants.startCoordinate))
					{
						infEWAS.addAttributeValue(ReactomeJavaConstants.startCoordinate, startCoordinate);
					}
					for (Object endCoordinate : ewasInst.getAttributeValuesList(ReactomeJavaConstants.endCoordinate))
					{
						infEWAS.addAttributeValue(ReactomeJavaConstants.endCoordinate, endCoordinate);
					}
					Object startCoordObj = infEWAS.getAttributeValue(ReactomeJavaConstants.startCoordinate);
					Object endCoordObj = infEWAS.getAttributeValue(ReactomeJavaConstants.endCoordinate);
					Integer startCoord = 0;
					Integer endCoord = 0;
					if (startCoordObj != null)
					{
						startCoord = Integer.valueOf(infEWAS.getAttributeValue(ReactomeJavaConstants.startCoordinate).toString());
					}
					if (endCoordObj != null)
					{
						endCoord = Integer.valueOf(infEWAS.getAttributeValue(ReactomeJavaConstants.endCoordinate).toString());
					}
					if (startCoord > 1 || endCoord > 1) {
						@SuppressWarnings("unchecked")
						ArrayList<String> infAttributeInstNames = (ArrayList<String>) ((GKInstance) ewasInst).getAttributeValuesList(ReactomeJavaConstants.name);
						infEWAS.addAttributeValue(ReactomeJavaConstants.name, infAttributeInstNames.get(0));
						infEWAS.addAttributeValue(ReactomeJavaConstants.name, homologueId);
					} else {
						infEWAS.addAttributeValue(ReactomeJavaConstants.name, homologueId);
					}

					// Infer residue modifications
					ArrayList<GKInstance> infModifiedResidues = new ArrayList<GKInstance>();
					boolean phosFlag = true;
					for (Object modifiedResidueObj : ewasInst.getAttributeValuesList(ReactomeJavaConstants.hasModifiedResidue))
					{
						GKInstance modifiedResidue = (GKInstance) modifiedResidueObj;
						GKInstance infModifiedResidue = GenerateInstance.newInferredGKInstance(modifiedResidue);
						infModifiedResidue.addAttributeValue(ReactomeJavaConstants.referenceSequence, infReferenceGeneProduct);
						for (Object coordinate : modifiedResidue.getAttributeValuesList(ReactomeJavaConstants.coordinate))
						{
							infModifiedResidue.addAttributeValue(ReactomeJavaConstants.coordinate, coordinate);
						}
						if (infModifiedResidue.getSchemClass().isValidAttribute(ReactomeJavaConstants.modification))
						{
							for (Object modified : modifiedResidue.getAttributeValuesList(ReactomeJavaConstants.modification))
							{
								infModifiedResidue.addAttributeValue(ReactomeJavaConstants.modification, modified);
							}
							
						}
						GKInstance psiMod = (GKInstance) modifiedResidue.getAttributeValue(ReactomeJavaConstants.psiMod);
						if (phosFlag && psiMod.getAttributeValue(ReactomeJavaConstants.name).toString().contains("phospho"))
						{
							String phosphoName = "phospho-" + infEWAS.getAttributeValue(ReactomeJavaConstants.name);
							@SuppressWarnings("unchecked")
							ArrayList<GKInstance> nameValues = (ArrayList<GKInstance>) infEWAS.getAttributeValuesList(ReactomeJavaConstants.name);
							nameValues.remove(0);
							infEWAS.setAttributeValue(ReactomeJavaConstants.name, phosphoName);
							infEWAS.addAttributeValue(ReactomeJavaConstants.name, nameValues); // In the Perl code, the name attribute is just the phospho-modified value. The identifier (homologueId/$inf_id) is dropped.
							phosFlag = false;
						}
						if (modifiedResidue.getAttributeValue(ReactomeJavaConstants.coordinate) != null)
						{
							// TODO: Abstract this out so that its 'fromSpecies';
							String newDisplayName = modifiedResidue.getAttributeValue(ReactomeJavaConstants._displayName).toString() + " (in Homo sapiens)";
							infModifiedResidue.addAttributeValue(ReactomeJavaConstants._displayName, newDisplayName);
						}
						for (Object psiModInst : modifiedResidue.getAttributeValuesList(ReactomeJavaConstants.psiMod))
						{
							infModifiedResidue.addAttributeValue(ReactomeJavaConstants.psiMod, (GKInstance) psiModInst);
						}
						infModifiedResidue = GenerateInstance.checkForIdenticalInstances(infModifiedResidue);
						infModifiedResidues.add((GKInstance) infModifiedResidue);
					}
					infEWAS.addAttributeValue(ReactomeJavaConstants.hasModifiedResidue, infModifiedResidues);
				
					infEWAS = GenerateInstance.checkForIdenticalInstances(infEWAS);
					if (GenerateInstance.addAttributeValueIfNeccesary(infEWAS, ewasInst, ReactomeJavaConstants.inferredFrom))
					{
						infEWAS.addAttributeValue(ReactomeJavaConstants.inferredFrom, ewasInst);
					}
					dba.updateInstanceAttribute(infEWAS, ReactomeJavaConstants.inferredFrom);
					if (GenerateInstance.addAttributeValueIfNeccesary(ewasInst, infEWAS, ReactomeJavaConstants.inferredTo))
					{
						ewasInst.addAttributeValue(ReactomeJavaConstants.inferredTo, infEWAS);
					}
					dba.updateInstanceAttribute(ewasInst, ReactomeJavaConstants.inferredTo);
					infEWASInstances.add((GKInstance) infEWAS);
				}
			}
		return infEWASInstances;
	}

/// Creates ReferenceGeneSequence instance based on ENSG identifier mapped to protein
	public static ArrayList<GKInstance> createReferenceDNASequence(String homologueId) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		ArrayList<GKInstance> referenceDNAInstances = new ArrayList<GKInstance>();
		ArrayList<String> ensgs = ensgMappings.get(homologueId);
		// TODO: Nullcheck?
		for (Object ensg : ensgs)
		{
			SchemaClass referenceDNAClass = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence);
			GKInstance referenceDNAInst = new GKInstance(referenceDNAClass);
			referenceDNAInst.setDbAdaptor(dba);
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.identifier, ensg);
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.referenceDatabase, ensgDbInst);
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
			referenceDNAInst = GenerateInstance.checkForIdenticalInstances(referenceDNAInst);
			referenceDNAInstances.add(referenceDNAInst);
			if (refDb)
			{
				//TODO: Logic for alternate id --> alt_id (arabidopsis)
				GKInstance alternateRefDNAInst = new GKInstance(referenceDNAClass);
				alternateRefDNAInst.setDbAdaptor(dba);
				alternateRefDNAInst.addAttributeValue(ReactomeJavaConstants.identifier, ensg);
				alternateRefDNAInst.addAttributeValue(ReactomeJavaConstants.referenceDatabase, alternateDbInst);
				alternateRefDNAInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
				alternateRefDNAInst = GenerateInstance.checkForIdenticalInstances(alternateRefDNAInst);
				referenceDNAInstances.add(alternateRefDNAInst);
			}
		}
		return referenceDNAInstances;
	}
	
//// These are setup functions called at the beginning of the script
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		InferEWAS.dba = dbAdaptor;
	}
	// Sets the HashMap of species-specific homologue-identifier mappings
	public static void setHomologueMappingFile(HashMap<String, String[]> homologueMappingsCopy) throws IOException
	{
		homologueMappings = homologueMappingsCopy;
	}
	
	// Read the species-specific ENSG gene-protein mappings, and create a Hashmap with the contents
	public static void readENSGMappingFile(String toSpecies) throws IOException
	{
		String mappingFileName = toSpecies + "_gene_protein_mapping.txt";
		String mappingFilePath = "src/main/resources/orthopairs/" + mappingFileName;
		FileReader fr = new FileReader(mappingFilePath);
		BufferedReader br = new BufferedReader(fr);

		String currentLine;
		while ((currentLine = br.readLine()) != null)
		{
			String[] tabSplit = currentLine.split("\t");
			String ensgKey = tabSplit[0];
			String[] spaceSplit = tabSplit[1].split(" ");
			for (String proteinId : spaceSplit) {
				String[] colonSplit = proteinId.split(":");
				if (ensgMappings.get(colonSplit[1]) == null)
				{
					ArrayList<String> singleArray = new ArrayList<String>();
					singleArray.add(ensgKey);
					ensgMappings.put(colonSplit[1], singleArray);
				} else {
					ensgMappings.get(colonSplit[1]).add(ensgKey);
				}
			}
		}
		br.close();
		fr.close();
	}
	
	// Fetches Uniprot DB instance
	@SuppressWarnings("unchecked")
	public static void createUniprotDbInst() throws Exception
	{
		 Collection<GKInstance> uniprotDbInstances = (Collection<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", "UniProt");
		 uniprotDbInst = uniprotDbInstances.iterator().next();
	}
	
	// Creates instance pertaining to the species Ensembl Protein DB
	public static void createEnsemblProteinDbInst(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnspAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String enspSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_PROTEIN";
		SchemaClass referenceDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		enspDbInst = new GKInstance(referenceDb);
		enspDbInst.setDbAdaptor(dba);
//		enspDbInst.addAttributeValue(ReactomeJavaConstants.name, "ENSEMBL"); // Commented out because the generic 'ENSEMBL' messes up the identical instance check
		enspDbInst.addAttributeValue(ReactomeJavaConstants.name, enspSpeciesDb);
		enspDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesReferenceDbUrl);
		enspDbInst.addAttributeValue(ReactomeJavaConstants.accessUrl, toSpeciesEnspAccessUrl);
		enspDbInst = GenerateInstance.checkForIdenticalInstances(enspDbInst);
	}
	
	// Creates instance pertaining to the species Ensembl Gene DB
	public static void createEnsemblGeneDBInst(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnsgAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_GENE";
		SchemaClass referenceDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		ensgDbInst = new GKInstance(referenceDb);
		ensgDbInst.setDbAdaptor(dba);
//		ensgDbInst.addAttributeValue(ReactomeJavaConstants.name, "ENSEMBL"); // Commented out because the generic 'ENSEMBL' messes up the identical instance check
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.name, ensgSpeciesDb);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesReferenceDbUrl);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.accessUrl, toSpeciesEnsgAccessUrl);
		ensgDbInst = GenerateInstance.checkForIdenticalInstances(ensgDbInst);
	}
	
	// Create instance pertaining to any alternative reference DB for the species
	public static void createAlternateReferenceDBInst(String toSpeciesLong, String alternateDbName, String toSpeciesAlternateDbUrl, String toSpeciesAlternateAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		SchemaClass alternateDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		alternateDbInst = new GKInstance(alternateDb);
		alternateDbInst.setDbAdaptor(dba);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.name, alternateDbName);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesAlternateDbUrl);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.accessUrl, toSpeciesAlternateAccessUrl);
		alternateDbInst = GenerateInstance.checkForIdenticalInstances(alternateDbInst);
		refDb = true;
	}
	
	// Sets the species instance for inferEWAS to use
	public static void setSpeciesInst(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
}
