<h2> Orthopairs: Protein ortholog file generation</h2>

To see how to run this script, check out the <a href="https://github.com/reactome/data-release-pipeline/new/feature/orthopairs/orthopairs#-running-orthopairs-">Running Orthopairs</a> section.

This step has been rewritten in Java and completely overhauled from the Perl <a href="https://github.com/reactome/Release/tree/master/scripts/release/orthopairs">version</a>.

The overall goal of Orthopairs remains the same: For each of Reactome's model organisms, find all human-model organism protein orthologs. These orthologs are the foundation of the <a href="https://github.com/reactome/data-release-pipeline/tree/develop/orthoinference">Orthoinference</a> step, which produces all electronically inferred ReactionlikeEvents and Pathways in the knowledgebase. 

<h3>Old Orthopairs</h3>

When the script was initially written, getting protein orthologs programmatically wasn't simple. Reactome accomplished it through three steps:

1) From Ensembl Biomart obtain all _protein-gene_ relationships for Human

_For each model organism_:<br>

  2. From Ensembl Biomart obtain all _gene-protein_ relationships for the organism
  - Biomart has become increasingly unstable as Ensembl's focus has moved to their RESTful API. Unfortunately, the information we want is not available through the API.

  3. From Ensembl Compara, obtain all human-organism gene orthologs
  - While Compara is stable, the process of obtaining the gene orthologs typically took around 24 hours
  
  4. With the organism's gene-protein list, the human protein-gene list and the human-organism gene ortholog list, map the species proteins to the human proteins.

<h3>New Orthopairs</h3>

Due to the above-mentioned shortcomings of the original script, as well as the introduction of a new resource, <a href="http://www.pantherdb.org/">PANTHER</a>, the algorithm for the step has been overhauled. PANTHER provides quarterly data releases in easily parsed flat files that bundles both Gene and Protein ortholog information together. Additionally, the files provide information about how diverged the ortholog is from the source species (in Reactome's case, Human). This gives more confidence in the computational inferences we provide to our users and significantly reduces the runtime of the script. Additionally, the Protein IDs are all from UniProt, which simplifies things during the inference process and harmonizes Reactome's protein ID linkouts.

There are a few catches with using this new resource though -- Unlike Protein IDs, Gene IDs don't come from a single resource in PANTHER. The old Orthopairs system provided all Gene IDs from Ensembl, which made those linkouts simpler. The PANTHER file provides a mix of Ensembl IDs and IDs from Model Organism Databases (MODs). As a result, we've had to add a step that maps all MOD IDs to Ensembl IDs, to ensure our linkouts are stable. This entails downloading mapping files from various MODs, which adds a bit of complexity to the code and potential future issues.

<h4>Model Organism Databases used in new Orthopairs</h4>

`Mouse Genome Informatics (MGI)` (<a href="http://www.informatics.jax.org/">link</a>) -- Mapping file: http://www.informatics.jax.org/downloads/reports/HGNC_homologene.rpt

`Rat Genome Database (RGD)` (<a href="https://rgd.mcw.edu/">link</a>) -- Mapping file: ftp://ftp.rgd.mcw.edu/pub/data_release/GENES_RAT.txt

`Xenbase (Frog)` (<a href="http://www.xenbase.org/entry/">link</a>) -- Mapping file: ftp://ftp.xenbase.org/pub/GenePageReports/GenePageEnsemblModelMapping.txt

`Zebrafish Information Network (ZFIN)` (<a href="https://zfin.org/">link</a>) Mapping file: https://zfin.org/downloads/ensembl_1_to_1.txt

`Saccharomyces Genome Database (SGD)` (<a href="https://www.yeastgenome.org/">link</a>) Mapping file downloaded from <a href="https://yeastmine.yeastgenome.org/yeastmine/bagDetails.do?scope=all&bagName=ALL_Verified_Uncharacterized_Dubious_ORFs">here</a>. File not programmatically accessible, so mapping information is stored in`src/main/resources/sgd_ids.txt`

<h3> Running Orthopairs </h3>

Orthopairs can be executed from the bash script <a href="https://github.com/reactome/data-release-pipeline/blob/feature/orthopairs/orthopairs/runOrthopairs.sh">runOrthopairs.sh</a>

Run `bash runOrthopairs.sh`

<h3> Checking Orthopairs output </h3>

There should be 2 files produced for each species in the directory corresponding to the release number. For example, if it was release 70, you would expect to find 2 files corresponding to mmus (Mouse): `70/mmus_gene_protein_mapping.txt` and `70/hsap_mmus_mapping.txt`.

Compare the line counts of the files to the same ones produced during the previous release. If they are similar, Orthopairs was likely run successfully. 

