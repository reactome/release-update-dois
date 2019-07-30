package org.reactome.release.downloadDirectory;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * Exports top-level pathways in Protege format, using the Perl code in GKB::WebUtils
 * Takes ~1.5 hours on my (Solomon) workstation, with 7 threads running 8 cores.
 * @author sshorser
 *
 */
public class ProtegeExporter
{
	private static final Logger logger = LogManager.getLogger();
	
	private String releaseDirectory;
	
	private int parallelism = ForkJoinPool.getCommonPoolParallelism();
	
	private List<String> extraIncludes = new ArrayList<>();
	
	private String pathToWrapperScript = "";
	
	public void execute(MySQLAdaptor dba)
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
//				for (GKInstance pathway : pathways)
				
//				int originalParallelism = ForkJoinPool.getCommonPoolParallelism();
//				System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(this.parallelism));
				ForkJoinPool pool = new ForkJoinPool(this.parallelism);
				
				// Prepare a shutdown hook to shut down the Perl processes in case this Java process is killed.
				// Otherwise, the Perl processes will keep going!
				Runtime.getRuntime().addShutdownHook(new Thread( () ->
					{
						logger.info("Shutting down thread pool");
						pool.shutdownNow();
					}));
				
				pool.submit(() -> {
					// parallelStream should use the degree of parallelism set on the "pool" object.
					pathways.parallelStream().forEach(pathway -> 
					{
						logger.info("Running protegeexport script for Pathway: {}", pathway.toString());
						// Run the script as a one-liner:
						// perl -MGKB::WebUtils -I$(pwd) -I/home/sshorser/perl5/lib/perl5/ -e 'my $wu = GKB::WebUtils->new_from_cgi(); $wu->create_protege_project_wo_orthologues("BLAH",123);'
						// Better: perl protegeexporter ID=12345
						ProcessBuilder processBuilder = new ProcessBuilder();
						// NOTE: if you run this on your workstation, you may need to Include the path to Bio::Root::Root if that package is installed on a non-default path.
						// example: "-I/home/someusername/perl5/lib/perl5"
	//					pb.command("perl", "-I/home/ubuntu/perl5/lib/perl5/","-I/home/sshorser/perl5/lib/perl5/","-I"+this.releaseDirectory+"/modules","protegeexporter","DEBUG=1","DB="+dba.getDBName(),"ID="+pathway.getDBID());
	//					List<String> cmdArgs = Arrays.asList("/usr/bin/perl", "-MGKB::WebUtils", "-I/home/ubuntu/perl5/lib/perl5/","-I/home/sshorser/perl5/lib/perl5/","-I"+this.releaseDirectory+"/modules",
	//								"-e", "'my $wu = GKB::WebUtils->new_from_cgi(); $wu->create_protege_project_wo_orthologues(\"Reactome_"+pathway.getDBID()+"\",["+pathway.getDBID()+"]);'");
						String fileName = "Reactome_pathway_" + pathway.getDBID() + "_"+ pathway.getDisplayName().toLowerCase().replace(" ", "_").replace("-", "_");
						List<String> cmdArgs = new ArrayList<>();
						cmdArgs.add("perl");
						cmdArgs.addAll(this.extraIncludes);
						cmdArgs.addAll(Arrays.asList("-I"+this.releaseDirectory+"/modules", "run_protege_exporter.pl", pathway.getDBID().toString(), fileName));
						processBuilder.command(cmdArgs)
									.directory(Paths.get(this.pathToWrapperScript).toFile())
									.inheritIO();
						
						logger.info("Command is: `{}`", String.join(" ", processBuilder.command()));
						Process process = null;
						try
						{
							process = processBuilder.start();
							int exitCode = process.waitFor();
							logger.info("Finished generating protege files for {}, exit code is {}", pathway.toString(), exitCode);
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
					});
//				System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(originalParallelism));
				}).get();
				pool.shutdown();
			}
		}
		catch (Exception e)
		{
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
	 * Set the degree of parallelism. Default will be the default degree of parallelism of the ForkJoinPool's common thread pool (usually, I think this is NUMBER_OF_CPUS - 1)
	 * @param parallelism
	 */
	public void setParallelism(int parallelism)
	{
		this.parallelism = parallelism;
	}

	/**
	 * If, for some reason, there are libraries/modules that are not in Perl's "@INC",
	 * add them here as a single string, such as "-I/home/MY_USER/perl5/lib/perl5/ -I/other/path/to_perl_libs/"
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
	
}
