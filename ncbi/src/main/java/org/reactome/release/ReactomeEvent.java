package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ReactomeEvent {
	private static Map<Session, Map<Long, ReactomeEvent>> eventCache = new HashMap<>();
	private static Logger logger = LogManager.getLogger();

	private long dbId;
	private String name;
	private String stableIdentifier;

	public ReactomeEvent(long dbId, String name, String stableIdentifier) {
		this.dbId = dbId;
		this.name = name;
		this.stableIdentifier = stableIdentifier;
	}

	public static Map<Long, ReactomeEvent> fetchReactomeEventMap(Session graphDBSession) {
		if (eventCache.containsKey(graphDBSession)) {
			return eventCache.get(graphDBSession);
		}

		logger.info("Computing Event map");

		Map<Long, ReactomeEvent> eventMap = graphDBSession.run(
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
		eventCache.put(graphDBSession, eventMap);

		logger.info("Finished computing Event map");

		return eventMap;
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
