package org.reactome.release;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class ReactomeEventTest {

	@Test
	public void reactomeEventNameCorrection() {
		ReactomeEvent reactomeEvent =
			new ReactomeEvent(1L, "something to do with sugars", "R-HSA-123456");

		assertThat(reactomeEvent.getName(), equalTo("Metabolism of sugars"));
	}
}
