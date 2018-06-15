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
			orthoInferrer.setAdaptor(dbAdaptor);
			ewasInferrer.setAdaptor(dbAdaptor);
			ewasInferrer.readMappingFile("ddis","hsap");
			ewasInferrer.readENSGMappingFile("ddis");
			ewasInferrer.createUniprotDbInst();
			ewasInferrer.createEnsemblProteinDbInst("Dictyostelium discoideum", "http://protists.ensembl.org/Dictyostelium_discoideum/Info/Index", "http://protists.ensembl.org/Dictyostelium_discoideum/Transcript/ProteinSummary?peptide=###ID###");
			ewasInferrer.createEnsemblGeneDBInst("Dictyostelium discoideum", "http://protists.ensembl.org/Dictyostelium_discoideum/Info/Index", "http://protists.ensembl.org/Dictyostelium_discoideum/geneview?gene=###ID###&db=core");
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
					for (GKInstance reactionInst : reactionInstances)
					{
						InferReaction.inferEvent(reactionInst);
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
