package downloadDirectory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

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
	
	@Before
	public void setup() throws FileNotFoundException, IOException
	{
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
	public void testProtegeExporterIT() throws SQLException
	{
		MySQLAdaptor adaptor = new MySQLAdaptor(TestProtegeExporter.host, TestProtegeExporter.name, TestProtegeExporter.user, TestProtegeExporter.password);
		
		ProtegeExporter testExporter = new ProtegeExporter();
		testExporter.setReleaseDirectory("/home/sshorser/workspaces/reactome/Release");
		testExporter.setPathToWrapperScript("/home/sshorser/workspaces/reactome/ReleaseNG/data-release-pipeline/downloadDirectory/src/main/resources/");
		testExporter.setExtraIncludes(Arrays.asList("-I/home/ubuntu/perl5/lib/perl5/","-I/home/sshorser/perl5/lib/perl5/"));
//		testExporter.setParallelism(4);
//		testExporter.setPathwayIdsToProcess(new HashSet<>(Arrays.asList(1670466L,8963743L,870392L,1500931L,5205647L)));
		testExporter.setDownloadDirectory(RELEASE_NUM);
		testExporter.execute(adaptor);
	}
	
}
