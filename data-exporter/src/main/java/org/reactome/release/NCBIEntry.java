package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * This class describes the relationship between a UniProt entry in Reactome
 * and its associated NCBI Gene identifiers.  It can generate the NCBI Gene
 * XML node for the relationship of UniProt and NCBI Gene as well as Reactome Pathways
 * (in which the UniProt entry participates) and NCBI Gene.
 * @author jweiser
 */
public class NCBIEntry implements Comparable<NCBIEntry> {
	private static final Logger logger = LogManager.getLogger();

	private static int linkId = 1;

	private UniProtReactomeEntry uniProtReactomeEntry;
	private Set<String> ncbiGeneIds;

	public NCBIEntry(UniProtReactomeEntry uniProtReactomeEntry, Set<String> ncbiGeneIds) {
		this.uniProtReactomeEntry = uniProtReactomeEntry;
		this.ncbiGeneIds = ncbiGeneIds;
	}

	public NCBIEntry(long uniprotDbId, String uniprotAccession, String uniprotDisplayName, Set<String> ncbiGeneIds) {
		this(UniProtReactomeEntry.get(uniprotDbId, uniprotAccession, uniprotDisplayName), ncbiGeneIds);
	}

	public String getUniprotAccession() {
		return this.uniProtReactomeEntry.getAccession();
	}

	public String getUniprotDisplayName() {
		return this.uniProtReactomeEntry.getDisplayName();
	}

	public Set<String> getNcbiGeneIds() {
		return this.ncbiGeneIds;
	}

	/**
	 * Retrieves the top level pathways of the events in which the NCBI Entry's UniProt entry participates
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Set of Reactome Events which are the top level pathways
	 */
	public Set<ReactomeEvent> getTopLevelPathways(Session graphDBSession) {
		return uniProtReactomeEntry.getTopLevelPathways(graphDBSession);
	}

	/**
	 * Retrieves the list of NCBI Entry objects from UniProt entries in the Reactome graph database
	 * which have NCBI Gene identifiers
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return List of NCBI Entry objects
	 */
	public static List<NCBIEntry> getUniProtToNCBIGeneEntries(Session graphDBSession) {
		logger.info("Generating UniProt accession to NCBI Gene mapping");

		StatementResult result = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rgp:ReferenceGeneProduct)-[:referenceDatabase]->(rd:ReferenceDatabase)",
				"MATCH (rgp)-[:referenceGene]->(rds:ReferenceDNASequence)",
				"WHERE rd.displayName = 'UniProt' AND rds.databaseName = 'NCBI Gene'",
				"RETURN rgp.dbId, rgp.displayName, rgp.identifier, rds.identifier",
				"ORDER BY rgp.identifier;"
			)
		);

		Map<UniProtReactomeEntry, Set<String>> uniprotToNCBIGene = new HashMap<>();
		while (result.hasNext()) {
			Record record = result.next();
			long uniprotDbId = record.get("rgp.dbId").asLong();
			String uniprotDisplayName = record.get("rgp.displayName").asString();
			String uniprotAccession = record.get("rgp.identifier").asString();
			String ncbiGeneID = record.get("rds.identifier").asString();

			UniProtReactomeEntry uniprot = UniProtReactomeEntry.get(uniprotDbId, uniprotAccession, uniprotDisplayName);
			Set<String> ncbiGeneIDs = uniprotToNCBIGene.computeIfAbsent(uniprot, k -> new HashSet<>());
			ncbiGeneIDs.add(ncbiGeneID);
		}

		List<NCBIEntry> ncbiEntries = new ArrayList<>();
		for (UniProtReactomeEntry uniprot : uniprotToNCBIGene.keySet()) {
			ncbiEntries.add(
				new NCBIEntry(uniprot, uniprotToNCBIGene.get(uniprot))
			);
		}
		Collections.sort(ncbiEntries);

		logger.info("Finished generating UniProt accession to NCBI Gene mapping");

		return ncbiEntries;
	}

	/**
	 * Compares UniProt accession values of this object and parameter
	 * @param obj NCBIEntry object to compare
	 * @return Value of String compare between this UniProt accession and the parameter's UniProt accession
	 */
	@Override
	public int compareTo(@Nonnull NCBIEntry obj) {
		return this.getUniprotAccession().compareTo(obj.getUniprotAccession());
	}

	/**
	 * Checks equality based on object type and value of UniProt accession, UniProt display name, and NCBI Gene ids
	 * @param obj Object to check for equality with the calling NCBI Entry object.
	 * @return <code>true</code> if the same object or an NCBI Entry object with the same UniProt accession,
	 * UniProt display name, and NCBI Gene ids.  Returns <code>false</code> otherwise.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (!(obj instanceof NCBIEntry)) {
			return false;
		}

		NCBIEntry other = (NCBIEntry) obj;

		return other.getUniprotAccession().equals(this.getUniprotAccession()) &&
			   other.getUniprotDisplayName().equals(this.getUniprotDisplayName()) &&
			   other.getNcbiGeneIds().equals(this.getNcbiGeneIds());
	}

	/**
	 * Retrieves a hash code based on the object's set fields
	 * @return Hash code of NCBI Entry object
	 */
	@Override
	public int hashCode() {
		return Objects.hash(getUniprotAccession(), getUniprotDisplayName(), getNcbiGeneIds());
	}

	/**
	 * Retrieves a String representation of the defining data of the NCBI Entry object
	 * @return String representation of NCBI Entry object
	 */
	@Override
	public String toString() {
		return this.getUniprotDisplayName() + " with NCBI Gene ids " + this.getNcbiGeneIds();
	}

	/**
	 * Returns the XML String describing the relationship between the object's UniProt entry and an NCBI Gene
	 * @param ncbiGene NCBI Gene identifier
	 * @return XML String for NCBI Gene Entity Link
	 */
	public String getEntityLinkXML(String ncbiGene) {
		return getLinkXML(
			ncbiGene,
			"&entity.base.url;",
			getUniprotAccession(),
			"Reactome Entity:" + getUniprotAccession()
		);
	}

	/**
	 * Returns the XML String describing the relationship between a Reactome pathway and an NCBI Gene
	 * @param ncbiGene NCBI Gene identifier
	 * @param pathway Reactome Event representing a pathway
	 * @return XML String for NCBI Gene Entity Link
	 */
	public String getEventLinkXML(String ncbiGene, ReactomeEvent pathway) {
		return getLinkXML(
			ncbiGene,
			"&event.base.url;",
			pathway.getStableIdentifier(),
			"Reactome Event:" + pathway.getName()
		);
	}

	private String getLinkXML(String ncbiGene, String base, String rule, String urlName) {
		return String.join(System.lineSeparator(),
			"\t<Link>",
			"\t\t<LinkId>" + linkId++ + "</LinkId>",
			"\t\t<ProviderId>4914</ProviderId>",
			"\t\t<ObjectSelector>",
			"\t\t\t<Database>Gene</Database>",
			"\t\t\t<ObjectList>",
			"\t\t\t\t<ObjId>" + ncbiGene + "</ObjId>",
			"\t\t\t</ObjectList>",
			"\t\t</ObjectSelector>",
			"\t\t<ObjectUrl>",
			"\t\t\t<Base>" + base + "</Base>",
			"\t\t\t<Rule>" + rule + "</Rule>",
			"\t\t\t<UrlName>" + urlName + "</UrlName>",
			"\t\t</ObjectUrl>",
			"\t</Link>"
		).concat(System.lineSeparator());
	}
}