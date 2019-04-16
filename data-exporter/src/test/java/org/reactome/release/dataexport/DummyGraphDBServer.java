package org.reactome.release.dataexport;

import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DummyGraphDBServer {
	private static DummyGraphDBServer dummyGraphDBServer;

	private ServerControls embeddedDatabaseServer;
	private Session session;

	public static DummyGraphDBServer getInstance() {
		if (dummyGraphDBServer == null) {
			dummyGraphDBServer = new DummyGraphDBServer();
		}

		return dummyGraphDBServer;
	}

	public void initializeNeo4j() {
		this.embeddedDatabaseServer = TestServerBuilders.newInProcessBuilder().newServer();
		this.session = GraphDatabase.driver(embeddedDatabaseServer.boltURI()).session();
	}

	public void populateDummyGraphDB() {
		List<String> cypherStatements;

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
			throw new RuntimeException("Populating dummy graph db failed", e);
		}

		StringBuilder query = new StringBuilder();
		cypherStatements.forEach(line -> query.append(line).append(System.lineSeparator()));

		getSession().run(query.toString());
	}

	public Session getSession() {
		return this.session;
	}
}
