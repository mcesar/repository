package repositoryTest;

import java.io.*;

import repository.*;

public class VoltRepositoryTranslatorTest {

	public static void main(String[] args) throws Exception {
		VoltRepositoryTranslator vr = new VoltRepositoryTranslator();
		String path = "src" + File.separator + "repositoryTest" + File.separator;
		if (!new File(".").getAbsolutePath().endsWith("repository-test/.")) {
			path = "repository-test" + File.separator + path;
		}
		File f = new File(path);
		vr.parseFilesInDir(f.getAbsolutePath());
	}
	
}