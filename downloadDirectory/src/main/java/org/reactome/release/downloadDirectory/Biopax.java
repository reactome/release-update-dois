package org.reactome.release.downloadDirectory;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biopax.validator.api.Validator;
import org.biopax.validator.api.ValidatorUtils;
import org.biopax.validator.api.beans.Validation;
import org.biopax.validator.impl.IdentifierImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.reactome.biopax.SpeciesAllPathwaysConverter;
import org.reactome.biopax.SpeciesAllPathwaysLevel3Converter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class Biopax {
  private static final Logger logger = LogManager.getLogger();
  private static ApplicationContext ctx;
  private static Validator validator;

  // BioPAX validation requires loading of bio ontologies, which takes a few minutes before starting the BioPAX process
  static {
   logger.info("Running BioPAX step");
   logger.info("Preparing BioPAX validation rules...");
	ctx = new ClassPathXmlApplicationContext(new String[]{
	  "META-INF/spring/appContext-validator.xml",
	  "META-INF/spring/appContext-loadTimeWeaving.xml"
	});
	validator = (Validator) ctx.getBean("validator");
	logger.info("Finished preparing BioPAX validation rules");
  }
  
  // Once BioPAX validation rules have loaded, the actual BioPAX process can start
  public static void execute(String username, String password, String host, String port, String database, String releaseNumber, String pathToSpeciesConfig, List<Integer> biopaxLevels) throws Exception {
		// Temporary location where BioPAX files are stored
		String biopaxDir = releaseNumber + "_biopax";
		File biopaxDirFile = new File(biopaxDir);
		if (!biopaxDirFile.exists()) {
			biopaxDirFile.mkdir();
		}

		JSONParser parser = new JSONParser();
		JSONObject speciesFile = (JSONObject) parser.parse(new FileReader(pathToSpeciesConfig));
		@SuppressWarnings("unchecked")
		Set<String> speciesKeys = speciesFile.keySet();
		// First, generate owl files. This particular step requires a local maven installation of the PathwayExchange jar (see README.md for DownloadDirectory)
		// We want to run BioPAX level 2 and BioPAX level 3, so the below loop starts with '2'

		for (int biopaxLevel : biopaxLevels) {
			for (String speciesKey : speciesKeys) {
				String speciesName = (String) ((JSONArray) ((JSONObject) speciesFile.get(speciesKey)).get("name")).get(0);
				logger.info("Generating BioPAX{} {} owl file...", biopaxLevel, speciesName);
				generateBioPAXFile(host, database, username, password, port, biopaxDir, speciesName, biopaxLevel);
			}

			String outfile = biopaxLevel == 2 ? "biopax2.zip" : "biopax.zip";
			validateBioPAX(biopaxDir, biopaxLevel);
			// Move zip file and remove temp directory
			String outpathBiopax = releaseNumber + "/" + outfile;
			Files.move(Paths.get(outfile), Paths.get(outpathBiopax), StandardCopyOption.REPLACE_EXISTING);
			FileUtils.deleteDirectory(new File(biopaxDir));
		}	
	}

	//Generate BioPAX files using the appropriate SpeciesAllPathwaysConverter function found in Pathway-Exchange
	private static void generateBioPAXFile(String host, String database, String username, String password, String port, String biopaxDir, String speciesName, int biopaxLevel) throws Exception {
		
		if (biopaxLevel == 2) {
			SpeciesAllPathwaysConverter converter = new SpeciesAllPathwaysConverter();
			converter.doDump(new String[]{host, database, username, password, port, biopaxDir, speciesName});			
		} else if (biopaxLevel == 3) {
			SpeciesAllPathwaysLevel3Converter level3converter = new SpeciesAllPathwaysLevel3Converter();
			level3converter.doDump(new String[]{host, database, username, password, port, biopaxDir, speciesName});
		}
	}
	
	// Next, once all species files for the current BioPAX level have been generated, we must validate them to make sure they are appropriately formatted
	private static void validateBioPAX(String biopaxDir, int biopaxLevel) throws Exception {

		// Rename files, replacing whitespace with underscores
		Files.newDirectoryStream(Paths.get(biopaxDir),
				  owlFilepath -> owlFilepath.toString().endsWith(".owl"))
				  .forEach(owlFile -> {
					  String owlFilename = owlFile.toFile().getPath();
					  owlFilename = owlFilename.replaceAll(" +", "_");
					  File formattedOwlFilename = new File(owlFilename);
					  owlFile.toFile().renameTo(formattedOwlFilename);
				  });
		// Validate each owl file in the biopax output directory produced by PathwaysConverter
		logger.info("Validating owl files...");
		runBiopaxValidation(biopaxDir);

		// Compress all Biopax and validation files into individual zip files
		logger.info("Zipping BioPAX" + biopaxLevel + " files...");
		writeFilesToZipFile(biopaxDir, ".owl", getBiopaxZipStream(biopaxLevel));
		writeFilesToZipFile(biopaxDir, ".xml", getValidatorZipStream(biopaxLevel));

		logger.info("Finished BioPAX");
	}

	// Zip utilities
	private static void writeFilesToZipFile(String biopaxDir, String fileExtension, ZipOutputStream zipOutputStream) throws IOException {
		Files.newDirectoryStream(Paths.get(biopaxDir), path -> path.toString().endsWith(fileExtension))
				.forEach(path -> {
					try {
						writeToZipFile(path.toFile(), zipOutputStream);
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
		zipOutputStream.close();
	}

	private static ZipOutputStream getBiopaxZipStream(int biopaxLevel) throws FileNotFoundException {
		return getZipOutputStream(biopaxLevel == 2 ? "biopax2.zip" : "biopax.zip");
	}
	
	private static ZipOutputStream getValidatorZipStream(int biopaxLevel) throws FileNotFoundException {
		return getZipOutputStream(biopaxLevel == 2 ? "biopax2_validator.zip" : "biopax_validator.zip");
	}
	
	private static ZipOutputStream getZipOutputStream(String fileName) throws FileNotFoundException {
		return new ZipOutputStream(new FileOutputStream(fileName));
	}
	
	//Function for compressing Biopax and validation files
	private static void writeToZipFile(File file, ZipOutputStream zipOutputStream) throws IOException {
			
		FileInputStream zipInputStream = new FileInputStream(file);
		zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
		byte[] bytes = new byte[1024];
		int length = 0;
		while ((length = zipInputStream.read(bytes)) >= 0) {
			zipOutputStream.write(bytes, 0, length);
		}
		zipOutputStream.closeEntry();
		zipInputStream.close();
	  }
  
  // This function runs each file in the biopax output directory through the biopax validator
  private static void runBiopaxValidation(String directory) throws Exception {

	Files.newDirectoryStream(Paths.get(directory),
	  owlFilename -> owlFilename.toString().endsWith(".owl"))
	  .forEach(owlFile -> {
		try {
		  String owlFilepath = owlFile.toFile().getPath();
		  logger.info("Validating BioPAX file:" + owlFilepath);
		  validate(ctx.getResource("file:" + owlFilepath));
		} catch (IOException e) {
		  e.printStackTrace();
		}
	  });
  }

  // Function taken from the Biopax validator project, largely imitating their own 'main' function
  // but leaving out much that we don't need for this. Validates each owl file that is passed through.
  private static void validate(Resource owlResource) throws IOException {

	boolean autofix = false;
	int maxErrors = 0;
	String profile = "notstrict";
	String outFormat = "xml";
	// Define a new  validation result for the input data
	Validation result = new Validation(new IdentifierImpl(), owlResource.getDescription(), autofix, null, maxErrors, profile);
	result.setDescription(owlResource.getDescription());

	validator.importModel(result, owlResource.getInputStream());
	validator.validate(result);
	result.setModel(null);
	result.setModelData(null);

	// Save the validation results
	PrintWriter writer = new PrintWriter(owlResource.getFile().getPath() + "_validation." + outFormat);
	ValidatorUtils.write(result, writer, null);
	writer.close();

	// Cleanup between files (though validator could instead check several resources and then write one report for all)
	validator.getResults().remove(result);
  }
}