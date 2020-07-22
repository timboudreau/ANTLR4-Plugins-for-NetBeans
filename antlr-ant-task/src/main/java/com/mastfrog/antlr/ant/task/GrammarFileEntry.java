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
package com.mastfrog.antlr.ant.task;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 *
 * @author Tim Boudreau
 */
final class GrammarFileEntry implements Comparable<GrammarFileEntry> {

    // XXX deal with comments inside lines
    private static final Pattern GRAMMAR_TYPE = Pattern.compile("([a-z]+)?\\s*grammar\\s+(\\S+);");
    private static final Pattern IMPORT = Pattern.compile("import\\s+(\\S+)\\s*;");
    private static final Pattern TOKEN_VOCAB = Pattern.compile(".*tokenVocab\\s*=\\s*(\\S+)\\s*;");
    final Path path;
    private int kind = -1;
    private final Set<String> dependencies = new HashSet<>();
    private final String grammarName;
    private String pkg;
    private final boolean isImport;

    GrammarFileEntry(Path path, Charset charset, Task task, boolean isImport) throws IOException {
        this.path = path;
        String body = new String(Files.readAllBytes(path), charset);
        Matcher m = GRAMMAR_TYPE.matcher(body);
        /* options { tokenVocab = MarkdownLexer; }*/
        if (m.find()) {
            if (m.group(1) == null) {
                kind = 2;
            } else if ("parser".equals(m.group(1))) {
                kind = 1;
            } else if ("lexer".equals(m.group(1))) {
                kind = 0;
            } else {
                task.log("Could not determine the grammar type from string '" + m.group(1) + "'.  Guessing it is broken; treating it as a lexer grammar. " + "To get rid of this message, specify the grammar order " + "explicitly with the `grammars` property of this task.");
                kind = 0;
            }
            grammarName = m.group(2);
        } else {
            task.log("Could not determine type of " + path.getFileName() + " via regex - " + "it may not be processed in the right order.  To fix, specify " + "the grammars to procecss in order rather than relying on this " + "Task to figure it out.", Project.MSG_WARN);
            // Probably will fail later anyway
            kind = 0;
            grammarName = rawName();
        }
        m = IMPORT.matcher(body);
        while (m.find()) {
            dependencies.add(m.group(1));
        }
        m = TOKEN_VOCAB.matcher(body);
        while (m.find()) {
            dependencies.add(m.group(1));
        }
        this.isImport = isImport;
    }

    boolean isImport() {
        return isImport;
    }

    Set<String> dependencies() {
        return Collections.unmodifiableSet(new TreeSet<>(dependencies));
    }

    void setPackage(String pkg) {
        this.pkg = pkg;
    }

    String getPackage() {
        return pkg;
    }

    static String rawFileName(Path source) {
        String gn = source.getFileName().toString();
        int ix = gn.lastIndexOf('.');
        if (ix > 0) {
            gn = gn.substring(0, ix);
        }
        return gn;
    }

    public String type() {
        switch (kind) {
            case 0:
                return "lexer";
            case 1:
                return "parser";
            case 2:
                return "combined";
            default:
                return "unknown";
        }
    }

    @Override
    public String toString() {
        return type() + " grammar " + grammarName + "(" + fileName() + ") pkg " + pkg;
    }

    public String grammarName() {
        return grammarName;
    }

    public String fileName() {
        return path.getFileName().toString();
    }

    public String rawName() {
        return rawFileName(path);
    }

    public Path parent() {
        return path.getParent();
    }

    boolean isParser() {
        return kind == 1;
    }

    @Override
    public int compareTo(GrammarFileEntry o) {
        if (o.dependencies.contains(fileName()) || o.dependencies.contains(rawName())) {
            return -1;
        } else if (dependencies.contains(o.fileName()) || dependencies.contains(o.rawName())) {
            return 1;
        }
        return Integer.compare(kind, o.kind);
    }

    boolean dependsOn(GrammarFileEntry imp) {
        return dependencies.contains(fileName()) || dependencies.contains(imp.rawName());
    }

}
