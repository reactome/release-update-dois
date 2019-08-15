package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;

import static org.reactome.release.downloadDirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class GOAGeneratorUtilities {

    // CrossReference IDs of excluded microbial species: C. trachomatis, E. coli, N. meningitidis, S. typhimurium, S. aureus, and T. gondii
    private static final List<String> microbialSpeciesToExclude = Arrays.asList(C_TRACHOMATIS_CROSS_REFERENCE, E_COLI_CROSS_REFERENCE, N_MENINGITIDIS_CROSS_REFERENCE, S_AUREUS_CROSS_REFERENCE, S_TYPHIMURIUM_CROSS_REFERENCE, T_GONDII_CROSS_REFERENCE);
    private static Map<String, Integer> dates = new HashMap<>();
    private static Set<String> goaLines = new HashSet<>();

    /**
     * Performs an AttributeQueryRequest on the incoming reaction instance. This will retrieve all protein's affiliated with the Reaction.
     * @param reactionInst -- GKInstance from ReactionlikeEvent class.
     * @return -- Set of GKInstances output from the AttributeQueryRequest.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static Set<GKInstance> retrieveProteins(GKInstance reactionInst) throws Exception {
        List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<>();
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Pathway, new String[]{ReactomeJavaConstants.hasEvent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Reaction, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntitySet, new String[]{ReactomeJavaConstants.hasMember}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));

        // All EWAS' associated with each of the above-stated classes will be output for this ReactionlikeEvent.
        String[] outClasses = new String[]{ReactomeJavaConstants.EntityWithAccessionedSequence};
        return InstanceUtilities.followInstanceAttributes(reactionInst, classesToFollow, outClasses);
    }

    /**
     * Verifys existence of ReferenceEntity and Species, and that the ReferenceDatabase associated with the ReferenceEntity is from UniProt.
     * @param referenceEntityInst -- GKInstance, ReferenceEntity instance from the protein/catalyst/reaction.
     * @param speciesInst -- GKInstance, Species instance from the protein/catalyst/reaction.
     * @return -- true/false indicating protein validity.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static boolean isValidProtein(GKInstance referenceEntityInst, GKInstance speciesInst) throws Exception {
        if (referenceEntityInst != null && speciesInst != null) {
            GKInstance referenceDatabaseInst = (GKInstance) referenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            if (referenceDatabaseInst != null && referenceDatabaseInst.getDisplayName().equals(UNIPROT_STRING) && speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference) != null) {
                return true;
            }
        }
        return false;
    }

    // This method checks the validity of the PhysicalEntity of a Catalyst instance by checking it has a compartment attribute.

    /**
     * Shared catalyst validation method between MolecularFunction and BiologicalProcess classes that checks for existence of compartment attribute.
     * @param catalystPEInst -- PhysicalEntity attribute from a Catalyst instance
     * @return -- true/false indicating PhysicalEntity validity.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static boolean isValidCatalystPE(GKInstance catalystPEInst) throws Exception {
        return catalystPEInst != null && catalystPEInst.getAttributeValue(ReactomeJavaConstants.compartment) != null;
    }

    /**
     * Checks if the PhysicalEntity is a 'multi-instance' type, which includes Complexes, EntitySets, and Polymers
     * @param physicalEntitySchemaClass
     * @return == true/false indicating if this instance has multiple subunits that comprise it.
     */
    public static boolean isMultiInstancePhysicalEntity(SchemaClass physicalEntitySchemaClass) {
        return physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer);
    }

    /**
     * Builds most of the GO annotation line that will be added to gene_association.reactome.
     * @param referenceEntityInst -- GKInstance, ReferenceEntity instance from the protein/catalyst/reaction.
     * @param goLetter -- String, can be "C", "F" or "P" for Cellular Component, Molecular Function, or Biological Process annotations, respectively.
     * @param goAccession -- String, GO accession taken from the protein/catalyst/reaction instance.
     * @param eventIdentifier -- String, StableIdentifier of the protein/catalyst/reaction. Will have either a 'REACTOME' or 'PMID' prefix.
     * @param evidenceCode -- String, Will be either "TAS" (Traceable Author Statement) or "EXP" (Experimentally Inferred). Most will be TAS, unless there is a PMID accession.
     * @param taxonIdentifier -- String, Reactome Species CrossReference identifier.
     * @return -- GO annotation line, excluding the DateTime and 'Reactome' columns.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static String generateGOALine(GKInstance referenceEntityInst, String goLetter, String goAccession, String eventIdentifier, String evidenceCode, String taxonIdentifier) throws Exception {
        List<String> goaLine = new ArrayList<>();
        goaLine.add(UNIPROT_KB_STRING);
        goaLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
        goaLine.add(getSecondaryIdentifier(referenceEntityInst));
        goaLine.add("");
        goaLine.add(goAccession);
        goaLine.add(eventIdentifier);
        goaLine.add(evidenceCode);
        goaLine.add("");
        goaLine.add(goLetter);
        goaLine.add("");
        goaLine.add("");
        goaLine.add(PROTEIN_STRING);
        goaLine.add(TAXON_PREFIX + taxonIdentifier);
        goaLines.add(String.join("\t", goaLine));
        return String.join("\t", goaLine);
    }

    /**
     * Returns the value for the 'secondaryIdentifier' column in the GOA line.
     * @param referenceEntityInst -- GKInstance, ReferenceEntity instance from the protein/catalyst/reaction.
     * @return -- String, value taken from the secondaryIdentifier, geneName or identifier attributes, whichever is not null.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static String getSecondaryIdentifier(GKInstance referenceEntityInst) throws Exception {
        if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier).toString();
        } else if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName).toString();
        } else {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        }
    }

    /**
     * Checks that the protein is not from an excluded microbial taxon.
     * @param taxonIdentifier -- String, Protein's Species' CrossReference identifier.
     * @return -- true if the taxonIdentifier is found in the microbialSpeciesToExclude array, false if not.
     */
    public static boolean isExcludedMicrobialSpecies(String taxonIdentifier) {
        return microbialSpeciesToExclude.contains(taxonIdentifier);
    }

    /**
     * Checks that the GO accession is not for Protein Binding. These don't receive a GO annotation since they require an "IPI" evidence code.
     * @param instance -- GKInstance
     * @return -- true if goAccession matches the protein binding annotation value, false if not.
     */
    public static boolean isProteinBindingAnnotation(GKInstance instance) throws Exception {
        String goAccession = instance.getAttributeValue(ReactomeJavaConstants.accession).toString();
        return goAccession.equals(PROTEIN_BINDING_ANNOTATION);
    }

    /**
     * Retrieves the stable identifier string associated with the incoming instance
     * @param eventInst -- GKInstance, Reaction or other Event instance
     * @return -- String, stable identifier string
     * @throws Exception -- MySQL exception
     */
    public static String getStableIdentifierIdentifier(GKInstance eventInst) throws Exception {
        GKInstance stableIdentifierInst = (GKInstance) eventInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
        String stableIdentifierIdentifier = stableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        return stableIdentifierIdentifier;
    }

    /**
     * Finds most recent modification date for a GOA line. This is a bit of a moving target since GOA lines can be generated convergently for
     * each type of GO annotation. Depending on if it is looking at the individual protein or whole reaction level, the date attribute
     * may not be the most recent. If it is found that the goaLine was generated earlier but that a more recent modification date exists based on the
     * entity that is currently being checked, then it will just update that date value in the hash associated with the line. (Yes, this is weird).
     * @param entityInst -- GKInstance, Protein/catalyst/reaction that is receiving a GO annotation.
     * @param goaLine -- String, GO annotation line, used for checking the 'dates' structure.
     * @return -- int, parsed from the dateTime of the entityInst's modified or created attributes.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static Integer assignDateForGOALine(GKInstance entityInst, String goaLine) throws Exception {
        int instanceDate;
        Collection<GKInstance> modifiedInstances = entityInst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (modifiedInstances.size() > 0) {
            List<GKInstance> modifiedInstancesList = new ArrayList<>(modifiedInstances);
            GKInstance mostRecentModifiedInst = modifiedInstancesList.get(modifiedInstancesList.size() - 1);
            instanceDate = getDate(mostRecentModifiedInst);
        } else {
            GKInstance createdInst = (GKInstance) entityInst.getAttributeValue(ReactomeJavaConstants.created);
            instanceDate = getDate(createdInst);
        }

        // Stores date in global hash that allows date value to be updated if a more recent date was found.
        if (dates.get(goaLine) == null || instanceDate > dates.get(goaLine)) {
            dates.put(goaLine, instanceDate);
        }
        return instanceDate;
    }

    /**
     * Retrieves date from instance and formats it for GO annotation file.
     * @param instanceEditInst -- GKInstance, instanceEdit from either a Modified or Created instance.
     * @return -- Integer, from instanceEdit's dateTime. Parsed to remove the Timestamp.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static int getDate(GKInstance instanceEditInst) throws Exception {
        return Integer.valueOf(instanceEditInst.getAttributeValue(ReactomeJavaConstants.dateTime).toString().split(" ")[0].replaceAll("-", ""));
    }

    /**
     * Iterates through the lines in the 'goaLines' list, retrieves the date associated with that line and also adds the 'Reactome' column before adding it to the gene_association.reactome file.
     * @throws IOException -- File writing/reading exceptions.
     */
    public static void outputGOAFile() throws IOException {

        final String GAF_HEADER = "!gaf-version: 2.1\n";

        Files.deleteIfExists(Paths.get(GOA_FILENAME));
        Files.write(Paths.get(GOA_FILENAME), GAF_HEADER.getBytes());

        List<String> lines = goaLines.stream().sorted().map(
                line -> String.join("\t", line, dates.get(line).toString(), REACTOME_STRING, "","")
        ).collect(Collectors.toList());
        Files.write(Paths.get(GOA_FILENAME), lines, StandardOpenOption.APPEND);

        gzipGOAFile();
    }

    /**
     * Gzips gene_association.reactome file
     * @throws IOException -- File writing/reading exceptions.
     */
    private static void gzipGOAFile() throws IOException {
        File goaFile = new File(GOA_FILENAME);
        File goaFileGZipped = new File(GOA_FILENAME + ".gz");
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(goaFileGZipped));
        try (FileInputStream fileInputStream = new FileInputStream(goaFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while((length=fileInputStream.read(buffer)) != -1) {
                gzipOutputStream.write(buffer, 0, length);
            }
            gzipOutputStream.close();
        }
    }

    /**
     * Move file into DownloadDirectory folder corresponding to release number.
     * @param targetDirectory -- String, where the file will be moved to.
     * @throws IOException -- If file or targetDirectory do not exist, this will be thrown.
     */
    public static void moveFile(String targetDirectory) throws IOException {
        String updatedTargetDirectory = targetDirectory + GOA_FILENAME + ".gz";
        Files.move(Paths.get(GOA_FILENAME + ".gz"), Paths.get(updatedTargetDirectory), StandardCopyOption.REPLACE_EXISTING);
    }
}
