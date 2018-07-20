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
import java.util.Collection;
import java.util.Collections;
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
	static MySQLAdaptor dbAdaptor = null;
	private static GKInstance speciesInst = null;
	static boolean refDb = true;
	private static HashMap<GKInstance,GKInstance> manualEventToNonHumanSource = new HashMap<GKInstance,GKInstance>();
	private static ArrayList<GKInstance> manualHumanEvents = new ArrayList<GKInstance>();
	
	//TODO: instance edit initialization; GO_CellularComponent instance;
	@SuppressWarnings("unchecked")
	public static void main(String args[]) throws Exception
	{
		String pathToConfig = "src/main/resources/config.properties";
		
		if (args.length > 0 && !args[0].equals(""))
		{
			pathToConfig = args[0];
		}
		
		PrintWriter eligibleFile = new PrintWriter("eligible_ddis_75.txt");
		PrintWriter inferredFile = new PrintWriter("inferred_ddis_75.txt");
		eligibleFile.close();
		inferredFile.close();
		
		InferReaction.setEligibleFilename("eligible_ddis_75.txt");
		InferReaction.setInferredFilename("inferred_ddis_75.txt");
		
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
		UpdateHumanEvents.setAdaptor(dbAdaptor);
		
		HashMap<String,String[]> homologueMappings = InferEvents.readHomologueMappingFile("ddis", "hsap");
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
		
		InferEvents.setSummationInst();
		InferEvents.setEvidenceTypeInst();
		OrthologousEntity.setComplexSummationInst();

		SkipTests.getSkipList("normal_event_skip_list.txt");
		// TODO: load_class_attribute_values_of_multiple_instances for reactions and ewas'
		// Get DB instances of source species
		Collection<GKInstance> sourceSpeciesInst = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("Species", "name", "=", speciesToInferFromLong);
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
					InferReaction.inferEvent(reactionInst);
				}
			}
		}
		UpdateHumanEvents.setInferredEvent(InferReaction.getInferredEvent());
		UpdateHumanEvents.updateHumanEvents(InferReaction.getInferrableHumanEvents());
		InferEvents.outputReport();		
	}
	
	public static void outputReport() throws IOException
	{
		int[] counts = InferReaction.getCounts();
		int percent = 100*counts[1]/counts[0];
		//TODO: Count warnings; manual report
		PrintWriter reportFile = new PrintWriter("report_ortho_inference_test_reactome_65.txt");
		reportFile.close();
		String results = "hsp to ddis:\t" + counts[1] + " out of " + counts[0] + " eligible reactions (" + percent + "%)";
		Files.write(Paths.get("report_ortho_inference_test_reactome_65.txt"), results.getBytes(), StandardOpenOption.APPEND);
		
	}

	// Read the species-specific orthopairs file, and create a HashMap with the contents
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
	
	public static void createSpeciesInst(String toSpeciesLong) throws Exception
	{
		SchemaClass referenceDb = dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.Species);
		speciesInst = new GKInstance(referenceDb);
		speciesInst.setDbAdaptor(dbAdaptor);
		speciesInst.addAttributeValue(ReactomeJavaConstants.name, toSpeciesLong);
		speciesInst = GenerateInstance.checkForIdenticalInstances(speciesInst);
	}
	public static void setSummationInst() throws Exception
	{
		GKInstance summationInst = new GKInstance(dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.Summation));
		summationInst.setDbAdaptor(dbAdaptor);
		summationInst.addAttributeValue(ReactomeJavaConstants.text, "This event has been computationally inferred from an event that has been demonstrated in another species.<p>The inference is based on the homology mapping in Ensembl Compara. Briefly, reactions for which all involved PhysicalEntities (in input, output and catalyst) have a mapped orthologue/paralogue (for complexes at least 75% of components must have a mapping) are inferred to the other species. High level events are also inferred for these events to allow for easier navigation.<p><a href='/electronic_inference_compara.html' target = 'NEW'>More details and caveats of the event inference in Reactome.</a> For details on the Ensembl Compara system see also: <a href='http://www.ensembl.org/info/docs/compara/homology_method.html' target='NEW'>Gene orthology/paralogy prediction method.</a>");
		summationInst = GenerateInstance.checkForIdenticalInstances(summationInst);
		InferReaction.setSummationInst(summationInst);
		UpdateHumanEvents.setSummationInst(summationInst);
	}
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
