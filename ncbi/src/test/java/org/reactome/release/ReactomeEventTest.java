package org.reactome.release;


import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ReactomeEventTest {

	@Test
	public void reactomeEventNameCorrection() {
		ReactomeEvent reactomeEvent =
			new ReactomeEvent(1L, "something to do with sugars", "R-HSA-123456");

		assertThat(reactomeEvent.getName(), equalTo("Metabolism of sugars"));
	}
}
