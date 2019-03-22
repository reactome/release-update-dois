package org.reactome.release;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Values;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

public class NCBIEntry implements Comparable<NCBIEntry> {
	private static int linkId = 1;
	private static final String rootTag = "LinkSet";
	private static Map<Long, Set<Long>> pathwayHierarchy;
	private static Map<Long, TopLevelPathway> topLevelPathwaysCache = new HashMap<>();

	private String uniprotAccession;
	private String uniprotDisplayName;
	private Set<String> ncbiGeneIds;
	private List<TopLevelPathway> topLevelPathways;

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

	public List<TopLevelPathway> getTopLevelPathways(Session graphDBSession) {
		if (this.topLevelPathways == null) {
			this.topLevelPathways = fetchTopLevelPathways(graphDBSession);
		}

		return this.topLevelPathways;
	}

	@Override
	public int compareTo(@Nonnull NCBIEntry o) {
		return this.getUniprotAccession().compareTo(o.getUniprotAccession());
	}

	private List<TopLevelPathway> fetchTopLevelPathways(Session graphDBSession) {
		return graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rgp:ReferenceGeneProduct)<-[:referenceEntity|:referenceSequence|:hasModifiedResidue]-" +
				"(ewas:EntityWithAccessionedSequence)<-[:hasComponent|:hasMember|:hasCandidate|:repeatedUnit" +
				"|:input|:output|:catalystActivity|:physicalEntity*]-(rle:ReactionLikeEvent)<-[:hasEvent]-(p:Pathway)",
				"WHERE rgp.identifier = {uniprotAccession}",
				"WITH rgp, p",
				"RETURN DISTINCT p.dbId"
			),
			Values.parameters(getUniprotAccession())
		)
		.stream()
		.map(record -> record.get("p.dbId").asLong())
		.flatMap(pathwayId -> fetchTopLevelPathwayHierarchy(graphDBSession).get(pathwayId).stream())
		.collect(Collectors.toList());
	}

	private static Map<Long, Set<Long>> fetchPathwayHierarchy(Session graphDBSession) {
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
			//pathwayHierarchy.put(pathwayId, parentPathwayIds);
		};

		return pathwayHierarchy;
	}

	private static Map<Long, Set<TopLevelPathway>> fetchTopLevelPathwayHierarchy(Session graphDBSession) {
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

	private static Set<Long> findTopLevelPathwayIds(Long pathwayId, Map<Long, Set<Long>> pathwayHierarchy) {
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

	private static TopLevelPathway getTopLevelPathwayObject(Session graphDBSession, Long topLevelPathwayId) {
		TopLevelPathway topLevelPathway = topLevelPathwaysCache.get(topLevelPathwayId);
		if (topLevelPathway != null) {
			return topLevelPathway;
		}

		topLevelPathway = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (p:Pathway)",
				"WHERE p.dbId = {}",
				"RETURN p.displayName, p.stId"
			),
			Values.parameters(topLevelPathwayId)
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

	@Override
	public int compareTo(NCBIEntry o) {
		return this.getUniprotAccession().compareTo(o.getUniprotAccession());
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
			patternToReplacement.put("L13a-mediated translation", "L13a-mediated translation");
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

	public static String getXMLHeader() {
		return String.join(System.lineSeparator(),
			"<?xml version=\"1.0\"?>",
			"<!DOCTYPE LinkSet PUBLIC \"-//NLM//DTD LinkOut 1.0//EN\"",
			"\"http://www.ncbi.nlm.nih.gov/entrez/linkout/doc/LinkOut.dtd\"",
			"[",
			"<!ENTITY entity.base.url \"http://www.reactome.org/content/query?q=UniProt:\">",
			"<!ENTITY event.base.url \"http://www.reactome.org/PathwayBrowser/#\">",
			"]>"
		);
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

	public String getEventLinkXML(String ncbiGene, TopLevelPathway pathway) {
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