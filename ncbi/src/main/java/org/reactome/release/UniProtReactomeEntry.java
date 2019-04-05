package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import javax.annotation.Nonnull;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class UniProtReactomeEntry implements Comparable<UniProtReactomeEntry> {
	private static Map<Long, UniProtReactomeEntry> uniProtReactomeEntryMap = new HashMap<>();

	private static Map<Session, Map<String, Set<ReactomeEvent>>> uniprotAccessionToTopLevelPathwaysCache =
		new HashMap<>();
	private static Map<Session, Map<String, Set<ReactomeEvent>>> uniprotAccessionToReactomeEventCache = new HashMap<>();
	private static Map<Session, Map<String, Set<Long>>> uniprotAccessionToReactionLikeEventIdCache = new HashMap<>();

	private static Logger logger = LogManager.getLogger();

	private long dbId;
	private String accession;
	private String displayName;
	private Set<ReactomeEvent> reactomeEvents;
	private Set<ReactomeEvent> topLevelPathways;

	public static UniProtReactomeEntry get(long dbId, String uniprotAccession, String uniprotDisplayName) {
		UniProtReactomeEntry uniProtReactomeEntry = uniProtReactomeEntryMap.computeIfAbsent(
			dbId, k -> new UniProtReactomeEntry(dbId, uniprotAccession, uniprotDisplayName)
		);

		if (accessionOrNameMismatched(uniProtReactomeEntry, uniprotAccession, uniprotDisplayName)) {
			throw new IllegalArgumentException(getExceptionMessage(
				uniProtReactomeEntry, dbId, uniprotAccession, uniprotDisplayName)
			);
		}

		return uniProtReactomeEntry;
	}

	private static boolean accessionOrNameMismatched(UniProtReactomeEntry uniProtReactomeEntry,
													 String uniprotAccession, String uniprotDisplayName) {
		return !uniProtReactomeEntry.getAccession().equals(uniprotAccession) ||
			   !uniProtReactomeEntry.getDisplayName().equals(uniprotDisplayName);
	}

	private static String getExceptionMessage(UniProtReactomeEntry uniProtReactomeEntry, long dbId,
											  String uniprotAccession, String uniprotDisplayName) {
		return String.join(System.lineSeparator(),
			"Cached UniProt Reactome Entry was " + uniProtReactomeEntry,
			" but passed values were ",
			String.join(System.lineSeparator(),
				"Db id: " + dbId,
				"Accession: " + uniprotAccession,
				"Display name: " + uniprotDisplayName
			)
		);
	}

	private UniProtReactomeEntry(long dbId, String accession, String displayName) {
		setDbId(dbId);
		setAccession(accession);
		setDisplayName(displayName);
	}

	public static Map<String, Set<ReactomeEvent>> fetchUniProtAccessionToTopLevelPathways(Session graphDBSession) {
		if (uniprotAccessionToTopLevelPathwaysCache.containsKey(graphDBSession)) {
			return uniprotAccessionToTopLevelPathwaysCache.get(graphDBSession);
		}

		logger.info("Computing UniProt to Top Level Pathways");

		Map<String, Set<ReactomeEvent>> uniprotAccessionToTopLevelPathways =
			fetchUniProtAccessionToReactomeEvents(graphDBSession).entrySet().stream().collect(Collectors.toMap(
			Map.Entry::getKey,
			entry -> entry.getValue()
					.stream()
					.filter(reactomeEvent ->
						PathwayHierarchyUtilities
							.getTopLevelPathwayIds(graphDBSession)
							.contains(reactomeEvent.getDbId())
					)
					.collect(Collectors.toSet())
		));
		uniprotAccessionToTopLevelPathwaysCache.put(graphDBSession, uniprotAccessionToTopLevelPathways);

		logger.info("Finished computing UniProt to Top Level Pathways");

		return uniprotAccessionToTopLevelPathways;
	}

	public static Map<String, Set<ReactomeEvent>> fetchUniProtAccessionToReactomeEvents(Session graphDBSession) {
		if (uniprotAccessionToReactomeEventCache.containsKey(graphDBSession)) {
			return uniprotAccessionToReactomeEventCache.get(graphDBSession);
		}

		logger.info("Computing UniProt to Reactome events");

		Map<Long, ReactomeEvent> eventCache = ReactomeEvent.fetchReactomeEventMap(graphDBSession);
		Map<String, Set<Long>> uniprotAccessionToReactionLikeEventId = fetchUniProtAccessionToRLEId(graphDBSession);
		Map<Long, Set<Long>> rleToPathwayId = PathwayHierarchyUtilities.fetchRLEIdToPathwayId(graphDBSession);

		AtomicInteger count = new AtomicInteger(0);
		Map<String, Set<ReactomeEvent>> uniprotAccessionToReactomeEvent = new ConcurrentHashMap<>();
		uniprotAccessionToReactionLikeEventId.keySet().parallelStream().forEach(uniprotAccession -> {
			Set<Long> reactionLikeEventIds =
				uniprotAccessionToReactionLikeEventId.computeIfAbsent(uniprotAccession, k -> new HashSet<>());

			// Add RLEs containing UniProt accession
			uniprotAccessionToReactomeEvent
				.computeIfAbsent(uniprotAccession, k -> new HashSet<>())
				.addAll(reactionLikeEventIds.stream().map(eventCache::get).collect(Collectors.toSet()));

			for (long reactionLikeEventId : reactionLikeEventIds) {
				Set<Long> pathwayIds = rleToPathwayId.computeIfAbsent(reactionLikeEventId,
																			  k -> new HashSet<>());
				uniprotAccessionToReactomeEvent
					.computeIfAbsent(uniprotAccession, k-> new HashSet<>())
					.addAll(pathwayIds.stream().map(eventCache::get).collect(Collectors.toSet()));
			}

			if (count.getAndIncrement() % 10000 == 0) {
				logger.info("Processed events for " + count.get() + " out of " +
					uniprotAccessionToReactionLikeEventId.size() + " (" +
					String.format(
						"%.2f",
						(100 * (double) count.get()) / uniprotAccessionToReactionLikeEventId.size()
					) + "%) " + LocalTime.now()
				);
			}
		});
		uniprotAccessionToReactomeEventCache.put(graphDBSession, uniprotAccessionToReactomeEvent);

		logger.info("Finished computing UniProt to Reactome events");

		return uniprotAccessionToReactomeEvent;
	}

	private static Map<String, Set<Long>> fetchUniProtAccessionToRLEId(Session graphDBSession) {
		if (uniprotAccessionToReactionLikeEventIdCache.containsKey(graphDBSession)) {
			return uniprotAccessionToReactionLikeEventIdCache.get(graphDBSession);
		}

		logger.info("Computing UniProt to RLE id");

		StatementResult statementResult = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rgp:ReferenceGeneProduct)<-[:referenceEntity|:referenceSequence|:hasModifiedResidue]-" +
				"(ewas:EntityWithAccessionedSequence)<-[:hasComponent|:hasMember|:hasCandidate|:repeatedUnit" +
				"|:input|:output|:catalystActivity|:physicalEntity*]-(rle:ReactionLikeEvent)",
				"RETURN DISTINCT rgp.identifier, rle.dbId"
			)
		);

		Map<String, Set<Long>> uniprotAccessionToReactionLikeEventId = new HashMap<>();
		while (statementResult.hasNext()) {
			Record record = statementResult.next();

			String uniprotAccession = record.get("rgp.identifier").asString();
			long reactionLikeEventId = record.get("rle.dbId").asLong();

			uniprotAccessionToReactionLikeEventId
				.computeIfAbsent(uniprotAccession, k -> new HashSet<>())
				.add(reactionLikeEventId);
		}

		uniprotAccessionToReactionLikeEventIdCache.put(graphDBSession, uniprotAccessionToReactionLikeEventId);

		logger.info("Finished computing UniProt to RLE id");

		return uniprotAccessionToReactionLikeEventId;
	}

	public long getDbId() { return this.dbId; }

	public String getAccession() {
		return this.accession;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	private void setDbId(long dbId) {
		this.dbId = dbId;
	}

	private void setAccession(String accession) {
		final List<Integer> ACCEPTED_UNIPROT_ACCESSION_LENGTHS = Arrays.asList(6, 10);
		if (accession == null) {
			throw new NullPointerException("UniProt Accession is null");
		}

		if (!ACCEPTED_UNIPROT_ACCESSION_LENGTHS.contains(accession.length())) {
			throw new IllegalArgumentException(
				accession + " is not a proper UniProt Accession.  Must be of length " + ACCEPTED_UNIPROT_ACCESSION_LENGTHS
			);
		}

		this.accession = accession;
	}

	private void setDisplayName(String displayName) {
		final String DISPLAY_NAME_PREFIX = "UniProt:";

		if (displayName == null) {
			throw new NullPointerException("UniProt Display Name is null");
		}

		if (!displayName.startsWith(DISPLAY_NAME_PREFIX)) {
			throw new IllegalArgumentException(
				displayName + " is not a proper UniProt Display Name.  Must start with " + DISPLAY_NAME_PREFIX
			);
		}

		this.displayName = displayName;
	}

	public Set<ReactomeEvent> getEvents(Session graphDBSession) {
		if (this.reactomeEvents == null) {
			this.reactomeEvents = fetchUniProtAccessionToReactomeEvents(graphDBSession)
				.computeIfAbsent(getAccession(), k -> new HashSet<>());
		}

		return this.reactomeEvents;
	}

	public Set<ReactomeEvent> getTopLevelPathways(Session graphDBSession) {
		if (this.topLevelPathways == null) {
			this.topLevelPathways = fetchUniProtAccessionToTopLevelPathways(graphDBSession)
				.computeIfAbsent(getAccession(), k -> new HashSet<>());
		}

		return this.topLevelPathways;
	}

	@Override
	public int compareTo(@Nonnull UniProtReactomeEntry o) {
		return this.getAccession().compareTo(o.getAccession());
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof UniProtReactomeEntry)) {
			return false;
		}

		return ((UniProtReactomeEntry) o).getDbId() == this.getDbId() &&
			   ((UniProtReactomeEntry) o).getAccession().equals(this.getAccession()) &&
			   ((UniProtReactomeEntry) o).getDisplayName().equals(this.getDisplayName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getDbId(), getAccession(), getDisplayName());
	}

	@Override
	public String toString() {
		return "UniProtReactomeEntry: " + System.lineSeparator() +
			String.join(System.lineSeparator(),
				"Db id: " + getDbId(),
				"Accession: " + getAccession(),
				"Display name: " + getDisplayName()
			);
	}
}