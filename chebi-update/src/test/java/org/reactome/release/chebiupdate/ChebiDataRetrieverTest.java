package org.reactome.release.chebiupdate;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault_Exception;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"org.apache.logging.log4j.*", "uk.ac.ebi.*", "javax.management.*", "javax.script.*", 
			"javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})
@PrepareForTest({ChebiDataRetriever.class})
public class ChebiDataRetrieverTest
{
	@Mock(name="chebiClient")
	ChebiWebServiceClient chebiClient = PowerMockito.mock(ChebiWebServiceClient.class);
	
	@Mock(name = "adaptor")
	MySQLAdaptor adaptor;
	
	@Mock
	GKInstance chebiRefDB;
	
	@Mock
	GKInstance molecule1;

	@Before
	public void setup() throws Exception
	{
		MockitoAnnotations.initMocks(this);
		
		PowerMockito.whenNew(ChebiWebServiceClient.class).withAnyArguments().thenReturn(chebiClient);
	}
	
	@Test
	public void testChEBIWSException() throws InvalidAttributeException, Exception
	{
		String identifier = "112233";
		when(molecule1.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn(identifier);
		try
		{
			when(chebiClient.getCompleteEntity(identifier)).thenThrow(new ChebiWebServiceFault_Exception("invalid ChEBI identifier", new ChebiWebServiceFault()));
			ChebiDataRetriever retriever = new ChebiDataRetriever(false);
			retriever.retrieveUpdatesFromChebi(Arrays.asList(molecule1), new HashMap<GKInstance, String>());
		}
		catch (ChebiWebServiceFault_Exception e)
		{
			e.printStackTrace();
			
			assertTrue(e.getMessage().contains("ERROR: ChEBI Identifier \""+identifier+"\" is not formatted correctly."));
		}
		catch (IOException e)
		{
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testChEBIWSException2() throws InvalidAttributeException, Exception
	{
		String identifier = "112233";
		when(molecule1.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn(identifier);
		Map<GKInstance, String> failedEntities = new HashMap<>();
		try
		{
			when(chebiClient.getCompleteEntity(identifier)).thenThrow(new ChebiWebServiceFault_Exception("the entity in question is deleted, obsolete, or not yet released", new ChebiWebServiceFault()));
			ChebiDataRetriever retriever = new ChebiDataRetriever(false);
			
			retriever.retrieveUpdatesFromChebi(Arrays.asList(molecule1), failedEntities);
		}
		catch (ChebiWebServiceFault_Exception e)
		{
			e.printStackTrace();
			assertTrue(e.getMessage().contains("ERROR: ChEBI Identifier \""+identifier+"\" is deleted, obsolete, or not yet released."));
			System.out.println(failedEntities);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testChEBIWSException3() throws InvalidAttributeException, Exception
	{
		String identifier = "112233";
		when(molecule1.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn(identifier);
		try
		{
			when(chebiClient.getCompleteEntity(identifier)).thenThrow(new ChebiWebServiceFault_Exception("(this should trigger a RuntimeException)", new ChebiWebServiceFault()));
			ChebiDataRetriever retriever = new ChebiDataRetriever(false);
			retriever.retrieveUpdatesFromChebi(Arrays.asList(molecule1), new HashMap<GKInstance, String>());
		}
		catch (ChebiWebServiceFault_Exception e)
		{
			e.printStackTrace();
			assertTrue(e.getMessage().contains("ERROR: ChEBI Identifier \""+identifier+"\" is deleted, obsolete, or not yet released."));
		}
		catch (IOException e)
		{
			e.printStackTrace();
			fail();
		}
		catch (RuntimeException e)
		{
			assertTrue(e.getMessage().contains("(this should trigger a RuntimeException)"));
		}
	}
	
	@Test
	public void testChEBIWSNullResponse() throws InvalidAttributeException, Exception
	{
		String identifier = "112233";
		when(molecule1.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn(identifier);
		Map<GKInstance, String> failedEntities = new HashMap<>();
		try
		{
			when(chebiClient.getCompleteEntity(identifier)).thenReturn(null);
			ChebiDataRetriever retriever = new ChebiDataRetriever(false);
			retriever.retrieveUpdatesFromChebi(Arrays.asList(molecule1), failedEntities);
		}
		catch (ChebiWebServiceFault_Exception e)
		{
			e.printStackTrace();
			fail();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			fail();
		}
		catch (RuntimeException e)
		{
			e.printStackTrace();
			fail();
		}
		System.out.println(failedEntities);
		assertTrue(failedEntities.size() > 0);
		assertTrue(failedEntities.get(molecule1).equals("ChEBI WebService response was NULL."));
	}
	
	@Test
	public void testChEBIGetEntityOK() throws InvalidAttributeException, Exception
	{
		Entity chebiEntity = Mockito.mock(Entity.class);
		String identifier = "112233";
		when(molecule1.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn(identifier);
		when(chebiClient.getCompleteEntity(identifier)).thenReturn(chebiEntity);
		ChebiDataRetriever retriever = new ChebiDataRetriever(false);
		Map<Long, Entity> mapping = retriever.retrieveUpdatesFromChebi(Arrays.asList(molecule1), new HashMap<GKInstance, String>());
		System.out.println(mapping.toString());
		assertTrue(mapping.size() > 0);
	}
	
	@Test
	public void testChEBINullIdentifier() throws IOException
	{
		ChebiDataRetriever retriever = new ChebiDataRetriever(true);
		Map<GKInstance, String> failedEntities = new HashMap<>();
		retriever.retrieveUpdatesFromChebi(Arrays.asList(molecule1), failedEntities);
		System.out.println(failedEntities);
		assertTrue(failedEntities.size() > 0);
		assertTrue(failedEntities.get(molecule1).contains(" has an empty/NULL identifier."));
	}
	
	@Test
	public void testChEBIDataRetrieverCache() throws InvalidAttributeException, Exception
	{
		Entity chebiEntity = Mockito.mock(Entity.class);
		String identifier = "112233";
		when(molecule1.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn(identifier);
		when(chebiClient.getCompleteEntity(identifier)).thenReturn(chebiEntity);
		ChebiDataRetriever retriever = new ChebiDataRetriever(true);
		Map<Long, Entity> mapping = retriever.retrieveUpdatesFromChebi(Arrays.asList(molecule1), new HashMap<GKInstance, String>());
		System.out.println(mapping.toString());
		assertTrue(mapping.size() > 0);
	}
}
