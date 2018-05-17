package org.reactome.release.test.chebiupdate;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.reactome.release.chebiupdate.ChebiUpdater;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

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
	GKInstance molecule3;

	@Mock
	GKInstance molecule4;
	
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
	ChebiUpdater updator = new ChebiUpdater(adaptor, true, 12345L);
	
	@Mock
	ResultSet duplicateResults;
	
	@Before
	void setupMolecule() throws InvalidAttributeException, Exception
	{
		MockitoAnnotations.initMocks(this);
	}
	
	@Test
	@MockitoSettings(strictness = Strictness.WARN)
	public void testUpdateMolecules() throws Exception
	{
		
		when(chebiEntity1.getChebiAsciiName()).thenReturn("NAME1");
		when(chebiEntity2.getChebiAsciiName()).thenReturn("NAME-2");
		
		when(chebiEntity1.getChebiId()).thenReturn("CHEBI:112233");
		when(chebiEntity2.getChebiId()).thenReturn("CHEBI:332211-2");
		
		DataItem d = new DataItem();
		d.setData("H2");
		when(chebiEntity1.getFormulae()).thenReturn(Arrays.asList( d ));
		when(chebiEntity2.getFormulae()).thenReturn(Arrays.asList( d ));
		
		when(chebiRefDB.getDBID()).thenReturn(123456L);
		
		when(adaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI")).thenReturn(Arrays.asList(chebiRefDB)) ;
		
		when(chebiClient.getCompleteEntity("112233")).thenReturn(chebiEntity1);
		when(chebiClient.getCompleteEntity("332211")).thenReturn(chebiEntity2);
		when(chebiClient.getCompleteEntity("445566")).thenReturn(null);
		when(chebiClient.getCompleteEntity("778899")).thenReturn(chebiEntity1);
		
		when(molecule1.getAttributeValue("identifier")).thenReturn("112233");
		when(molecule1.getAttributeValuesList("name")).thenReturn(Arrays.asList("NAME1"));
		when(molecule1.getAttributeValue("formula")).thenReturn("H3");
		when(molecule1.getDBID()).thenReturn(123L);
		when(adaptor.fetchInstance(123L)).thenReturn(molecule1);
		
		when(molecule2.getAttributeValue("identifier")).thenReturn("332211");
		when(molecule2.getAttributeValuesList("name")).thenReturn(Arrays.asList("NAME2"));
		when(molecule2.getDBID()).thenReturn(456L);
		when(adaptor.fetchInstance(456L)).thenReturn(molecule2);
		
		when(molecule3.getAttributeValue("identifier")).thenReturn("445566");
		
		when(molecule4.getAttributeValue("identifier")).thenReturn("778899");
		when(molecule4.getAttributeValuesList("name")).thenReturn(Arrays.asList("NAME1"));
		when(molecule4.getAttributeValue("formula")).thenReturn("H2");
		when(molecule4.getDBID()).thenReturn(444L);
		when(adaptor.fetchInstance(444L)).thenReturn(molecule4);
		
		
		when(refEnt1.getAttributeValuesList("name")).thenReturn(Arrays.asList("OTHERNAME", "NAME1", "BLAH"));
		when(refEnt2.getAttributeValuesList("name")).thenReturn(Arrays.asList("TEST", "NAME2"));
		when(refEnt3.getAttributeValuesList("name")).thenReturn(Arrays.asList("NAME-2", "ASDFQ@#$FASFASDF","NOPE"));
		
		Collection<GKInstance> referrers1 = Arrays.asList(refEnt1);
		Collection<GKInstance> referrers2 = Arrays.asList(refEnt2,refEnt3); 
		
		when(molecule1.getReferers("referenceEntity")).thenReturn(referrers1);
		when(molecule2.getReferers("referenceEntity")).thenReturn(referrers2);
		Collection<GKInstance> molecules = Arrays.asList(molecule1, molecule2, molecule3, molecule4);
		when(adaptor.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", "123456")).thenReturn(molecules);
		
		updator.updateChebiReferenceMolecules();

		// If we got this far, it means nothing crashed.
		assertTrue(true);
	}
	
	
	@Test
	@MockitoSettings(strictness = Strictness.WARN)
	public void testCheckForDuplicates() throws SQLException, Exception
	{
		when(adaptor.executeQuery(anyString(), (List) nullable(List.class))).thenReturn(duplicateResults);
		when(duplicateResults.next()).thenReturn(true).thenReturn(true).thenReturn(false);
		when(duplicateResults.getInt(1)).thenReturn(123).thenReturn(456);
		when(duplicateResults.getInt(2)).thenReturn(2).thenReturn(3);
		
		when(adaptor.fetchInstanceByAttribute("ReferenceMolecule", "identifier", "=", 123)).thenReturn(Arrays.asList(molecule1));
		when(adaptor.fetchInstanceByAttribute("ReferenceMolecule", "identifier", "=", 456)).thenReturn(Arrays.asList(molecule2));
		
		updator.checkForDuplicates();
	}
}
