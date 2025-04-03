package org.reactome.release.updateDOIs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.reactome.server.service.model.GKInstance;
import org.reactome.server.service.model.ReactomeJavaConstants;
import org.reactome.server.service.persistence.Neo4JAdaptor;

@RunWith(MockitoJUnitRunner.class)
public class TestUpdateDOIs {

	@Mock
	private Neo4JAdaptor mockAdaptor;

	@Mock
	private PropertyManager mockPropertyManager;

	@Mock
	private GKInstance instance;

	private static final Path MOCK_REPORT_PATH = Paths.get("reportPath");
	private static final int MOCK_RELEASE_NUMBER = 70;
	private static final long MOCK_PERSON_DBID = 12345L;

	@Test
	public void testfindNewDOIsAndUpdate() throws Exception {
		setUpMockPropertyManager();
		UpdateDOIs check = new UpdateDOIs(mockPropertyManager);

		Collection<GKInstance> testResults = Arrays.asList(instance);
		Mockito.when((GKInstance) instance.getAttributeValue("stableIdentifier"))
			.thenReturn(instance);
		Mockito.when(instance.getDisplayName())
			.thenReturn("HSA-12345");

		Mockito.when(instance.getDBID())
			.thenReturn(67890L);

		Mockito.when(mockAdaptor.fetchInstancesByClass(ReactomeJavaConstants.Pathway))
			.thenReturn(testResults);

		check.findAndUpdateDOIs(MOCK_REPORT_PATH.toString());
	}

	@Test
	public void testfindNewDOIsAndUpdateEmptyList() throws Exception {
		setUpMockPropertyManager();
		UpdateDOIs check = new UpdateDOIs(mockPropertyManager);

		Mockito.when(mockAdaptor.fetchInstancesByClass(ReactomeJavaConstants.Pathway))
			.thenReturn(new ArrayList<GKInstance>());

		check.findAndUpdateDOIs(MOCK_REPORT_PATH.toString());
	}

	@Test
	public void testfindNewDOIsAndUpdateDeepEmptyList() throws Exception {
		setUpMockPropertyManager();
		UpdateDOIs check = new UpdateDOIs(mockPropertyManager);

		Collection<GKInstance> testResults = Arrays.asList(instance);

		Mockito.when((GKInstance) instance.getAttributeValue("stableIdentifier"))
			.thenReturn(instance);
		Mockito.when(instance.getDisplayName())
			.thenReturn("Name Value");
		Mockito.when(instance.getDBID())
			.thenReturn(67890L);

		Mockito.when(mockAdaptor.fetchInstancesByClass(ReactomeJavaConstants.Pathway))
			.thenReturn(testResults);

		check.findAndUpdateDOIs(MOCK_REPORT_PATH.toString());
	}

	private void setUpMockPropertyManager() {
		Mockito.when(mockPropertyManager.getGKCentralDbAdaptor())
			.thenReturn(mockAdaptor);
		Mockito.when(mockPropertyManager.getReleaseDbAdaptor())
			.thenReturn(mockAdaptor);
		Mockito.when(mockPropertyManager.getPersonId())
			.thenReturn(MOCK_PERSON_DBID);
		Mockito.when(mockPropertyManager.getReleaseNumber())
			.thenReturn(MOCK_RELEASE_NUMBER);
		Mockito.when(mockPropertyManager.getTestMode())
			.thenReturn(true);
	}
}