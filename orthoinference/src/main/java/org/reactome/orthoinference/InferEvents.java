package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

/**
 * 
 * @author jcook
 *
 */

public class InferEvents 
{
//	private static final String SchemaClass = null;
	
	static MySQLAdaptor dbAdaptor = null;
	private static HashMap<String, String[]> homologueMappings = new HashMap<String,String[]>();
	private static GKInstance speciesInst = null;
	static boolean refDb = true;
	
	@SuppressWarnings("unchecked")
	public static void main(String args[]) throws Exception
	{
		String pathToConfig = "src/main/resources/config.properties";
		
		if (args.length > 0 && !args[0].equals(""))
		{
			pathToConfig = args[0];
		}
		
		PrintWriter eligibleFile = new PrintWriter("eligible_ddis_75.txt");
		eligibleFile.close();
		
		InferReaction.setEligibleFilename("eligible_ddis_75.txt");
		
		Collection<GKInstance> sourceSpeciesInst;
		
			Properties props = new Properties();
			props.load(new FileInputStream(pathToConfig));
			
			//TODO: Create config equivalent for species as seen in Config_Species.pm; Parameterize all input values in properties file
//			ArrayList<String> speciesToInferTo = new ArrayList<String>(Arrays.asList("ddis"));
//			String speciesToInferFromShort = "hsap";
			Object speciesToInferFromLong = "Homo sapiens";
			String username = props.getProperty("username");
			String password = props.getProperty("password");
			String database = props.getProperty("database");
			String host = props.getProperty("host");
			int port = Integer.valueOf(props.getProperty("port"));

			// Set-Up
			//TODO: 'Setup' function -- Organize by class
			dbAdaptor = new MySQLAdaptor(host, database, username, password, port);	
			InferReaction.setAdaptor(dbAdaptor);
			SkipTests.setAdaptor(dbAdaptor);
			GenerateInstance.setAdaptor(dbAdaptor);
			OrthologousEntity.setAdaptor(dbAdaptor);
			InferEWAS.setAdaptor(dbAdaptor);
			
			InferEvents.readHomologueMappingFile("ddis", "hsap");
			ProteinCount.setHomologueMappingFile(homologueMappings);
			InferEWAS.setHomologueMappingFile(homologueMappings);
			InferEWAS.readENSGMappingFile("ddis");
			InferEWAS.createUniprotDbInst();
			InferEWAS.createEnsemblProteinDbInst("Dictyostelium discoideum", "http://protists.ensembl.org/Dictyostelium_discoideum/Info/Index", "http://protists.ensembl.org/Dictyostelium_discoideum/Transcript/ProteinSummary?peptide=###ID###");
			InferEWAS.createEnsemblGeneDBInst("Dictyostelium discoideum", "http://protists.ensembl.org/Dictyostelium_discoideum/Info/Index", "http://protists.ensembl.org/Dictyostelium_discoideum/geneview?gene=###ID###&db=core");
			if (refDb)
			{
				InferEWAS.createAlternateReferenceDBInst("Dictyostelium discoideum", "dictyBase", "http://www.dictybase.org/", "http://dictybase.org/db/cgi-bin/search/search.pl?query=###ID###");
			}
			
			InferEvents.createSpeciesInst("Dictyostelium discoideum");
			OrthologousEntity.setSpeciesInst(speciesInst);
			InferEWAS.setSpeciesInst(speciesInst);
			GenerateInstance.setSpeciesInst(speciesInst);
			
			InferReaction.setEvidenceTypeInst();
			InferReaction.setSummationInst();
			OrthologousEntity.setComplexSummationInst();
			
			SkipTests.getSkipList("normal_event_skip_list.txt");
			
			// TODO: load_class_attribute_values_of_multiple_instances for reactions and ewas'
			// Get DB instances of source species
			sourceSpeciesInst = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("Species", "name", "=", speciesToInferFromLong);
			if (!sourceSpeciesInst.isEmpty())
			{
				String dbId = null;
				for (GKInstance speciesInst : sourceSpeciesInst) 
				{
					dbId = speciesInst.getAttributeValue("DB_ID").toString();
					
				}
				// Gets Reaction instances of source species
				Collection<GKInstance> reactionInstances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("ReactionlikeEvent", "species", "=", dbId);
				if (!reactionInstances.isEmpty()) // Output error message if it is empty
				{
					for (GKInstance reactionInst : reactionInstances)
					{
						InferReaction.inferEvent(reactionInst);
					}
				}
			}	
	}

	// Read the species-specific orthopairs file, and create a HashMap with the contents
	public static void readHomologueMappingFile(String toSpecies, String fromSpecies) throws IOException
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
			homologueMappings.put(mapKey, spaceSplit);
		}
		br.close();
		fr.close();
	}
	
	public static void createSpeciesInst(String toSpeciesLong) throws Exception
	{
		SchemaClass referenceDb = dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.Species);
		speciesInst = new GKInstance(referenceDb);
		speciesInst.setDbAdaptor(dbAdaptor);
		speciesInst.addAttributeValue(ReactomeJavaConstants.name, toSpeciesLong);
		speciesInst = GenerateInstance.checkForIdenticalInstances(speciesInst);
	}
}
