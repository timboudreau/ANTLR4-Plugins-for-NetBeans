package org.nemesis.test.fixtures.support;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.Consumer;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public final class ProjectTestHelper {

    private Class<?> testClass;

    ProjectTestHelper(Class<?> testClass) {
        this.testClass = testClass;
    }

    public static ProjectTestHelper relativeTo(Class<?> testClass) {
        return new ProjectTestHelper(testClass);
    }

    public static MavenProjectBuilder projectBuilder() {
        return new MavenProjectBuilder();
    }

    public static Path createStructure(Path in, String[] spec, Map<String, FileObject> lkp, Consumer<Project> pc) throws IOException, URISyntaxException {
        Path result = null;
        for (String sp : spec) {
            if (sp.charAt(0) == '~') {
                String[] fileAndContents = sp.split(":");
                Path p = in.resolve(fileAndContents[0].substring(1));
                if (!Files.exists(p.getParent())) {
                    Files.createDirectories(p.getParent());
                }
                p = Files.createFile(p);
                if (fileAndContents.length == 2) {
                    Files.write(p, fileAndContents[1].getBytes(UTF_8), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                }
                if (result == null) {
                    result = p;
                }
                lkp.put(p.getFileName().toString(), FileUtil.toFileObject(FileUtil.normalizeFile(p.toFile())));
            } else {
                Path p = in.resolve(sp);
                Files.createDirectories(p);
                if (result == null) {
                    result = p;
                }
            }
        }
        if (result != null) {
            pc.accept(ProjectManager.getDefault().findProject(FileUtil.toFileObject(FileUtil.normalizeFile(result.toFile()))));
        }
        return result;
    }

    public Project findAntlrGrammarProject() throws URISyntaxException, IOException {
        return findProject(findAntlrGrammarProjectDir());
    }

    public Path findAntlrGrammarProjectDir() throws URISyntaxException {
        Path result = projectBaseDir().getParent().resolve("antlr-language-grammar");
        assertTrue(Files.exists(result), result.toString());
        return result;
    }

    static void assertTrue(boolean val, String msg) {
        if (!val) {
            throw new AssertionError(msg);
        }
    }

    private Path findTestProjectsParent() throws URISyntaxException {
        Path p = projectBaseDir();
        if ("antlr-project-helpers-maven".equals(p.getFileName().toString())) {
            return p;
        }
        return p.getParent().resolve("antlr-project-helpers-maven").normalize();
    }

    public Project findChildProjectWithChangedAntlrDirsProject() throws URISyntaxException, IOException {
        return findProject(findChildOfParentThatChangesDirsProjectDir());
    }

    public Path findChildOfParentThatChangesDirsProjectDir() throws URISyntaxException {
        return findTestProjectsParent()
                .resolve("antlr-maven-test-projects/child-with-changed-antlr-dir").normalize();
    }

    public Project findChildProjectWithChangedAntlrDirAndEncodingProject() throws URISyntaxException, IOException {
        return findProject(findChildProjectWithChangedAntlrDirAndEncoding());
    }

    public static Project findProject(Path path) throws IOException {
        assertTrue(Files.exists(path), "Does not exist: " + path);
        assertTrue(Files.isDirectory(path), "Not a directory: " + path);
        File file = path.toFile();
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        if (fo == null) {
            throw new AssertionError("No FileObject for " + path);
        }
        Project result = ProjectManager.getDefault().findProject(fo);
        if (result == null) {
            throw new AssertionError("No project for " + result + " is ProjectManager set up correctly?");
        }
        return result;
    }

    public Path findChildProjectWithChangedAntlrDirAndEncoding() throws URISyntaxException {
        return findTestProjectsParent()
                .resolve("antlr-maven-test-projects/other-parent-that-changes-encoding/child-with-changed-antlr-dir-and-encoding").normalize();
    }

    public Path projectBaseDir() throws URISyntaxException {
        Path baseDir = Paths.get(testClass
                .getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParent().getParent();
        return baseDir;
    }

    private ProjectTestHelper() {
        throw new AssertionError();
    }
}
