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
	
	public void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		InferEWAS.dba = dbAdaptor;
	}

	private static HashMap<String, String[]> homologueMappings = new HashMap<String,String[]>();
	private static HashMap<String, GKInstance> seenRPS = new HashMap<String,GKInstance>();
	private static HashMap<String, ArrayList<String>> ensgMappings = new HashMap<String,ArrayList<String>>();
	private static GKInstance ensgDbInst = null;
	private static GKInstance enspDbInst = null;
	private static GKInstance alternateDbInst = null;
	private static GKInstance uniprotDbInst = null;
	//TODO: Remove static value when scaling up species total
	static boolean refDb = false;
	private static GKInstance speciesInst = null;
	
	//TODO: Add parent function that organizes the EWAS setup
	//TODO: Create uniprot and ensemble reference database variables for EWAS setup
	// Read the species-specific orthopairs file, and create a HashMap with the contents
	public void readMappingFile(String toSpecies, String fromSpecies) throws IOException
	{
		String mappingFileName = fromSpecies + "_" + toSpecies + "_mapping.txt";
		String mappingFilePath = "src/main/resources/orthopairs/" + mappingFileName;
		FileReader fr = new FileReader(mappingFilePath);
		BufferedReader br = new BufferedReader(fr);
		
		String currentLine;
		while ((currentLine = br.readLine()) != null)
		{
			String[] tabSplit = currentLine.split("\t");
			String mapKey = tabSplit[0];
			String[] spaceSplit = tabSplit[1].split(" ");
			InferEWAS.homologueMappings.put(mapKey, spaceSplit);
		}
		br.close();
		fr.close();
	}
	
	// Fetches Uniprot DB instance
	@SuppressWarnings("unchecked")
	public void createUniprotDbInst() throws Exception
	{
		 Collection<GKInstance> uniprotDbInstances = (Collection<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", "UniProt");
		 uniprotDbInst = uniprotDbInstances.iterator().next();
	}
	
	// Read the species-specific ENSG gene-protein mappings, and create a Hashmap with the contents
	public void readENSGMappingFile(String toSpecies) throws IOException
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
	
	// Creates instance pertaining to the species Ensembl Protein DB
	public void createEnsemblProteinDbInst(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnspAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
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
	public void createEnsemblGeneDBInst(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnsgAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
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
	public void createAlternateReferenceDBInst(String toSpeciesLong, String alternateDbName, String toSpeciesAlternateDbUrl, String toSpeciesAlternateAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		SchemaClass alternateDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		alternateDbInst = new GKInstance(alternateDb);
		alternateDbInst.setDbAdaptor(dba);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.name, alternateDbName);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesAlternateDbUrl);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.accessUrl, toSpeciesAlternateAccessUrl);
		alternateDbInst = GenerateInstance.checkForIdenticalInstances(alternateDbInst);
		refDb = true;
		//TODO: Check for identical instances
	}
	
	// Sets the species instance for inferEWAS to use
	public void setSpeciesInst(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
	
	// Creates an array inferred EWAS instances from the homologue mappings file (hsap_species_mapping.txt)
	public static ArrayList<GKInstance> inferEWAS(GKInstance infAttributeInst) throws InvalidAttributeException, Exception
	{
		ArrayList<GKInstance> infEWASInstances = new ArrayList<GKInstance>();
		String referenceEntityId = ((GKInstance) infAttributeInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
		//TODO: $opt_filt cutoff (??);
		if (homologueMappings.get(referenceEntityId) != null)
			{
				// Iterate through the array of homologue mappings, creating inferred EWAS instances
				for (Object homologue : homologueMappings.get(referenceEntityId))
				{
					String[] splitHomologue = homologue.toString().split(":");
					String homologueSource = splitHomologue[0];
					String homologueId = splitHomologue[1];
					GKInstance infReferenceGeneProduct = GenerateInstance.newInferredGKInstance((GKInstance) infAttributeInst.getAttributeValue(ReactomeJavaConstants.referenceEntity));
					if (seenRPS.get(homologueId) == null)
					{
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.identifier, homologueId);
						GKInstance referenceDb = null;
						if (homologueSource.equals("ENSP"))
						{
							referenceDb = enspDbInst;
						} else {
							referenceDb = uniprotDbInst;
						}
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.referenceDatabase,  referenceDb);
						// Creating inferred reference gene product
						// Equivalent of create_ReferenceDNASequence function in infer_events.pl
						ArrayList<GKInstance> inferredReferenceDNAInstances = InferEWAS.createReferenceDNASequence(homologueId);
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.referenceGene, inferredReferenceDNAInstances);
						infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
						infReferenceGeneProduct = GenerateInstance.checkForIdenticalInstances(infReferenceGeneProduct);
//						System.out.println(infReferenceGeneProduct.getAttributeValue("DB_ID"));
						seenRPS.put(homologueId, infReferenceGeneProduct);
					} else {
						infReferenceGeneProduct = seenRPS.get(homologueId);
					}
					
					// Creating inferred EWAS 
					GKInstance infEWAS = GenerateInstance.newInferredGKInstance(infAttributeInst); 
					infEWAS.addAttributeValue(ReactomeJavaConstants.referenceEntity, infReferenceGeneProduct);									
					infEWAS.addAttributeValue(ReactomeJavaConstants.startCoordinate, infAttributeInst.getAttributeValue(ReactomeJavaConstants.startCoordinate));
					infEWAS.addAttributeValue(ReactomeJavaConstants.endCoordinate, infAttributeInst.getAttributeValue(ReactomeJavaConstants.endCoordinate));
					Integer startCoord = Integer.valueOf(infEWAS.getAttributeValue(ReactomeJavaConstants.startCoordinate).toString());
					Integer endCoord = Integer.valueOf(infEWAS.getAttributeValue(ReactomeJavaConstants.endCoordinate).toString());
					if (startCoord > 1 || endCoord > 1) {
						@SuppressWarnings("unchecked")
						ArrayList<String> infAttributeInstNames = (ArrayList<String>) ((GKInstance) infAttributeInst).getAttributeValuesList(ReactomeJavaConstants.name);
						infEWAS.addAttributeValue(ReactomeJavaConstants.name, infAttributeInstNames.get(0));
						infEWAS.addAttributeValue(ReactomeJavaConstants.name, homologueId);
					} else {
						infEWAS.addAttributeValue(ReactomeJavaConstants.name, homologueId);
					}
						
					// Infer residue modifications
					@SuppressWarnings("unchecked")
					ArrayList<GKInstance> modifiedResidues = ((ArrayList<GKInstance>) infAttributeInst.getAttributeValuesList(ReactomeJavaConstants.hasModifiedResidue));
					ArrayList<GKInstance> infModifiedResidues = new ArrayList<GKInstance>();
					boolean phosFlag = true;
					for (GKInstance modifiedResidue : modifiedResidues)
					{
						GKInstance infModifiedResidue = GenerateInstance.newInferredGKInstance((GKInstance) modifiedResidue);
						// TODO: Coordinate and modification arrays need to be accounted for
						infModifiedResidue.addAttributeValue(ReactomeJavaConstants.coordinate, modifiedResidue.getAttributeValue(ReactomeJavaConstants.coordinate));
						infModifiedResidue.addAttributeValue(ReactomeJavaConstants.referenceSequence, infReferenceGeneProduct);
						if (infModifiedResidue.getSchemClass().isValidAttribute(ReactomeJavaConstants.modification))
						{
							infModifiedResidue.addAttributeValue(ReactomeJavaConstants.modification, modifiedResidue.getAttributeValue(ReactomeJavaConstants.modification));
						}
						GKInstance psiMod = (GKInstance) modifiedResidue.getAttributeValue(ReactomeJavaConstants.psiMod);
						if (phosFlag && psiMod.getAttributeValue(ReactomeJavaConstants.name).toString().contains("phospho"))
						{
							//TODO: This function gets the above start/end coordinate name, and then replaces name with only it
							String phosphoName = "phospho-" + infEWAS.getAttributeValue(ReactomeJavaConstants.name);
							@SuppressWarnings("unchecked")
							ArrayList<GKInstance> nameValues = (ArrayList<GKInstance>) infEWAS.getAttributeValuesList(ReactomeJavaConstants.name);
							nameValues.remove(0);
							infEWAS.setAttributeValue(ReactomeJavaConstants.name, phosphoName);
							infEWAS.addAttributeValue(ReactomeJavaConstants.name, nameValues);
							phosFlag = false;
						}
						if (modifiedResidue.getAttributeValue(ReactomeJavaConstants.coordinate) != null)
						{
							// TODO: Abstract this out so that its 'fromSpecies'; Perl version sees _displayName updated the displayName attribute as well
							String newDisplayName = modifiedResidue.getAttributeValue(ReactomeJavaConstants._displayName).toString() + " (in Homo sapiens)";
							infModifiedResidue.addAttributeValue(ReactomeJavaConstants._displayName, newDisplayName);
						}
						// TODO: add "residue"; check for identical instances
						infModifiedResidue.addAttributeValue(ReactomeJavaConstants.psiMod, modifiedResidue.getAttributeValue(ReactomeJavaConstants.psiMod));
						infModifiedResidue = GenerateInstance.checkForIdenticalInstances(infModifiedResidue);
						infModifiedResidues.add((GKInstance) infModifiedResidue);		
					}
					infEWAS.addAttributeValue(ReactomeJavaConstants.hasModifiedResidue, infModifiedResidues);
					infEWAS = GenerateInstance.checkForIdenticalInstances(infEWAS);
					//TODO: addAttributesValueIfNecessary -- inferredTo/inferredFrom; updateAttribute
					infEWASInstances.add((GKInstance) infEWAS);
				}
			} //TODO: Empty homologue check (the else to this brackets if)
		return infEWASInstances;
	}
	
	// Creates ReferenceGeneSequence instance based on ENSG identifier mapped to protein
	public static ArrayList<GKInstance> createReferenceDNASequence(String homologueId) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		ArrayList<GKInstance> referenceDNAInstances = new ArrayList<GKInstance>();
		ArrayList<String> ensgs = ensgMappings.get(homologueId);
		
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
}
