package org.reactome.orthoinference.inferrers;

import org.gk.model.GKInstance;

public class ComplexInferrer implements Inferrer {

	@Override
	public GKInstance infer(GKInstance sourceEvent) {
		// TODO Auto-generated method stub
		// See: sub infer_complex_polymer in infer_events.pl
		long totalProteinCount = 0;
		long inferredProteinCount = 0;
		long maxProteinCount = 0;
		// TODO: Need something get protein counts. See the subroutine count_distinct_proteins in infer_events.pl
		long percent = (inferredProteinCount * 100) / totalProteinCount; 
		
		GKInstance newInferredComplex = new GKInstance();
				
				
		return null;
	}

}
