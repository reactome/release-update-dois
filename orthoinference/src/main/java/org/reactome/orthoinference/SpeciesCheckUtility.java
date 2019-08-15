package org.reactome.orthoinference;

import java.util.Collection;

import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;

public class SpeciesCheckUtility {

	// Determines if there is a species attribute in any constituent instances of entityInst.
	// Unless its an 'OtherEntity' (which will return false), the function will check the instance or iterate on it's
	// sub-instances until it finds an existing 'species' attribute, or else it will return false.
	@SuppressWarnings("unchecked")
	public static boolean checkForSpeciesAttribute(GKInstance entityInst) throws Exception
	{
		if (entityInst.getSchemClass().isa(OtherEntity))
		{
			return false;
		} else if (entityInst.getSchemClass().isa(EntitySet)) // || entityInst.getSchemClass().isa(Polymer) || entityInst.getSchemClass().isa(EntitySet))
		{
			for (GKInstance memberInst : (Collection<GKInstance>) entityInst.getAttributeValuesList(hasMember))
			{
				if (checkForSpeciesAttribute(memberInst))
				{
					return true;
				}
			}
			if (entityInst.getSchemClass().isa(CandidateSet)) {
				for (GKInstance candidateInst : (Collection<GKInstance>) entityInst.getAttributeValuesList(hasCandidate))
				{
					if (checkForSpeciesAttribute(candidateInst))
					{
						return true;
					}
				}
			}
			return false;
		} else if (entityInst.getSchemClass().isa(Complex))
		{
			for (GKInstance componentInst : (Collection<GKInstance>) entityInst.getAttributeValuesList(hasComponent))
			{
				if (checkForSpeciesAttribute(componentInst))
				{
					return true;
				}
			}
			return false;
		} else if (entityInst.getSchemClass().isa(Polymer))
		{
			for (GKInstance repeatedUnitInst : (Collection<GKInstance>) entityInst.getAttributeValuesList(repeatedUnit))
			{
				if (checkForSpeciesAttribute(repeatedUnitInst))
				{
					return true;
				}
			}
			return false;
		} else {
			if (entityInst.getSchemClass().isValidAttribute(species) && entityInst.getAttributeValue(species) != null)
			{
				return true;
			} else {
				return false;
			}
		}
	}
}
