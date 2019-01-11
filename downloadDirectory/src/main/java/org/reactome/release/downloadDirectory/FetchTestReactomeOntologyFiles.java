package org.reactome.release.downloadDirectory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
	private static Connection connect = null;
	private static Statement statement = null;
	private static ResultSet resultSet = null;
	
	public static void execute(MySQLAdaptor dba, String username, String password, String host, String database, String releaseNumber) throws SQLException, ClassNotFoundException, UnsupportedEncodingException, FileNotFoundException, IOException {
		logger.info("Running FetchTestReactomeOntologyFiles step");
		Class.forName("com.mysql.jdbc.Driver");
		connect = DriverManager.getConnection("jdbc:mysql://" + host + "/" + database + "?" + "user=" + username + "&password=" + password);
		statement = connect.createStatement();
		resultSet = statement.executeQuery("SELECT ontology FROM Ontology");
		// The returned value is a single blob composed of binary and text. The three files produced by this step (pprj, pins, pont) are found within this blob.
		// A handful of regexes and conditional statements are used to handle this data and output the 3 files. 
		String pprjFilename = "reactome_data_model.pprj";
		String pontFilename = "reactome_data_model.pont";
		String pinsFilename = "reactome_data_model.pins";
		PrintWriter pprjWriter = new PrintWriter(pprjFilename);
		PrintWriter pontWriter = new PrintWriter(pontFilename);
		PrintWriter pinsWriter = new PrintWriter(pinsFilename);
		
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
				
				String[] splitLine = str.split(";");
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
				str += "\n";
				
				// Generate pprj file
				if (dateTimeCounter == 1 && pprjSwitch) {
					if (str.contains("pprj_file_content")) {
						str = "\n";
						pprjSwitch = false;
					}
					if (str.contains(".pont")) {
						str = str.replaceAll("[a-zA-Z0-9]+.pont", pontFilename);
					}
					if (str.contains(".pins")) {
						str = str.replaceAll("[a-zA-Z0-9]+.pins", pinsFilename);
					}
					Files.write(Paths.get(pprjFilename), str.getBytes(), StandardOpenOption.APPEND);
				}
				
				// Generate pont file
				if (dateTimeCounter == 2 && str.startsWith(";")) {
					pontSwitch = true;
				}
				if (pontSwitch) {
					if (str.contains("pont_file_content")) {
						String[] asciiSplit = str.split("\\)\\)\\)");
						str = asciiSplit[0] + ")))\n";
						pontSwitch = false;
					}
					Files.write(Paths.get(pontFilename), str.getBytes(), StandardOpenOption.APPEND);
				}
				
				// Generate pins file
				if (dateTimeCounter == 3) {
					if (str.contains("pins_file_stub")) {
						str = "\n";
					}

					if (pinsSwitch) {
						if (str.startsWith(";") || str.startsWith("(") || str.startsWith(")") || str.startsWith("\n") || str.startsWith("\t")) {
							Files.write(Paths.get(pinsFilename), str.getBytes(), StandardOpenOption.APPEND);
						} else {
							pinsSwitch = false;
						}
					}
				}
			}
		}
		pprjWriter.close();
		pontWriter.close();
		pinsWriter.close();
		String outpathName = releaseNumber + "/reactome_data_model.";
		Files.move(Paths.get(pprjFilename), Paths.get(outpathName + "pprj"), StandardCopyOption.REPLACE_EXISTING); 
		Files.move(Paths.get(pontFilename), Paths.get(outpathName + "pont"), StandardCopyOption.REPLACE_EXISTING); 
		Files.move(Paths.get(pinsFilename), Paths.get(outpathName + "pins"), StandardCopyOption.REPLACE_EXISTING); 
		
		logger.info("Finished FetchTestReactomeOntologyFiles");
	}
}
