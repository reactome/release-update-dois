package org.reactome.release;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NCBITest {
	private NCBI.UniProtInfo uniProtInfo;

	@Before
	public void createUniProtInfo() {
		uniProtInfo = new NCBI.UniProtInfo(1L, "test UniProt", "P12345");
	}

	@Test
	public void sameUniProtObjectIsEqual() {
		assertEquals(uniProtInfo, uniProtInfo);
	}

	@Test
	public void differentUniProtObjectsWithSameValuesEqual() {
		assertEquals(uniProtInfo, new NCBI.UniProtInfo(1L, "test UniProt", "P12345"));
	}

	@Test
	public void differentUniProtObjectsWithDifferentValuesNotEqual() {
		assertNotEquals(uniProtInfo, new NCBI.UniProtInfo(2L, "another test UniProt", "Q54321"));
	}

}