package org.reactome.release;

import org.neo4j.driver.v1.Session;

import javax.annotation.Nonnull;
import java.util.*;

public class UniProtReactomeEntry implements Comparable<UniProtReactomeEntry> {
	private static Map<Long, UniProtReactomeEntry> uniProtReactomeEntryMap = new HashMap<>();

	private long dbId;
	private String accession;
	private String displayName;
	private Set<PathwayHierarchyUtilities.ReactomeEvent> reactomeEvents;
	private Set<PathwayHierarchyUtilities.ReactomeEvent> topLevelPathways;

	public static void clearCache() {
		uniProtReactomeEntryMap.clear();
	}

	public static UniProtReactomeEntry get(long dbId, String uniprotAccession, String uniprotDisplayName) {
		UniProtReactomeEntry uniProtReactomeEntry = uniProtReactomeEntryMap.computeIfAbsent(
			dbId, k -> new UniProtReactomeEntry(dbId, uniprotAccession, uniprotDisplayName)
		);

		if (accessionOrNameMismatched(uniProtReactomeEntry, uniprotAccession, uniprotDisplayName)) {
			throw new IllegalArgumentException(
				String.join(System.lineSeparator(),
					"Cached UniProt Reactome Entry was " + uniProtReactomeEntry,
					" but passed values were ",
					String.join(System.lineSeparator(),
						"Db id: " + dbId,
						"Accession: " + uniprotAccession,
						"Display name: " + uniprotDisplayName
					)
				)
			);
		}

		return uniProtReactomeEntry;
	}

	private static boolean accessionOrNameMismatched(UniProtReactomeEntry uniProtReactomeEntry,
													 String uniprotAccession, String uniprotDisplayName) {
		return !uniProtReactomeEntry.getAccession().equals(uniprotAccession) ||
			   !uniProtReactomeEntry.getDisplayName().equals(uniprotDisplayName);
	}

	private UniProtReactomeEntry(long dbId, String accession, String displayName) {
		setDbId(dbId);
		setAccession(accession);
		setDisplayName(displayName);
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

	public Set<PathwayHierarchyUtilities.ReactomeEvent> getEvents(Session graphDBSession) {
		if (this.reactomeEvents == null) {
			this.reactomeEvents = fetchEvents(graphDBSession);
		}

		return this.reactomeEvents;
	}

	private Set<PathwayHierarchyUtilities.ReactomeEvent> fetchEvents(Session graphDBSession) {
		return PathwayHierarchyUtilities.fetchUniProtAccessionToReactomeEvents(graphDBSession)
			.computeIfAbsent(getAccession(), k -> new HashSet<>());
	}

	public Set<PathwayHierarchyUtilities.ReactomeEvent> getTopLevelPathways(Session graphDBSession) {
		if (this.topLevelPathways == null) {
			this.topLevelPathways = fetchTopLevelPathways(graphDBSession);
		}

		return this.topLevelPathways;
	}

	private Set<PathwayHierarchyUtilities.ReactomeEvent> fetchTopLevelPathways(Session graphDBSession) {
		return PathwayHierarchyUtilities.fetchUniProtAccessionToTopLevelPathways(graphDBSession)
			.computeIfAbsent(getAccession(), k -> new HashSet<>());
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