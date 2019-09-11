package org.reactome.util.general;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.reactome.util.general.CollectionUtils.safeList;
import static org.reactome.util.general.CollectionUtils.safeSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class CollectionUtilsTest {

	private Set<Integer> testSet = new HashSet<>(Arrays.asList(1,2,3));
	private List<Integer> testListWithUniqueValues = Arrays.asList(1,2,3);
	private List<Integer> testListWithDuplicateValues = Arrays.asList(1,1,2,3);

	@Test
	public void nullCollectionForSafeListReturnsEmptyList() {
		List<Integer> safeList = safeList(null);

		assertThat(safeList, is(empty()));
	}

	@Test
	public void nullCollectionForSafeSetReturnsEmptySet() {
		Set<Integer> safeSet = safeSet(null);

		assertThat(safeSet, is(empty()));
	}

	@Test
	public void emptyListForSafeListReturnsEmptyList() {
		List<Integer> safeList = safeList(new ArrayList<>());

		assertThat(safeList, is(empty()));
	}

	@Test
	public void emptyListForSafeSetReturnsEmptySet() {
		Set<Integer> safeSet = safeSet(new ArrayList<>());

		assertThat(safeSet, is(empty()));
	}

	@Test
	public void emptySetForSafeListReturnsEmptyList() {
		List<Integer> safeList = safeList(new HashSet<>());

		assertThat(safeList, is(empty()));
	}

	@Test
	public void emptySetForSafeSetReturnsEmptySet() {
		Set<Integer> safeSet = safeSet(new HashSet<>());

		assertThat(safeSet, is(empty()));
	}

	@Test
	public void listForSafeListReturnsEqualList() {
		List<Integer> safeList = safeList(testListWithDuplicateValues);

		assertThat(safeList, is(equalTo(testListWithDuplicateValues)));
	}

	@Test
	public void listWithUniqueElementsForSafeSetReturnsEquivalentSet() {
		Set<Integer> safeSet = safeSet(testListWithUniqueValues);

		assertThat(safeSet, is(equalTo(testSet)));
	}

	@Test
	public void listWithDuplicateElementsForSafeSetReturnsEquivalentSet() {
		Set<Integer> safeSet = safeSet(testListWithDuplicateValues);

		assertThat(safeSet, is(equalTo(testSet)));
	}

	@Test
	public void setForSafeListReturnsEquivalentList() {
		List<Integer> safeList = safeList(testSet);

		assertThat(safeList, is(equalTo(testListWithUniqueValues)));
	}
}
