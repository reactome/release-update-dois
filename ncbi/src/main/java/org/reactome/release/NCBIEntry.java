package org.reactome.release;

import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Values;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

public class NCBIEntry implements Comparable<NCBIEntry> {
	private static int linkId = 1;
	private static final String rootTag = "LinkSet";

	private String uniprotAccession;
	private String uniprotDisplayName;
	private Set<String> ncbiGeneIds;
	private List<PathwayHierarchyUtilities.TopLevelPathway> topLevelPathways;

	public NCBIEntry(String uniprotAccession, String uniprotDisplayName, Set<String> ncbiGeneIds) {
		this.uniprotAccession = uniprotAccession;
		this.uniprotDisplayName = uniprotDisplayName;
		this.ncbiGeneIds = ncbiGeneIds;
	}

	public String getUniprotAccession() {
		return this.uniprotAccession;
	}

	public String getUniprotDisplayName() {
		return this.uniprotDisplayName;
	}

	public Set<String> getNcbiGeneIds() {
		return this.ncbiGeneIds;
	}

	public List<PathwayHierarchyUtilities.TopLevelPathway> getTopLevelPathways(Session graphDBSession) {
		if (this.topLevelPathways == null) {
			this.topLevelPathways = fetchTopLevelPathways(graphDBSession);
		}

		return this.topLevelPathways;
	}

	@Override
	public int compareTo(@Nonnull NCBIEntry o) {
		return this.getUniprotAccession().compareTo(o.getUniprotAccession());
	}

	private List<PathwayHierarchyUtilities.TopLevelPathway> fetchTopLevelPathways(Session graphDBSession) {
		return graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rgp:ReferenceGeneProduct)<-[:referenceEntity|:referenceSequence|:hasModifiedResidue]-" +
				"(ewas:EntityWithAccessionedSequence)<-[:hasComponent|:hasMember|:hasCandidate|:repeatedUnit" +
				"|:input|:output|:catalystActivity|:physicalEntity*]-(rle:ReactionLikeEvent)<-[:hasEvent]-(p:Pathway)",
				"WHERE rgp.identifier = {uniprotAccession}",
				"RETURN DISTINCT p.dbId"
			),
			Values.parameters("uniprotAccession", getUniprotAccession())
		)
		.stream()
		.map(record -> record.get("p.dbId").asLong())
		.flatMap(pathwayId ->
			PathwayHierarchyUtilities.fetchTopLevelPathwayHierarchy(graphDBSession)
				.computeIfAbsent(pathwayId, k -> new HashSet<>())
				.stream()
		)
		.collect(Collectors.toList());
	}

	public static String getXMLHeader() {
		return String.join(System.lineSeparator(),
			"<?xml version=\"1.0\"?>",
			"<!DOCTYPE LinkSet PUBLIC \"-//NLM//DTD LinkOut 1.0//EN\"",
			"\"http://www.ncbi.nlm.nih.gov/entrez/linkout/doc/LinkOut.dtd\"",
			"[",
			"<!ENTITY entity.base.url \"https://www.reactome.org/content/query?q=UniProt:\">",
			"<!ENTITY event.base.url \"https://www.reactome.org/PathwayBrowser/#\">",
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

	public String getEventLinkXML(String ncbiGene, PathwayHierarchyUtilities.TopLevelPathway pathway) {
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