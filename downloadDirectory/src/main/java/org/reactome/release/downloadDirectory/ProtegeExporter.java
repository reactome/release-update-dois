package org.reactome.release.downloadDirectory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * Exports top-level pathways in Protege format, using the Perl code in GKB::WebUtils, and then
 * packages the resulting files into a single tar file.
 * Takes ~1.5 hours on my (Solomon) workstation, with 7 threads running 8 cores. Of course, it ran the system
 * at a very high load. 5 threads (concurrent protege exporters) finished in almost the same amount of time
 * and the system was not too overloaded. Less than that seemed to complete slower.
 * @author sshorser
 *
 */
public class ProtegeExporter
{
	private static final String PROTEGE_ARCHIVE_PATH = "/tmp/protege_files.tar";
	private static final String PROTEGE_FILES_DIR = PROTEGE_ARCHIVE_PATH.replace(".tar", "/");
	private static final Logger logger = LogManager.getLogger();
	private String releaseDirectory;
	private int parallelism = ForkJoinPool.getCommonPoolParallelism();
	private List<String> extraIncludes = new ArrayList<>();
	private String pathToWrapperScript = "";
	private String downloadDirectory;
	private Set<Long> pathwayIdsToProcess = new HashSet<>();
	
	public ProtegeExporter()
	{
		// Default no-arg consturctor.
	}
	
	public ProtegeExporter(Properties props, String releaseDir, String downloadDir)
	{
		this.setReleaseDirectory(releaseDir);
		String propsPrefix = "protegeexporter";
		int degP = Integer.parseInt(props.getProperty(propsPrefix + ".parallelism"));
		this.setParallelism(degP);
		String extraIncludeStr = props.getProperty(propsPrefix+".extraIncludes");
		if (extraIncludeStr != null && !extraIncludeStr.trim().isEmpty())
		{
			List<String> extraIncs = Arrays.asList(extraIncludeStr.split(","));
			this.setExtraIncludes(extraIncs);
		}
		String pathToWrapper = props.getProperty(propsPrefix+".pathToWrapperScript");
		this.setPathToWrapperScript(pathToWrapper);
		this.setDownloadDirectory(downloadDir);
		
		String filterIds = props.getProperty(propsPrefix+".filterIds");
		if (filterIds != null && !filterIds.trim().isEmpty())
		{
			List<String> ids = Arrays.asList(filterIds.split(","));
			this.setPathwayIdsToProcess( ids.stream().map(Long::valueOf).collect(Collectors.toSet()) );
		}
	}
	
	public void execute(MySQLAdaptor dba)
	{
		// Create the protege files.
		createProtegeFiles(dba);
		// Now put all of the files into an archive.
		tarProtegeFiles();
	}

	private void createProtegeFiles(MySQLAdaptor dba)
	{
		try
		{
			@SuppressWarnings("unchecked")
			Collection<GKInstance> frontPages =  dba.fetchInstancesByClass(ReactomeJavaConstants.FrontPage);
			// There should only ever be one FrontPage object, but the database returns a list, so let's just go with that...
			for (GKInstance frontPage : frontPages)
			{
				@SuppressWarnings("unchecked")
				List<GKInstance> pathways = frontPage.getAttributeValuesList(ReactomeJavaConstants.frontPageItem);
				logger.info("{} pathways from the FrontPage to export in protege format.", pathways.size());

				ForkJoinPool pool = new ForkJoinPool(this.parallelism);
				
				// Prepare a shutdown hook to shut down thread pool. Shutting down
				// the child Perl processes may not be feasible, so just let the user
				// know they could still be running.
				Runtime.getRuntime().addShutdownHook(new Thread( () ->
				{
					logger.info("Shutting down thread pool. IF this program was terminated prematurely, there might still be orphan Perl processe runing."
								+ " Check to see if any are still running, and kill them if necssary.");
					pool.shutdownNow();
				}));
				// Filtering is in effect IF the set has something in it.
				final boolean filterInEffect = !this.pathwayIdsToProcess.isEmpty();
				if (filterInEffect)
				{
					logger.info("Filtering is in effect. ONLY the following pathways will be exported to protege: {}", this.pathwayIdsToProcess.toString());
				}
				LocalDateTime overallStart = LocalDateTime.now();
				// submit jobs to pool:
				pool.submit(() ->
				{
					// parallelStream should use the degree of parallelism set on the "pool" object.
					pathways.parallelStream().forEach(pathway -> 
					{
						boolean processPathway = true;
						if (filterInEffect)
						{
							if (!this.pathwayIdsToProcess.contains(pathway.getDBID()))
							{
								processPathway = false;
							}
						}
						if (processPathway)
						{
							logger.info("Running protegeexport script for Pathway: {}", pathway.toString());
							// include a normalized display_name in the output to make it easiser to identify the contents of an archive.
							String normalizedDisplayName = pathway.getDisplayName().toLowerCase().replace(" ", "_").replace("-", "_").replace("(", "").replace(")", "");
							String fileName = "Reactome_pathway_" + pathway.getDBID() + "_" + normalizedDisplayName;
							List<String> cmdArgs = new ArrayList<>();
							cmdArgs.add("perl");
							cmdArgs.addAll(this.extraIncludes);
							cmdArgs.addAll(Arrays.asList("-I"+this.releaseDirectory+"/modules", "run_protege_exporter.pl", pathway.getDBID().toString(), fileName));
							// Build the process.
							ProcessBuilder processBuilder = new ProcessBuilder();
							processBuilder.command(cmdArgs)
										.directory(Paths.get(this.pathToWrapperScript).toFile())
										.inheritIO();
							logger.info("Command is: `{}`", String.join(" ", processBuilder.command()));
							Process process = null;
							try
							{
								LocalDateTime exportStart = LocalDateTime.now();
								process = processBuilder.start();
								int exitCode = process.waitFor();
								LocalDateTime exportEnd = LocalDateTime.now();
								logger.info("Finished generating protege files for {}; Elapsed time: {}; Exit code is {}", pathway.toString(), Duration.between(exportStart, exportEnd), exitCode);
								if (exitCode == 0)
								{
									Files.createDirectories(Paths.get("/tmp/protege_files"));
									// Move the file to the protege_files, overwrite existing.
									Files.move(Paths.get("/tmp/"+fileName+".tar.gz"), Paths.get(PROTEGE_FILES_DIR+fileName+".tar.gz"), StandardCopyOption.REPLACE_EXISTING);
								}
								else
								{
									// Should a non-zero exit throw an exception and interrupt the whole process? I am leaning to "no" - if a *single* pathway fails to export,
									// I think I'd want the rest of them to continue, and then we can re-run for just the failed pathway.
									logger.error("Non-zero exit code from protegeexporter script: {}. Check logs for more details.", exitCode);
								}
							}
							catch (IOException e)
							{
								e.printStackTrace();
							}
							catch (InterruptedException e)
							{
								e.printStackTrace();
								process.destroyForcibly();
							}
						}
					});
				}).get();
				LocalDateTime overallEnd = LocalDateTime.now();
				pool.shutdown();
				logger.info("Overall elapsed time: {}", Duration.between(overallStart, overallEnd));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * TARs the protege files.
	 * @param pathToProtegeFiles The path to where the protege archive files are. They need to be collected into a single archive.
	 */
	private void tarProtegeFiles()
	{
		try(FileOutputStream protegeTar = new FileOutputStream(PROTEGE_ARCHIVE_PATH);
			TarArchiveOutputStream tarOutStream = new TarArchiveOutputStream(protegeTar);)
		{
			Files.list(Paths.get(PROTEGE_FILES_DIR)).forEach( protegeFile -> 
			{
				// Sanity check on the file: Since we're just iterating through a directory, there could be other stuff in there.
				// Only continue if:
				// 	1) the file is a NORMAL file (not a directory) and
				// 	2) it matches the pattern Reactome_pathway_.*\.tar\.gz
				if (protegeFile.toFile().isFile() && protegeFile.getFileName().toString().matches("Reactome_pathway_.*\\.tar\\.gz"))
				{
					logger.info("Adding {} to protege_files.tar", protegeFile.toString());
					try(InputStream is = new FileInputStream(protegeFile.toFile());)
					{
						// Create an entry with the name being the name of the file.
						TarArchiveEntry entry = new TarArchiveEntry(protegeFile.getFileName().toString());
						entry.setSize(protegeFile.toFile().length());
						tarOutStream.putArchiveEntry(entry);
						
						final int len = 1024;
						byte[] buff = new byte[len];
						long bytesRead = is.read(buff, 0, len);
						while (bytesRead > 0)
						{
							// If bytesRead + len > entrySize then an exception will be thrown, so always take min of len,bytesRead. 
							tarOutStream.write(buff, 0, (int) Math.min(len, bytesRead));
							bytesRead = is.read(buff, 0, len);
						}
						
						tarOutStream.closeArchiveEntry();
						tarOutStream.flush();
					}
					catch (FileNotFoundException e)
					{
						e.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					
				}
			});
			tarOutStream.finish();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		// Now that we're finished creating the tar file, move it to the download directory. Overwrite existing.
		try
		{
			Files.move(Paths.get(PROTEGE_ARCHIVE_PATH), Paths.get(this.downloadDirectory + "/protege_files.tar"), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			logger.error("An error occurred while trying to move {} to the download directory ({}). You may need to move it manually.", PROTEGE_ARCHIVE_PATH, this.downloadDirectory);
			e.printStackTrace();
		}
	}
	
	/**
	 * Set the path to the Release directory. This path will be the base for including the Reactome GKB modules, as:
	 * <pre>"-I"+this.releaseDirectory+"/modules"</pre>
	 * @param releaseDirectory
	 */
	public void setReleaseDirectory(String releaseDirectory)
	{
		this.releaseDirectory = releaseDirectory;
	}

	/**
	 * Set the degree of parallelism. Default will be the default degree of parallelism of the ForkJoinPool's common thread pool (usually, I think this is NUMBER_OF_CPUS - 1).
	 * This code will run multiple protege exports in parallel, so if you set this to 3, you should see up to three perl processes running protege export. If you use setPathwayIdsToProcess
	 * to set a single pathway, increasing parallelism won't make this run any faster.
	 * @param parallelism
	 */
	public void setParallelism(int parallelism)
	{
		this.parallelism = parallelism;
	}

	/**
	 * If, for some reason, there are libraries/modules that are not in Perl's "@INC",
	 * add them here as list, be sure to prefix each with "-I", as <code>Arrays.asList("-I/home/MY_USER/perl5/lib/perl5/","-I/other/path/to_perl_libs/")</code>
	 * @param extraIncludes
	 */
	public void setExtraIncludes(List<String> extraIncludes)
	{
		this.extraIncludes = extraIncludes;
	}

	/**
	 * Specify the path to the directory that contains the wrapper script.
	 * @param pathToWrapperScript
	 */
	public void setPathToWrapperScript(String pathToWrapperScript)
	{
		this.pathToWrapperScript = pathToWrapperScript;
	}

	/**
	 * Sets the path to where the Download Directory is located.
	 * This is where files for download will be placed.
	 * @param downloadDir
	 */
	public void setDownloadDirectory(String downloadDir)
	{
		this.downloadDirectory = downloadDir;
	}

	/**
	 * Set a list of DB IDs for pathways to filter on. ONLY pathways that are in this list will be exported.
	 * This functionality is intended primarily for testing, but you could use it in production if you want to export
	 * specific pathways.
	 * @param pathwayIds
	 */
	public void setPathwayIdsToProcess(Set<Long> pathwayIds)
	{
		this.pathwayIdsToProcess = pathwayIds;
	}	
}
