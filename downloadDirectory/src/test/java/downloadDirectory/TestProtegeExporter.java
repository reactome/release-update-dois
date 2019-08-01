package downloadDirectory;

import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Before;
import org.junit.Test;
import org.reactome.release.downloadDirectory.ProtegeExporter;

@SuppressWarnings("static-method")
public class TestProtegeExporter
{
	private static final String RELEASE_NUM = "70";
	private static String host;
	private static String name;
	private static String user;
	private static String password;
	// Set pathToJavaRoot from the command line as: -DpathToJavaRoot="/path/to/data-release-pipeline/downloadDirectory/"
	static final String pathToJavaRoot = System.getProperty("pathToJavaRoot");
	// Set clean up from the command line as: -Dcleanup=true
	static final boolean cleanupAfterTest = Boolean.valueOf(System.getProperty("cleanup"));
	
	@Before
	public void setup() throws FileNotFoundException, IOException, Exception
	{
		if (pathToJavaRoot == null || pathToJavaRoot.trim().isEmpty())
		{
			throw new Exception("pathToJavaRoot cannot be empty! Set the value when running Java as: -DpathToJavaRoot=\"/path/to/data-release-pipeline/downloadDirectory/\"");
		}
		
		System.out.println("pathToJavaRoot is: " + pathToJavaRoot);
		
		Properties props = new Properties();
		try(FileInputStream fis = new FileInputStream("src/test/resources/db.properties"))
		{
			props.load(fis);
			TestProtegeExporter.host = props.getProperty("db.host");
			TestProtegeExporter.name = props.getProperty("db.name");
			TestProtegeExporter.user = props.getProperty("db.user");
			TestProtegeExporter.password = props.getProperty("db.password");
			String release = RELEASE_NUM;
			Files.createDirectories(Paths.get(release+"/"));
		}
	}
	
	@Test
	public void testProtegeExporterIT() throws SQLException, IOException
	{
		MySQLAdaptor adaptor = new MySQLAdaptor(TestProtegeExporter.host, TestProtegeExporter.name, TestProtegeExporter.user, TestProtegeExporter.password);
		
		ProtegeExporter testExporter = new ProtegeExporter();
		
		testExporter.setReleaseDirectory("/home/sshorser/workspaces/reactome/Release");
		testExporter.setPathToWrapperScript(pathToJavaRoot + "src/main/resources/");
		testExporter.setExtraIncludes(Arrays.asList("-I/home/ubuntu/perl5/lib/perl5/","-I/home/sshorser/perl5/lib/perl5/"));
		testExporter.setParallelism(4);
		HashSet<Long> pathwayIds = new HashSet<>(Arrays.asList(1670466L,8963743L,870392L,1500931L,5205647L));
		testExporter.setPathwayIdsToProcess(pathwayIds);
		testExporter.setDownloadDirectory(RELEASE_NUM);
		testExporter.setSpeciesToProcess(new HashSet<>(Arrays.asList("Mycobacterium tuberculosis")));
		testExporter.execute(adaptor);
		final String pathToFinalTar = pathToJavaRoot + RELEASE_NUM + "/protege_files.tar";
		assertTrue(Files.exists(Paths.get(pathToFinalTar)));
		assertTrue(Files.size(Paths.get(pathToFinalTar)) > 0);
		
		// Now, let's see if the tar contents are valid.
		try(InputStream inStream = new FileInputStream(pathToFinalTar);
			TarArchiveInputStream tains = new TarArchiveInputStream(inStream);)
		{
			boolean done = false;
			boolean tarContainsPathwayID = false;
			while (!done)
			{
				TarArchiveEntry entry = tains.getNextTarEntry();
				if (entry == null)
				{
					done = true;
				}
				else
				{
					for (Long pathwayId : pathwayIds)
					{
						if (entry.getName().contains(pathwayId.toString()) && entry.getSize() > 0)
						{
							tarContainsPathwayID = true;
							// can exit the loop early if at least one pathway ID was found.
							done = true;
						}
					}
				}
			}
			assertTrue(tarContainsPathwayID);
		}
		if (cleanupAfterTest)
		{
			System.out.println("You specified \"cleanup\", so " + pathToFinalTar + " will now be removed.");
			Files.delete(Paths.get(pathToFinalTar));
		}
	}
	
}
