package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

public class GOAGeneratorConstants {

    public static final String GOA_FILENAME = "gene_association.reactome";

    public static final String BIOLOGICAL_PROCESS_LETTER = "P";
    public static final String CELLULAR_COMPONENT_LETTER = "C";
    public static final String MOLECULAR_FUNCTION_LETTER = "F";

    // Species with alternate GO compartment
    public static final String B_ANTHRACIS_CROSS_REFERENCE = "1392";
    public static final String C_BOTULINUM_CROSS_REFERENCE = "1491";
    public static final String HIV_1_CROSS_REFERENCE = "11676";

    // Microbial species that are excluded
    public static final String C_TRACHOMATIS_CROSS_REFERENCE = "813";
    public static final String E_COLI_CROSS_REFERENCE = "562";
    public static final String N_MENINGITIDIS_CROSS_REFERENCE = "491";
    public static final String S_AUREUS_CROSS_REFERENCE = "1280";
    public static final String S_TYPHIMURIUM_CROSS_REFERENCE = "90371";
    public static final String T_GONDII_CROSS_REFERENCE = "5811";

    // Excluded GO accession that requires an IPI prefix that we do not currently handle
    public static final String PROTEIN_BINDING_ANNOTATION = "0005515";

    // These strings are used during Biological Process annotation generation. Since they
    // are specific to this module, I thought it appropriate that they had their own constants
    // despite already being in ReactomeJavaConstants.
    public static final String ACCESSION_STRING = "accession";
    public static final String EVENT_STRING = "event";

    public static final String UNIPROT_KB_STRING = "UniProtKB";
    public static final String UNIPROT_STRING = "UniProt";
    public static final String REACTOME_IDENTIFIER_PREFIX = "REACTOME:";
    public static final String PUBMED_IDENTIFIER_PREFIX = "PMID:";
    public static final String GO_IDENTIFIER_PREFIX = "GO:";
    public static final String TRACEABLE_AUTHOR_STATEMENT_CODE = "TAS";
    public static final String INFERRED_FROM_EXPERIMENT_CODE = "EXP";
    public static final String PROTEIN_STRING = "protein";
    public static final String TAXON_PREFIX = "taxon:";
    public static final String REACTOME_STRING = "Reactome";

}
