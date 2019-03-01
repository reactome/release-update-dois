package org.reactome.release.downloadDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

/**
 *
 * @author jcook
 *		Explanation of this module:
		We are taking from value of the 'ontology' attribute from test_reactome_XX.Ontology. This returns a blob that can be divided into 3 sections: pprj, pont, and pins.
		Unfortunately, the blob isn't cleanly divided into 3 sections, as between sections 1 and 2 there are some extra content not needed.
		The content of the pprj file appears first. We take all content from the blob until the string 'pprj_file_content' appears. This signifies the end of that file.
		Next, we iterate through the lines of the blob until a second dateTime string appears  (signifying the start of the pont file) and subsequently
		add all content until the string 'pont_file_content' appears, signifying the end of the pont file.
		Finally, the rest of the blob pertains to the 'pins' file, so the remaining content is appended to the pins file.
 */

public class FetchTestReactomeOntologyFiles {
	private static final Logger logger = LogManager.getLogger();

	public static void execute(MySQLAdaptor dba, String releaseNumber) throws SQLException, ClassNotFoundException, UnsupportedEncodingException, FileNotFoundException, IOException {

		logger.info("Running FetchTestReactomeOntologyFiles step");
		ResultSet resultSet = dba.executeQuery("SELECT ontology FROM Ontology", null);
		// The returned value is a single blob composed of binary and text. The three files produced by this step (pprj, pins, pont) are found within this blob.
		// A handful of regexes and conditional statements are used to handle this data and output the 3 files.
		String pprjFilename = "reactome_data_model.pprj";
		String pontFilename = "reactome_data_model.pont";
		String pinsFilename = "reactome_data_model.pins";

		createOutputFile(pprjFilename);
		createOutputFile(pontFilename);
		createOutputFile(pinsFilename);

		while (resultSet.next()) {

			Blob blob = resultSet.getBlob("ontology");
			BufferedReader br = new BufferedReader(new InputStreamReader(blob.getBinaryStream()));

			int dateTimeCounter = 0;
			boolean pprjSwitch = true;
			boolean pontSwitch = false;
			boolean pinsSwitch = true;
			String str;
			logger.info("Generating " + pprjFilename + ", " + pontFilename + ", and " + pinsFilename);
			while ((str = br.readLine()) != null) {
				String line = str;
				String[] splitLine = line.split(";");
				// A very specific regex for matching a datetime string -- Could probably be matched to a specific format that looks cleaner
				if (splitLine.length > 1 && splitLine[1].matches("( [A-Z][a-z]{2}){2} [0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} [A-Z]{3} [0-9]{4}")) {
					String dateTime = ";" + splitLine[1] + "\n";
					dateTimeCounter++;
					if (pprjSwitch && dateTimeCounter == 1) {
						Files.write(Paths.get(pprjFilename), dateTime.getBytes(), StandardOpenOption.APPEND);
					}
					if (dateTimeCounter == 2) {
						Files.write(Paths.get(pontFilename), dateTime.getBytes(), StandardOpenOption.APPEND);
					}
					if (dateTimeCounter == 3) {
						Files.write(Paths.get(pinsFilename), dateTime.getBytes(), StandardOpenOption.APPEND);
					}
					continue;
				}
				line += "\n";

				// Generate pprj file
				if (dateTimeCounter == 1 && pprjSwitch) {
					if (line.contains("pprj_file_content")) {
						line = "\n";
						pprjSwitch = false;
					}
					if (line.contains(".pont")) {
						line = line.replaceAll("[a-zA-Z0-9]+.pont", pontFilename);
					}
					if (line.contains(".pins")) {
						line = line.replaceAll("[a-zA-Z0-9]+.pins", pinsFilename);
					}
					Files.write(Paths.get(pprjFilename), line.getBytes(), StandardOpenOption.APPEND);
				}

				// Generate pont file
				if (dateTimeCounter == 2 && line.startsWith(";")) {
					pontSwitch = true;
				}
				if (pontSwitch) {
					if (line.contains("pont_file_content")) {
						String[] asciiSplit = line.split("\\)\\)\\)");
						line = asciiSplit[0] + ")))\n";
						pontSwitch = false;
					}
					Files.write(Paths.get(pontFilename), line.getBytes(), StandardOpenOption.APPEND);
				}

				// Generate pins file
				if (dateTimeCounter == 3) {
					if (line.contains("pins_file_stub")) {
						line = "\n";
					}

					if (pinsSwitch) {
						if (line.startsWith(";") || line.startsWith("(") || line.startsWith(")") || line.startsWith("\n") || line.startsWith("\t")) {
							Files.write(Paths.get(pinsFilename), line.getBytes(), StandardOpenOption.APPEND);
						} else {
							pinsSwitch = false;
						}
					}
				}
			}
		}
		String outpathName = releaseNumber + "/reactome_data_model.";
		moveFile(pprjFilename, outpathName + "pprj");
		moveFile(pontFilename, outpathName + "pont");
		moveFile(pinsFilename, outpathName + "pins");

		logger.info("Finished FetchTestReactomeOntologyFiles");
	}

	private static void createOutputFile(String filename) throws IOException {
		File file = new File(filename);
		if (file.exists()) {
			file.delete();
		}
		file.createNewFile();
	}

	private static void moveFile(String filename, String outfilePath) throws IOException {
		Files.move(Paths.get(filename), Paths.get(outfilePath), StandardCopyOption.REPLACE_EXISTING);

	}
}
