package org.reactome.release;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.Session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.reactome.release.Utilities.appendWithNewLine;

/**
 * File generator for Europe PMC.  This class will produce files for
 * Europe PMC profile XML describing Reactome as a data provider and
 * Europe PMC link XML describing Reactome pathways annotated directly
 * (i.e. not on parent or child pathways) with PubMed literature references.
 * @author jweiser
 */
public class EuropePMC {
	private static final Logger logger = LogManager.getLogger();
	private static final String rootTag = "links";
	private static final int reactomeProviderID = 1903;

	private String outputDir;
	private int reactomeVersion;

	/**
	 * Returns a new instance of the Europe PMC File Generator
	 * @param outputDir Directory path for output files
	 * @param reactomeVersion Release version of Reactome
	 */
	public static EuropePMC getInstance(String outputDir, int reactomeVersion) {
		return new EuropePMC(outputDir, reactomeVersion);
	}

	private EuropePMC(String outputDir, int reactomeVersion) {
		this.outputDir = outputDir;
		this.reactomeVersion = reactomeVersion;
	}

	/**
	 * Queries graph database for all human pathways with literature references
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Set of Europe PMC Link objects describing the pathway to literature reference annotations
	 */
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

	/**
	 * Writes Europe PMC profile and link files to pre-set output directory
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @throws IOException Thrown if creating or appending for either file fails
	 */
	public void writeEuropePMCFiles(Session graphDBSession) throws IOException {
		logger.info("Writing Europe PMC files");

		writeEuropePMCProfileFile();
		writeEuropePMCLinkFile(graphDBSession);

		logger.info("Finished writing Europe PMC files");
	}

	/**
	 * Writes Europe PMC profile file to pre-set output directory.  Content describes Reactome, is static, and
	 * is pre-defined.
	 * @throws IOException Thrown if creating or appending for file fails
	 */
	private void writeEuropePMCProfileFile() throws IOException {
		Path europePMCProfileFilePath = getEuropePMCProfileFilePath();

		Files.deleteIfExists(europePMCProfileFilePath);
		Files.createFile(europePMCProfileFilePath);

		logger.info("Writing Europe PMC Profile file");

		appendWithNewLine(getEuropePMCProfileXML(), europePMCProfileFilePath);

		logger.info("Finished writing Europe PMC Profile file");
	}

	/**
	 * Produces Europe PMC profile XML content
	 * @return XML string describing Reactome as a data provider to Europe PMC
	 */
	private String getEuropePMCProfileXML() {
		return String.join(System.lineSeparator(),
			getXMLDeclaration(),
			"<providers>",
			indentString("<provider>", 1),
			indentString("<id>" + reactomeProviderID + "</id>",2),
			indentString("<resourceName>Reactome</resourceName>", 2),
			indentString(
				"<description>" +
					"Reactome is a free, open-source, curated and peer-reviewed pathway database. " +
					"Our goal is to provide intuitive bioinformatics tools for the visualization, " +
					"interpretation and analysis of pathway knowledge to support basic research, genome analysis, " +
					"modeling, systems biology and education." +
				"</description>",
				2
			),
			indentString("<email>help@reactome.org</email>", 2),
			indentString("</provider>", 1),
			"</providers>"
		);
	}

	/**
	 * Writes Europe PMC link file to pre-set output directory.  Pathway and literature reference data retrieved from
	 * the graph database.
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @throws IOException Thrown if creating or appending for either file fails
	 */
	private void writeEuropePMCLinkFile(Session graphDBSession) throws IOException {
		Path europePMCLinkFilePath = getEuropePMCLinkFilePath();

		Files.deleteIfExists(europePMCLinkFilePath);
		Files.createFile(europePMCLinkFilePath);

		logger.info("Writing Europe PMC Link file");

		appendWithNewLine(getXMLDeclaration(), europePMCLinkFilePath);
		appendWithNewLine(getOpenRootTag(), europePMCLinkFilePath);

		for (EuropePMCLink europePMCLink : fetchEuropePMCLinks(graphDBSession)) {
			appendWithNewLine(europePMCLink.getLinkXML(), europePMCLinkFilePath);
		}
		appendWithNewLine(getCloseRootTag(), europePMCLinkFilePath);

		logger.info("Finished writing Europe PMC Link file");
	}

	/**
	 * Standard XML declaration header
	 * @return XML declaration as String
	 */
	private String getXMLDeclaration() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
	}

	/**
	 * Opening XML tag of Europe PMC link file
	 * @return Opening XML tag as String
	 */
	private String getOpenRootTag() {
		return "<" + rootTag + ">";
	}

	/**
	 * Closing XML tag of Europe PMC link file
	 * @return Closing XML tag as String
	 */
	private String getCloseRootTag() {
		return "</" + rootTag + ">";
	}

	/**
	 * Indents String by given number of tabs
	 * @param string String to indent
	 * @param numberOfIndents Number of times to indent string
	 * @return Tab-indented string
	 */
	private static String indentString(String string, int numberOfIndents) {
		return StringUtils.repeat("\t", numberOfIndents).concat(string);
	}

	/**
	 * Returns Path for Europe PMC Profile File based on pre-set output directory and Reactome version
	 * @return Path object for Europe PMC Profile File
	 */
	private Path getEuropePMCProfileFilePath() {
		return Paths.get(outputDir, "europe_pmc_profile_reactome_" + reactomeVersion + ".xml");
	}

	/**
	 * Returns Path for Europe PMC Link File based on pre-set output directory and Reactome version
	 * @return Path object for Europe PMC Link File
	 */
	private Path getEuropePMCLinkFilePath() {
		return Paths.get(outputDir, "europe_pmc_links_reactome_" + reactomeVersion + ".xml");
	}

	/**
	 * Class to hold the relationship between a Reactome pathway and a literature reference.  The class can generate
	 * a Europe PMC Link XML Node based on this relationship to be used in the exported Europe PMC Link File.
	 * @author jweiser
	 */
	public static class EuropePMCLink {
		private String pathwayDisplayName;
		private String pathwayStableId;
		private String pubMedIdentifier;

		/**
		 * Creates Europe PMC Link object with pathway display name, stable id, and PubMed literature reference
		 * numeric identifier.
		 * @param pathwayDisplayName Reactome Pathway Display Name
		 * @param pathwayStableId Reactome Pathway Stable Identifier (i.e. R-HSA-XXXXXX)
		 * @param pubMedIdentifier PubMed literature reference numeric identifier
		 */
		public EuropePMCLink(String pathwayDisplayName, String pathwayStableId, String pubMedIdentifier) {
			setPathwayDisplayName(pathwayDisplayName);
			setPathwayStableId(pathwayStableId);
			setPubMedIdentifier(pubMedIdentifier);
		}

		/**
		 * Retrieves the Reactome Pathway Display Name for the Link annotation
		 * @return Reactome Pathway Display Name
		 */
		public String getPathwayDisplayName() {
			return pathwayDisplayName;
		}

		/**
		 * Sets the Reactome Pathway Display Name
		 * @param pathwayDisplayName Reactome Pathway Display Name
		 */
		private void setPathwayDisplayName(String pathwayDisplayName) {
			this.pathwayDisplayName = pathwayDisplayName;
		}

		/**
		 * Retrieves the Reactome Pathway Stable Identifier for the Link annotation
		 * @return Reactome Pathway Stable Identifier
		 */
		public String getPathwayStableId() {
			return pathwayStableId;
		}

		/**
		 * Sets Reactome Pathway Stable identifier as long as it begins with "R-HSA" indicating it is a human pathway
		 * @param pathwayStableId Reactome Pathway Stable Identifier
		 * @throws IllegalArgumentException Throws IllegalArgumentException if the pathway stable id doesn't begin
		 * with "R-HSA"
		 */
		private void setPathwayStableId(String pathwayStableId) {
			if (!pathwayStableId.startsWith("R-HSA")) {
				throw new IllegalArgumentException(
					"Pathway stable id " + pathwayStableId + " does not start with R-HSA " +
					"(i.e. it is not a human stable id)"
				);
			}

			this.pathwayStableId = pathwayStableId;
		}

		/**
		 * Retrieves the PubMed identifier for the Link annotation
		 * @return PubMed identifier as String
		 */
		public String getPubMedIdentifier() {
			return pubMedIdentifier;
		}

		/**
		 * Sets PubMed literature identifier as long as it is numeric
		 * @param pubMedIdentifier PubMed literature identifier
		 * @throws IllegalArgumentException Throws IllegalArgumentException if the PubMed literature reference isn't
		 * numeric
		 */
		private void setPubMedIdentifier(String pubMedIdentifier) {
			if (!pubMedIdentifier.matches("\\d+")) {
				throw new IllegalArgumentException(
					"PubMed identifier " + pubMedIdentifier + " is not numeric"
				);
			}

			this.pubMedIdentifier = pubMedIdentifier;
		}

		/**
		 * URL representing the Link XML annotation's Reactome Pathway
		 * @return Pathway URL as String
		 */
		private String getPathwayURL() {
			return ReactomeConstants.PATHWAY_BROWSER_URL + getPathwayStableId();
		}

		/**
		 * Link XML based on pre-set Reactome's Europe PMC provider id and the annotation's pathway name, URL, and
		 * PubMed literature identifier
		 * @return Link XML tag as String
		 */
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

		/**
		 * Checks equality based on object type and value of pathway display name, stable id, and PubMed identifier
		 * @param o Object to check for equality with the calling Europe PMC Link object.
		 * @return True if the same object or a Europe PMC Link object with the same pathway display name,
		 * stable, id
		 * and PubMed identifier.  Returns false otherwise.
		 */
		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}

			if (!(o instanceof EuropePMCLink)) {
				return false;
			}

			EuropePMCLink other = (EuropePMCLink) o;

			return this.getPathwayDisplayName().equals(other.getPathwayDisplayName()) &&
				   this.getPathwayStableId().equals(other.getPathwayStableId()) &&
				   this.getPubMedIdentifier().equals(other.getPubMedIdentifier());
		}

		/**
		 * Retrieves a hash code based on the object's set fields
		 * @return Hash code of Europe PMC Link object
		 */
		@Override
		public int hashCode() {
			return Objects.hash(
				getPathwayDisplayName(),
				getPathwayStableId(),
				getPubMedIdentifier()
			);
		}

		/**
		 * Retrieves a String representation of the defining data of the Europe PMC Link object
		 * @return String representation of Europe PMC Link object
		 */
		@Override
		public String toString() {
			return "Europe PMC Link: " +
				getPathwayDisplayName() +
				" (" + getPathwayStableId() + ")" +
				" PubMed " + getPubMedIdentifier();
		}
	}
}