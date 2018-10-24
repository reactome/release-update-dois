package org.reactome.release.downloadDirectory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biopax.validator.api.Validator;
import org.biopax.validator.api.ValidatorUtils;
import org.biopax.validator.api.beans.Validation;
import org.biopax.validator.impl.IdentifierImpl;
import org.jdom.*;
import org.reactome.biopax.SpeciesAllPathwaysConverter;
import org.reactome.biopax.SpeciesAllPathwaysLevel3Converter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class Biopax {
  private static final Logger logger = LogManager.getLogger();
  static ApplicationContext ctx;
  static boolean autofix = false;
  static int maxErrors = 0;
  static String profile = "notstrict";
  static String outFormat = "xml";
  static Validator validator;

  static {
   logger.info("Running BioPAX");
    // Biopax validation requires loading of bio ontologies, which takes a few minutes
    ctx = new ClassPathXmlApplicationContext(new String[]{
      "META-INF/spring/appContext-validator.xml",
      "META-INF/spring/appContext-loadTimeWeaving.xml"
    });
    validator = (Validator) ctx.getBean("validator");
  }
  //TODO: Remove white space from filenames
  public static void execute(String username, String password, String host, String port, String database, String releaseNumber) throws Exception {
    //the first arg is a path to biopax owl files to validate
	  	String biopaxDir = releaseNumber + "_biopax";
        Process makeBiopaxDir = Runtime.getRuntime().exec("mkdir -p " + biopaxDir);
        makeBiopaxDir.waitFor();
		for (int i = 0; i < 2; i++) {
			int biopaxLevel = i + 2;
			if (i == 0) {
				logger.info("Generating BioPAX2 Species owl Files...");
				SpeciesAllPathwaysConverter.main(new String[]{host, database, username, password, port, biopaxDir});
			} else {
				logger.info("Generating BioPAX3 Species owl Files...");
				SpeciesAllPathwaysLevel3Converter.main(new String[]{host, database, username, password, port, biopaxDir});
			}
			
			// Rename files
		    Files.newDirectoryStream(Paths.get(biopaxDir),
		    	      path -> path.toString().endsWith(".owl"))
		    	      .forEach(f -> {
		    	          String spath = f.toFile().getPath();
		    	          spath = spath.replaceAll(" +", "_");
		    	          File formattedFile = new File(spath);
		    	          f.toFile().renameTo(formattedFile);
		    	      });
		    // Validate each owl file produced by PathwaysConverter
		    logger.info("Validating owl files...");
			runBiopaxValidation(biopaxDir);

			// Compress all Biopax and validation files into individual zip files
			logger.info("Zipping BioPAX" + biopaxLevel + " files...");
			FileOutputStream biopaxOutputStream;
			FileOutputStream validatorOutputStream;
			if (i == 0) {
				biopaxOutputStream = new FileOutputStream("biopax2.zip");
				validatorOutputStream = new FileOutputStream("biopax2_validator.zip");
			} else {
				biopaxOutputStream = new FileOutputStream("biopax.zip");
				validatorOutputStream = new FileOutputStream("biopax_validator.zip");
			}
			ZipOutputStream biopaxZipStream = new ZipOutputStream(biopaxOutputStream);
			ZipOutputStream validatorZipStream = new ZipOutputStream(validatorOutputStream);
		    Files.newDirectoryStream(Paths.get(biopaxDir),
    	      path -> (path.toString().endsWith(".xml") || path.toString().endsWith(".owl")))
    	      .forEach(f -> {
    	          String spath = f.toFile().getPath();
    	          File file = new File(spath);
	        	  try {
	    	          if (spath.endsWith(".owl")) {
	    	        	writeToZipFile(file, biopaxZipStream);
	    	          } else if (spath.endsWith(".xml")) {
						writeToZipFile(file, validatorZipStream);
	    	          }
				  } catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				  }
    	      });
		    biopaxZipStream.close();
		    validatorZipStream.close();
		    
		    logger.info("Finished BioPAX");
		}
		Runtime.getRuntime().exec("mv biopax2.zip " + releaseNumber);
//		Runtime.getRuntime().exec("mv biopax2_validator.zip " + releaseNumber);
		Runtime.getRuntime().exec("mv biopax.zip " + releaseNumber);
//		Runtime.getRuntime().exec("mv biopax_validator.zip " + releaseNumber);
//		Process removeBiopaxDir = Runtime.getRuntime().exec("rm -r " + biopaxDir);
//		removeBiopaxDir.waitFor();
  }
	//Function for compressing Biopax and validation files
  public static void writeToZipFile(File file, ZipOutputStream zipOutputStream) throws IOException {
		
	FileInputStream zipInputStream = new FileInputStream(file);
	ZipEntry zipEntry = new ZipEntry(file.getName().toString());
	zipOutputStream.putNextEntry(zipEntry);
	byte[] bytes = new byte[1024];
	int length = 0;
	while ((length = zipInputStream.read(bytes)) >= 0) {
		zipOutputStream.write(bytes, 0, length);
	}
	zipOutputStream.closeEntry();
	zipInputStream.close();
  }
  
  public static void runBiopaxValidation(String directory) throws Exception {
    //TODO: rename orig. owl files; validate them; generate output file names and write the result
    Files.newDirectoryStream(Paths.get(directory),
      path -> path.toString().endsWith(".owl"))
      .forEach(f -> {
        try {
          String spath = f.toFile().getPath();
          logger.info("Validating BioPAX file:" + spath);
          validate(ctx.getResource("file:" + spath));
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
  }

  // Function taken from the Biopax validator project, largely imitating their own 'main' function
  // but leaving out much that we don't need for this
  static void validate(Resource owlResource) throws IOException {
    // define a new  validation result for the input data
    Validation result = new Validation(new IdentifierImpl(), owlResource.getDescription(), autofix, null, maxErrors, profile);
    result.setDescription(owlResource.getDescription());

    validator.importModel(result, owlResource.getInputStream());
    validator.validate(result);
    result.setModel(null);
    result.setModelData(null);

    // Save the validation results
    PrintWriter writer = new PrintWriter(owlResource.getFile().getPath() + "_validation." + outFormat);
//    Source xsltSrc = (outFormat.equalsIgnoreCase("html"))
//      ? new StreamSource(ctx.getResource("classpath:html-result.xsl").getInputStream())
//        : null;
//    ValidatorUtils.write(result, writer, xsltSrc);
    ValidatorUtils.write(result, writer, null);
    writer.close();

    //cleanup between files (though validator could instead check several resources and then write one report for all)
    validator.getResults().remove(result);
  }
}