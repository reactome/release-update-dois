package org.reactome.release.downloadDirectory;

import org.reactome.biopax.SpeciesAllPathwaysConverter;
import org.reactome.biopax.SpeciesAllPathwaysLevel3Converter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.ResourceUtils;

import org.biopax.validator.api.*;
import org.biopax.validator.api.beans.Validation;
import org.biopax.validator.impl.IdentifierImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.jdom.*;

public class Biopax {
	static ApplicationContext ctx;
	static boolean autofix = false;
	static int maxErrors = 0;
	static String profile = "notstrict";
	static String outFormat = "xml";
	
	public static void execute(String username, String password, String host, int port, String database, int releaseNumber) throws Exception {
		System.out.println("Running Biopax...");
		// Biopax validation requires loading of rules, which takes a few minutes
		System.out.println("\tFetching validation rules");
		ctx = new ClassPathXmlApplicationContext(new String[] {"META-INF/spring/appContext-loadTimeWeaving.xml", "META-INF/spring/appContext-validator.xml"});
		Validator validator = (Validator) ctx.getBean("validator");
		
		String biopaxDir = releaseNumber + "_biopax";
		// We want to run Biopax twice, once each for Biopax level 2 and 3
		for (int i = 0; i < 2; i++) {
//			Runtime.getRuntime().exec("rm -fr " + releaseNumber);
			if (i == 0) {
				System.out.println("\tGenerating Biopax2 Species Files");
				SpeciesAllPathwaysConverter.main(new String[]{host, "test_reactome_66_final", username, password, Integer.toString(port), biopaxDir});
			} else {
				System.out.println("\tGenerating Biopax3 Species Files");
				SpeciesAllPathwaysLevel3Converter.main(new String[]{host, "test_reactome_66_final", username, password, Integer.toString(port), biopaxDir});
			}
			
		File folder = new File(biopaxDir);
		File[] folderFiles = folder.listFiles();
		// Rename owl files removing spaces, and then validate them
		System.out.println("\tRunning Biopax validation on each species...");
		for (int j = 0; j < folderFiles.length; j++) {
			if (folderFiles[j].toString().endsWith(".owl")) {
				String owlFile = folderFiles[j].toString();
				owlFile = owlFile.replaceAll(" +", "_");
				File formattedOwlFile = new File(owlFile);
				folderFiles[j].renameTo(formattedOwlFile);
				runValidator(validator, getFileToValidate(owlFile), owlFile.split("/")[1].split("\\.")[0], biopaxDir);
			}
		}	
		folderFiles = folder.listFiles();

		// Compress all Biopax and validation files into individual zip files
		FileOutputStream biopaxOutputStream;
		FileOutputStream validatorOutputStream;
		System.out.println("\tBeginning compression of Biopax files...");
		if (i == 0) {
			biopaxOutputStream = new FileOutputStream("biopax2.zip");
			validatorOutputStream = new FileOutputStream("biopax2_validator.zip");
		} else {
			biopaxOutputStream = new FileOutputStream("biopax.zip");
			validatorOutputStream = new FileOutputStream("biopax_validator.zip");
		}
//		
		ZipOutputStream biopaxZipStream = new ZipOutputStream(biopaxOutputStream);
		ZipOutputStream validatorZipStream = new ZipOutputStream(validatorOutputStream);
		
		for (int j = 0; j < folderFiles.length; j++) {
			if (folderFiles[j].toString().endsWith(".owl")) {
				writeToZipFile(folderFiles[j], biopaxZipStream);
			} else if (folderFiles[j].toString().endsWith(".xml")) {
				writeToZipFile(folderFiles[j], validatorZipStream);
			}
		}
		biopaxZipStream.close();
		validatorZipStream.close();
		System.out.println("\tBiopax run complete");
		}
		Runtime.getRuntime().exec("mv biopax2.zip " + releaseNumber);
		Runtime.getRuntime().exec("mv biopax2_validator.zip " + releaseNumber);
		Runtime.getRuntime().exec("mv biopax.zip " + releaseNumber);
		Runtime.getRuntime().exec("mv biopax_validator.zip " + releaseNumber);
		Process removeBiopaxDir = Runtime.getRuntime().exec("rm -r " + biopaxDir);
		removeBiopaxDir.waitFor();
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
	
	// Function taken from the Biopax validator project, largely imitating their own 'main' function but leaving out much that we don't need for this
	public static void runValidator(Validator validator, Resource owlFileAsResource, String speciesName, String biopaxDir) throws IOException {
		
		System.out.println("\t\t Validating " + speciesName);
		Validation result = new Validation(new IdentifierImpl(), owlFileAsResource.getDescription(), autofix, null, maxErrors, profile);
		result.setDescription(owlFileAsResource.getDescription());
		// The actual validation function
		try {
			validator.importModel(result, owlFileAsResource.getInputStream());
			validator.validate(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		result.setModel(null);
		result.setModelData(null);

		// Save the validation results
		PrintWriter writer;
		writer = new PrintWriter(biopaxDir + "/" + speciesName + "_validator_output." + outFormat);
		Source xsltSrc = (outFormat.equalsIgnoreCase("html")) ? new StreamSource(ctx.getResource("classpath:html-result.xsl").getInputStream()) : null;
		ValidatorUtils.write(result, writer, xsltSrc);
		writer.close();

		validator.getResults().remove(result);
			
	}
	// Turns a filename into a resource and formats it before its sent to 'runValidator'
	public static Resource getFileToValidate(String owlFile) {
		
		Resource resource = null;
		if (!ResourceUtils.isUrl(owlFile)) {
			owlFile = "file:" + owlFile;
			resource = ctx.getResource(owlFile);
		}
		return resource;
	}
}