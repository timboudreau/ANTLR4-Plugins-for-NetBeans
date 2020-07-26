/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.jfs.javac;

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
    private int sourceLevel = 8;
    private int targetLevel = 8;
    private boolean verbose;
    private boolean ideMode = true;
    private boolean suppressAbortOnBadClassFile = true;

    public JavacOptions() {

    }

    private JavacOptions(JavacOptions x) {
        setFrom(x);
    }

    void setFrom(JavacOptions opts) {
        if (opts == this) {
            return;
        }
        debug = opts.debug;
        warn = opts.warn;
        maxWarnings = opts.maxWarnings;
        maxErrors = opts.maxErrors;
        runAnnotationProcessors = opts.runAnnotationProcessors;
        encoding = opts.encoding;
        sourceLevel = opts.sourceLevel;
        targetLevel = opts.targetLevel;
        verbose = opts.verbose;
        ideMode = opts.ideMode;
    }

    public JavacOptions nonIdeMode() {
        ideMode = false;
        return this;
    }

    public JavacOptions verbose() {
        this.verbose = true;
        return this;
    }

    public JavacOptions sourceAndTargetLevel(int tgt) {
        sourceLevel = tgt;
        targetLevel = tgt;
        return this;
    }

    public JavacOptions sourceLevel(int level) {
        sourceLevel = level;
        return this;
    }

    public JavacOptions targetLevel(int level) {
        targetLevel = level;
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

    public JavacOptions abortOnBadClassFile() {
        suppressAbortOnBadClassFile = false;
        return this;
    }

    public enum DebugInfo {
        NONE, LINES, VARS, SOURCE;

        public String toString() {
            return name().toLowerCase();
        }
    }

    private static String levelString(int sourceOrTarget) {
        if (sourceOrTarget < 8) {
            return "1." + sourceOrTarget;
        }
        return Integer.toString(sourceOrTarget);
    }

    @Override
    public String toString() {
        List<String> opts = options(null);
        StringBuilder sb = new StringBuilder();
        for (String opt : opts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(opt);
        }
        return sb.toString();
    }

    public List<String> options(JavaCompiler compiler) {
        // Borrowed from NetBeans
        List<String> options = new ArrayList<>(9);
        if (verbose) {
            options.add("-verbose");
        }
        if (ideMode) {
            // Javac runs inside the IDE
            options.add("-XDide");
        }
        //        options.add("-XDsave-parameter-names");
        // When a class file cannot be read, produce an error type instead of failing with an exception
        if (suppressAbortOnBadClassFile) {
            options.add("-XDsuppressAbortOnBadClassFile");
        }
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
        options.add(levelString(sourceLevel));
        options.add("-target");
        options.add(levelString(targetLevel));
        boolean lastWasRemoved = false;
        for (Iterator<String> iter = options.iterator(); iter.hasNext();) {
            String opt = iter.next();
            if (opt.startsWith("-")) {
                if (compiler != null && compiler.isSupportedOption(opt) == -1) {
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
