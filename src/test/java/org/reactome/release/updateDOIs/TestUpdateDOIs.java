package org.reactome.release.updateDOIs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestUpdateDOIs {

	@Mock
	private MySQLAdaptor mockAdaptor;

	@Mock
	private GKInstance instance;

	private static final Path MOCK_REPORT_PATH = Paths.get("reportPath");
	private static final int MOCK_RELEASE_NUMBER = 70;
	private static final long MOCK_PERSON_DBID = 12345L;

	@Test
	public void testfindNewDOIsAndUpdate() throws Exception
	{
		UpdateDOIs check = new UpdateDOIs();
		check.setAdaptors(mockAdaptor, mockAdaptor);

		Collection<GKInstance> testResults = Arrays.asList(instance);
		Mockito.when((GKInstance) instance.getAttributeValue("stableIdentifier")).thenReturn(instance);
		Mockito.when(instance.getDisplayName()).thenReturn("HSA-12345");

		Mockito.when(instance.getAttributeValue("name")).thenReturn("Name Value");
		Mockito.when(instance.getAttributeValue("DB_ID")).thenReturn("67890");

		Mockito.when(mockAdaptor.fetchInstanceByAttribute("Pathway", "doi", "NOT REGEXP", "^10.3180")).thenReturn(testResults);
		Mockito.when(mockAdaptor.fetchInstanceByAttribute("Pathway", "DB_ID", "=", "67890")).thenReturn(testResults);

		check.findAndUpdateDOIs(MOCK_PERSON_DBID, MOCK_REPORT_PATH, MOCK_RELEASE_NUMBER, true);
	}

	@Test
	public void testfindNewDOIsAndUpdateEmptyList() throws Exception
	{
		UpdateDOIs check = new UpdateDOIs();
		check.setAdaptors(mockAdaptor, mockAdaptor);

		Mockito.when(mockAdaptor.fetchInstanceByAttribute("Pathway", "doi", "NOT REGEXP", "^10.3180")).thenReturn(new ArrayList<GKInstance>());
		Mockito.when(mockAdaptor.fetchInstanceByAttribute("Pathway", "DB_ID", "=", "67890")).thenReturn(new ArrayList<GKInstance>());

		check.findAndUpdateDOIs(MOCK_PERSON_DBID, MOCK_REPORT_PATH, MOCK_RELEASE_NUMBER, true);
	}

	@Test
	public void testfindNewDOIsAndUpdateDeepEmptyList() throws Exception
	{
		UpdateDOIs check = new UpdateDOIs();
		check.setAdaptors(mockAdaptor, mockAdaptor);

		Collection<GKInstance> testResults = Arrays.asList(instance);

		Mockito.when((GKInstance) instance.getAttributeValue("stableIdentifier")).thenReturn(instance);
		Mockito.when(instance.getAttributeValue("name")).thenReturn("Name Value");
		Mockito.when(instance.getAttributeValue("DB_ID")).thenReturn("67890");

		Mockito.when(mockAdaptor.fetchInstanceByAttribute("Pathway", "doi", "NOT REGEXP", "^10.3180")).thenReturn(testResults);
		Mockito.when(mockAdaptor.fetchInstanceByAttribute("Pathway", "DB_ID", "=", "67890")).thenReturn(new ArrayList<GKInstance>());

		check.findAndUpdateDOIs(MOCK_PERSON_DBID, MOCK_REPORT_PATH, MOCK_RELEASE_NUMBER,true);
	}
}