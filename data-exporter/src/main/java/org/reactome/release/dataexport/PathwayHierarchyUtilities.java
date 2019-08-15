package org.reactome.release.dataexport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilities class for retrieving Reactome Pathway Hierarchy information. This class has logic
 * for querying relationships between Reaction Like Events and their Pathways, child and parent
 * Pathways, and Top Level Pathways in Reactome.
 * @author jweiser
 */
public class PathwayHierarchyUtilities {
	private static final Logger logger = LogManager.getLogger();

	private static Map<Session, Map<Long, Set<Long>>> rleToPathwayIdCache = new HashMap<>();
	private static Map<Session, Map<Long, Set<Long>>> pathwayHierarchyCache = new HashMap<>();
	private static Map<Session, Set<Long>> topLevelPathwayIdsCache = new HashMap<>();

	/**
	 * Retrieves, from the graph database, a Map of Reaction Like Event database identifiers
	 * to the Set of Pathway database identifiers in which each Reaction Like Event participates
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Map of Reaction Like Event identifier to Set of Pathway identifiers
	 * @throws IllegalStateException Thrown if the graphDBSession parameter is null
	 */
	public static Map<Long, Set<Long>> fetchRLEIdToPathwayId(Session graphDBSession) {
		if (graphDBSession == null) {
			throw new IllegalStateException("Neo4j driver session parameter is null");
		}

		if (rleToPathwayIdCache.containsKey(graphDBSession)) {
			return rleToPathwayIdCache.get(graphDBSession);
		}

		logger.info("Computing RLE id to Pathway id");

		StatementResult statementResult = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rle:ReactionLikeEvent)<-[:hasEvent*]-(p:Pathway)",
				"RETURN DISTINCT rle.dbId, p.dbId"
			)
		);

		Map<Long, Set<Long>> rleToPathwayId = new HashMap<>();
		while (statementResult.hasNext()) {
			Record record = statementResult.next();

			long reactionLikeEventId = record.get("rle.dbId").asLong();
			long pathwayId = record.get("p.dbId").asLong();

			rleToPathwayId
				.computeIfAbsent(reactionLikeEventId, k -> new HashSet<>())
				.add(pathwayId);
		}

		rleToPathwayIdCache.put(graphDBSession, rleToPathwayId);

		logger.info("Finished computing RLE id to Pathway id");

		return rleToPathwayId;
	}

	/**
	 * Retrieves, from the graph database, a Map of child Pathway database identifiers
	 * to the Set of parent Pathway database identifiers.  If a pathway has no parent pathway,
	 * it is not included as a key in the returned map (though, it may be in a value if it itself is
	 * a parent of another pathway)
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Map of child Pathway identifier to Set of Parent pathway identifiers
	 */
	public static Map<Long, Set<Long>> fetchPathwayHierarchy(Session graphDBSession) {
		if (pathwayHierarchyCache.containsKey(graphDBSession)) {
			return pathwayHierarchyCache.get(graphDBSession);
		}

		if (graphDBSession == null) {
			throw new IllegalStateException("Neo4j driver session parameter is null");
		}

		logger.info("Computing Pathway Hierarchy");

		StatementResult statementResult = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (p:Pathway)<-[:hasEvent]-(pp:Pathway)",
				"RETURN DISTINCT p.dbId, pp.dbId"
			)
		);

		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();
		while (statementResult.hasNext()) {
			Record record = statementResult.next();

			long pathwayId = record.get("p.dbId").asLong();
			long parentPathwayId = record.get("pp.dbId").asLong();

			Set<Long> parentPathwayIds = pathwayHierarchy.computeIfAbsent(pathwayId, k -> new HashSet<>());
			parentPathwayIds.add(parentPathwayId);
		}

		pathwayHierarchyCache.put(graphDBSession, pathwayHierarchy);

		logger.info("Finished computing Pathway Hierarchy");

		return pathwayHierarchy;
	}

	/**
	 * Retrieves, from the graph database, the Set of Pathway database identifiers that
	 * are marked with the label TopLevelPathway
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Set of database identifiers for top level pathways
	 */
	public static Set<Long> getTopLevelPathwayIds(Session graphDBSession) {
		if (topLevelPathwayIdsCache.containsKey(graphDBSession)) {
			return topLevelPathwayIdsCache.get(graphDBSession);
		}

		logger.info("Computing Top Level Pathway ids");

		Set<Long> topLevelPathwayIds = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (p:TopLevelPathway)",
				"RETURN p.dbId"
			)
		)
		.stream()
		.map(record -> record.get("p.dbId").asLong())
		.collect(Collectors.toSet());

		topLevelPathwayIdsCache.put(graphDBSession, topLevelPathwayIds);

		logger.info("Finished computing Top Level Pathway ids");

		return topLevelPathwayIds;
	}


	/**
	 * Finds the Set of Pathway database identifiers for the top level pathways of a given pathway id
	 * within a provided pathway hierarchy
	 * @param pathwayId Database identifier of the pathway for which to find top level pathway ids
	 * @param pathwayHierarchy Map of child pathway database identifier to parent pathway database identifiers
	 * @return Set of database identifiers for top level pathways
	 */
	public static Set<Long> findTopLevelPathwayIds(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		checkPathwayIdAndHierarchyAreValid(pathwayId, pathwayHierarchy);

		Set<Long> topLevelPathways = new HashSet<>();

		Set<Long> parentPathwayIds = pathwayHierarchy.get(pathwayId);
		if (parentPathwayIds == null || parentPathwayIds.isEmpty()) {
			topLevelPathways.add(pathwayId);
			return topLevelPathways;
		} else {
			for (Long parentPathwayId : parentPathwayIds) {
				topLevelPathways.addAll(findTopLevelPathwayIds(parentPathwayId, pathwayHierarchy));
			}
		}

		return topLevelPathways;
	}

	/**
	 * Checks that the provided Pathway Hierarchy exists and contains the provided Pathway Id.
	 * @param pathwayId Database identifier of the pathway for which to find top level pathway ids
	 * @param pathwayHierarchy Map of child pathway database identifier to parent pathway database identifiers
	 * @throws IllegalStateException Thrown if the Pathway Hierarchy map provided is null or empty
	 */
	private static void checkPathwayIdAndHierarchyAreValid(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		// A pathway hierarchy must be provided to find a top level pathway
		if (pathwayHierarchy == null || pathwayHierarchy.isEmpty()) {
			throw new IllegalStateException("Pathway Hierarchy has no values");
		}

		// The pathway id is invalid if does not exist in the hierarchy
		if (!existsInHierarchy(pathwayId, pathwayHierarchy)) {
			logger.warn("Pathway id " + pathwayId + " does not exist in the provided Pathway Hierarchy");
		}
	}

	/**
	 * Checks if the provided pathwayId exists as a key (child pathway identifier) or within a value (set of parent
	 * pathway identifiers)
	 * @param pathwayId Database identifier of the pathway for which to find top level pathway ids
	 * @param pathwayHierarchy Map of child pathway database identifier to parent pathway database identifiers
	 * @return <code>true</code> if the pathway id exists as a key or within a value (i.e. set of numeric ids) for
	 * the pathwayHierarchy map. Returns <code>false</code> otherwise.
	 */
	private static boolean existsInHierarchy(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		return pathwayHierarchy.containsKey(pathwayId) ||
			pathwayHierarchy
			.keySet()
			.stream()
			.flatMap(id -> pathwayHierarchy.get(id).stream())
			.collect(Collectors.toList())
			.contains(pathwayId);
	}
}