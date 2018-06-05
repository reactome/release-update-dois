package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

public class InferEWAS {
	
	private static MySQLAdaptor dba;
	
	public void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		InferEWAS.dba = dbAdaptor;
	}

	private static HashMap<String, String[]> homologueMappings = new HashMap<String,String[]>();
	private static HashMap<String, ArrayList<String>> ensgMappings = new HashMap<String,ArrayList<String>>();
	private static GKInstance ensgDbInst = null;
	private static GKInstance enspDbInst = null;
	private static GKInstance alternateDbInst = null;
	private static GKInstance uniprotDbInst = null;
	static boolean refDb = false;
	private static GKInstance speciesInst = null;
	
	//TODO: Add parent function that organizes the EWAS setup
	//TODO: Create uniprot and ensemble reference database variables for EWAS setup
	// Read the species-specific orthopairs file, and create a HashMap with the contents
	public void readMappingFile(String toSpecies, String fromSpecies)
	{
		String mappingFileName = fromSpecies + "_" + toSpecies + "_mapping.txt";
		String mappingFilePath = "src/main/resources/orthopairs/" + mappingFileName;
		try {
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Fetches Uniprot DB instance
	@SuppressWarnings("unchecked")
	public void createUniprotDbInst()
	{
		try
		{
		 Collection<GKInstance> uniprotDbInstances = (Collection<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", "UniProt");
		 uniprotDbInst = uniprotDbInstances.iterator().next();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Read the species-specific ENSG gene-protein mappings, and create a Hashmap with the contents
	public void readENSGMappingFile(String toSpecies)
	{
		String mappingFileName = toSpecies + "_gene_protein_mapping.txt";
		String mappingFilePath = "src/main/resources/orthopairs/" + mappingFileName;
		try {
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
//					ensgMappings.get(colonSplit[1]).add(ensgKey);
				}
			}
			br.close();
			fr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Creates instance pertaining to the species Ensembl Protein DB
	public void createEnsemblProteinDbInst(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnspAccessUrl)
	{
		try
		{
		String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_PROTEIN";
		SchemaClass referenceDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		enspDbInst = new GKInstance(referenceDb);
		enspDbInst.addAttributeValue(ReactomeJavaConstants.name, "Ensembl");
		enspDbInst.addAttributeValue(ReactomeJavaConstants.name, ensgSpeciesDb);
		enspDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesReferenceDbUrl);
		enspDbInst.addAttributeValue(ReactomeJavaConstants.accessUrl, toSpeciesEnspAccessUrl);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Creates instance pertaining to the species Ensembl Gene DB
	public void createEnsemblGeneDBInst(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnsgAccessUrl)
	{
		try 
		{
		// TODO: How to store multiple values in same attribute eg: name below, start/end coord name during ewas inference
		String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_GENE";
		SchemaClass referenceDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		ensgDbInst = new GKInstance(referenceDb);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.name, "ENSEMBL");
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.name, ensgSpeciesDb);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesReferenceDbUrl);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.accessUrl, toSpeciesEnsgAccessUrl);
		//TODO: Check for identical instances function
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Create instance pertaining to any alternative reference DB for the species
	public void createAlternateReferenceDBInst(String toSpeciesLong, String alternateDbName, String toSpeciesAlternateDbUrl, String toSpeciesAlternateAccessUrl)
	{
		try
		{
		SchemaClass alternateDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		alternateDbInst = new GKInstance(alternateDb);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.name, alternateDbName);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesAlternateDbUrl);
		alternateDbInst.addAttributeValue(ReactomeJavaConstants.accessUrl, toSpeciesAlternateAccessUrl);
		refDb = true;
		//TODO: Check for identical instances
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	// Sets the species instance for inferEWAS to use
	public void setSpeciesInst(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
	
	// Creates an inferred EWAS instance
	public ArrayList<GKInstance> inferEWAS(GKInstance infAttributeInst)
	{
		ArrayList<GKInstance> infEWASInstances = new ArrayList<GKInstance>();
		try {
		GenerateInstance createInferredInstance = new GenerateInstance();
		
		String referenceEntityId = ((GKInstance) infAttributeInst.getAttributeValue("referenceEntity")).getAttributeValue("identifier").toString();
		
		if (homologueMappings.get(referenceEntityId) != null)
			{
				// Iterate through the array of values, creating EWAS inferred instances
				for (Object homologue : homologueMappings.get(referenceEntityId))
				{
					String[] splitHomologue = homologue.toString().split(":");
					String homologueSource = splitHomologue[0];
					String homologueId = splitHomologue[1];
					//TODO: Make sure returned array sizes and structure is as expected
					// Creating inferred reference gene product
					Instance infReferenceGeneProduct = createInferredInstance.newInferredInstance((GKInstance) infAttributeInst.getAttributeValue("referenceEntity"));
					GKInstance referenceDb = null;
					if (homologueSource.equals("ENSP"))
					{
						referenceDb = enspDbInst;
					} else {
						referenceDb = uniprotDbInst;
					}
					infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.referenceDatabase,  referenceDb);
					infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.identifier, homologueId);
					// Equivalent of create_ReferenceDNASequence function in infer_events.pl
					ArrayList<GKInstance> inferredReferenceDNAInstances = InferEWAS.createReferenceDNASequence(homologueId);
					infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.referenceGene, inferredReferenceDNAInstances);
					infReferenceGeneProduct.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
					//TODO: check for identical instances, add to hash so that repeats don't happen
					
					// Creating inferred EWAS 
					Instance infEWAS = createInferredInstance.newInferredInstance(infAttributeInst); 
					infEWAS.addAttributeValue(ReactomeJavaConstants.referenceEntity, infReferenceGeneProduct);
					infEWAS.addAttributeValue(ReactomeJavaConstants.name, homologueId);
					infEWAS.addAttributeValue(ReactomeJavaConstants.startCoordinate, infAttributeInst.getAttributeValue("startCoordinate"));
					infEWAS.addAttributeValue(ReactomeJavaConstants.endCoordinate, infAttributeInst.getAttributeValue("endCoordinate"));
//					try {
//						Integer startCoord = Integer.valueOf(infEWAS.getAttributeValue("startCoordinate").toString());
//						Integer endCoord = Integer.valueOf(infEWAS.getAttributeValue("endCoordinate").toString());
//						if (startCoord > 1 || endCoord > 1) {
//							
//						}
//					} catch (NullPointerException e) {
//					}
					//TODO: Required resolving array values for attributes
//					if ((startCoord != null && Integer.valueOf(startCoord) > 1) || (endCoord != null && Integer.valueOf(endCoord) > 1))
//					{
//						System.out.println("Yay");
//					} else {
//						System.out.println("Boo");
//					}

					@SuppressWarnings("unchecked")
					ArrayList<GKInstance> modifiedResidues = ((ArrayList<GKInstance>) infAttributeInst.getAttributeValuesList("hasModifiedResidue"));
					ArrayList<GKInstance> infModifiedResidues = new ArrayList<GKInstance>();
					boolean phosFlag = true;
					for (GKInstance modifiedResidue : modifiedResidues)
					{
						Instance infModifiedResidue = createInferredInstance.newInferredInstance((GKInstance) modifiedResidue);
						//TODO: Array fix
						infModifiedResidue.addAttributeValue(ReactomeJavaConstants.coordinate, modifiedResidue.getAttributeValue("coordinate"));
						infModifiedResidue.addAttributeValue(ReactomeJavaConstants.referenceSequence, infReferenceGeneProduct);
						//TODO: is valid attribute and array fix
						GKInstance psiMod = (GKInstance) modifiedResidue.getAttributeValue("psiMod");
						if (phosFlag && psiMod.getAttributeValue("name").toString().contains("phospho"))
						{
							//TODO: This function gets the above start/end coordinate name, and then replaces name with only it
							String phosphoName = "phospho-" + infEWAS.getAttributeValue("name").toString();
							infEWAS.addAttributeValue(ReactomeJavaConstants.name, phosphoName);
							phosFlag = false;
							if (modifiedResidue.getAttributeValue("coordinate") != null)
							{
								// Abstract this out so that its 'fromSpecies'
								String newDisplayName = modifiedResidue.getAttributeValue("_displayName").toString() + " (in Homo sapiens)";
								infModifiedResidue.addAttributeValue(ReactomeJavaConstants._displayName, newDisplayName);
							}
							// TODO: Array fix and check for identical instances
							infModifiedResidue.addAttributeValue(ReactomeJavaConstants.psiMod, modifiedResidue.getAttributeValue("psiMod"));
							infModifiedResidues.add((GKInstance) infModifiedResidue);	
						}
					}
					infEWAS.addAttributeValue(ReactomeJavaConstants.hasModifiedResidue, infModifiedResidues);
					//TODO: Check for identical instances
					//TODO: addAttributesValueIfNecessary, inferredTo & inferredFrom, updateAttribute (delay if possible)
					infEWASInstances.add((GKInstance) infEWAS);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return infEWASInstances;
	}
	
	// Creates ReferenceGeneSequence instance based on ENSG identifier mapped to protein
	//TODO: Check for identical instances
	public static ArrayList<GKInstance> createReferenceDNASequence(String homologueId)
	{
		ArrayList<GKInstance> referenceDNAInstances = new ArrayList<GKInstance>();
		ArrayList<String> ensgs = ensgMappings.get(homologueId);
		
		for (Object ensg : ensgs)
		{
			try {
			SchemaClass referenceDNAClass = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence);
			GKInstance referenceDNAInst = new GKInstance(referenceDNAClass);
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.identifier, ensg);
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.referenceDatabase, ensgDbInst);
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
			referenceDNAInstances.add(referenceDNAInst);
			//TODO: Check for identical instances
			if (refDb)
			{
				GKInstance alternateRefDNAInst = new GKInstance(referenceDNAClass);
				alternateRefDNAInst.addAttributeValue(ReactomeJavaConstants.identifier, ensg);
				alternateRefDNAInst.addAttributeValue(ReactomeJavaConstants.referenceDatabase, alternateDbInst);
				alternateRefDNAInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
				referenceDNAInstances.add(alternateRefDNAInst);	
			}
			//TODO: Logic for alt_refdb --> alt_id (arabidopsis)
			//TODO: Check for identical instances
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return referenceDNAInstances;
	}
}
