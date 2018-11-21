package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

public class CreateReleaseTarball {
	private static final Logger logger = LogManager.getLogger();
	public static boolean runPerl = true;
	
	public static void execute(String releaseNumber, String releaseDownloadDir) throws IOException, InterruptedException {
		logger.info("Running CreateReleaseTarball");
		
		if (runPerl) {
			String tarballCommand = "perl " + releaseDownloadDir + "/make_release_tarball.pl " + releaseNumber;
			Process runPerlTarballCommand = Runtime.getRuntime().exec(tarballCommand);
			runPerlTarballCommand.waitFor();
		} else {
		
		
			String tarDirRelease = "reactome_tar/" + releaseNumber + "/Release";
			String absReleaseDir = "/usr/local/gkb/scripts/release/website_files_update";
			String absReactomeDir = "/usr/local/reactomes/Reactome/production/Website/static/download/" + releaseNumber;
			releaseNumber = releaseNumber.replace("/", "");
			//TODO: When the dust has settled, make sure the tarball contains everything we need
			
			// Remove any existing Release repositorys
			File releaseDir = new File(tarDirRelease);
			releaseDir.mkdirs();
			if (releaseDir.list().length > 0) {
				Process removeReleaseRepo = Runtime.getRuntime().exec("rm -r " + tarDirRelease);
				removeReleaseRepo.waitFor();
			} 
			logger.info("Cloning Release: https://github.com/reactome/Release.git...");
			try {
				Git.cloneRepository().setURI("https://github.com/reactome/Release.git").setDirectory(Paths.get(tarDirRelease).toFile()).call();
				logger.info("Finshed cloning Release repository");
			} catch (GitAPIException e) {
				e.printStackTrace();
			} 
			logger.info("Copying files into reactome_tar directory...");
			String tarDirGKB = "reactome_tar/" + releaseNumber + "/reactome/GKB";
			new File(tarDirGKB).mkdirs();
			try {
				FileUtils.copyDirectory(new File(tarDirRelease + "/website"), new File(tarDirGKB));
			} catch (FileNotFoundException ignore) {}
			
			Runtime.getRuntime().exec("rm -f " + tarDirGKB + "/website/html/stats.html");
			Runtime.getRuntime().exec("rm -f " + tarDirGKB + "/website/html/stats.png");
			Files.copy(Paths.get(absReleaseDir + "/stats_v" + releaseNumber + ".html"), Paths.get(tarDirGKB + "/website/html/"), StandardCopyOption.REPLACE_EXISTING);
			Files.copy(Paths.get(absReleaseDir + "/stats_v" + releaseNumber + ".png"), Paths.get(tarDirGKB + "/website/html/"), StandardCopyOption.REPLACE_EXISTING);
			Runtime.getRuntime().exec("rm -fr " + tarDirGKB + "/website/html/download/");
			new File(tarDirGKB + "/website/html/download/" + releaseNumber).mkdirs();
			
			Process copyReleaseHtml = Runtime.getRuntime().exec("cp -t"  + tarDirGKB + "/website/html/download/ " + tarDirRelease + "/website/html/download/all_interactions.html " + tarDirRelease + "/website/html/download/index.html ");
			copyReleaseHtml.waitFor();
	
			// This step might not be needed anymore, according to a comment in the old code (make_release_tarball.pl)
			logger.info("Producing config.tar.gz file from Release/third_party_install...");
			new File(tarDirGKB + "/third_party_install").mkdirs();
			Process tarThirdPartyInstall = Runtime.getRuntime().exec("tar czf " + tarDirGKB + "/third_party_install/config.tar.gz -C " + tarDirRelease + "/third_party_install etc usr");
			tarThirdPartyInstall.waitFor();
			
			String analysisServiceDir = "reactome_tar/" + releaseNumber + "/reactome/AnalysisService";
			new File(analysisServiceDir + "/temp").mkdirs();
			new File(analysisServiceDir + "/input").mkdirs();
		
			logger.info("Copying fireworks directory into reactome_tar...");
			FileUtils.copyDirectory(new File(absReactomeDir + "/fireworks"), new File(tarDirGKB + "/website/html/download/" + releaseNumber + "/fireworks"));
			logger.info("Copying diagram files into reactome_tar...");
			Files.copy(Paths.get(absReactomeDir + "/diagram"), Paths.get(tarDirGKB + "/website/html/download/" + releaseNumber), StandardCopyOption.REPLACE_EXISTING);
			
			Files.copy(Paths.get("/usr/local/reactomes/Reactome/production/AnalysisService/input/analysis.bin"), Paths.get(analysisServiceDir + "/input/analysis.bin"), StandardCopyOption.REPLACE_EXISTING);
			
			// It usually created a 'RESTful' directory here. All it contains is an empty 'temp' subdirectory, so it wasn't included for this rewrite.
			
			// Tar everything
			
			logger.info("Generating reactome tarball...");
			Process reactomeTar = Runtime.getRuntime().exec("tar czf " + releaseNumber + "/reactome.tar.gz -C reactome_tar/" + releaseNumber + "/reactome/ .");
			reactomeTar.waitFor();
	
			Process removeReactomeTarDir = Runtime.getRuntime().exec("rm -r reactome_tar");
			removeReactomeTarDir.waitFor();
		}
		// TODO: Solr, apache-tomcat(?), install_reactome.sh modification
		logger.info("Finished CreateReleaseTarball");
	}
}
