package org.reactome.release.downloadDirectory;

import org.reactome.biopax.SpeciesAllPathwaysConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.ResourceUtils;

import org.biopax.validator.api.*;
import org.biopax.validator.api.beans.Validation;
import org.biopax.validator.api.beans.ValidatorResponse;
import org.biopax.validator.impl.IdentifierImpl;
import org.biopax.validator.impl.ValidatorImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
		//TODO: Change to exporter directory and remove any folders of current release
		SpeciesAllPathwaysConverter.main(new String[]{host, "test_reactome_66_final", username, password, Integer.toString(port), Integer.toString(releaseNumber)});
		
		File folder = new File(releaseNumber + "/");
		File[] folderFiles = folder.listFiles();
		
		ctx = new ClassPathXmlApplicationContext(new String[] {"META-INF/spring/appContext-loadTimeWeaving.xml", "META-INF/spring/appContext-validator.xml"});
		Validator validator = (Validator) ctx.getBean("validator");
		
		for (int i = 0; i < folderFiles.length; i++) {
			
			if (folderFiles[i].toString().endsWith(".owl")) {
				String owlFile = folderFiles[i].toString();
				owlFile = owlFile.replaceAll(" +", "_");
				File formattedOwlFile = new File(owlFile);
				folderFiles[i].renameTo(formattedOwlFile);
				runValidator(validator, getFileToValidate(owlFile), owlFile.split("/")[1].split("\\.")[0], releaseNumber);
			}
		}	
	}
	
protected static void runValidator(Validator validator, Resource owlFileAsResource, String speciesName, int releaseNumber) throws IOException {
		
		final ValidatorResponse consolidatedReport = new ValidatorResponse();
		
		Validation result = new Validation(new IdentifierImpl(), owlFileAsResource.getDescription(), autofix, null, maxErrors, profile);
		result.setDescription(owlFileAsResource.getDescription());
		try {
			validator.importModel(result, owlFileAsResource.getInputStream());
			validator.validate(result);
			
			consolidatedReport.addValidationResult(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		result.setModel(null);
		result.setModelData(null);

		// Save the validation results
		PrintWriter writer;
		writer = new PrintWriter(releaseNumber + "/" + speciesName + "_validator_output." + outFormat);
		Source xsltSrc = (outFormat.equalsIgnoreCase("html")) ? new StreamSource(ctx.getResource("classpath:html-result.xsl").getInputStream()) : null;
		ValidatorUtils.write(result, writer, xsltSrc);
		writer.close();

		validator.getResults().remove(result);
			
	}
	
	public static Resource getFileToValidate(String owlFile) {
		
		Resource resource = null;
		if (!ResourceUtils.isUrl(owlFile)) {
			owlFile = "file:" + owlFile;
			resource = ctx.getResource(owlFile);
		}
		return resource;
	}
}