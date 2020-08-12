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
package org.nemesis.antlr.project.spi.addantlr;

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * When adding Antlr support, the UI can offer to generate a skeleton grammar;
 * this class provides the varieties available and can generate the files.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {
    "COMBINED=Combined Grammar (lexer + parser in one)",
    "LEXER_AND_PARSER=Separate Lexer and Parser Grammars",
    "LEXER_ONLY=Lexer Grammar (no parsing)",
    "NONE=None"
})
public enum SkeletonGrammarType {
    COMBINED,
    LEXER_AND_PARSER,
    LEXER_ONLY,
    NONE;
    @StaticResource
    private static final String SKELETON_COMBINED_GRAMMAR = "org/nemesis/antlr/project/spi/addantlr/SkeletonCombinedGrammar.g4.txt";

    public static SkeletonGrammarType defaultType() {
        int ix = defaultOrdinal();
        if (ix >= 0 && ix < values().length) {
            return values()[ix];
        }
        return COMBINED;
    }

    public static void saveDefaultType(@NonNull SkeletonGrammarType type) {
        int ix = defaultOrdinal();
        if (ix != type.ordinal()) {
            NbPreferences.forModule(SkeletonGrammarType.class).putInt("skeleton", type.ordinal());
        }
    }

    public int numberOfFilesGenerated(boolean hasImportDir) {
        int result;
        switch (this) {
            case COMBINED:
                result = 1;
                break;
            case LEXER_AND_PARSER:
                result = 2;
                break;
            case LEXER_ONLY:
                result = 1;
                break;
            case NONE:
                return 0;
            default:
                throw new AssertionError(this);
        }
        if (hasImportDir) {
            result++;
        }
        return result;
    }

    static int defaultOrdinal() {
        return NbPreferences.forModule(SkeletonGrammarType.class).getInt("skeleton", 0);
    }

    public boolean isGenerate() {
        return this != NONE;
    }

    public String toString() {
        return NbBundle.getMessage(SkeletonGrammarType.class, name());
    }

    /**
     * Generate a skeleton grammar in the passed project, if this instance does
     * that.
     *
     * @param project The project
     * @param srcDir The source directory
     * @param importsDir The imports dir or null
     * @return A runnable which can open the files once generation is complete
     * @throws IOException
     */
    @CheckForNull
    public Runnable generate(@NullAllowed String grammarName, @NonNull Project project, @NonNull FileObject srcDir, @NullAllowed FileObject importsDir, @NullAllowed String pkg, Charset charset) throws IOException {
        try {
            return doGenerate(grammarName, project, srcDir, importsDir, pkg, charset);
        } catch (Exception | Error ex) {
            Exceptions.printStackTrace(ex);
            return () -> {
            };
        }
    }

    private Runnable doGenerate(@NullAllowed String grammarName, @NonNull Project project, @NonNull FileObject srcDir, @NullAllowed FileObject importsDir, @NullAllowed String pkg, Charset charset) throws IOException {
        if (!isGenerate()) {
            return null;
        }
        if (pkg != null && !pkg.isEmpty()) {
            srcDir = FileUtil.createFolder(srcDir, pkg.replace('.', '/'));
        }
        String[] lines;
        String filename = UnixPath.get(SKELETON_COMBINED_GRAMMAR).getFileName().toString();
        try (final InputStream in = NewAntlrConfigurationInfo.class.getResourceAsStream(filename)) {
            lines = Streams.readString(in, "UTF-8", 256).split("\n");
        }
        List<String> parserRules = new ArrayList<>();
        List<String> lexerRules = new ArrayList<>();
        List<String> fragmentRules = new ArrayList<>();
        List<String> current = null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > 0 && lines[i].charAt(0) == '#') {
                String cmd = lines[i].substring(1).trim();
                switch (cmd) {
                    case "parser":
                        current = parserRules;
                        break;
                    case "lexer":
                        current = lexerRules;
                        break;
                    case "fragments":
                        current = fragmentRules;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown instruction "
                                + cmd + " in " + SKELETON_COMBINED_GRAMMAR);
                }
            } else if (!lines[i].isEmpty() && current != null) {
                current.add(lines[i]);
            }
        }
        String pfx = Strings.isBlank(grammarName) ? grammarNamePrefix(project) : grammarName;
        String sep = System.lineSeparator();
        String lexerName = pfx + "Lexer";
        List<FileObject> created = new ArrayList<>();
        // XXX since the project isn't updated and we're in the mutex
        // this could trigger some badness, trying to read the encoding
        // property we haven't written yet if our FileEncodingQuery for Antlr
        // is touched
        switch (this) {
            // Note we write the files in reverse-dependency order - otherwise
            // opening the document can cause the parsing plumbing to start trying
            // to build things before their dependencies exist or are populated
            //
            // Really this is an issue of RebuildSubscriptions listening
            // synchronously on the antlr folders of the project
            case COMBINED:
                writeCombinedGrammar(srcDir, pfx, importsDir, parserRules, lexerRules, fragmentRules, sep, created, charset);
                break;
            case LEXER_AND_PARSER:
                writeLexerGrammar(srcDir, lexerName, importsDir,
                        pfx, sep, fragmentRules, created, lexerRules, charset);
                writeParserGrammar(pfx, srcDir, sep, parserRules, lexerName, created, charset);
                break;
            case LEXER_ONLY:
                writeLexerGrammar(srcDir, lexerName, importsDir, pfx, sep, fragmentRules, created, lexerRules, charset);
                break;
            default:
                throw new AssertionError("Non writing or unhandled type " + this);
        }
        return GeneratedGrammarOpener.getDefault().createOpenerTask(created);
    }

    void writeCombinedGrammar(FileObject srcDir, String pfx, FileObject importsDir, List<String> parserRules, List<String> lexerRules, List<String> fragmentRules, String sep, List<FileObject> created, Charset charset) throws IOException {
        FileObject combinedGrammar = srcDir.createData(pfx, "g4");
        List<List<String>> combinedLexerLineGroups = importsDir == null ? Arrays.asList(parserRules, lexerRules, fragmentRules) : Arrays.asList(parserRules, lexerRules);
        writeOneGrammar("", combinedGrammar, pfx, sep, combinedLexerLineGroups, charset, sb -> {
            if (importsDir != null) {
                sb.append(sep).append("import ").append(fragmentsName(pfx)).append(';').append(sep);
            }
        });
        created.add(combinedGrammar);
        if (importsDir != null) {
            writeFragmentsGrammar(pfx, importsDir, sep, fragmentRules, charset, created);
        }
    }

    void writeParserGrammar(String pfx, FileObject srcDir, String sep, List<String> parserRules, String lexerName, List<FileObject> created, Charset charset) throws IOException {
        String parserName = pfx + "Parser";
        FileObject parserGrammar = srcDir.createData(parserName, "g4");
        writeOneGrammar("parser", parserGrammar, parserName, sep, Arrays.asList(parserRules), charset, sb -> {
            // String lexerName = pfx + "Lexer";
            sb.append(sep).append("options { tokenVocab = ").append(lexerName).append("; }").append(sep);
        });
        created.add(parserGrammar);
    }

    Charset writeLexerGrammar(FileObject srcDir, String lexerName, FileObject importsDir, String pfx, String sep, List<String> fragmentRules, List<FileObject> created, List<String> lexerRules, Charset charset) throws IOException {
        FileObject lexerGrammar = srcDir.createData(lexerName, "g4");
        if (importsDir != null) {
            writeFragmentsGrammar(pfx, importsDir, sep, fragmentRules, charset, created);
        }
        List<List<String>> lexerRuleSet = importsDir != null ? Arrays.asList(lexerRules, fragmentRules) : Arrays.asList(lexerRules);
        writeOneGrammar("lexer", lexerGrammar, lexerName, sep, lexerRuleSet, charset, sb -> {
            if (importsDir != null) {
                sb.append("import ").append(fragmentsName(pfx)).append(';').append(sep);
            }
        });
        created.add(lexerGrammar);
        return charset;
    }

    private static String fragmentsName(String prefix) {
        return prefix.toLowerCase();
    }

    void writeFragmentsGrammar(String pfx, FileObject importsDir, String sep, List<String> fragmentRules, Charset charset, List<FileObject> created) throws IOException {
        String fragmentsName = fragmentsName(pfx);
        FileObject baseGrammar = importsDir.createData(fragmentsName, "g4");
        writeOneGrammar("lexer", baseGrammar, fragmentsName, sep, Arrays.asList(fragmentRules), charset);
        created.add(baseGrammar);
    }

    void writeOneGrammar(String grammarTypePrefix, FileObject grammarFile, String grammarName, String lineSeparator, List<List<String>> lineSets, Charset charset) throws IOException {
        writeOneGrammar(grammarTypePrefix, grammarFile, grammarName, lineSeparator, lineSets, charset, null);
    }

    void writeOneGrammar(String grammarTypePrefix, FileObject grammarFile, String grammarName, String lineSeparator, List<List<String>> lineSets, Charset charset, Consumer<StringBuilder> afterFirstline) throws IOException {
        try (final OutputStream out = grammarFile.getOutputStream()) {
            StringBuilder grammarText = new StringBuilder(256);
            if (!grammarTypePrefix.isEmpty()) {
                grammarText.append(grammarTypePrefix).append(" grammar ");
            } else {
                grammarText.append("grammar ");
            }

            grammarText.append(grammarName).append(';').append(lineSeparator);
            if (afterFirstline != null) {
                afterFirstline.accept(grammarText);
            }
            appendLineGroups(grammarText, lineSeparator, lineSets);
            if (!grammarText.toString().endsWith(lineSeparator)) {
                grammarText.append(lineSeparator);
            }
            // Ensure a trailing newline
            if (!grammarText.toString().endsWith(lineSeparator + lineSeparator)) {
                grammarText.append(lineSeparator);
            }
            if (grammarText.charAt(grammarText.length() - 1) != '\n') {
                grammarText.append(lineSeparator);
            }
            out.write(grammarText.toString().getBytes(charset));
        }
    }

    private static StringBuilder appendLineGroups(StringBuilder to, String sep, List<List<String>> lines) {
        to.append(sep);
        for (Iterator<List<String>> it = lines.iterator(); it.hasNext();) {
            List<String> group = it.next();
            appendLines(group, to, sep);
            if (it.hasNext()) {
                to.append(sep);
            }
        }
        return to;
    }

    private static StringBuilder appendLines(List<String> lines, StringBuilder to, String sep) {
        for (String line : lines) {
            to.append(line).append(sep);
        }
        return to;
    }

    public static String grammarNamePrefix(Project project) {
        ProjectInformation info = ProjectUtils.getInformation(project);
        String result;
        if (info == null) {
            result = project.getProjectDirectory().getName();
        } else {
            result = info.getName();
            // A crude workaround - the "name' for maven projects includes .jar
            // and the version, so we otherwise wind up with grammar names like
            // Testmavenproject1jar1Dot0HyphenMinusSnapshotLexer.g4
            if (result.endsWith("-SNAPSHOT")) {
                result = project.getProjectDirectory().getName();
            }
            String dn = info.getDisplayName();
            if (dn != null && dn.length() < result.length()) {
                result = Strings.dashesToCamelCase(dn.replace("\\s+", "-").replace('.', '-'));
            }
        }
        if (Strings.isBlank(result)) {
            result = "Something";
        }
        result = Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(result);
        if (!Character.isUpperCase(result.charAt(0))) {
            result = Strings.capitalize(result);
        }
        // If the project name ends with "lexer" or "parser" or "language"
        // remove it so we don't generate FooParserParser
        result = exciseSuffix(result, "parser");
        result = exciseSuffix(result, "lexer");
        result = exciseSuffix(result, "language");
        result = exciseSuffix(result, "grammar");
        return result;
    }

    static String exciseSuffix(String result, String suffix) {
        if (result.toLowerCase().endsWith(suffix) && result.length() > suffix.length()) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }

}
