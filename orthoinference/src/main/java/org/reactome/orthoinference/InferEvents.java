package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * 
 * @author jcook
 *
 */

public class InferEvents 
{	
	static MySQLAdaptor dbAdaptor = null;
	private static GKInstance speciesInst = null;
	private static HashMap<GKInstance,GKInstance> manualEventToNonHumanSource = new HashMap<GKInstance,GKInstance>();
	private static ArrayList<GKInstance> manualHumanEvents = new ArrayList<GKInstance>();
	
	//TODO: instance edit initialization; GO_CellularComponent instance; Config_Species.pm; load_class_attribute_values_of_multiple_instances (complex/ewas)
	@SuppressWarnings("unchecked")
	public static void main(String args[]) throws Exception
	{
		String pathToConfig = "src/main/resources/config.properties";
		String pathToSpeciesConfig = "src/main/resources/Species.json";
		
		if (args.length > 0 && !args[0].equals(""))
		{
			pathToConfig = args[0];
		}
		
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
		
		Object speciesToInferFromLong = "Homo sapiens";
		String username = props.getProperty("username");
		String password = props.getProperty("password");
		String database = props.getProperty("database");
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));
		
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);	
		InferReaction.setAdaptor(dbAdaptor);
		SkipTests.setAdaptor(dbAdaptor);
		GenerateInstance.setAdaptor(dbAdaptor);
		OrthologousEntity.setAdaptor(dbAdaptor);
		InferEWAS.setAdaptor(dbAdaptor);
		UpdateHumanEvents.setAdaptor(dbAdaptor);
		
		SkipTests.getSkipList("normal_event_skip_list.txt");
		ArrayList<String> speciesList = new ArrayList<String>(Arrays.asList("pfal", "spom", "scer", "ddis", "cele", "sscr", "btau", "cfam", "mmus", "rnor", "ggal", "tgut", "xtro", "drer", "dmel", "atha", "osat"));
		
		JSONParser parser = new JSONParser();
		Object obj = parser.parse(new FileReader(pathToSpeciesConfig));
		JSONObject jsonObject = (JSONObject) obj;
		for (String species : speciesList)
		{
			JSONObject speciesObject = (JSONObject) jsonObject.get(species);
			JSONArray speciesNames = (JSONArray) speciesObject.get("name");
			String speciesName = (String) speciesNames.get(0);
			System.out.println("Beginning orthoinference of " + speciesName + ".");
			JSONObject refDb = (JSONObject) speciesObject.get("refdb");
			String refDbUrl = (String) refDb.get("url");
			String refDbProteinUrl = (String) refDb.get("access");
			String refDbGeneUrl = (String) refDb.get("ensg_access");
			
			JSONObject altRefDb = (JSONObject) speciesObject.get("alt_refdb");
		
			// Creates two files that a) list reactions that are eligible for inference and b) those that are successfully inferred
			String eligibleFilename = "eligible_" + species	+ "_75.txt";
			String inferredFilename = "inferred_" + species + "_75.txt";
			PrintWriter eligibleFile = new PrintWriter(eligibleFilename);
			PrintWriter inferredFile = new PrintWriter(inferredFilename);
			eligibleFile.close();
			inferredFile.close();
			InferReaction.setEligibleFilename(eligibleFilename);
			InferReaction.setInferredFilename(inferredFilename);
		
			// Set static variables (DB/Species Instances, mapping files) that will be repeatedly used 
			try {
				HashMap<String,String[]> homologueMappings = InferEvents.readHomologueMappingFile(species, "hsap");
				ProteinCount.setHomologueMappingFile(homologueMappings);
				InferEWAS.setHomologueMappingFile(homologueMappings);
				homologueMappings = new HashMap<String,String[]>();
			} catch (FileNotFoundException e) {
				System.out.println("Unable to locate " + speciesName +" mapping file: hsap_" + species + "_mapping.txt. Orthology prediction not possible.");
				continue;
			}
			InferEWAS.readENSGMappingFile(species);
			InferEWAS.createUniprotDbInst();
	
			InferEWAS.createEnsemblProteinDbInst(speciesName, refDbUrl, refDbProteinUrl);
			InferEWAS.createEnsemblGeneDBInst(speciesName, refDbUrl, refDbGeneUrl);
			if (altRefDb != null)
			{
				JSONArray altRefDbNames = (JSONArray) altRefDb.get("dbname");
				String altRefDbUrl = (String) altRefDb.get("url");
				String altRefDbAccess = (String) altRefDb.get("access");
				InferEWAS.createAlternateReferenceDBInst(speciesName, (String) altRefDbNames.get(0), altRefDbUrl, altRefDbAccess);
			} else {
				InferEWAS.updateRefDb();
			}
			InferEvents.createSpeciesInst(speciesName);
			OrthologousEntity.setSpeciesInst(speciesInst);
			InferEWAS.setSpeciesInst(speciesInst);
			GenerateInstance.setSpeciesInst(speciesInst);
			InferEvents.setSummationInst();
			InferEvents.setEvidenceTypeInst();
			OrthologousEntity.setComplexSummationInst();
		
		
		// Gets DB instances of source species
		Collection<GKInstance> sourceSpeciesInst = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("Species", "name", "=", speciesToInferFromLong);
		if (!sourceSpeciesInst.isEmpty())
		{
			String dbId = sourceSpeciesInst.iterator().next().getDBID().toString();
			// Gets Reaction instances of source species
			Collection<GKInstance> reactionInstances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("ReactionlikeEvent", "species", "=", dbId);
			if (!reactionInstances.isEmpty()) // TODO: Output error message if it is empty
			{
				for (GKInstance reactionInst : reactionInstances)
				{
					// Check if the current Reaction already exists for this species, that it is a valid instance (passes some filters), and that it doesnt have a Disease attribute. 
					// Adds to manualHumanEvents array if it passes conditions.
					ArrayList<GKInstance> previouslyInferredInstances = new ArrayList<GKInstance>();
					for (GKInstance orthoEventInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(ReactomeJavaConstants.orthologousEvent))
					{
						GKInstance reactionSpeciesInst = (GKInstance) orthoEventInst.getAttributeValue(ReactomeJavaConstants.species);
						if (reactionSpeciesInst.getDBID() == speciesInst.getDBID() && orthoEventInst.getAttributeValue(ReactomeJavaConstants.isChimeric) == null)
						{
							previouslyInferredInstances.add(orthoEventInst);
						}
					}
					for (GKInstance inferredFromInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(ReactomeJavaConstants.inferredFrom))
					{
						GKInstance reactionSpeciesInst = (GKInstance) inferredFromInst.getAttributeValue(ReactomeJavaConstants.species);
						if (reactionSpeciesInst.getDBID() == speciesInst.getDBID() && inferredFromInst.getAttributeValue(ReactomeJavaConstants.isChimeric) == null)
						{
							previouslyInferredInstances.add(inferredFromInst);
						}
					}
					if (previouslyInferredInstances.size() > 0)
					{
						GKInstance prevInfInst = previouslyInferredInstances.get(0);
						if (prevInfInst.getAttributeValue(ReactomeJavaConstants.disease) == null) 
						{
							manualEventToNonHumanSource.put(reactionInst, prevInfInst);
							manualHumanEvents.add(reactionInst);
						} else {
							System.out.println("Skipping building of hierarchy around pre-existing disease reaction " + prevInfInst);
						}
						continue;
					}
					// This Reaction doesn't already exist for this species, and an orthologous inference will be attempted.
					InferReaction.inferEvent(reactionInst);
				}
			}
		}
		UpdateHumanEvents.setInferredEvent(InferReaction.getInferredEvent());
		UpdateHumanEvents.updateHumanEvents(InferReaction.getInferrableHumanEvents());
		InferEvents.outputReport(species);	
		InferEvents.resetVariables();
		System.gc();
		}
	}
	
	public static void outputReport(String species) throws IOException
	{
		int[] counts = InferReaction.getCounts();
		int percent = 100*counts[1]/counts[0];
		//TODO: Count warnings; manual report
		PrintWriter reportFile = new PrintWriter("report_ortho_inference_test_reactome_65.txt");
		reportFile.close();
		String results = "hsap to " + species + ":\t" + counts[1] + " out of " + counts[0] + " eligible reactions (" + percent + "%)";
		Files.write(Paths.get("report_ortho_inference_test_reactome_65.txt"), results.getBytes(), StandardOpenOption.APPEND);
		//TODO: manual human events handling
	}
	
	public static void resetVariables() {
		InferReaction.resetVariables();
		OrthologousEntity.resetVariables();
		InferEWAS.resetVariables();
		ProteinCount.resetVariables();
		GenerateInstance.resetVariables();
		UpdateHumanEvents.resetVariables();
		manualEventToNonHumanSource = new HashMap<GKInstance,GKInstance>();
		manualHumanEvents = new ArrayList<GKInstance>();
	}

	// Read the species-specific orthopair 'mapping' file, and create a HashMap with the contents
	public static HashMap<String, String[]> readHomologueMappingFile(String toSpecies, String fromSpecies) throws IOException
	{
		String mappingFileName = fromSpecies + "_" + toSpecies + "_mapping.txt";
		String mappingFilePath = "src/main/resources/orthopairs/" + mappingFileName;
		FileReader fr = new FileReader(mappingFilePath);
		BufferedReader br = new BufferedReader(fr);
		
		String currentLine;
		HashMap<String, String[]> homologueMappings = new HashMap<String,String[]>();
		while ((currentLine = br.readLine()) != null)
		{
			String[] tabSplit = currentLine.split("\t");
			String mapKey = tabSplit[0];
			String[] spaceSplit = tabSplit[1].split(" ");
			homologueMappings.put(mapKey, spaceSplit);
		}
		br.close();
		fr.close();
		return homologueMappings;
	}
	
	// Find the instance specific to this species
	public static void createSpeciesInst(String toSpeciesLong) throws Exception
	{
		SchemaClass referenceDb = dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.Species);
		speciesInst = new GKInstance(referenceDb);
		speciesInst.setDbAdaptor(dbAdaptor);
		speciesInst.addAttributeValue(ReactomeJavaConstants.name, toSpeciesLong);
		speciesInst = GenerateInstance.checkForIdenticalInstances(speciesInst);
	}
	// Create and set static Summation instance 
	public static void setSummationInst() throws Exception
	{
		GKInstance summationInst = new GKInstance(dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.Summation));
		summationInst.setDbAdaptor(dbAdaptor);
		summationInst.addAttributeValue(ReactomeJavaConstants.text, "This event has been computationally inferred from an event that has been demonstrated in another species.<p>The inference is based on the homology mapping in Ensembl Compara. Briefly, reactions for which all involved PhysicalEntities (in input, output and catalyst) have a mapped orthologue/paralogue (for complexes at least 75% of components must have a mapping) are inferred to the other species. High level events are also inferred for these events to allow for easier navigation.<p><a href='/electronic_inference_compara.html' target = 'NEW'>More details and caveats of the event inference in Reactome.</a> For details on the Ensembl Compara system see also: <a href='http://www.ensembl.org/info/docs/compara/homology_method.html' target='NEW'>Gene orthology/paralogy prediction method.</a>");
		summationInst = GenerateInstance.checkForIdenticalInstances(summationInst);
		InferReaction.setSummationInst(summationInst);
		UpdateHumanEvents.setSummationInst(summationInst);
	}
	// Create and set static EvidenceType instance
	public static void setEvidenceTypeInst() throws Exception
	{
		GKInstance evidenceTypeInst = new GKInstance(dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.EvidenceType));
		evidenceTypeInst.setDbAdaptor(dbAdaptor);
		evidenceTypeInst.addAttributeValue(ReactomeJavaConstants.name, "inferred by electronic annotation");
		evidenceTypeInst.addAttributeValue(ReactomeJavaConstants.name, "IEA");
		evidenceTypeInst = GenerateInstance.checkForIdenticalInstances(evidenceTypeInst);
		InferReaction.setEvidenceTypeInst(evidenceTypeInst);
		UpdateHumanEvents.setEvidenceTypeInst(evidenceTypeInst);
	}
}
