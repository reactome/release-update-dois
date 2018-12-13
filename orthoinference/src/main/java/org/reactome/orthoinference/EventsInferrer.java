package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.reactome.release.common.database.InstanceEditUtils;

/**
 *
 * @author jcook
 * 
 * The Java version of infer_events.pl -- The gist of this module is that it looks at all existing Human ReactionlikeEvent (RlE) instances (mostly Reactions and BlackBoxEvents) in the Test_Reactome database,
 * and attempts to computationally infer them in each of Reactome's model organisms. Each RlE is broken down into its primary components (input, output, catalyst, and regulator), which are themselves broken
 * into their PhysicalEntity subunits. The homology data used for the inference process currently comes from Ensembl Compara and is generated during the 'Orthopairs' step of the Reactome release process.
 * After all inference attempts for each RlE has been completed in an organism, the pathways that contain the reactions are filled with these newly inferred ones. 
 * 
 *
 */

public class EventsInferrer
{
	static MySQLAdaptor dbAdaptor = null;
	private static String releaseVersion = "";
	private static GKInstance instanceEditInst;
	private static GKInstance speciesInst;
	private static Map<GKInstance,GKInstance> manualEventToNonHumanSource = new HashMap<GKInstance,GKInstance>();
	private static List<GKInstance> manualHumanEvents = new ArrayList<GKInstance>();

	@SuppressWarnings("unchecked")
	public static void eventInferrer(Properties props, String pathToConfig, String species) throws Exception
	{
		releaseVersion = props.getProperty("releaseNumber");
		String pathToOrthopairs = props.getProperty("pathToOrthopairs");
		String pathToSpeciesConfig = props.getProperty("pathToSpeciesConfig");
		String dateOfRelease = props.getProperty("dateOfRelease");
		int personId = Integer.valueOf(props.getProperty("personId"));
		setReleaseDates(dateOfRelease);
		
		// Set up DB adaptor using config.properties file
		String username = props.getProperty("username");
		String password = props.getProperty("password");
		String database = props.getProperty("database");
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));
		
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		setDbAdaptors(dbAdaptor);
		
		SkipInstanceChecker.getSkipList("normal_event_skip_list.txt");
		
		JSONParser parser = new JSONParser();
		Object obj = parser.parse(new FileReader(pathToSpeciesConfig));
		JSONObject jsonObject = (JSONObject) obj;

		// Parse Species information (found in Species.json config file)
		JSONObject speciesObject = (JSONObject) jsonObject.get(species);
		JSONArray speciesNames = (JSONArray) speciesObject.get("name");
		String speciesName = (String) speciesNames.get(0);
		System.out.println("Beginning orthoinference of " + speciesName + ".");
		JSONObject refDb = (JSONObject) speciesObject.get("refdb");
		String refDbUrl = (String) refDb.get("url");
		String refDbProteinUrl = (String) refDb.get("access");
		String refDbGeneUrl = (String) refDb.get("ensg_access");
		
		// Creates two files that a) list reactions that are eligible for inference and b) those that are successfully inferred
		String eligibleFilename = "eligible_" + species	+ "_75.txt";
		String inferredFilename = "inferred_" + species + "_75.txt";
		File eligibleFile = new File(eligibleFilename);
		eligibleFile.createNewFile();
		File inferredFile = new File(inferredFilename);
		inferredFile.createNewFile();
		ReactionInferrer.setEligibleFilename(eligibleFilename);
		ReactionInferrer.setInferredFilename(inferredFilename);

		// Set static variables (DB/Species Instances, mapping files) that will be repeatedly used
		setInstanceEdits(personId);
		
		try {
			Map<String,String[]> homologueMappings = readHomologueMappingFile(species, "hsap", pathToOrthopairs);
			ProteinCountUtility.setHomologueMappingFile(homologueMappings);
			EWASInferrer.setHomologueMappingFile(homologueMappings);
		} catch (FileNotFoundException e) {
			System.out.println("Unable to locate " + speciesName +" mapping file: hsap_" + species + "_mapping.txt. Orthology prediction not possible.");
		}
		EWASInferrer.readENSGMappingFile(species);
		EWASInferrer.createUniprotDbInstance();
		EWASInferrer.createEnsemblProteinDbInstance(speciesName, refDbUrl, refDbProteinUrl);
		EWASInferrer.createEnsemblGeneDBInstance(speciesName, refDbUrl, refDbGeneUrl);
		
		JSONObject altRefDbJSON = (JSONObject) speciesObject.get("alt_refdb");
		if (altRefDbJSON != null)
		{
			//TODO: Simplify this block
			EWASInferrer.createAlternateReferenceDBInstance(speciesName, altRefDbJSON);
		} else {
			EWASInferrer.updateRefDb();
		}
		createAndSetSpeciesInstance(speciesName);
		setSummationInstance();
		setEvidenceTypeInstance();
		OrthologousEntityGenerator.setComplexSummationInstance();

/**
 *  Start of ReactionlikeEvent inference. Retrieves all human ReactionlikeEvents, and attempts to infer each for the species.
 */
		// Gets DB instance of source species (human)
		Collection<GKInstance> sourceSpeciesInst = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("Species", "name", "=", "Homo sapiens");
		if (sourceSpeciesInst.isEmpty())
		{
			System.out.println("Could not find Species instance for Homo sapiens");
			return;
		}
		String humanInstanceDbId = sourceSpeciesInst.iterator().next().getDBID().toString();
		// Gets Reaction instances of source species (human)
		Collection<GKInstance> reactionInstances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("ReactionlikeEvent", "species", "=", humanInstanceDbId);

		List<Long> dbids = new ArrayList<Long>();
		Map<Long, GKInstance> reactionMap = new HashMap<Long, GKInstance>();
		for (GKInstance reactionInst : reactionInstances) {
			dbids.add(reactionInst.getDBID());
			reactionMap.put(reactionInst.getDBID(), reactionInst);
		}
		// For now sort the instances by DB ID so that it matches the Perl sequence
		Collections.sort(dbids);
		
		for (Long dbid : dbids)
		{
			GKInstance reactionInst = reactionMap.get(dbid);
			// Check if the current Reaction already exists for this species, that it is a valid instance (passes some filters), and that it doesn't have a Disease attribute.
			// Adds to manualHumanEvents array if it passes conditions. This code block allows you to re-run the code without re-inferring instances.
			List<GKInstance> previouslyInferredInstances = new ArrayList<GKInstance>();
			previouslyInferredInstances = checkIfPreviouslyInferred(reactionInst, orthologousEvent, previouslyInferredInstances);
			previouslyInferredInstances = checkIfPreviouslyInferred(reactionInst, inferredFrom, previouslyInferredInstances);
			if (previouslyInferredInstances.size() > 0)
			{
				GKInstance prevInfInst = previouslyInferredInstances.get(0);
				if (prevInfInst.getAttributeValue(disease) == null)
				{
					manualEventToNonHumanSource.put(reactionInst, prevInfInst);
					manualHumanEvents.add(reactionInst);
				} else {
					System.out.println("Skipping building of hierarchy around pre-existing disease reaction " + prevInfInst);
				}
				continue;
			}
			// This Reaction doesn't already exist for this species, and an orthologous inference will be attempted.
			System.out.println("\t" + reactionInst);
			try {
				ReactionInferrer.reactionInferrer(reactionInst);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		HumanEventsUpdater.setInferredEvent(ReactionInferrer.getInferredEvent());
		HumanEventsUpdater.updateHumanEvents(ReactionInferrer.getInferrableHumanEvents());
		outputReport(species);
		resetVariables();
		System.gc();
		System.out.println("Finished orthoinference of " + speciesName + ".");
	}

	private static void setReleaseDates(String dateOfRelease) 
	{
		ReactionInferrer.setReleaseDate(dateOfRelease);
		HumanEventsUpdater.setReleaseDate(dateOfRelease);
	
	}

	@SuppressWarnings("unchecked")
	private static List<GKInstance> checkIfPreviouslyInferred(GKInstance reactionInst, String attribute, List<GKInstance> previouslyInferredInstances) throws InvalidAttributeException, Exception 
	{
		for (GKInstance attributeInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(attribute))
		{
			GKInstance reactionSpeciesInst = (GKInstance) attributeInst.getAttributeValue(species);
			if (reactionSpeciesInst.getDBID() == speciesInst.getDBID() && attributeInst.getAttributeValue(isChimeric) == null)
			{
				previouslyInferredInstances.add(attributeInst);
			}
		}
		return previouslyInferredInstances;
	}

	private static void outputReport(String species) throws IOException
	{
		int eligibleCount = ReactionInferrer.getEligibleCount();
		int inferredCount = ReactionInferrer.getInferredCount();
		float percentInferred = (float) 100*inferredCount/eligibleCount;
		// Create file if it doesn't exist
		String reportFilename = "report_ortho_inference_test_reactome_" + releaseVersion + ".txt";
		File reportFile = new File(reportFilename);
		reportFile.createNewFile();
		String results = "hsap to " + species + ":\t" + inferredCount + " out of " + eligibleCount + " eligible reactions (" + String.format("%.2f", percentInferred) + "%)\n";
		Files.write(Paths.get(reportFilename), results.getBytes(), StandardOpenOption.APPEND);
	}
	
	// Statically store the adaptor variable in each class
	private static void setDbAdaptors(MySQLAdaptor dbAdaptor2) 
	{
		ReactionInferrer.setAdaptor(dbAdaptor);
		SkipInstanceChecker.setAdaptor(dbAdaptor);
		InstanceUtilities.setAdaptor(dbAdaptor);
		OrthologousEntityGenerator.setAdaptor(dbAdaptor);
		EWASInferrer.setAdaptor(dbAdaptor);
		HumanEventsUpdater.setAdaptor(dbAdaptor);
		
	}

	// Read the species-specific orthopair 'mapping' file, and create a HashMap with the contents
	private static Map<String, String[]> readHomologueMappingFile(String toSpecies, String fromSpecies, String pathToOrthopairs) throws IOException
	{
		String orthopairsFileName = fromSpecies + "_" + toSpecies + "_mapping.txt";
		String orthopairsFilePath = pathToOrthopairs + orthopairsFileName;
		FileReader fr = new FileReader(orthopairsFilePath);
		BufferedReader br = new BufferedReader(fr);

		Map<String, String[]> homologueMappings = new HashMap<String,String[]>();
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
		return homologueMappings;
	}

	// Find the instance specific to this species
	private static void createAndSetSpeciesInstance(String toSpeciesLong) throws Exception
	{
		SchemaClass referenceDb = dbAdaptor.getSchema().getClassByName(Species);
		speciesInst = new GKInstance(referenceDb);
		speciesInst.setDbAdaptor(dbAdaptor);
		speciesInst.addAttributeValue(created, instanceEditInst);
		speciesInst.addAttributeValue(name, toSpeciesLong);
		speciesInst.addAttributeValue(_displayName, toSpeciesLong);
		speciesInst = InstanceUtilities.checkForIdenticalInstances(speciesInst);
		OrthologousEntityGenerator.setSpeciesInstance(speciesInst);
		EWASInferrer.setSpeciesInstance(speciesInst);
		InstanceUtilities.setSpeciesInstance(speciesInst);
	}
	// Create and set static Summation instance
	private static void setSummationInstance() throws Exception
	{
		GKInstance summationInst = new GKInstance(dbAdaptor.getSchema().getClassByName(Summation));
		summationInst.setDbAdaptor(dbAdaptor);
		summationInst.addAttributeValue(created, instanceEditInst);
		String summationText = "This event has been computationally inferred from an event that has been demonstrated in another species.<p>The inference is based on the homology mapping in Ensembl Compara. Briefly, reactions for which all involved PhysicalEntities (in input, output and catalyst) have a mapped orthologue/paralogue (for complexes at least 75% of components must have a mapping) are inferred to the other species. High level events are also inferred for these events to allow for easier navigation.<p><a href='/electronic_inference_compara.html' target = 'NEW'>More details and caveats of the event inference in Reactome.</a> For details on the Ensembl Compara system see also: <a href='http://www.ensembl.org/info/docs/compara/homology_method.html' target='NEW'>Gene orthology/paralogy prediction method.</a>";
		summationInst.addAttributeValue(text, summationText);
		summationInst.addAttributeValue(_displayName, summationText);
		summationInst = InstanceUtilities.checkForIdenticalInstances(summationInst);
		
		ReactionInferrer.setSummationInstance(summationInst);
		HumanEventsUpdater.setSummationInstance(summationInst);
	}
	// Create and set static EvidenceType instance
	private static void setEvidenceTypeInstance() throws Exception
	{
		GKInstance evidenceTypeInst = new GKInstance(dbAdaptor.getSchema().getClassByName(EvidenceType));
		evidenceTypeInst.setDbAdaptor(dbAdaptor);
		evidenceTypeInst.addAttributeValue(created, instanceEditInst);
		String evidenceTypeText = "inferred by electronic annotation";
		evidenceTypeInst.addAttributeValue(name, evidenceTypeText);
		evidenceTypeInst.addAttributeValue(name, "IEA");
		evidenceTypeInst.addAttributeValue(_displayName, evidenceTypeText);
		evidenceTypeInst = InstanceUtilities.checkForIdenticalInstances(evidenceTypeInst);
		ReactionInferrer.setEvidenceTypeInstance(evidenceTypeInst);
		HumanEventsUpdater.setEvidenceTypeInstance(evidenceTypeInst);
	}
	
	private static void setInstanceEdits(int personId) throws Exception 
	{
		instanceEditInst = InstanceEditUtils.createInstanceEdit(dbAdaptor, personId, "org.reactome.orthoinference");
		InstanceUtilities.setInstanceEdit(instanceEditInst);
		OrthologousEntityGenerator.setInstanceEdit(instanceEditInst);
		EWASInferrer.setInstanceEdit(instanceEditInst);
		HumanEventsUpdater.setInstanceEdit(instanceEditInst);
	}
	
	// Reduce memory usage after species inference complete
	private static void resetVariables() 
	{
		ReactionInferrer.resetVariables();
		OrthologousEntityGenerator.resetVariables();
		EWASInferrer.resetVariables();
		ProteinCountUtility.resetVariables();
		InstanceUtilities.resetVariables();
		HumanEventsUpdater.resetVariables();
		manualEventToNonHumanSource = new HashMap<GKInstance,GKInstance>();
		manualHumanEvents = new ArrayList<GKInstance>();
	}
}
