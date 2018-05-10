package org.reactome.orthoinference.inferrers;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;

public class ComplexInferrer implements Inferrer
{

	private MySQLAdaptor adaptor;
	
	public ComplexInferrer(MySQLAdaptor adaptor)
	{
		// TODO Auto-generated constructor stub
		this.adaptor = adaptor;
	}
	
	
	@Override
	public GKInstance infer(GKInstance sourceEvent)
	{
		// TODO Auto-generated method stub
		// See: sub infer_complex_polymer in infer_events.pl

		SetOfInferredCounts counts = this.countProteins(sourceEvent, this.adaptor);

		// TODO: Need something get protein counts. See the subroutine count_distinct_proteins in infer_events.pl
		long percent = (counts.getInferred() * 100) / counts.getTotal(); 
		
		GKInstance newInferredComplex = new GKInstance();
				
		
		return null;
	}

}
