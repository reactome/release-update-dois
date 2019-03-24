package org.reactome.release;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


public class PathwayHierarchyUtilitiesTest {
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
}