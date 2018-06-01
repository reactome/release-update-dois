package org.reactome.orthoinference;

import java.io.FileInputStream;
//import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.*;
import org.gk.schema.SchemaClass;

//import com.mysql.jdbc.exceptions.jdbc4.MySQLDataException;
//import org.reactome.orthoinference.inferrers.ComplexInferrer;
//import org.reactome.orthoinference.inferrers.GenomeEncodedEntityInferrer;

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
			
			InferEWAS ewasInferrer = new InferEWAS();
			ewasInferrer.setAdaptor(dbAdaptor);
			ewasInferrer.readMappingFile("ddis","hsap");
			ewasInferrer.readENSGMappingFile("ddis");
			ewasInferrer.createEnsemblGeneDBInst("Dictyostelium discoideum", "http://protists.ensembl.org/Dictyostelium_discoideum/Info/Index", "http://protists.ensembl.org/Dictyostelium_discoideum/geneview?gene=###ID###&db=core");
			if (refDb)
			{
				ewasInferrer.createAlternateReferenceDBInst("Dictyostelium discoideum", "dictyBase", "http://www.dictybase.org/", "http://dictybase.org/db/cgi-bin/search/search.pl?query=###ID###");
			}
			InferEvents.createSpeciesInst("Dictyostelium discoideum");
			ewasInferrer.setSpeciesInst(speciesInst);
			
			// Get DB instances of source species
			List<AttributeQueryRequest> aqrList = new ArrayList<AttributeQueryRequest>();
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
				aqrList = new ArrayList<AttributeQueryRequest>();
				AttributeQueryRequest sourceSpeciesReactions = dbAdaptor.createAttributeQueryRequest("ReactionlikeEvent", "species", "=", dbId);
				aqrList.add(sourceSpeciesReactions);
				Set<GKInstance> reactionInstances = (Set<GKInstance>) dbAdaptor._fetchInstance(aqrList);
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
	
	
	
	
	
	
//	private static void loadDataFiles(String targetSpeciesName) {
//		// TODO Auto-generated method stub
//		
//	}
//
//	private static void inferEvents(GKInstance reaction)
//	{
//		GKInstance inferredEvent = new GKInstance();
//		boolean inputsInferredSuccessfully = inferInputs(reaction, inferredEvent);
//		if (inputsInferredSuccessfully)
//		{
//			boolean outputsInferredSuccessfully = inferOutputs(reaction, inferredEvent);
//			if (outputsInferredSuccessfully)
//			{
//				boolean catalystsInferredSuccessfully = inferCatalyst(reaction, inferredEvent);
//				if (catalystsInferredSuccessfully)
//				{
//					boolean regulationsInferredSuccessfully = inferRegulation(reaction, inferredEvent);
//					if (regulationsInferredSuccessfully)
//					{
//						updateSourceReaction(reaction);
//					}
//				}
//			}
//		}
//		
//	}
//
//	private static void updateSourceReaction(GKInstance reaction) {
//		// TODO Auto-generated method stub
//		
//	}
//
//	private static boolean inferRegulation(GKInstance reaction, GKInstance inferredEvent) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	private static boolean inferCatalyst(GKInstance reaction, GKInstance inferredEvent) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	private static boolean inferOutputs(GKInstance reaction, GKInstance inferredEvent) {
//		// TODO Auto-generated method stub
//		inferAttributes(reaction);
//		return false;
//	}
//
//	private static void inferAttributes(GKInstance reaction) {
//		// TODO Auto-generated method stub
//		@SuppressWarnings("unchecked")
//		Collection<GKInstance> attributes = (Collection<GKInstance>)reaction.getSchemaAttributes();
//		for (GKInstance attribute : attributes)
//		{
//			createOrthologousEntity(attribute);
//		}
//	}
//
//	private static GKInstance createOrthologousEntity(GKInstance attribute) {
//		// TODO Auto-generated method stub
//		GKInstance inferredEvent = new GKInstance();
//		switch (attribute.getSchemClass().getName())
//		{
//			case "Complex":
//				inferredEvent = inferComplexPolymer(attribute);
//				break;
//			case "Polymer":
//				inferredEvent = inferComplexPolymer(attribute);
//				break;
//			case "GenomeEncodedEntity":
//				inferredEvent = inferGenomeEncodedEntity(attribute);
//				break;
//			case "EntitySet":
//				inferredEvent = inferEntitySet(attribute);
//				break;
//			case "SimpleEntity":
//				//inferredEvent = inferSimpleEntity(attribute);
//				// See the note in infer_events.pl:629-634 RE: inferring SimpleEntities
//				inferredEvent = attribute; 
//				break;
//			default:
//				System.err.println("Unknown PhysicalEntity class: "+attribute.getSchemClass().getName());
//				break;
//		}
//		return inferredEvent;
//	}
//
//	private static GKInstance inferSimpleEntity(GKInstance attribute) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	private static GKInstance inferEntitySet(GKInstance attribute) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	private static GKInstance inferGenomeEncodedEntity(GKInstance attribute) {
//		// TODO Auto-generated method stub
//		GenomeEncodedEntityInferrer inferrer = new GenomeEncodedEntityInferrer();
//		return inferrer.infer(attribute);
//	}
//
//	private static GKInstance inferComplexPolymer(GKInstance attribute) {
//		// TODO Auto-generated method stub
//		// TODO: Logic to determine if input is complex or polymer.
//		ComplexInferrer complexInferrer = new ComplexInferrer(adaptor);
//		return complexInferrer.infer(attribute);
//	}
//
//	private static boolean inferInputs(GKInstance reaction, GKInstance inferredEvent) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	//TODO Also need a version of this that taks in a list ofvoid DBIDs, see https://github.com/reactome/Release/blob/master/scripts/release/orthoinference/infer_events.pl#L191
//	private static Collection<GKInstance> getReactions(String speciesToInferFrom)
//	{
//		try
//		{
//			@SuppressWarnings("unchecked")
//			List<GKInstance> speciesIDs = (List<GKInstance>) adaptor.fetchInstanceByAttribute("Taxon", "_displayName", "=", speciesToInferFrom);
//			if (speciesIDs != null && speciesIDs.size() > 0)
//			{
//				Long speciesID = speciesIDs.get(0).getDBID();
//				return (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReactionLikeEvent", "species", "=", speciesID);
//			}
//			else
//			{
//				System.err.println("No specices available for species with name \""+speciesToInferFrom+"\"");
//			}
//		}
//		catch (Exception e)
//		{
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		return null;
//	}
}
