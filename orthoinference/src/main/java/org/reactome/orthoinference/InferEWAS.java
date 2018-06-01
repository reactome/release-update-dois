package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
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
	
	// Creates instance pertaining to the species ENSG DB
	public void createEnsemblGeneDBInst(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnsgAccessUrl)
	{
		try 
		{
		String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_GENE";
		SchemaClass referenceDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		ensgDbInst = new GKInstance(referenceDb);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.name, "ENSEMBL");
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.name, ensgSpeciesDb);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesReferenceDbUrl);
		ensgDbInst.addAttributeValue(ReactomeJavaConstants.url, toSpeciesEnsgAccessUrl);
		//TODO: Check for identical instances function
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Creates an inferred EWAS instance
	public void inferEWAS(GKInstance infAttributeInst)
	{
		try {
		GenerateInstance createInferredInstance = new GenerateInstance();
		
		String referenceEntityId = ((GKInstance) infAttributeInst.getAttributeValue("referenceEntity")).getAttributeValue("identifier").toString();
		
		if (homologueMappings.get(referenceEntityId) != null)
			{
			System.out.println("Hello");
				// Iterate through the array of values, creating EWAS inferred instances
				for (Object homologue : homologueMappings.get(referenceEntityId))
				{
					String[] splitHomologue = homologue.toString().split(":");
					String homologueSource = splitHomologue[0];
					String homologueId = splitHomologue[1];
					
					Instance infReferenceGeneProduct = createInferredInstance.newInferredInstance((GKInstance) infAttributeInst.getAttributeValue("referenceEntity"));
					InferEWAS.createReferenceDNASequence(homologueId);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Creates ReferenceGeneSequence instance based on ENSG identifier mapped to protein
	public static void createReferenceDNASequence(String homologueId)
	{
		ArrayList<String> ensgs = ensgMappings.get(homologueId);
		for (Object ensg : ensgs)
		{
			try {
			SchemaClass referenceDNAClass = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence);
			GKInstance referenceDNAInst = new GKInstance(referenceDNAClass);
			
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.identifier, ensg);
			referenceDNAInst.addAttributeValue(ReactomeJavaConstants.referenceDatabase, ensgDbInst);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
