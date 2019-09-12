package org.reactome.util.general;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CollectionUtils {

	/**
	 * Returns a list with the elements in the passed collection or an empty list if the passed collection is
	 * null.  This method is meant to guard against null values to eliminate the need for conditionals checking
	 * for null and to allow uses such as iteration in loops directly.
	 * @param collection Collection of type E
	 * @return List of type E with the elements in the collection or an empty List of type E if the collection is null
	 */
	public static <E> List<E> safeList(Collection<E> collection) {
		return Optional.ofNullable(collection).map(CollectionUtils::convertToList).orElse(Collections.emptyList());
	}

	/**
	 * Returns a set with the elements in the passed collection or an empty set if the passed collection is
	 * null.  This method is meant to guard against null values to eliminate the need for conditionals checking
	 * for null and to allow uses such as iteration in loops directly.
	 * @param collection Collection of type E
	 * @return Set of type E with the elements in the collection or an empty Set of type E if the collection is null
	 */
	public static <E> Set<E> safeSet(Collection<E> collection) {
		return Optional.ofNullable(collection).map(CollectionUtils::convertToSet).orElse(Collections.emptySet());
	}

	private static <E> List<E> convertToList(Collection<E> collection) {
		return new ArrayList<>(collection);
	}

	private static <E> Set<E> convertToSet(Collection<E> collection) {
		return new HashSet<>(collection);
	}
}
