package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Class for describing Events (Pathways and Reaction Like Events) in Reactome.
 * @author jweiser
 */
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

	/**
	 * Retrieves, from the graph database, the map of event database identifiers (both Pathways and
	 * Reaction Like Events) to the ReactomeEvent object representing each event
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Map of Reactome Event database identifiers in Reactome to their ReactomeEvent objects
	 */
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

	/**
	 * Retrieves the Reactome database identifier for the represented event
	 * @return Reactome event database identifier
	 */
	public long getDbId() {
		return this.dbId;
	}

	/**
	 * Retrieves the Reactome display name for the represented event.  The name is standardized before being returned.
	 * @return Reactome event display name
	 */
	public String getName() {
		return fixName(this.name);
	}

	/**
	 * Transforms Reactome event names that contain key words or phrases to standardized names
	 * @param name Name to be considered for standardization
	 * @return Standardized Reactome event display name if it contains pre-set words or phrases or the name passed as
	 * a parameter, otherwise.
	 */
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

	/**
	 * Retrieves the Reactome Stable Identifier for the represented event
	 * @return Reactome event Stable Identifier
	 */
	public String getStableIdentifier() {
		return stableIdentifier;
	}

	/**
	 * Checks equality based on object type and value of event db id, display name and stable identifier
	 * @param o Object to check for equality with the calling ReactomeEvent
	 * @return <code>true</code> if the same object or a ReactomeEvent object with the same db id, display name,
	 * and stable identifier.  Returns <code>false</code> otherwise.
	 */
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

	/**
	 * Retrieves a hash code based on the object's set fields
	 * @return Hash code of ReactomeEvent object
	 */
	@Override
	public int hashCode() {
		return Objects.hash(getDbId(), getName(), getStableIdentifier());
	}

	/**
	 * Retrieves a String representation of the defining data of the ReactomeEvent object
	 * @return String representation of ReactomeEvent object
	 */
	@Override
	public String toString() {
		return String.join("\t",
			Long.toString(getDbId()),
			getName(),
			getStableIdentifier()
		);
	}
}
