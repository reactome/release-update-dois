package org.reactome.release;


import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ReactomeEventTest {

	@Test
	public void reactomeEventNameCorrection() {
		final long DUMMY_DB_ID = 1L;
		final String DUMMY_STABLE_ID = "R-HSA-123456";

		ReactomeEvent reactomeEvent =
			new ReactomeEvent(DUMMY_DB_ID, "something to do with sugars", DUMMY_STABLE_ID);

		assertThat(reactomeEvent.getName(), equalTo("Metabolism of sugars"));
	}
}
