package org.nemesis.antlr.grammar.file.resolver;

import java.io.IOException;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class ProjectTestHelper {

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
        findAntlrGrammarProject();
        return result;
    }

    public static Path findAntlrGrammarProject() throws URISyntaxException {
        Path result = projectBaseDir().getParent().resolve("antlr-language-grammar");
        assertTrue(Files.exists(result), result.toString());
        return result;
    }

    public static Path projectBaseDir() throws URISyntaxException {
        Path baseDir = Paths.get(ProjectTestHelper.class
                .getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParent().getParent();
        return baseDir;
    }

    private ProjectTestHelper() {
        throw new AssertionError();
    }
}
