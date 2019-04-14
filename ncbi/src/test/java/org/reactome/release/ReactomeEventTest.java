package org.reactome.release;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ReactomeEventTest {

	@Test
	public void noNCBIEntriesFromEmptyDatabase() {
		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();

		Map<Long, ReactomeEvent> reactomeEventCache =
			ReactomeEvent.fetchReactomeEventMap(dummyGraphDBServer.getSession());

		assertThat(reactomeEventCache, is(anEmptyMap()));
	}


	@Test
	public void retrievesNCBIEntryFromDummyGraphDB() {
		final long EVENT_DB_ID = 69563L;
		final String EVENT_DISPLAY_NAME = "p53-Dependent G1 DNA Damage Response";
		final String EVENT_ST_ID = "R-HSA-69563";

		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();
		dummyGraphDBServer.populateDummyGraphDB();

		Map<Long, ReactomeEvent> reactomeEventCache =
			ReactomeEvent.fetchReactomeEventMap(dummyGraphDBServer.getSession());

		assertThat(reactomeEventCache, is(aMapWithSize(7)));

		ReactomeEvent expectedEvent = new ReactomeEvent(EVENT_DB_ID, EVENT_DISPLAY_NAME, EVENT_ST_ID);
		assertThat(reactomeEventCache, hasEntry(EVENT_DB_ID, expectedEvent));
	}

	@Test
	public void reactomeEventNameCorrection() {
		final long DUMMY_DB_ID = 1L;
		final String DUMMY_STABLE_ID = "R-HSA-123456";
		final String NAME_STRING_CONTAINING_SUGAR = "something to do with sugars";

		ReactomeEvent reactomeEvent =
			new ReactomeEvent(DUMMY_DB_ID, NAME_STRING_CONTAINING_SUGAR, DUMMY_STABLE_ID);

		assertThat(reactomeEvent.getName(), equalTo("Metabolism of sugars"));
	}
}
