package org.reactome.release;

import org.neo4j.driver.v1.Session;

import javax.annotation.Nonnull;
import java.util.*;

public class NCBIEntry implements Comparable<NCBIEntry> {
	private static int linkId = 1;
	private static final String rootTag = "LinkSet";

	private UniProtReactomeEntry uniProtReactomeEntry;
	private Set<String> ncbiGeneIds;

	public NCBIEntry(UniProtReactomeEntry uniProtReactomeEntry, Set<String> ncbiGeneIds) {
		this.uniProtReactomeEntry = uniProtReactomeEntry;
		this.ncbiGeneIds = ncbiGeneIds;
	}

	public NCBIEntry(long dbId, String uniprotAccession, String uniprotDisplayName, Set<String> ncbiGeneIds) {
		this(UniProtReactomeEntry.get(dbId, uniprotAccession, uniprotDisplayName), ncbiGeneIds);
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

	public Set<PathwayHierarchyUtilities.ReactomeEvent> getEvents(Session graphDBSession) {
		return uniProtReactomeEntry.getEvents(graphDBSession);
	}

	public Set<PathwayHierarchyUtilities.ReactomeEvent> getTopLevelPathways(Session graphDBSession) {
		return uniProtReactomeEntry.getTopLevelPathways(graphDBSession);
	}

	@Override
	public int compareTo(@Nonnull NCBIEntry o) {
		return this.getUniprotAccession().compareTo(o.getUniprotAccession());
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof NCBIEntry)) {
			return false;
		}

		NCBIEntry other = (NCBIEntry) o;

		return other.getUniprotAccession().equals(this.getUniprotAccession()) &&
			   other.getUniprotDisplayName().equals(this.getUniprotDisplayName()) &&
			   other.getNcbiGeneIds().equals(this.getNcbiGeneIds());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getUniprotAccession(), getUniprotDisplayName(), getNcbiGeneIds());
	}

	public static String getXMLHeader() {
		return String.join(System.lineSeparator(),
			"<?xml version=\"1.0\"?>",
			"<!DOCTYPE LinkSet PUBLIC \"-//NLM//DTD LinkOut 1.0//EN\"",
			"\"http://www.ncbi.nlm.nih.gov/entrez/linkout/doc/LinkOut.dtd\"",
			"[",
			"<!ENTITY entity.base.url \"" + ReactomeConstants.UNIPROT_QUERY_URL + "\">",
			"<!ENTITY event.base.url \"" + ReactomeConstants.PATHWAY_BROWSER_URL + "\">",
			"]>"
		).concat(System.lineSeparator());
	}

	public static String getOpenRootTag() {
		return "<" + rootTag + ">";
	}

	public static String getCloseRootTag() {
		return "</" + rootTag + ">";
	}

	public String getEntityLinkXML(String ncbiGene, String uniprotAccession) {
		return getLinkXML(
			ncbiGene,
			"&entity.base.url;",
			uniprotAccession,
			"Reactome Entity:" + uniprotAccession
		);
	}

	public String getEventLinkXML(String ncbiGene, PathwayHierarchyUtilities.ReactomeEvent pathway) {
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