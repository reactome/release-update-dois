<h2> Generating the Gene Ontology Annotation File from Reactome's Knowledgebase </h2>

This step produces the `gene_association.reactome` file that can be downloaded from Reactome. The file contains annotations from proteins in curated <b>ReactionlikeEvent</b> (RlE) instances in Reactome's knowledgebase.
The annotations are generated for the standard GO terms, <i>Cellular Compartment</i>, <i>Molecular Function</i> and <i>Biological Process</i>, which have accessions that can be retrieved from particular proteins that make up a ReactionlikeEvent.
Further information can be found <a href="https://www.ebi.ac.uk/GOA">here</a>.

Below is a description of how annotations are generated for each of the GO terms. 

<h3> Cellular Compartment</h3>

This is the simplest of the three GO categories to annotate. All <b>EntityWithAccessionedSequence</b> (EWAS) instances associated with a RlE, both above and below in the hierarchy, are retrieved using an AttributeQueryRequest.
Each of these proteins will receive a GO annotation if it has UniProt as its ReferenceDatabase and if it has a valid Species instance. For Cellular Compartment annotations, this includes species that do not have an alternative GO compartment (<i>HIV 1, C. botulinum, B. anthracis</i>). 
The <b>GO accession</b> is retrieved from the protein's <b>compartment</b> instance.

<h3> Molecular Function</h3>

Molecular Function annotations come from the <b>CatalystActivity</b> instance of a RlE. The proteins used to generate the annotations can come from the catalyst's <b>ActiveUnit</b> if it exists, or the <b>PhysicalEntity</b> if it does not.
Only instances that are EWAS', or are EntitySets comprised of only EWAS's can be used to generate GO annotations. Once all acceptable catalyst proteins have been retrieved and these have been filtered to include those from UniProt and that have a valid Species instance, GO annotation will be attempted.

These protein annotations are generated differently depending on if the Catalyst has an <b>PubMed</b> literature reference. Those that do will received an event identifier (6th column) prefixed with "PMID". Otherwise they receive the typical "REACTOME" prefix. A line will be produced for each PMID in `gene_association.reactome`.
The <b>GO accession</b> is retrieved from <b>activity</b> attribute of the catalyst.

<h3> Biological Process </h3>

Biological Process annotations can ge generated from <b>CatalystActivity</b> proteins (similar to Molecular Function) or, if no catalyst exists, from all proteins in the RlE (similar to Cellular Compartment).

<h4>Catalyst protein annotations</h4> 

For RlEs that do have a catalyst, annotations are generated just from a catalyst's <b>PhysicalEntity</b> instance. All PhysicalEntity types are eligible for GO annotation. 

<h4>Non-catalyst protein annotations</h4> 

Protein retrieval for those that do not have a catalyst is done using the same AttributeQueryRequest method described in the Cellular Compartment section.

<br>
Once all proteins have been retrieved, they are filtered based on source database (needs to be UniProt) and species validity again. Once a protein passes these filters, the program will attempt to retrieve the Biological Process accession from the RlE instance's <b>goBiologicalProcess</b> attribute.
Those that do not have a Biological Process accession will have their <b>Event</b> referrals checked. This process can recurse twice before the program will abandon annotation generation for this protein. 
Once an accession (or accessions) has been found for the protein, the GO annotation will be generated. 

<h4> Sample lines from gene_association.reactome </h4>

```
#SourceDB RefEntityIdentifier SecondaryIdentifier N/A GO_Accession  EventIdentifier EvidenceCode  N/A  GO_Annotation  N/A N/A ObjectType  NCBI_TaxonID  RecentModificationDate  GOASource
!gaf-version: 2.1
UniProtKB       A0A075B6P5      KV228_HUMAN             GO:0005576      REACTOME:R-HSA-166753   TAS             C                       protein taxon:9606      20161111        Reactome
UniProtKB       A0A075B6P5      KV228_HUMAN             GO:0006956      REACTOME:R-HSA-166663   TAS             P                       protein taxon:9606      20190308        Reactome
UniProtKB       A0AVI4  TM129_HUMAN             GO:0061630      PMID:24807418   EXP             F                       protein taxon:9606      20170111        Reactome 
UniProtKB       A5YM72  CRNS1_HUMAN             GO:0047730      REACTOME:R-HSA-6786245  TAS             F                       protein taxon:9606      20150706        Reactome
```

This shows different annotation types that can be found in `gene_association.reactome`. {TODO: Describe the N/As and some of the repetitive column values}
