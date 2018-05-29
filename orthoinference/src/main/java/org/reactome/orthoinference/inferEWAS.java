package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;

import org.gk.model.GKInstance;

public class inferEWAS {

	public static HashMap<String, ArrayList> homologues = new HashMap<String,ArrayList>();
	
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
				ArrayList<String> valueSplit = new ArrayList<String>();
				for (String spaceValue : spaceSplit)
				{
					String[] colonSplit = spaceValue.split(":");
					valueSplit.add(colonSplit[1]);
				}
				homologues.put(mapKey, valueSplit);
			}
			br.close();
			fr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void inferEWAS(GKInstance attrInf)
	{
		try {
		
		String refEntityId = ((GKInstance) attrInf.getAttributeValue("referenceEntity")).getAttributeValue("identifier").toString();
		
		if (homologues.get(refEntityId) != null)
			{
				System.out.println(refEntityId);
				for (Object homologue : homologues.get(refEntityId))
				{
					System.out.println("  " + homologue);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
