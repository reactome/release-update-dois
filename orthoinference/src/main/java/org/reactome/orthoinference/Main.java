package org.reactome.orthoinference;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.orthoinference.inferrers.ComplexInferrer;
import org.reactome.orthoinference.inferrers.GenomeEncodedEntityInferrer;



public class Main
{
	private static MySQLAdaptor adaptor;
	
	public static void main(String args[])
	{
		// TODO: Parameterize all of these input values
		//originally, this list was found in https://github.com/reactome/Release/blob/master/modules/GKB/Config_Species.pm
		ArrayList<String> speciesToInferTo = (ArrayList<String>) Arrays.asList("Arabidopsis thaliana","Bos taurus", "Gallus gallus" );
		String speciesToInferFrom = "Homo sapiens";
		String host = "localhost";
		String database = "test_reactome_64";
		String username = "root";
		String password = "root";
		int port = 3306;
		try
		{
			adaptor = new MySQLAdaptor(host, database, username, password, port);
			// outer loop is target species
			// TODO: Maybe parallelize on this outer loop?
			for (String targetSpeciesName : speciesToInferTo)
			{
				// TODO: Load input files. Orthopairs, etc... for targetSpeciesName
				// Store the data from these file in some sort of in-memory cache so that they can be accessed later on, during inference.
				loadDataFiles(targetSpeciesName);
				Collection<GKInstance> reactions = getReactions(speciesToInferFrom);
				// inner loop is reactions
				for (GKInstance reaction : reactions)
				{
					inferEvents(reaction);
				}
			}
		}
		catch (SQLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void loadDataFiles(String targetSpeciesName) {
		// TODO Auto-generated method stub
		
	}

	private static void inferEvents(GKInstance reaction)
	{
		GKInstance inferredEvent = new GKInstance();
		boolean inputsInferredSuccessfully = inferInputs(reaction, inferredEvent);
		if (inputsInferredSuccessfully)
		{
			boolean outputsInferredSuccessfully = inferOutputs(reaction, inferredEvent);
			if (outputsInferredSuccessfully)
			{
				boolean catalystsInferredSuccessfully = inferCatalyst(reaction, inferredEvent);
				if (catalystsInferredSuccessfully)
				{
					boolean regulationsInferredSuccessfully = inferRegulation(reaction, inferredEvent);
					if (regulationsInferredSuccessfully)
					{
						updateSourceReaction(reaction);
					}
				}
			}
		}
		
	}

	private static void updateSourceReaction(GKInstance reaction) {
		// TODO Auto-generated method stub
		
	}

	private static boolean inferRegulation(GKInstance reaction, GKInstance inferredEvent) {
		// TODO Auto-generated method stub
		return false;
	}

	private static boolean inferCatalyst(GKInstance reaction, GKInstance inferredEvent) {
		// TODO Auto-generated method stub
		return false;
	}

	private static boolean inferOutputs(GKInstance reaction, GKInstance inferredEvent) {
		// TODO Auto-generated method stub
		inferAttributes(reaction);
		return false;
	}

	private static void inferAttributes(GKInstance reaction) {
		// TODO Auto-generated method stub
		@SuppressWarnings("unchecked")
		Collection<GKInstance> attributes = (Collection<GKInstance>)reaction.getSchemaAttributes();
		for (GKInstance attribute : attributes)
		{
			createOrthologousEntity(attribute);
		}
	}

	private static GKInstance createOrthologousEntity(GKInstance attribute) {
		// TODO Auto-generated method stub
		GKInstance inferredEvent = new GKInstance();
		switch (attribute.getSchemClass().getName())
		{
			case "Complex":
				inferredEvent = inferComplexPolymer(attribute);
				break;
			case "Polymer":
				inferredEvent = inferComplexPolymer(attribute);
				break;
			case "GenomeEncodedEntity":
				inferredEvent = inferGenomeEncodedEntity(attribute);
				break;
			case "EntitySet":
				inferredEvent = inferEntitySet(attribute);
				break;
			case "SimpleEntity":
				//inferredEvent = inferSimpleEntity(attribute);
				// See the note in infer_events.pl:629-634 RE: inferring SimpleEntities
				inferredEvent = attribute; 
				break;
			default:
				System.err.println("Unknown PhysicalEntity class: "+attribute.getSchemClass().getName());
				break;
		}
		return inferredEvent;
	}

	private static GKInstance inferSimpleEntity(GKInstance attribute) {
		// TODO Auto-generated method stub
		return null;
	}

	private static GKInstance inferEntitySet(GKInstance attribute) {
		// TODO Auto-generated method stub
		return null;
	}

	private static GKInstance inferGenomeEncodedEntity(GKInstance attribute) {
		// TODO Auto-generated method stub
		GenomeEncodedEntityInferrer inferrer = new GenomeEncodedEntityInferrer();
		return inferrer.infer(attribute);
	}

	private static GKInstance inferComplexPolymer(GKInstance attribute) {
		// TODO Auto-generated method stub
		// TODO: Logic to determine if input is complex or polymer.
		ComplexInferrer complexInferrer = new ComplexInferrer();
		return complexInferrer.infer(attribute);
	}

	private static boolean inferInputs(GKInstance reaction, GKInstance inferredEvent) {
		// TODO Auto-generated method stub
		return false;
	}

	//TODO Also need a version of this that taks in a list ofvoid DBIDs, see https://github.com/reactome/Release/blob/master/scripts/release/orthoinference/infer_events.pl#L191
	private static Collection<GKInstance> getReactions(String speciesToInferFrom)
	{
		try
		{
			@SuppressWarnings("unchecked")
			List<GKInstance> speciesIDs = (List<GKInstance>) adaptor.fetchInstanceByAttribute("Taxon", "_displayName", "=", speciesToInferFrom);
			if (speciesIDs != null && speciesIDs.size() > 0)
			{
				Long speciesID = speciesIDs.get(0).getDBID();
				return (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReactionLikeEvent", "species", "=", speciesID);
			}
			else
			{
				System.err.println("No specices available for species with name \""+speciesToInferFrom+"\"");
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
