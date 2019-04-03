package org.reactome.release;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EuropePMC {
	private static final Logger logger = LogManager.getLogger();
	private static final String rootTag = "links";

	private String outputDir;
	private int reactomeVersion;

	public static EuropePMC getInstance(String outputDir, int reactomeVersion) {
		return new EuropePMC(outputDir, reactomeVersion);
	}

	private EuropePMC(String outputDir, int reactomeVersion) {
		this.outputDir = outputDir;
		this.reactomeVersion = reactomeVersion;
	}

	public static Set<EuropePMCLink> fetchEuropePMCLinks(Session graphDBSession) {
		logger.info("Fetching Europe PMC Links from Reactome Pathways");

		Set<EuropePMCLink> europePMCLinks = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (p:Pathway)-[:literatureReference]->(lr:LiteratureReference)",
				"WHERE p.speciesName = 'Homo sapiens' AND lr.pubMedIdentifier IS NOT NULL",
				"RETURN DISTINCT p.displayName, p.stId, lr.pubMedIdentifier",
				"ORDER BY p.displayName"
			)
		)
		.stream()
		.map(record ->
			new EuropePMCLink(
				record.get("p.displayName").asString(),
				record.get("p.stId").asString(),
				Integer.toString(record.get("lr.pubMedIdentifier").asInt())
			)
		).collect(Collectors.toCollection(LinkedHashSet::new));

		logger.info("Finished fetching Europe PMC Links from Reactome Pathways");

		return europePMCLinks;
	}

	public void writeEuropePMCFiles(Session graphDBSession) throws IOException {
		logger.info("Writing Europe PMC files");

		writeEuropePMCProfileFile();
		writeEuropePMCLinkFile(graphDBSession);

		logger.info("Finished writing Europe PMC files");
	}

	private void writeEuropePMCProfileFile() throws IOException {

		Path europePMCProfileFilePath = getEuropePMCProfileFilePath();

		Files.deleteIfExists(europePMCProfileFilePath);
		Files.createFile(europePMCProfileFilePath);

		logger.info("Writing Europe PMC Profile file");

		Files.write(
			europePMCProfileFilePath,
			getEuropePMCProfileXML().getBytes(),
			StandardOpenOption.APPEND
		);

		logger.info("Finished writing Europe PMC Profile file");
	}

	private String getEuropePMCProfileXML() {
		return String.join(System.lineSeparator(),
			getXMLDeclaration(),
			"<providers>",
			indentString("<provider>", 1),
			indentString("<id>4914</id>",2),
			indentString("<resourceName>Reactome</resourceName>", 2),
			indentString("<description>Reactome Pathway Stable Identifiers literature references</description>", 2),
			indentString("<email>help@reactome.org</email>", 2),
			indentString("</provider>", 1),
			"</providers>"
		);
	}

	private void writeEuropePMCLinkFile(Session graphDBSession) throws IOException {
		Path europePMCLinkFilePath = getEuropePMCLinkFilePath();

		Files.deleteIfExists(europePMCLinkFilePath);
		Files.createFile(europePMCLinkFilePath);

		logger.info("Writing Europe PMC Link file");

		Files.write(
			europePMCLinkFilePath,
			getXMLDeclaration().concat(System.lineSeparator()).getBytes(),
			StandardOpenOption.APPEND
		);
		Files.write(
			europePMCLinkFilePath,
			getOpenRootTag().concat(System.lineSeparator()).getBytes(),
			StandardOpenOption.APPEND
		);
		for (EuropePMCLink europePMCLink : fetchEuropePMCLinks(graphDBSession)) {
			Files.write(
				europePMCLinkFilePath,
				europePMCLink.getLinkXML().concat(System.lineSeparator()).getBytes(),
				StandardOpenOption.APPEND
			);
		}
		Files.write(europePMCLinkFilePath, getCloseRootTag().getBytes(), StandardOpenOption.APPEND);

		logger.info("Finished writing Europe PMC Link file");
	}

	private String getXMLDeclaration() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
	}

	private String getOpenRootTag() {
		return "<" + rootTag + ">";
	}

	private String getCloseRootTag() {
		return "</" + rootTag + ">";
	}

	private static String indentString(String string, int numberOfIndents) {
		return StringUtils.repeat("\t", numberOfIndents).concat(string);
	}

	private Path getEuropePMCProfileFilePath() {
		return Paths.get(outputDir, "europe_pmc_profile_reactome_" + reactomeVersion);
	}

	private Path getEuropePMCLinkFilePath() {
		return Paths.get(outputDir, "europe_pmc_links_reactome_" + reactomeVersion);
	}

	public static class EuropePMCLink {
		private static final int reactomeProviderID = 4914;
		private String pathwayDisplayName;
		private String pathwayStableId;
		private String pubMedIdentifier;

		public EuropePMCLink(String pathwayDisplayName, String pathwayStableId, String pubMedIdentifier) {
			setPathwayDisplayName(pathwayDisplayName);
			setPathwayStableId(pathwayStableId);
			setPubMedIdentifier(pubMedIdentifier);
		}

		public String getPathwayDisplayName() {
			return pathwayDisplayName;
		}

		private void setPathwayDisplayName(String pathwayDisplayName) {
			this.pathwayDisplayName = pathwayDisplayName;
		}

		public String getPathwayStableId() {
			return pathwayStableId;
		}

		private void setPathwayStableId(String pathwayStableId) {
			if (!pathwayStableId.startsWith("R-HSA")) {
				throw new IllegalArgumentException(
					"Pathway stable id " + pathwayStableId + " does not start with R-HSA " +
					"(i.e. it is not a human stable id)"
				);
			}

			this.pathwayStableId = pathwayStableId;
		}

		public String getPubMedIdentifier() {
			return pubMedIdentifier;
		}

		private void setPubMedIdentifier(String pubMedIdentifier) {
			if (!pubMedIdentifier.matches("\\d+")) {
				throw new IllegalArgumentException(
					"PubMed identifier " + pubMedIdentifier + " is not numeric"
				);
			}

			this.pubMedIdentifier = pubMedIdentifier;
		}

		private String getPathwayURL() {
			return "https://reactome.org/PathwayBrowser/#/" + getPathwayStableId();
		}

		public String getLinkXML() {
			return String.join(System.lineSeparator(),
				indentString("<link providerId=\"" + reactomeProviderID + "\">", 1),
				indentString("<resource>",2),
				indentString("<title>" + getPathwayDisplayName() + "</title>", 3),
				indentString("<url>" + getPathwayURL() + "</url>", 3),
				indentString("</resource>",2),
				indentString("<record>", 2),
				indentString("<source>MED</source>", 3),
				indentString("<id>" + getPubMedIdentifier() + "</id>", 3),
				indentString("</record>",2),
				indentString("</link>",1)
			);
		}
	}
}