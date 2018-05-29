package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;

import org.gk.model.GKInstance;
import org.gk.model.Instance;

public class inferEWAS {

	public static HashMap<String, String[]> homologues = new HashMap<String,String[]>();
	
	//TODO: Add parent function that organizes the EWAS setup
	//TODO: Create uniprot and ensemble reference database variables for EWAS setup
	// Read the species-specific orthopairs file, and creates a HashMap with the contents
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
	
	// Creates an inferred EWAS instance
	public void inferEWAS(GKInstance attrInf)
	{
		try {
		createInferredInstance createInferredInstance = new createInferredInstance();
		
		String refEntityId = ((GKInstance) attrInf.getAttributeValue("referenceEntity")).getAttributeValue("identifier").toString();
		
		if (homologues.get(refEntityId) != null)
			{
				System.out.println(refEntityId);
				// Iterate through the array of values, creating EWAS inferred instances
				for (Object homologue : homologues.get(refEntityId))
				{
					String[] splitHomologue = homologue.toString().split(":");
					String homologueSource = splitHomologue[0];
					String homologueId = splitHomologue[1];
					
					Instance infRefGeneProd = createInferredInstance.newInferredInstance((GKInstance) attrInf.getAttributeValue("referenceEntity"));
					inferEWAS.createReferenceGeneProduct(homologueId);
				}
				System.exit(0);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void createReferenceGeneProduct(String homologueId)
	{
		System.out.println(homologueId);
	}
}
