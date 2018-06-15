package org.reactome.orthoinference;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
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
	private static GKInstance speciesInst = null;
	static boolean refDb = true;
	
	Collection<GKInstance> dois;
	
	@SuppressWarnings("unchecked")
	public static void main(String args[]) throws Exception
	{
		String pathToConfig = "src/main/resources/config.properties";
		
		if (args.length > 0 && !args[0].equals(""))
		{
			pathToConfig = args[0];
		}
		// TODO: Parameterize all of these input values
		//originally, this list was found in https://github.com/reactome/Release/blob/master/modules/GKB/Config_Species.pm
		
		Collection<GKInstance> sourceSpeciesInst;
		
		try
		{
			Properties props = new Properties();
			props.load(new FileInputStream(pathToConfig));
			
			//TODO: Create config equivalent for species as seen in Config_Species.pm
//			ArrayList<String> speciesToInferTo = new ArrayList<String>(Arrays.asList("ddis"));
//			String speciesToInferFromShort = "hsap";
			Object speciesToInferFromLong = "Homo sapiens";
			String username = props.getProperty("username");
			String password = props.getProperty("password");
			String database = props.getProperty("database");
			String host = props.getProperty("host");
			int port = Integer.valueOf(props.getProperty("port"));
			
			// Set-Up
			dbAdaptor = new MySQLAdaptor(host, database, username, password, port);	
			InferReaction.setAdaptor(dbAdaptor);
			OrthologousEntity orthoInferrer = new OrthologousEntity();
			InferEWAS ewasInferrer = new InferEWAS();
			GenerateInstance instanceGenerator = new GenerateInstance();
			GenerateInstance.setAdaptor(dbAdaptor);
			orthoInferrer.setAdaptor(dbAdaptor);
			ewasInferrer.setAdaptor(dbAdaptor);
			ewasInferrer.readMappingFile("ddis","hsap");
			ewasInferrer.readENSGMappingFile("ddis");
			ewasInferrer.createUniprotDbInst();
			ewasInferrer.createEnsemblProteinDbInst("Dictyostelium discoideum", "http://protists.ensembl.org/Dictyostelium_discoideum/Info/Index", "http://protists.ensembl.org/Dictyostelium_discoideum/Transcript/ProteinSummary?peptide=###ID###");
			ewasInferrer.createEnsemblGeneDBInst("Dictyostelium discoideum", "http://protists.ensembl.org/Dictyostelium_discoideum/Info/Index", "http://protists.ensembl.org/Dictyostelium_discoideum/geneview?gene=###ID###&db=core");
			System.exit(0);
			if (refDb)
			{
				ewasInferrer.createAlternateReferenceDBInst("Dictyostelium discoideum", "dictyBase", "http://www.dictybase.org/", "http://dictybase.org/db/cgi-bin/search/search.pl?query=###ID###");
			}
			InferEvents.createSpeciesInst("Dictyostelium discoideum");
			orthoInferrer.setSpeciesInst(speciesInst);
			ewasInferrer.setSpeciesInst(speciesInst);
			instanceGenerator.setSpeciesInst(speciesInst);
			
			// Get DB instances of source species
			sourceSpeciesInst = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("Species", "name", "=", speciesToInferFromLong);
			@SuppressWarnings("unused")
			InferReaction reactionInferrer = new InferReaction();
			
			if (!sourceSpeciesInst.isEmpty())
			{
				String dbId = null;
				for (GKInstance speciesInst : sourceSpeciesInst) 
				{
					dbId = speciesInst.getAttributeValue("DB_ID").toString();
					
				}
				
				// Gets Reaction instances of source species
				Collection<GKInstance> reactionInstances = (Collection<GKInstance>) dbAdaptor.fetchInstanceByAttribute("ReactionlikeEvent", "species", "=", dbId);
				if (!reactionInstances.isEmpty())
				{
					// Remove
					ArrayList<GKInstance> filteredReactions = new ArrayList<GKInstance>();
					filteredReactions.add(null);
					filteredReactions.add(null);
					filteredReactions.add(null);
					filteredReactions.add(null);
//					filteredReactions.add(null);
//					filteredReactions.add(null);
					int count = 0;
					for (GKInstance reactionInst : reactionInstances)
					{
//						if (count == 5) { System.exit(0); }
						// Remove all except line 111 inferEvent
						String dBId = reactionInst.getAttributeValue("DB_ID").toString();
						if (dBId.equals("68712")) //|| dBId.equals("68611") || dBId.equals("68615") || dBId.equals("68603"))
						{
							((ArrayList<GKInstance>) filteredReactions).set(0, reactionInst);
//						InferReaction.inferEvent(reactionInst);
						count++;
//						} else if (dBId.equals("77585"))
//						{
//							((ArrayList<GKInstance>) filteredReactions).set(1, reactionInst);
//						} else if (dBId.equals("5661122"))
//						{
//							((ArrayList<GKInstance>) filteredReactions).set(2, reactionInst);
//						} else if (dBId.equals("5672950"))
//						{
//							((ArrayList<GKInstance>) filteredReactions).set(3, reactionInst);
//						} else if (dBId.equals("5672950"))
//						{
//							((ArrayList<GKInstance>) filteredReactions).set(4, reactionInst);
//						} else if (dBId.equals("71593"))
//						{
//							((ArrayList<GKInstance>) filteredReactions).set(5, reactionInst);
						}
					}
					
					for (GKInstance filtReactionInst : filteredReactions)
					{
						if (filtReactionInst != null)
						{
						InferReaction.inferEvent(filtReactionInst);
						}
					}
				}
			}	
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	//TODO: Perl creates 'Name' using an array -- Is that a Perlism or a Database-ism?
	//TODO: Check for identical instances
	public static void createSpeciesInst(String toSpeciesLong)
	{
		try 
		{
		SchemaClass referenceDb = dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.Species);
		speciesInst = new GKInstance(referenceDb);
		speciesInst.addAttributeValue(ReactomeJavaConstants.name, toSpeciesLong);
		speciesInst = GenerateInstance.checkForIdenticalInstances(speciesInst);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
