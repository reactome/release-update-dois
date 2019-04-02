package org.reactome.release.generateGOAnnotationFile;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.io.FileInputStream;
import java.util.*;

public class Main {

    private static final String uniprotDbString = "UniProtKB";
    private static final String PROTEIN_BINDING_ANNOTATION = "0005515";
    private static final List<String> speciesWithAlternateGOCompartment = new ArrayList<>(Arrays.asList("11676", "211044", "1491", "1392"));
    private static final List<String> microbialSpeciesToExclude = new ArrayList<>(Arrays.asList("813", "562", "491", "90371", "1280", "5811"));
    private static List<String> goTerms = new ArrayList<String>(Arrays.asList("Cellular Component", "Molecular Function", "Biological Process"));

    public static void main(String[] args) throws Exception {

        Properties props = new Properties();
        props.load(new FileInputStream("src/main/resources/config.properties"));

        String username = props.getProperty("username");
        String password = props.getProperty("password");
        String database = props.getProperty("database");
        String host = props.getProperty("host");
        int port = Integer.valueOf(props.getProperty("port"));

        MySQLAdaptor dbAdaptor = new MySQLAdaptor(host, database, username, password, port);

        Collection<GKInstance> inferredReactionInstances = new ArrayList<GKInstance>();
        Set<Long> inferredReactionDbIds = new HashSet<Long>();

        for (GKInstance reactionInst : (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass("ReactionlikeEvent")) {
            
            if (!isInferred(reactionInst)) {
                processProteins(reactionInst);
            }
        }
    }

    private static void processProteins(GKInstance reactionInst) throws Exception {

        List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Pathway, new String[]{ReactomeJavaConstants.hasEvent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Reaction, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntitySet, new String[]{ReactomeJavaConstants.hasMember}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));

        String[] outClasses = new String[]{ReactomeJavaConstants.EntityWithAccessionedSequence};

        for (GKInstance ewasInst : (Collection<GKInstance>) InstanceUtilities.followInstanceAttributes(reactionInst, classesToFollow, outClasses)) {
            GKInstance referenceEntityInst = (GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.species);
            if (validEWAS(referenceEntityInst, speciesInst)) {

                List<String> goAnnotationLine = new ArrayList<String>();
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                if (taxonIdentifier != PROTEIN_BINDING_ANNOTATION && !speciesWithAlternateGOCompartment.contains(taxonIdentifier) && !microbialSpeciesToExclude.contains(taxonIdentifier)) {
                    //TODO: GO_CellularComponent check... is it needed?
                    //TODO: Compartment check... is it needed?
                    GKInstance reactionStableIdentifierInst = (GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                    goAnnotationLine.add(uniprotDbString);
                    goAnnotationLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                    goAnnotationLine.add(getSecondaryIdentifier(referenceEntityInst));
                    goAnnotationLine.add("");
                    goAnnotationLine.add(getGOAccession(ewasInst));
                    goAnnotationLine.add("REACTOME:" + reactionStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                    goAnnotationLine.add("TAS");
                    goAnnotationLine.add("");
                    goAnnotationLine.add("C");
                    goAnnotationLine.add("");
                    goAnnotationLine.add("");
                    goAnnotationLine.add("protein");
                    goAnnotationLine.add("taxon:" + taxonIdentifier);
                }
            }
        }



    }

    private static String getGOAccession(GKInstance ewasInst) throws Exception {
        GKInstance compartmentInst = (GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.compartment);
        if (compartmentInst != null) {
            return "GO:" + compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
        }
        return null;
    }

    private static String getSecondaryIdentifier(GKInstance referenceEntityInst) throws Exception {
        if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier).toString();
        } else if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName).toString();
        } else {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        }
    }

    private static boolean validEWAS(GKInstance referenceEntityInst, GKInstance speciesInst) throws Exception {
        if (referenceEntityInst != null && speciesInst != null) {
            GKInstance referenceDatabaseInst = (GKInstance) referenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            if (referenceDatabaseInst != null && referenceDatabaseInst.getDisplayName().equals("UniProt") && speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInferred(GKInstance reactionInst) throws Exception {
        if (isElectronicallyInferred(reactionInst) || isManuallyInferred(reactionInst)) {
            return true;
        }
        return false;
    }

    private static boolean isManuallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    private static boolean isElectronicallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null ? true : false;
    }
}
