package org.reactome.release;

import org.junit.jupiter.api.*;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EuropePMCTest {
	private ServerControls embeddedDatabaseServer;
	private Session session;

	@BeforeEach
	void initializeNeo4j() {
		this.embeddedDatabaseServer = TestServerBuilders
			.newInProcessBuilder()
			.newServer();

		this.session = GraphDatabase.driver(embeddedDatabaseServer.boltURI()).session();
	}

	@Test
	public void emptySetOfEuropePMCLinksFromEmptyDatabase() {
		Set<EuropePMC.EuropePMCLink> europePMCLinks = EuropePMC.fetchEuropePMCLinks(session);

		assertThat(europePMCLinks, is(empty()));
	}

	@Test
	public void singleEuropePMCLinkFromMockGraphDB() {
		populateMockGraphDB(session);
		EuropePMC.EuropePMCLink expectedEuropePMCLink = new EuropePMC.EuropePMCLink(
			"p53-Dependent G1 DNA Damage Response",
			"R-HSA-69563",
			"9153395"
		);

		Set<EuropePMC.EuropePMCLink> europePMCLinks = EuropePMC.fetchEuropePMCLinks(session);

		assertThat(europePMCLinks, contains(expectedEuropePMCLink));
	}

	@Test
	public void throwExceptionWithNonHumanStableId() {
		final String DUMMY_DISPLAY_NAME = "Pathway name";
		final String DUMMY_PUBMED_ID = "7654321";

		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> new EuropePMC.EuropePMCLink(DUMMY_DISPLAY_NAME,"R-MMU-12345",DUMMY_PUBMED_ID),
			"Expected creation of EuropePMCLink object to throw exception due to non-human stable id, but it didn't"
		);

		assertThat(thrown.getMessage(), containsString("not a human stable id"));
	}

	@Test
	public void throwExceptionWithNonNumericPubMedId() {
		final String DUMMY_DISPLAY_NAME = "Pathway name";
		final String DUMMY_STABLE_ID = "R-HSA-12345";

		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> new EuropePMC.EuropePMCLink(DUMMY_DISPLAY_NAME, DUMMY_STABLE_ID, "abc123"),
			"Expected creation of EuropePMCLink object to throw exception due to non-numeric PubMed id, but it didn't"
		);

		assertThat(thrown.getMessage(), containsString("is not numeric"));
	}

	private void populateMockGraphDB(Session session) {
		List<String> cypherStatements = new ArrayList<>();

		try {
			cypherStatements =
				Files.readAllLines(Paths.get(
					Objects.requireNonNull(
						getClass()
						.getClassLoader()
						.getResource("mock_reactome_graphdb.txt")
					)
					.getPath()
				));
		} catch (IOException e) {
			fail();
		}

		StringBuilder query = new StringBuilder();
		cypherStatements.forEach(line -> query.append(line).append(System.lineSeparator()));

		session.run(query.toString());
	}
}
