package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = AntlrLibrary.class, position = Integer.MIN_VALUE)
public class UnitTestAntlrLibrary implements AntlrLibrary {

    private final Path[] paths;

    public UnitTestAntlrLibrary() throws URISyntaxException, IOException {
        List<Path> paths = new LinkedList<>();
        Path libsDir = TestDir.projectBaseDir().resolve(Paths.get("release", "libs"));
        assert Files.exists(libsDir) : "Does not exist: " + libsDir;
        Files.list(libsDir).filter(p -> {
            String fn = p.getFileName().toString();
            return fn.endsWith(".jar") && !fn.contains("javadoc");
        }).forEach(paths::add);
        this.paths = paths.toArray(new Path[0]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Path p : paths) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(p.toString().replace('\\', '/'));
        }
        return sb.toString();
    }

    @Override
    public URL[] getClasspath() {
        try {
            URL[] result = new URL[paths.length];
            for (int i = 0; i < paths.length; i++) {
                result[i] = paths[i].toUri().toURL();
            }
            return result;
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }
}
