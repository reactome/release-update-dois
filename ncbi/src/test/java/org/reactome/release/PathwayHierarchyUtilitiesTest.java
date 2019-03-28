package org.reactome.release;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


public class PathwayHierarchyUtilitiesTest {
	@Mock private Session graphDBSession;
	@Mock private StatementResult statementResult;
	@Mock private Record record;
	@Mock private Value value;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Test
	public void findTopLevelPathwayIdsWithGrandParentPathways() {
		long pathwayId = 1L;
		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();
		pathwayHierarchy.put(1L, new HashSet<>(Arrays.asList(2L, 3L)));
		pathwayHierarchy.put(2L, new HashSet<>(Arrays.asList(4L, 5L)));

		Set<Long> topLevelPathwayIds = PathwayHierarchyUtilities.findTopLevelPathwayIds(pathwayId, pathwayHierarchy);

		// The top level pathways for id of "1" should be "3" (parent pathway) and
		// "4" and "5" (grandparent pathways through intermediate parent pathway "2")
		assertThat(topLevelPathwayIds, is(new HashSet<>(Arrays.asList(3L, 4L, 5L))));
	}

	@Test
	public void findTopLevelPathwayIdsWithNoParents() {
		long pathwayId = 1L;
		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();
		pathwayHierarchy.put(1L, new HashSet<>());

		Set<Long> topLevelPathwayIds = PathwayHierarchyUtilities.findTopLevelPathwayIds(pathwayId, pathwayHierarchy);

		// If the pathway has no parents in the pathway hierarchy, it is a top level pathway
		assertThat(topLevelPathwayIds, is(new HashSet<>(Collections.singletonList(1L))));
	}

	@Test
	public void findTopLevelPathwayIdsWithEmptyPathwayHierarchy() throws IllegalStateException {
		long pathwayId = 1L;
		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();

		expectedException.expect(IllegalStateException.class);
		expectedException.expectMessage("hierarchy has no values");
		PathwayHierarchyUtilities.findTopLevelPathwayIds(pathwayId, pathwayHierarchy);
	}

	@Test
	public void findTopLevelPathwayIdsWithUnknownPathwayId() throws IllegalStateException {
		long pathwayId = 1L;
		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();
		pathwayHierarchy.put(2L, new HashSet<>(Arrays.asList(3L, 4L ,5L)));

		expectedException.expect(IllegalStateException.class);
		expectedException.expectMessage(pathwayId + " does not exist");
		PathwayHierarchyUtilities.findTopLevelPathwayIds(pathwayId, pathwayHierarchy);
	}

	@Test
	public void fetchPathwayHierarchy() {
		MockitoAnnotations.initMocks(this);

		when(graphDBSession.run(any(String.class))).thenReturn(statementResult);
		when(statementResult.hasNext()).thenReturn(
			true, true,
			true, true,
			true, true,
			true, true,
			false);
		when(statementResult.next()).thenReturn(record);
		when(record.get(any(String.class))).thenReturn(value);
		when(value.asLong()).thenReturn(
			1L, 2L,
			1L, 3L,
			2L, 4L,
			3L, 5L
		);

		Map<Long, Set<Long>> pathwayHierarchy = PathwayHierarchyUtilities.fetchPathwayHierarchy(graphDBSession);

		assertThat(pathwayHierarchy.get(1L), hasSize(2));
		assertThat(pathwayHierarchy.get(1L), contains(2L, 3L));
		assertThat(pathwayHierarchy.get(2L), hasSize(1));
		assertThat(pathwayHierarchy.get(2L), contains(4L));
		assertThat(pathwayHierarchy.get(3L), hasSize(1));
		assertThat(pathwayHierarchy.get(3L), contains(5L));
		assertThat(pathwayHierarchy.get(4L), nullValue());
	}

	@Test
	public void topLevelPathwayNameCorrection() {
		PathwayHierarchyUtilities.ReactomeEvent reactomeEvent =
			new PathwayHierarchyUtilities.ReactomeEvent(1L, "something to do with sugars", "R-HSA-123456");

		assertThat(reactomeEvent.getName(), equalTo("Metabolism of sugars"));
	}
}