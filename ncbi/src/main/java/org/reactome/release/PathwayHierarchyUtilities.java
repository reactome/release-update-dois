package org.reactome.release;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Values;

import java.util.*;
import java.util.stream.Collectors;

public class PathwayHierarchyUtilities {
	private static Map<Long, Set<Long>> pathwayHierarchy;
	private static Map<Long, TopLevelPathway> topLevelPathwaysCache = new HashMap<>();

	public static Map<Long, Set<TopLevelPathway>> fetchTopLevelPathwayHierarchy(Session graphDBSession) {
		if (pathwayHierarchy == null) {
			pathwayHierarchy = fetchPathwayHierarchy(graphDBSession);
		}

		Map<Long, Set<TopLevelPathway>> topLevelPathwayHierarchy = new HashMap<>();
		for (Long pathwayId : pathwayHierarchy.keySet()) {
			topLevelPathwayHierarchy
				.computeIfAbsent(pathwayId, k -> new HashSet<>())
				.addAll(
					findTopLevelPathwayIds(pathwayId, pathwayHierarchy)
						.stream()
						.map(
							topLevelPathwayId -> getTopLevelPathwayObject(graphDBSession, topLevelPathwayId)
						)
						.collect(Collectors.toList())
				);
		}
		return topLevelPathwayHierarchy;
	}

	public static Map<Long, Set<Long>> fetchPathwayHierarchy(Session graphDBSession) {
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
		};

		return pathwayHierarchy;
	}

	public static Set<Long> findTopLevelPathwayIds(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		// A pathway hierarchy must be provided to find a top level pathway
		if (pathwayHierarchy == null || pathwayHierarchy.isEmpty()) {
			throw new IllegalStateException("Pathway hierarchy has no values");
		}

		// The pathway id is invalid if does not exist in the hierarchy
		if (!existsInHierarchy(pathwayId, pathwayHierarchy)) {
			throw new IllegalStateException("Pathway id " + pathwayId + " does not exist in the provided pathway " +
											"hierarchy");
		}

		Set<Long> topLevelPathways = new HashSet<>();

		Set<Long> parentPathwayIds = pathwayHierarchy.get(pathwayId);
		if (parentPathwayIds == null || parentPathwayIds.isEmpty()) {
			topLevelPathways.add(pathwayId);
			return topLevelPathways;
		} else {
			for (Long parentPathwayId :  parentPathwayIds) {
				topLevelPathways.addAll(findTopLevelPathwayIds(parentPathwayId, pathwayHierarchy));
			}
		}

		return topLevelPathways;
	}

	private static boolean existsInHierarchy(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		return pathwayHierarchy.containsKey(pathwayId) ||
			pathwayHierarchy
			.keySet()
			.stream()
			.flatMap(id -> pathwayHierarchy.get(id).stream())
			.collect(Collectors.toList())
			.contains(pathwayId);
	}

	private static TopLevelPathway getTopLevelPathwayObject(Session graphDBSession, long topLevelPathwayId) {
		TopLevelPathway topLevelPathway = topLevelPathwaysCache.get(topLevelPathwayId);
		if (topLevelPathway != null) {
			return topLevelPathway;
		}

		topLevelPathway = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (p:Pathway)",
				"WHERE p.dbId = {topLevelPathwayId}",
				"RETURN p.displayName, p.stId"
			),
			Values.parameters("topLevelPathwayId", topLevelPathwayId)
		)
			.stream()
			.map(
				record -> new TopLevelPathway(
					record.get("p.displayName").asString(),
					record.get("p.stId").asString()
				)
			)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No top level pathway for db id " + topLevelPathwayId));

		topLevelPathwaysCache.put(topLevelPathwayId, topLevelPathway);
		return topLevelPathway;
	}


	public static class TopLevelPathway {
		private String name;
		private String stableIdentifier;

		public TopLevelPathway(String name, String stableIdentifier) {
			this.name = name;
			this.stableIdentifier = stableIdentifier;
		}

		public String getName() {
			return fixName(this.name);
		}

		private String fixName(String name) {
			Map<String, String> patternToReplacement = new TreeMap<>();
			patternToReplacement.put("amino acids", "Metabolism of nitrogenous molecules");
			patternToReplacement.put("Cycle, Mitotic", "Cell Cycle (Mitotic)");
			patternToReplacement.put("L13a-mediated translational", "L13a-mediated translation");
			patternToReplacement.put("Abortive initiation after", "Abortive initiation");
			patternToReplacement.put("Formation of the Cleavage and Polyadenylation", "Cleavage and Polyadenylation");
			patternToReplacement.put("energy metabolism", "Energy Metabolism");
			patternToReplacement.put("sugars", "Metabolism of sugars");

			return patternToReplacement
					.keySet()
					.stream()
					.filter(name::matches)
					.map(patternToReplacement::get)
					.reduce((first, second) -> second) // Get last element in stream
					.orElse(name); // Default to original name if no replacement
		}

		public String getStableIdentifier() {
			return stableIdentifier;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}

			if (!(o instanceof TopLevelPathway)) {
				return false;
			}

			TopLevelPathway oTLP = (TopLevelPathway) o;

			return	getName().equals(oTLP.getName()) &&
					getStableIdentifier().equals(oTLP.getStableIdentifier());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getName(), getStableIdentifier());
		}

		@Override
		public String toString() {
			return String.join("\t",
				getName(),
				getStableIdentifier()
			);
		}
	}
}
