package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaCompiler;

/**
 *
 * @author Tim Boudreau
 */
public class JavacOptions {

    private static final Logger LOG = Logger.getLogger(JavacOptions.class.getName());

    private DebugInfo debug = DebugInfo.NONE;
    private boolean warn = false;
    private int maxWarnings = 0;
    private int maxErrors = 1;
    private boolean runAnnotationProcessors;
    private Charset encoding = UTF_8;
    private int sourceAndTarget = 8;

    public JavacOptions() {

    }

    private JavacOptions(JavacOptions x) {
        this.debug = x.debug;
        this.warn = x.warn;
        this.maxWarnings = x.maxWarnings;
        this.maxErrors = x.maxErrors;
        this.runAnnotationProcessors = x.runAnnotationProcessors;
        this.encoding = x.encoding;
    }

    public JavacOptions sourceAndTargetLevel(int tgt) {
        this.sourceAndTarget = tgt;
        return this;
    }

    public JavacOptions copy() {
        return new JavacOptions(this);
    }

    public JavacOptions withCharset(Charset encoding) {
        this.encoding = encoding;
        return this;
    }

    public JavacOptions withDebugInfo(DebugInfo debug) {
        this.debug = debug;
        return this;
    }

    public JavacOptions withMaxWarnings(int maxWarnings) {
        this.maxWarnings = maxWarnings;
        return this;
    }

    public JavacOptions withMaxErrors(int maxErrors) {
        this.maxErrors = maxErrors;
        return this;
    }

    public JavacOptions runAnnotationProcessors(boolean runAnnotationProcessors) {
        this.runAnnotationProcessors = runAnnotationProcessors;
        return this;
    }

    public enum DebugInfo {
        NONE, LINES, VARS, SOURCE;

        public String toString() {
            return name().toLowerCase();
        }
    }

    public List<String> options(JavaCompiler compiler) {
        // Borrowed from NetBeans
        List<String> options = new ArrayList<>(9);
        // Javac runs inside the IDE
        options.add("-XDide");
        //        options.add("-XDsave-parameter-names");
        // When a class file cannot be read, produce an error type instead of failing with an exception
        options.add("-XDsuppressAbortOnBadClassFile");
        // performance - disable debug info
        options.add("-g:" + debug.toString());
        if (!warn) {
            options.add("-nowarn");
        }
        if (maxErrors >= 0) {
            options.add("-Xmaxerrs");
            options.add(Integer.toString(maxErrors));
        }
        if (maxWarnings >= 0) {
            options.add("-Xmaxwarns");
            options.add(Integer.toString(maxWarnings));
        }

        options.add("-XDbreakDocCommentParsingOnError=false"); // Turn off compile fails for javadoc
        if (!runAnnotationProcessors) {
            options.add("-proc:none"); // Do not try to run annotation processors
        }
        options.add("-encoding");
        options.add(encoding.name());
        // If we do not do this, building generated sources will fail on
        // JDK 9 for lack of a module info, or if we generate one, will
        // fail on JDK 8
        
        options.add("-source");
        options.add(Integer.toString(sourceAndTarget));
        options.add("-target");
        options.add(Integer.toString(sourceAndTarget));
        boolean lastWasRemoved = false;
        for (Iterator<String> iter = options.iterator(); iter.hasNext();) {
            String opt = iter.next();
            if (opt.startsWith("-")) {
                if (compiler.isSupportedOption(opt) == -1) {
                    LOG.log(Level.FINE, "Remove unsupported javac arg ''{0}''", opt);
                    iter.remove();
                    lastWasRemoved = true;
                    continue;
                }
            } else if (lastWasRemoved) {
                LOG.log(Level.FINE, "Remove supplementary javac arg ''{0}''", opt);
                iter.remove();
            }
            lastWasRemoved = false;
        }
        return options;
    }
}
