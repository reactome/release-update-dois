package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import static org.bbop.util.ProcessCommunicator.run;

public class CreateReleaseTarball {
	private static final Logger logger = LogManager.getLogger();
	public static boolean runPerl = true;
	
	public static void execute(String releaseNumber, String releaseDownloadDir) throws IOException, InterruptedException {
		logger.info("Running CreateReleaseTarball step");
		
		if (runPerl) {
			runCommand("perl " + releaseDownloadDir + "/make_release_tarball.pl " + releaseNumber);
		} else {
			final String tarDirRelease = "reactome_tar/" + releaseNumber + "/Release";
			final String absReleaseDir = "/usr/local/gkb/scripts/release/website_files_update";
			final String absReactomeDir = "/usr/local/reactomes/Reactome/production/Website/static/download/" + releaseNumber;
			final String releaseRepo = "https://github.com/reactome/Release.git";
			final String tarDirGKB = "reactome_tar/" + releaseNumber + "/reactome/GKB";
			final String statsHTML = tarDirGKB + "/website/html/stats.html";
			final String statsPNG = tarDirGKB + "/website/html/stats.png";
			final String statsReleaseHTMLSource = absReleaseDir + "/stats_v" + releaseNumber + ".html";
			final String statsReleasePNGSource = absReleaseDir + "/stats_v" + releaseNumber + ".png";
			final String statsReleaseTarget = tarDirGKB + "/website/html/";
			final String downloadHTMLPath = tarDirGKB + "/website/html/download/";
			final String allInteractionsHTML = tarDirRelease + "/website/html/download/all_interactions.html";
			final String indexHTML = tarDirRelease + "/website/html/download/index.html";
			final String thirdPartyRepoPath = tarDirGKB + "/third_party_install";
			final String thirdParthTargetPath = tarDirRelease + "/third_party_install";
			final String configTarPath = tarDirGKB + "/third_party_install/config.tar.gz";
			final String analysisServicePath = "reactome_tar/" + releaseNumber + "/reactome/AnalysisService";
			final String analysisBinSourcePath = "/usr/local/reactomes/Reactome/production/AnalysisService/input/analysis.bin";
			final String analysisBinTargetPath = analysisServicePath + "/input/analysis.bin";
			final String fireworksSourcePath = absReactomeDir + "/fireworks";
			final String fireworksTargetPath = tarDirGKB + "/website/html/download/" + releaseNumber + "/fireworks";
			final String diagramsSourcePath = absReactomeDir + "/diagram";
			final String diagramsTargetPath = tarDirGKB + "/website/html/download/" + releaseNumber;
			final String tarballSourcePath = releaseNumber + "/reactome.tar.gz";
			final String tarballTargetPath = "reactome_tar/" + releaseNumber + "/reactome/";
			releaseNumber = releaseNumber.replace("/", "");
			//TODO: When the dust has settled, make sure the tarball contains everything we need
			
			// Remove any existing Release repositorys
			Files.createDirectories(Paths.get(tarDirRelease));
			logger.info("Cloning Release: " + releaseRepo + "...");
			try {
				Git.cloneRepository().setURI(releaseRepo).setDirectory(Paths.get(tarDirRelease).toFile()).call();
				logger.info("Finshed cloning Release repository");
			} catch (GitAPIException e) {
				logger.error("Release repository could not be cloned from GitHub due to the following error: ", e);
			} 
			logger.info("Copying files into reactome_tar directory...");
			Files.createDirectories(Paths.get(tarDirGKB));
			try {
				FileUtils.copyDirectory(new File(tarDirRelease + "/website"), new File(tarDirGKB));
			} catch (Exception e) {
				logger.error("Could not copy directory due to the following error: " + e);
			}

			runCommand("rm -f " + statsHTML);
			runCommand("rm -f " + statsPNG);
			copyFile(Paths.get(statsReleaseHTMLSource), Paths.get(statsReleaseTarget));
			copyFile(Paths.get(statsReleasePNGSource), Paths.get(statsReleaseTarget));
			runCommand("rm -fr " + downloadHTMLPath);
			Files.createDirectories(Paths.get(downloadHTMLPath + releaseNumber));
			
			runCommand("cp -t"  + downloadHTMLPath + " " + allInteractionsHTML + " " + indexHTML);
	
			// This step might not be needed anymore, according to a comment in the old code (make_release_tarball.pl)
			logger.info("Producing config.tar.gz file from " + thirdPartyRepoPath + "...");
			Files.createDirectories(Paths.get(thirdPartyRepoPath));
			runCommand("tar czf " + configTarPath + " -C " + thirdParthTargetPath + " etc usr");

			Files.createDirectories(Paths.get(analysisServicePath + "/temp"));
			Files.createDirectories(Paths.get(analysisServicePath + "/input"));
		
			logger.info("Copying fireworks directory into reactome_tar...");
			FileUtils.copyDirectory(new File(fireworksSourcePath), new File(fireworksTargetPath));
			logger.info("Copying diagram files into reactome_tar...");
			copyFile(Paths.get(diagramsSourcePath), Paths.get(diagramsTargetPath));
			copyFile(Paths.get(analysisBinSourcePath), Paths.get(analysisBinTargetPath));
			// It usually created a 'RESTful' directory here. All it contains is an empty 'temp' subdirectory, so it wasn't included for this rewrite.
			
			// Tar everything
			
			logger.info("Generating reactome tarball...");
			runCommand("tar czf " + tarballSourcePath + " -C " + tarballTargetPath + ".");
	
			runCommand("rm -r reactome_tar/");
		}
		// TODO: Solr, apache-tomcat(?), install_reactome.sh modification
		logger.info("Finished CreateReleaseTarball");
	}

	private static void runCommand(String commandString) throws IOException, InterruptedException {
		Process command = Runtime.getRuntime().exec(commandString);
		command.waitFor();
	}

	private static void copyFile(Path sourcePath, Path targetPath) throws IOException {
		Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
	}
}
