package org.reactome.release.test.chebiupdate;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collection;

import org.mockito.MockitoAnnotations;
import org.reactome.release.chebiupdate.ChebiUpdater;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
public class ChebiUpdaterTest
{
	@Mock(name = "chebiClient")
	ChebiWebServiceClient chebiClient;
	
	@Mock(name = "adaptor")
	MySQLAdaptor adaptor;
	
	@Mock
	GKInstance chebiRefDB;
	
	@Mock
	GKInstance molecule1;
	
	@Mock
	GKInstance molecule2;

	@Mock
	GKInstance refEnt1;
	
	@Mock
	GKInstance refEnt2;
	
	@Mock
	GKInstance refEnt3;

	
	// need to mock the Chebi Entities because they don't have a setter for Formulae
	@Mock
	Entity chebiEntity1;
	
	@Mock
	Entity chebiEntity2;

	@InjectMocks
	ChebiUpdater updator = new ChebiUpdater(adaptor, true);
	
	@Test
	@MockitoSettings(strictness = Strictness.WARN)
	public void testChebiUpdater() throws Exception
	{
		MockitoAnnotations.initMocks(this);
		
		when(chebiEntity1.getChebiAsciiName()).thenReturn("NAME1");
		when(chebiEntity2.getChebiAsciiName()).thenReturn("NAME2");
		
		when(chebiEntity1.getChebiId()).thenReturn("CHEBI:112233");
		when(chebiEntity2.getChebiId()).thenReturn("CHEBI:332211");
		
		DataItem d = new DataItem();
		d.setData("H2");
		when(chebiEntity1.getFormulae()).thenReturn(Arrays.asList( d ));
		when(chebiEntity2.getFormulae()).thenReturn(Arrays.asList( d ));
		
		when(chebiRefDB.getDBID()).thenReturn(123456L);
		
		when(adaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI")).thenReturn(Arrays.asList(chebiRefDB)) ;
		
		//doReturn(chebiEntity1).when(wsClient.getCompleteEntity("112233"));
		when(chebiClient.getCompleteEntity("112233")).thenReturn(chebiEntity1);
		//doReturn(chebiEntity2).when(wsClient.getCompleteEntity("332211"));
		when(chebiClient.getCompleteEntity("332211")).thenReturn(chebiEntity2);
		
		when(molecule1.getAttributeValue("identifier")).thenReturn("112233");
		when(molecule1.getAttributeValuesList("name")).thenReturn(Arrays.asList("NAME1"));
		when(molecule1.getDBID()).thenReturn(123L);
		when(adaptor.fetchInstance(123L)).thenReturn(molecule1);
		when(molecule2.getAttributeValue("identifier")).thenReturn("332211");
		when(molecule2.getAttributeValuesList("name")).thenReturn(Arrays.asList("NAME2"));
		when(molecule2.getDBID()).thenReturn(456L);
		when(adaptor.fetchInstance(456L)).thenReturn(molecule2);
		
		when(refEnt1.getAttributeValuesList("name")).thenReturn(Arrays.asList("OTHERNAME", "BLAH"));
		when(refEnt2.getAttributeValuesList("name")).thenReturn(Arrays.asList("TEST", "NAME2"));
		when(refEnt3.getAttributeValuesList("name")).thenReturn(Arrays.asList("TESTTEST", "ASDFQ@#$FASFASDF","NOPE"));
		
		Collection<GKInstance> referrers1 = Arrays.asList(refEnt1);
		Collection<GKInstance> referrers2 = Arrays.asList(refEnt2,refEnt3); 
		
		when(molecule1.getReferers("referenceEntity")).thenReturn(referrers1);
		when(molecule2.getReferers("referenceEntity")).thenReturn(referrers2);
		Collection<GKInstance> molecules = Arrays.asList(molecule1, molecule2);
		when(adaptor.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", "123456")).thenReturn(molecules);
		
		updator.updateChebiReferenceMolecules();
	}
	
	
}
