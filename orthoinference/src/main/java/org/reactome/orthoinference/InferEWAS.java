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
	
	public static MySQLAdaptor dba;
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}

	public static HashMap<String, String[]> homologues = new HashMap<String,String[]>();
	public static HashMap<String, ArrayList> ensgMappings = new HashMap<String,ArrayList>();
	public static GKInstance ensgDb = null;
	
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
				homologues.put(mapKey, spaceSplit);
			}
			br.close();
			fr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Creates instance pertaining to the species ENSG DB
	public void createEnsemblGeneDB(String toSpeciesLong, String toSpeciesRefDbUrl, String toSpeciesEnsgAccessUrl)
	{
		try 
		{
		String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_GENE";
		SchemaClass referenceDb = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
		ensgDb = new GKInstance(referenceDb);
		ensgDb.addAttributeValue(ReactomeJavaConstants.name, "ENSEMBL");
		ensgDb.addAttributeValue(ReactomeJavaConstants.name, ensgSpeciesDb);
		ensgDb.addAttributeValue(ReactomeJavaConstants.url, toSpeciesRefDbUrl);
		ensgDb.addAttributeValue(ReactomeJavaConstants.url, toSpeciesEnsgAccessUrl);
		//TODO: Check for identical instances function
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
						ArrayList singleArray = new ArrayList();
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
	
	// Creates an inferred EWAS instance
	public void inferEWAS(GKInstance attrInf)
	{
		try {
		GenerateInstance createInferredInstance = new GenerateInstance();
		
		String refEntityId = ((GKInstance) attrInf.getAttributeValue("referenceEntity")).getAttributeValue("identifier").toString();
		
		if (homologues.get(refEntityId) != null)
			{
				// Iterate through the array of values, creating EWAS inferred instances
				for (Object homologue : homologues.get(refEntityId))
				{
					String[] splitHomologue = homologue.toString().split(":");
					String homologueSource = splitHomologue[0];
					String homologueId = splitHomologue[1];
					
					Instance infRefGeneProd = createInferredInstance.newInferredInstance((GKInstance) attrInf.getAttributeValue("referenceEntity"));
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
		ArrayList ensgs = ensgMappings.get(homologueId);
		for (Object ensg : ensgs)
		{
			try {
			SchemaClass refDNAClass = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence);
			GKInstance refDNAInst = new GKInstance(refDNAClass);
			
			refDNAInst.addAttributeValue(ReactomeJavaConstants.identifier, ensg);
			refDNAInst.addAttributeValue(ReactomeJavaConstants.referenceDatabase, ensgDb);
			System.out.println(refDNAInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
