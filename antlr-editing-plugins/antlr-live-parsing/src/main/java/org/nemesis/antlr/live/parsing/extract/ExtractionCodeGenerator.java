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
package org.nemesis.antlr.live.parsing.extract;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;

/**
 * Takes ParserExtractor.template (symlink to ParserExtractor.java in this
 * package), and munges it to work against a particular package, grammar name
 * and style of grammar.
 *
 * @author Tim Boudreau
 */
public class ExtractionCodeGenerator {

    public static final String PARSER_EXTRACTOR = "ParserExtractor";
    public static final String PARSER_EXTRACTOR_SOURCE_FILE = PARSER_EXTRACTOR + ".java";
    public static final String PARSER_EXTRACTOR_TEMPLATE = PARSER_EXTRACTOR + ".template";

    private static final Logger LOG = Logger.getLogger(ExtractionCodeGenerator.class.getName());

    /**
     * Save to disk.
     *
     * @param originalGrammarFile The original grammar file it should be
     * relative to
     * @param pkg The package name in the destination location
     * @param prefix The class name prefix, typically the name of the grammar
     * but may differ in character case
     * @param dir The destination directory
     * @param lexerOnly If true, omit any code relating to dealing with a parser
     * java class which will not exist for lexer grammars
     * @return A path to the created file
     * @throws IOException If reading the template fails or writing fails
     */
    public static Path saveExtractorSourceTo(Path originalGrammarFile, String pkg, String prefix, Path dir, boolean lexerOnly, String lexerGrammarName) throws IOException {
        if (Files.exists(dir.getParent().resolve("CompileResult.java"))) {
            throw new AssertionError("Writing extractor over itself: " + dir);
        }
        Path dest = dir.resolve(PARSER_EXTRACTOR_SOURCE_FILE);
        if (!Files.exists(dest)) {
            Path lexerFile = dir.resolve(prefix + "Lexer.java");
            String lexerSuffix = "Lexer";
            if (!Files.exists(lexerFile)) {
                // Fragment only files get compiled to grammarname.java, for
                // whatever reason
                lexerFile = dir.resolve(prefix + ".java");
                lexerSuffix = "lexer";
            }
            LOG.log(Level.FINER, "Save extractor to {0} wih lexer fn {1}, lexer only? {2}", new Object[]{dest, lexerFile, lexerOnly});
            String javaCode = getLexerExtractorSourceCode(originalGrammarFile, pkg, prefix, lexerOnly, lexerSuffix, lexerGrammarName);
            Files.write(dest, javaCode.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        }
        return dest;
    }

    public static ExtractionCodeGenerationResult saveExtractorSourceCode(Path realSourceFile, JFS fs, String pkg, String grammarName, String lexerGrammarName) throws IOException {
        ExtractionCodeGenerationResult result = new ExtractionCodeGenerationResult(grammarName, pkg);
        // We presume antlr generation has been done here
//        Iterable<JavaFileObject> generated = fs.list(SOURCE_OUTPUT, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false);
//        generated = CollectionUtils.concatenate(generated, fs.list(SOURCE_PATH, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false));
        Iterable<JavaFileObject> generated = fs.list(SOURCE_OUTPUT, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false);
        generated = CollectionUtils.concatenate(generated, fs.list(SOURCE_PATH, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false));

        FileObject candidateLexer = null;
        FileObject foundParser = null;
        FileObject foundLexer = null;
        String lexerSuffix = "Lexer";
        String realPrefix = grammarName; // case may not match, so prefer values derived from actual file names
        // A bit of black magic here:
        // 1.  Though not documented, case difference between grammar name and grammar file name
        //     are tolerated by Antlr.  We have the grammar name from source generation, but we do not
        //     actually know the exact file name.  So we iterate all generated files and try to find the
        //     parser and lexer
        // 2.  If you run Antlr against a file with fragments only - a partial lexer grammer,
        //     you get a file named "grammarfile.java" which *is* a lexer but does not have Lexer
        //     as its suffix (or maybe this has to do with grammar file name case?).  So if we didn't
        //     find fooLexer.java or FooLexer.java, we also look for foo.java as a fallback
        // 3.  Depending on what all we're generating, we may have generated parsers and lexers for
        //     not just the target grammar, but things it imported, so we need to be sure we're getting
        //     the thing we're really after.
        // 4.  If we compiled a lexer grammar, there will not *be* a parser source file, and we need to
        //     know that to remove all the lines from ParserExtractor.java which are suffixed with //parser
        //     so it will be compilable.
        // 5.  If your grammar and .g4 file are named FooParser, you will not get a parser class named
        //     FooParserParser as you might expect, but just FooParser.
        //
        //     Are we having fun yet?
        if (grammarName != null) {
            foundParser = fs.get(SOURCE_PATH, UnixPath.get(grammarName + ".java"));
            if (foundLexer == null) {
                foundParser = fs.get(SOURCE_OUTPUT, UnixPath.get(grammarName + ".java"));
            }

        }
        if (lexerGrammarName != null) {
            foundLexer = fs.get(SOURCE_PATH, UnixPath.get(lexerGrammarName + ".java"));
            if (foundLexer == null) {
                foundLexer = fs.get(SOURCE_PATH, UnixPath.get(lexerGrammarName + ".java"));
            }
        }
        StringBuilder genInfo = new StringBuilder("\n/*\n");
        genInfo.append("Looking for '").append(grammarName).append("'\n\n");
        genInfo.append("Passed lexer grammar name '").append(lexerGrammarName).append("'\n");
        String pfx = grammarName.endsWith("Parser") ? grammarName.substring(0, grammarName.length() - 6) : grammarName;
        genInfo.append("Expected prefix for lexer: '").append(pfx).append("'\n");
        for (FileObject fo : generated) {
            if (foundParser != null && foundLexer != null) {
                genInfo.append("  Have lexer ").append(foundLexer).append(" and parser ").append(foundParser).append(" - done\n");
                break;
            }
            String fn = Paths.get(fo.getName()).getFileName().toString();
            if (fn.endsWith(".java")) {
                genInfo.append("examine ").append(fo.getName()).append(" as name ").append(fn).append('\n');
                String nm = fn.substring(0, fn.length() - 5);

                if (nm.toLowerCase().endsWith("lexer") || nm.toLowerCase().endsWith("parser") || grammarName.equalsIgnoreCase(nm)) {
                    genInfo.append("  ends with lexer or parser - marking examined\n");
                    result.examined(nm);
                }
                // Look for GrammarNameParser.java - if it is not present,
                // we probably have a lexer grammar
                if (foundParser == null && nm.endsWith("Parser") && nm.length() > 6) {
                    String prefix = nm.substring(0, nm.length() - 6);
                    genInfo.append("  is a parser, prefix ").append(prefix).append('\n');
                    if (prefix.equalsIgnoreCase(grammarName) || prefix.equalsIgnoreCase(pfx) || prefix.equals(nm)) {
                        realPrefix = prefix;
                        foundParser = fo;
                        genInfo.append("  bingo, got the parser: ").append(fo.getName()).append('\n');
                        continue;
                    }
                }
                if (foundLexer == null && lexerGrammarName != null && nm.equals(lexerGrammarName)) {
                    genInfo.append("  lexerGrammarName ").append(lexerGrammarName).append(" matches ").append(nm).append(" - it is the lexer\n");
                    foundLexer = fo;
                    if (foundParser != null) {
                        break;
                    }
                }
                // Look for a lexer class; for *some* grammars, we wind
                // up with just GrammarName.java, so we check for that too
                if (foundLexer == null && (nm.endsWith("Lexer") || nm.endsWith("lexer"))) {
                    String prefix = nm.substring(0, nm.length() - 5);
                    genInfo.append("  looks like a lexer - ").append(prefix).append(" matches grammarName ")
                            .append(grammarName).append("? ").append(prefix.equalsIgnoreCase(grammarName))
                            .append(" matches computed prefix '").append(pfx).append("'?")
                            .append(pfx.equals(prefix))
                            .append("\n");
                    if (prefix.equalsIgnoreCase(grammarName) || pfx.equalsIgnoreCase(prefix)) {
                        realPrefix = prefix;
                        lexerSuffix = nm.substring(prefix.length());
                        foundLexer = fo;
                        genInfo.append("Determining ").append(fo.getName())
                                .append(" to be the lexer; lexerSuffix '").append(lexerSuffix).append("'\n");
                        continue;
                    }
                }
                if (foundParser != null && foundLexer != null) {
                    genInfo.append("  Have lexer ").append(foundLexer).append(" and parser ").append(foundParser).append(" - done\n");
                    break;
                }
                if (grammarName.equalsIgnoreCase(nm) && !nm.endsWith("Parser")) {
                    genInfo.append("  Partial name match '").append(nm).append("' marking ").append(fo.getName()).append(" as fallback lexer candidate\n");
                    candidateLexer = fo;
                }
            }
        }
        // If we didn't find a lexer but did find a candidate java file using
        // just the grammar name, assume that's our lexer
        if (foundLexer == null && candidateLexer != null) {
            lexerSuffix = "";
        }
        if (foundLexer == null && candidateLexer != null) {
            genInfo.append("Using candidate partial-name-match lexer as the lexer ").append(candidateLexer.getName());
            foundLexer = candidateLexer;
        }
        // If we found nothing, we're not going to generate anything compilable,
        // so bail here so we don't leave behind either a class missing parts
        // needed when generation is fixed, or an uncompilable turd
        if (foundLexer == null && candidateLexer == null && foundParser == null) {
            LOG.log(Level.WARNING, "Did not find a parser or a lexer: {0}", genInfo);
            return result;
        }

        genInfo.append(" \n*/\n");
        String code = getLexerExtractorSourceCode(realSourceFile, pkg, realPrefix, foundParser == null, lexerSuffix,
                lexerGrammarName);

        UnixPath extractorPath = UnixPath.get(pkg.replace('.', '/'), PARSER_EXTRACTOR_SOURCE_FILE);
        Debug.message("Generated source code " + realSourceFile + " in " + pkg, () -> code + genInfo);

        JFSFileObject fo = fs.get(SOURCE_PATH, extractorPath);
        if (fo != null && Strings.charSequencesEqual(fo.getCharContent(true), code, false)) {
            // Avoid touching the file if we would regenerate the same content - it will
            // cause the test whether files have been modified and the parser should be
            // regenerated to always return true, making every keystroke lead to a furious
            // amount of work
            return result.setResult(fo);
        }
        return result.setResult(fs.create(extractorPath, SOURCE_PATH, code));
    }

    public static String getLexerExtractorSourceCode(Path originalGrammarFile, String pkg,
            String classNamePrefix, boolean lexerOnly, String lexerSuffix, String passedLexerName) throws IOException {
        String result = Streams.readResourceAsUTF8(ParserExtractor.class, PARSER_EXTRACTOR_TEMPLATE);
        assert result != null : PARSER_EXTRACTOR_TEMPLATE + " not adjacent to " + ExtractionCodeGenerator.class;

        String lexerFinalName = passedLexerName == null ? classNamePrefix + lexerSuffix
                : passedLexerName;

        String extractorPackage = AntlrProxies.class.getPackage().getName();
        // Change the package statement
        result = result.replace("package org.nemesis.antlr.live.parsing.extract;",
                "package " + pkg + ";");
        // Replace the import with an import of the actual lexer class (even though we're in
        // the same package ordinarily - this could be generated into wherever and an import
        // from the same package is harmless)
        result = result.replace("import ignoreme.placeholder.DummyLanguageLexer;",
                "\nimport " + pkg + "." + lexerFinalName + ";" + "import " + extractorPackage + ".AntlrProxies;\n");

        // Same for the parser, if lexerOnly is false
        result = result.replace("import ignoreme.placeholder.DummyLanguageParser;",
                lexerOnly ? "" : "\nimport " + pkg + "." + classNamePrefix + "Parser;//parser\n");
        // Replace class name occurrences in source
        result = result.replaceAll("DummyLanguageLexer", lexerFinalName);
        result = result.replaceAll("DummyLanguageParser", classNamePrefix + "Parser");
        // Replace the string constant that provides the name for the ParseTreeProxy
        result = result.replaceAll("\"DummyLanguage\"", '"' + classNamePrefix + '"');
        // Replace the path constant, so we have that too
        result = result.replaceAll("/replace/with/path", originalGrammarFile.toString());
        // If a lexer grammar, remove parser related stuff as it would be uncompilable
        // with no parser
        if (lexerOnly) {
            StringBuilder sb = new StringBuilder(result.length());
            for (String line : result.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.endsWith("//parser") || line.startsWith("//")
                        || line.startsWith("* ") || line.startsWith("/*") || "*".equals(line) || "*/".equals(line)) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            result = sb.toString();
        }
        result = result.replaceAll(" //parser", "").replaceAll("\n\n", "\n");
        return result;
    }
}
