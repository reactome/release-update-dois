package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PathwayHierarchyUtilities {
	private static final Logger logger = LogManager.getLogger();

	private static Map<String, Set<ReactomeEvent>> uniprotAccessionToTopLevelPathways;
	private static Map<String, Set<ReactomeEvent>> uniprotAccessionToReactomeEvent;
	private static Map<String, Set<Long>> uniprotAccessionToReactionLikeEventId;
	private static Map<Long, Set<Long>> rleToPathwayId;
	private static Map<Long, Set<Long>> pathwayHierarchy;
	private static Set<Long> topLevelPathwayIds;
	private static Map<Long, Set<ReactomeEvent>> topLevelPathwayHierarchy;
	private static Map<Long, ReactomeEvent> eventCache;

	public static Map<String, Set<ReactomeEvent>> fetchUniProtAccessionToTopLevelPathways(Session graphDBSession) {
		if (uniprotAccessionToTopLevelPathways != null) {
			return uniprotAccessionToTopLevelPathways;
		}

		uniprotAccessionToTopLevelPathways =
			fetchUniProtAccessionToReactomeEvents(graphDBSession).entrySet().stream().collect(Collectors.toMap(
			Map.Entry::getKey,
			entry -> entry.getValue()
					.stream()
					.filter(reactomeEvent -> getTopLevelPathwayIds(graphDBSession).contains(reactomeEvent.getDbId()))
					.collect(Collectors.toSet())
		));

		return uniprotAccessionToTopLevelPathways;
	}

	public static Map<String, Set<ReactomeEvent>> fetchUniProtAccessionToReactomeEvents(Session graphDBSession) {
		if (uniprotAccessionToReactomeEvent != null) {
			return uniprotAccessionToReactomeEvent;
		}

		System.out.println("Computing uniprot to reactome events " + LocalTime.now());
		Map<Long, ReactomeEvent> eventCache = fetchReactomeEventCache(graphDBSession);
		Map<String, Set<Long>> uniprotAccessionToReactionLikeEventId = fetchUniProtAccessionToRLEId(graphDBSession);
		Map<Long, Set<Long>> rleToPathwayId = fetchRLEIdToPathwayId(graphDBSession);
		Map<Long, Set<Long>> pathwayHierarchy = fetchPathwayHierarchy(graphDBSession);

		AtomicInteger count = new AtomicInteger(0);
		uniprotAccessionToReactomeEvent = new ConcurrentHashMap<>();
		uniprotAccessionToReactionLikeEventId.keySet().parallelStream().forEach(uniprotAccession -> {
			//System.out.println("Finding events for " + uniprotAccession + " " + LocalTime.now());
			Set<Long> reactionLikeEventIds =
				uniprotAccessionToReactionLikeEventId.computeIfAbsent(uniprotAccession, k -> new HashSet<>());

			// Add RLEs containing UniProt accession
			//System.out.println("Finding RLEs for " + uniprotAccession + " " + LocalTime.now());
			uniprotAccessionToReactomeEvent
				.computeIfAbsent(uniprotAccession, k -> new HashSet<>())
				.addAll(reactionLikeEventIds.stream().map(id -> eventCache.get(id)).collect(Collectors.toSet()));
			//System.out.println("Fininshed finding RLEs for " + uniprotAccession + " " + LocalTime.now());

			//System.out.println("Finding pathways for " + uniprotAccession + " " + LocalTime.now());
			for (long reactionLikeEventId : reactionLikeEventIds) {
				Set<Long> lowerLevelPathwayIds = rleToPathwayId.computeIfAbsent(reactionLikeEventId,
																			  k -> new HashSet<>());

				//System.out.println("Finding low level pathways for " + uniprotAccession + " " + LocalTime.now());
				// Add lower level pathways containing UniProt accession
				uniprotAccessionToReactomeEvent
					.computeIfAbsent(uniprotAccession, k -> new HashSet<>())
					.addAll(lowerLevelPathwayIds.stream().map(id -> eventCache.get(id)).collect(Collectors.toSet()));
				//System.out.println("Finished finding low level pathways for " + uniprotAccession + " " + LocalTime
				// .now());

				//System.out.println("Finding top level pathways for " + uniprotAccession + " " + LocalTime.now());
				// Add higher level pathways containing UniProt accession
				uniprotAccessionToReactomeEvent
					.computeIfAbsent(uniprotAccession, k -> new HashSet<>())
					.addAll(
						lowerLevelPathwayIds
							.stream()
							.flatMap(id -> findAncestorPathwayIds(id, pathwayHierarchy).stream())
							.map(id -> eventCache.get(id))
							.collect(Collectors.toSet())
					);
				//System.out.println("Finished finding top level pathways for " + uniprotAccession + " " + LocalTime
				// .now());
			}
			//System.out.println("Finished finding pathways for " + uniprotAccession + " " + LocalTime.now());
			//System.out.println("Finished finding events for " + uniprotAccession + " " + LocalTime.now());

			if (count.getAndIncrement() % 10000 == 0) {
				System.out.println("Processed events for " + count.get() + " out of "
								   + uniprotAccessionToReactionLikeEventId.size() + " (" +
								   String.format("%.2f", (100 * (double) count.get()) / uniprotAccessionToReactionLikeEventId.size()) + "%) " +
								   LocalTime.now());
			}
		});
		//System.out.println("Finished computing uniprot to reactome events " + LocalTime.now());

		return uniprotAccessionToReactomeEvent;
	}


	public static Map<String, Set<Long>> fetchUniProtAccessionToRLEId(Session graphDBSession) {
		if (uniprotAccessionToReactionLikeEventId != null) {
			return uniprotAccessionToReactionLikeEventId;
		}

		System.out.println("Computing uniprot to RLE id " + LocalTime.now());
		StatementResult statementResult = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rgp:ReferenceGeneProduct)<-[:referenceEntity|:referenceSequence|:hasModifiedResidue]-" +
				"(ewas:EntityWithAccessionedSequence)<-[:hasComponent|:hasMember|:hasCandidate|:repeatedUnit" +
				"|:input|:output|:catalystActivity|:physicalEntity*]-(rle:ReactionLikeEvent)",
				"RETURN DISTINCT rgp.identifier, rle.dbId"
			)
		);

		uniprotAccessionToReactionLikeEventId = new HashMap<>();
		while (statementResult.hasNext()) {
			Record record = statementResult.next();

			String uniprotAccession = record.get("rgp.identifier").asString();
			long reactionLikeEventId = record.get("rle.dbId").asLong();

			uniprotAccessionToReactionLikeEventId
				.computeIfAbsent(uniprotAccession, k -> new HashSet<>())
				.add(reactionLikeEventId);
		};
		System.out.println("Finished computing uniprot to RLE id " + LocalTime.now());

		return uniprotAccessionToReactionLikeEventId;
	}

	public static Map<Long, Set<ReactomeEvent>> fetchTopLevelPathwayHierarchy(Session graphDBSession) {
		if (topLevelPathwayHierarchy != null) {
			return topLevelPathwayHierarchy;
		}

		System.out.println("Computing top level pathway hierarchy " + LocalTime.now());
		Map<Long, Set<Long>> pathwayHierarchy = fetchPathwayHierarchy(graphDBSession);

		topLevelPathwayHierarchy = new HashMap<>();
		for (long pathwayId : pathwayHierarchy.keySet()) {
			topLevelPathwayHierarchy
				.computeIfAbsent(pathwayId, k -> new HashSet<>())
				.addAll(
					findTopLevelPathwayIds(pathwayId, pathwayHierarchy)
						.stream()
						.map(topLevelPathwayId -> fetchReactomeEventCache(graphDBSession).get(topLevelPathwayId))
						.collect(Collectors.toList())
				);
		}
		System.out.println("Finished computing top level pathway hierarchy " + LocalTime.now());
		return topLevelPathwayHierarchy;
	}

	public static Map<Long, Set<Long>> fetchRLEIdToPathwayId(Session graphDBSession) {
		if (rleToPathwayId != null) {
			return rleToPathwayId;
		}

		if (graphDBSession == null) {
			throw new IllegalStateException("Neo4j driver session parameter is null");
		}

		System.out.println("Computing rle id to pathway id " + LocalTime.now());
		StatementResult statementResult = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rle:ReactionLikeEvent)<-[:hasEvent]-(p:Pathway)",
				"RETURN DISTINCT rle.dbId, p.dbId"
			)
		);

		rleToPathwayId = new HashMap<>();
		while (statementResult.hasNext()) {
			Record record = statementResult.next();

			long reactionLikeEventId = record.get("rle.dbId").asLong();
			long pathwayId = record.get("p.dbId").asLong();

			rleToPathwayId
				.computeIfAbsent(reactionLikeEventId, k -> new HashSet<>())
				.add(pathwayId);
		};
		System.out.println("Finished computing rle id to pathway id " + LocalTime.now());

		return rleToPathwayId;
	}

	public static Map<Long, Set<Long>> fetchPathwayHierarchy(Session graphDBSession) {
		if (pathwayHierarchy != null) {
			return pathwayHierarchy;
		}

		if (graphDBSession == null) {
			throw new IllegalStateException("Neo4j driver session parameter is null " + LocalTime.now());
		}

		System.out.println("Computing pathway hierarchy " + LocalTime.now());
		StatementResult statementResult = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (p:Pathway)<-[:hasEvent]-(pp:Pathway)",
				"RETURN DISTINCT p.dbId, pp.dbId"
			)
		);

		pathwayHierarchy = new HashMap<>();
		while (statementResult.hasNext()) {
			Record record = statementResult.next();

			long pathwayId = record.get("p.dbId").asLong();
			long parentPathwayId = record.get("pp.dbId").asLong();

			Set<Long> parentPathwayIds = pathwayHierarchy.computeIfAbsent(pathwayId, k -> new HashSet<>());
			parentPathwayIds.add(parentPathwayId);
		};
		System.out.println("Finished computing pathway hierarchy " + LocalTime.now());

		return pathwayHierarchy;
	}

	private static Set<Long> getTopLevelPathwayIds(Session graphDBSession) {
		if (topLevelPathwayIds != null) {
			return topLevelPathwayIds;
		}

		System.out.println("Computing top level pathway ids " + LocalTime.now());
		Map<Long, Set<Long>> pathwayHierarchy = fetchPathwayHierarchy(graphDBSession);

		topLevelPathwayIds = pathwayHierarchy
							.keySet()
							.stream()
							.flatMap(id -> findTopLevelPathwayIds(id, pathwayHierarchy).stream())
							.collect(Collectors.toSet());
		System.out.println("Finished computing top level pathway ids " + LocalTime.now());
		return topLevelPathwayIds;
	}

	public static Set<Long> findTopLevelPathwayIds(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		checkPathwayIdAndHiearchyAreValid(pathwayId, pathwayHierarchy);

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

	public static Set<Long> findAncestorPathwayIds(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		checkPathwayIdAndHiearchyAreValid(pathwayId, pathwayHierarchy);

		Set<Long> ancestorPathwayIds = new HashSet<>();

		Set<Long> parentPathwayIds = pathwayHierarchy.get(pathwayId);
		if (parentPathwayIds != null && !parentPathwayIds.isEmpty()) {
			for (Long parentPathwayId :  parentPathwayIds) {
				ancestorPathwayIds.add(parentPathwayId);
				ancestorPathwayIds.addAll(findAncestorPathwayIds(parentPathwayId, pathwayHierarchy));
			}
		}

		return ancestorPathwayIds;
	}

	private static void checkPathwayIdAndHiearchyAreValid(long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
		// A pathway hierarchy must be provided to find a top level pathway
		if (pathwayHierarchy == null || pathwayHierarchy.isEmpty()) {
			throw new IllegalStateException("Pathway hierarchy has no values");
		}

		// The pathway id is invalid if does not exist in the hierarchy
		if (!existsInHierarchy(pathwayId, pathwayHierarchy)) {
			logger.warn("Pathway id " + pathwayId + " does not exist in the provided pathway hierarchy");
		}
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

	private static Map<Long, ReactomeEvent> fetchReactomeEventCache(Session graphDBSession) {
		if (eventCache != null) {
			return eventCache;
		}

		System.out.println("Computing event cache " + LocalTime.now());
		eventCache = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (e:Event)",
				"RETURN e.dbId, e.displayName, e.stId"
			)
		)
		.stream()
		.collect(Collectors.toMap(
			record -> record.get("e.dbId").asLong(),

			record -> new ReactomeEvent(
				record.get("e.dbId").asLong(),
				record.get("e.displayName").asString(),
				record.get("e.stId").asString()
			)
		));
		System.out.println("Finished computing event cache " + LocalTime.now());

		return eventCache;
	}

	public static class ReactomeEvent {
		private long dbId;
		private String name;
		private String stableIdentifier;

		public ReactomeEvent(long dbId, String name, String stableIdentifier) {
			this.dbId = dbId;
			this.name = name;
			this.stableIdentifier = stableIdentifier;
		}

		public long getDbId() {
			return this.dbId;
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
				.filter(name::contains)
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

			if (!(o instanceof ReactomeEvent)) {
				return false;
			}

			ReactomeEvent oRE = (ReactomeEvent) o;

			return	getDbId() == oRE.getDbId() &&
					getName().equals(oRE.getName()) &&
					getStableIdentifier().equals(oRE.getStableIdentifier());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getDbId(), getName(), getStableIdentifier());
		}

		@Override
		public String toString() {
			return String.join("\t",
				Long.toString(getDbId()),
				getName(),
				getStableIdentifier()
			);
		}
	}
}