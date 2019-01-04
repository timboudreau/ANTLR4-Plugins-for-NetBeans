package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.openide.util.Lookup;
import org.openide.util.Parameters;

/**
 * Provides the classpath for invoking a specific version of Antlr,
 * which may or may not [maven] by the binary bundled with this
 * plugin.
 *
 * @author Tim Boudreau
 */
public interface AntlrLibrary extends Supplier<URL[]> {

    /**
     * Get the default instance, which uses the bundled
     * Antlr library binaries.
     *
     * @return An AntlrLibrary
     */
    static AntlrLibrary getDefault() {
        return Lookup.getDefault().lookup(AntlrLibrary.class);
    }

    /**
     * Get the classpath as an array of JAR and folder URLs.
     *
     * @return The classpath contents
     */
    URL[] getClasspath();

    /**
     * Create a new AntlrLibrary from some paths to jars or folders
     * or both.
     *
     * @param paths The paths
     * @return an Antlr library
     * @throws IllegalArgumentException if any of the paths do not
     * exist
     */
    static AntlrLibrary create(Path... paths) {
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) {
            if (!Files.exists(paths[i])) {
                throw new IllegalArgumentException("Does not exist: " + paths[i]);
            }
            try {
                urls[i] = paths[i].toUri().toURL();
            } catch (MalformedURLException ex) {
                throw new AssertionError(ex);
            }
        }
        return new AntlrLibrary() {
            @Override
            public URL[] getClasspath() {
                return urls;
            }

            @Override
            public Path[] paths() {
                return paths;
            }
        };
    }

    /**
     * Implements Supplier&lt;URL[]&gt;.
     * @return An array of URLs
     */
    default URL[] get() {
        return getClasspath();
    }

    /**
     * Returns the classpath as an array of Path instances.
     *
     * @return A path
     */
    default Path[] paths() {
        URL[] urls = getClasspath();
        Path[] result = new Path[urls.length];
        try {
            for (int i = 0; i < urls.length; i++) {
                result[i] = Paths.get(urls[i].toURI());
            }
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
        return result;
    }

    /**
     * Returns a new AntlrLibrary which includes some additional
     * URLs in its classpath.
     *
     * @param additional The additional URLs
     * @return
     */
    default AntlrLibrary with(URL... additional) {
        Parameters.notNull("additional", additional);
        if (additional.length == 0) {
            return this;
        }
        return () -> {
            List<URL> base = new ArrayList<>(Arrays.asList(AntlrLibrary.this.getClasspath()));
            base.addAll(Arrays.asList(additional));
            return base.toArray(new URL[base.size()]);
        };
    }

    /**
     * Returns a new AntlrLibrary which includes some additional
     * Paths in its classpath.
     *
     * @param additional The additional URLs
     * @return
     */
    default AntlrLibrary with(Path... additional) {
        Parameters.notNull("additional", additional);
        if (additional.length == 0) {
            return this;
        }
        return () -> {
            List<URL> base = new ArrayList<>(Arrays.asList(AntlrLibrary.this.getClasspath()));
            for (Path pth : additional) {
                try {
                    base.add(pth.toUri().toURL());
                } catch (MalformedURLException ex) {
                    throw new AssertionError(ex);
                }
            }
            return base.toArray(new URL[base.size()]);
        };
    }

    static AntlrLibrary forOwnerOf(Path file) {
        return getDefault();
    }
}
