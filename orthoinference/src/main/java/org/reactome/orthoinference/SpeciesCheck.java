package org.reactome.orthoinference;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;

public class SpeciesCheck {

	// Determines if there is a species attribute in any constituent instances of entityInst
	// Unless its an 'OtherEntity', the function will check the instance or iterate on  it's
	// sub-instances until it finds an existing 'species' attribute, or else it will return false.
	public static boolean hasSpecies(GKInstance entityInst) throws InvalidAttributeException, Exception
	{
		if (entityInst.getSchemClass().isa(ReactomeJavaConstants.OtherEntity))
		{
			return false;
		} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) // || entityInst.getSchemClass().isa(ReactomeJavaConstants.Polymer) || entityInst.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
		{
			for (Object member : entityInst.getAttributeValuesList(ReactomeJavaConstants.hasMember))
			{
				if (SpeciesCheck.hasSpecies((GKInstance) member))
				{
					return true;
				}
			}
			if (entityInst.getSchemClass().isa(ReactomeJavaConstants.CandidateSet)) {
				for (Object candidate : entityInst.getAttributeValuesList(ReactomeJavaConstants.hasCandidate))
				{
					if (SpeciesCheck.hasSpecies((GKInstance) candidate))
					{
						return true;
					}
				}
			}
			return false;
		} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.Complex))
		{
			for (Object component : entityInst.getAttributeValuesList(ReactomeJavaConstants.hasComponent))
			{
				if (SpeciesCheck.hasSpecies((GKInstance) component))
				{
					return true;
				}
			}
			return false;
		} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.Polymer))
		{
			for (Object monomer : entityInst.getAttributeValuesList(ReactomeJavaConstants.repeatedUnit))
			{
				if (SpeciesCheck.hasSpecies((GKInstance) monomer))
				{
					return true;
				}
			}
			return false;
		} else {
			if (entityInst.getAttributeValue(ReactomeJavaConstants.species) != null)
			{
				return true;
			} else {
				return false;
			}
		}
	}
}
